package com.reelguard.app.service
import com.reelguard.app.R

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.reelguard.app.manager.OverlayManager
import com.reelguard.app.manager.QuotaManager

class ReelBlockerAccessibilityService : AccessibilityService() {

    private lateinit var quotaManager: QuotaManager
    private lateinit var overlayManager: OverlayManager
    private val handler = Handler(Looper.getMainLooper())
    private var autoExitRunnable: Runnable? = null

    // Suivi de session
    private var currentPackage: String = ""
    private var isInReelsSection = false
    private var currentReelStartTime = 0L

    // Flush périodique du temps (max toutes les 3s pour éviter trop d'écriture prefs)
    private var lastFlushTime = 0L
    private val FLUSH_DEBOUNCE_MS = 3000L

    // Timer de session : flush toutes les 5s même sans swipe, détecte la sortie via home button
    private var sessionTimerRunnable: Runnable? = null
    private val SESSION_TIMER_MS = 5000L

    private var youtubeExitPending = false

    companion object {
        val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.pinterest",
            "com.twitter.android",
            "com.X.android"
        )

        private val IGNORE_PACKAGES = setOf(
            "android", "com.android.systemui",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.inputmethod"
        )

        private val MESSAGING_KEYWORDS = mapOf(
            "com.instagram.android" to listOf("direct", "thread", "inbox"),
            "com.facebook.katana" to listOf("messenger", "thread", "inbox"),
            "com.snapchat.android" to listOf("chat", "conversation")
        )

        // Mots-clés des onglets Reels/Shorts (content description du bouton — résistant à l'obfuscation)
        private val REELS_TAB_KEYWORDS = mapOf(
            "com.google.android.youtube" to listOf("shorts"),
            "com.facebook.katana"        to listOf("reels", "watch", "video", "vidéo", "reel"),
            "com.snapchat.android"       to listOf("spotlight"),
            "com.instagram.android"      to listOf("reels", "reel")
        )

        private val YOUTUBE_NON_SHORTS_TABS = listOf("home", "accueil", "explore",
            "subscriptions", "abonnements", "library", "bibliothèque", "you")

        // IDs de vues : fallback si les autres méthodes échouent.
        // NE PAS inclure shorts_shelf_cell / shorts_video_cell : ce sont les cartes
        // de la shelf "Shorts" sur l'accueil YouTube (faux-positif garanti).
        // Pour Facebook : noms obfusqués, on inclut tous les IDs potentiels connus.
        private val REEL_VIEW_IDS = listOf(
            // Instagram
            "clips_viewer_container", "reel_viewer", "clips_tab", "reel_player_page",
            // YouTube
            "shorts_container", "shorts_player",
            // Facebook (plusieurs variantes selon la version)
            "reels_viewer", "reels_view", "fb_reels", "reels_player", "reel_player",
            "reels_root", "reels_feed", "fb_reel", "reels_item",
            // Snapchat
            "spotlight_video", "spotlight_container",
            // Pinterest
            "video_pin", "story_pin"
        )
    }

    private val backReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlayManager.hideOverlay()
            cancelAutoExit()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        quotaManager = QuotaManager.getInstance(applicationContext)
        overlayManager = OverlayManager(applicationContext)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            packageNames = TARGET_PACKAGES.toTypedArray()
            notificationTimeout = 100
        }
        serviceInfo = info

        val filter = IntentFilter("com.reelguard.action.PRESS_BACK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(backReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(backReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val event = event ?: return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName || pkg in IGNORE_PACKAGES || pkg.startsWith("com.android.")) return
        if (pkg !in TARGET_PACKAGES) { onLeaveTargetApp(); return }
        if (!quotaManager.isBlockingEnabledForApp(pkg)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED   -> handleWindowChange(event, pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED          -> handleScrollOrContent(event, pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleScrollOrContent(event, pkg)
            AccessibilityEvent.TYPE_VIEW_CLICKED           -> handleClick(event, pkg)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CLIC SUR UN ONGLET DE NAVIGATION
    // ────────────────────────────────────────────────────────────────────────────

    private fun handleClick(event: AccessibilityEvent, pkg: String) {
        val text = (event.text?.joinToString(" ") ?: "").lowercase()
        val desc = event.contentDescription?.toString()?.lowercase() ?: ""
        val combined = "$text $desc"

        val reelsKeywords = REELS_TAB_KEYWORDS[pkg] ?: return
        if (reelsKeywords.any { combined.contains(it) }) {
            if (!isInReelsSection) enterReelsSection(pkg)
            youtubeExitPending = false
            return
        }

        if (pkg == "com.google.android.youtube" && isInReelsSection) {
            if (YOUTUBE_NON_SHORTS_TABS.any { combined.contains(it) }) exitReelsSection()
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CHANGEMENT DE FENÊTRE / ACTIVITÉ
    // ────────────────────────────────────────────────────────────────────────────

    private fun handleWindowChange(event: AccessibilityEvent, pkg: String) {
        if (pkg != currentPackage) {
            flushCurrentReel()
            currentPackage = pkg
        }

        if (quotaManager.isMessagingExceptionEnabled() && isMessagingContext(event, pkg)) {
            exitReelsSection(); return
        }

        val inReels = isReelsContext(event, pkg)

        if (inReels && !isInReelsSection) {
            enterReelsSection(pkg)
        } else if (!inReels && isInReelsSection) {
            if (pkg == "com.instagram.android") {
                exitReelsSection(); return
            }
        }

        if (isInReelsSection) {
            // YouTube/Facebook : flush du temps à chaque chargement de vidéo
            // (Instagram/TikTok/Snapchat : géré via TYPE_VIEW_SCROLLED + session timer)
            if (pkg in setOf("com.google.android.youtube", "com.facebook.katana")) {
                softFlushTime()
            }
            checkAndBlock(pkg)  // toujours vérifier le quota au chargement d'une vidéo
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // SCROLL / CHANGEMENT DE CONTENU
    // ────────────────────────────────────────────────────────────────────────────

    private fun handleScrollOrContent(event: AccessibilityEvent, pkg: String) {
        if (!isInReelsSection) {
            if (!tryEnterViaScroll(event, pkg)) return
            return
        }

        // WINDOW_CONTENT_CHANGED : uniquement pour la détection d'entrée (bloc ci-dessus)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        if (quotaManager.isMessagingExceptionEnabled() && isMessagingContext(event, pkg)) return

        // softFlushTime() débounce à 3s : pas de double-comptage même si handleWindowChange
        // appelle déjà softFlushTime() pour YouTube/Facebook.
        if (softFlushTime()) checkAndBlock(pkg)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DÉTECTION TARDIVE VIA SCROLL
    // ────────────────────────────────────────────────────────────────────────────

    private fun tryEnterViaScroll(event: AccessibilityEvent, pkg: String): Boolean {
        if (pkg in setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")) {
            enterReelsSection(pkg); return true
        }

        // Facebook : noms de classe ET IDs de vues sont obfusqués → les vérifications
        // string ne matchent jamais. On va directement à la vérification de l'arbre.
        if (pkg == "com.facebook.katana") {
            val root = rootInActiveWindow ?: return false
            if (nodeContainsViewIds(root, REEL_VIEW_IDS)) { enterReelsSection(pkg); return true }
            return false
        }

        val scrollClass = event.className?.toString()?.lowercase() ?: ""
        val sourceId    = event.source?.viewIdResourceName?.lowercase() ?: ""

        val hit = when (pkg) {
            "com.google.android.youtube" ->
                scrollClass.contains("short") || sourceId.contains("short")
            "com.instagram.android" ->
                scrollClass.contains("reel") || scrollClass.contains("clip") ||
                sourceId.contains("reel") || sourceId.contains("clip")
            "com.snapchat.android" ->
                scrollClass.contains("spotlight") || sourceId.contains("spotlight")
            else -> false
        }

        if (hit) { enterReelsSection(pkg); return true }

        val root = rootInActiveWindow ?: return false
        if (nodeContainsViewIds(root, REEL_VIEW_IDS)) { enterReelsSection(pkg); return true }
        return false
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DÉTECTION DU CONTEXTE (pour TYPE_WINDOW_STATE_CHANGED)
    // ────────────────────────────────────────────────────────────────────────────

    private fun isReelsContext(event: AccessibilityEvent, pkg: String): Boolean {
        val className   = event.className?.toString()?.lowercase() ?: ""
        val eventText   = (event.text?.joinToString(" ") ?: "").lowercase()
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""

        val hit = when (pkg) {
            "com.instagram.android" ->
                className.contains("reel") || className.contains("clip")
            "com.google.android.youtube" ->
                // eventText retiré : risque de faux-positif si le titre de la fenêtre inclut
                // du contenu visible (section "Shorts" dans le feed de l'accueil).
                className.contains("short") || contentDesc.contains("short")
            "com.facebook.katana" ->
                // TYPE_WINDOW_STATE_CHANGED se déclenche sur les changements d'Activity/Fragment,
                // PAS sur les mises à jour de contenu du feed. eventText contient le titre
                // de la section (ex: "Reels"), sans risque de faux-positif depuis le feed.
                // className obfusqué par Facebook → peu fiable, gardé en bonus.
                className.contains("reel") || eventText.contains("reel") || contentDesc.contains("reel")
            "com.snapchat.android" ->
                className.contains("spotlight") || eventText.contains("spotlight")
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> true
            else -> false
        }
        if (hit) return true

        val root = rootInActiveWindow ?: return false
        return nodeContainsViewIds(root, REEL_VIEW_IDS)
    }

    private fun isMessagingContext(event: AccessibilityEvent, pkg: String): Boolean {
        val keywords = MESSAGING_KEYWORDS[pkg] ?: return false
        val className = event.className?.toString()?.lowercase() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""
        return keywords.any { className.contains(it) || text.contains(it) }
    }

    private fun nodeContainsViewIds(node: AccessibilityNodeInfo?, ids: List<String>, depth: Int = 0): Boolean {
        if (node == null || depth > 6) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (ids.any { viewId.contains(it) }) return true
        for (i in 0 until node.childCount) {
            if (nodeContainsViewIds(node.getChild(i), ids, depth + 1)) return true
        }
        return false
    }

    // ────────────────────────────────────────────────────────────────────────────
    // GESTION DE SESSION
    // ────────────────────────────────────────────────────────────────────────────

    private fun enterReelsSection(pkg: String) {
        isInReelsSection = true
        currentReelStartTime = System.currentTimeMillis()
        lastFlushTime = 0L
        youtubeExitPending = false
        startSessionTimer()
        if (quotaManager.isFocusModeActive()) checkAndBlock(pkg)
    }

    /**
     * Flush périodique du temps accumulé dans la section Reels.
     * Debounce de 3s pour limiter les écritures dans SharedPreferences.
     * Retourne true si un flush a eu lieu (pour conditionner checkAndBlock).
     */
    private fun softFlushTime(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFlushTime < FLUSH_DEBOUNCE_MS) return false
        lastFlushTime = now
        val elapsed = if (currentReelStartTime > 0L) now - currentReelStartTime else 0L
        if (elapsed > 0L) {
            quotaManager.recordReelTime(elapsed)
            currentReelStartTime = now  // repart de zéro pour éviter le double-comptage
        }
        return true
    }

    /**
     * Timer qui tourne toutes les 5s pendant la session Reels.
     * Deux rôles :
     * 1. Flush périodique du temps (regarder sans swiper → temps quand même compté)
     * 2. Détection du bouton home : rootInActiveWindow change de package → sortie propre
     *    null = transition vidéo YouTube en cours → on ne sort PAS (évite les faux-positifs)
     */
    private fun startSessionTimer() {
        stopSessionTimer()
        val runnable = object : Runnable {
            override fun run() {
                if (!isInReelsSection) return
                val activePkg = rootInActiveWindow?.packageName?.toString()
                // Ne sortir QUE sur un package étranger CONFIRMÉ.
                // null = arbre en cours de chargement (transition YouTube, etc.) → rester en session.
                if (activePkg != null && activePkg !in TARGET_PACKAGES) {
                    exitReelsSection()
                    return
                }
                softFlushTime()
                checkAndBlock(activePkg ?: currentPackage)
                handler.postDelayed(this, SESSION_TIMER_MS)
            }
        }
        sessionTimerRunnable = runnable
        handler.postDelayed(runnable, SESSION_TIMER_MS)
    }

    private fun stopSessionTimer() {
        sessionTimerRunnable?.let { handler.removeCallbacks(it) }
        sessionTimerRunnable = null
    }

    private fun exitReelsSection() {
        flushCurrentReel()
        isInReelsSection = false
        stopSessionTimer()
        overlayManager.hideOverlay()
    }

    private fun flushCurrentReel() {
        if (isInReelsSection && currentReelStartTime > 0) {
            val duration = System.currentTimeMillis() - currentReelStartTime
            if (duration > 500) quotaManager.recordReelTime(duration)
            currentReelStartTime = 0L
        }
    }

    private fun onLeaveTargetApp() {
        exitReelsSection()
        cancelAutoExit()
        currentPackage = ""
    }

    // ────────────────────────────────────────────────────────────────────────────
    // BLOCAGE
    // ────────────────────────────────────────────────────────────────────────────

    private fun checkAndBlock(pkg: String) {
        val status = quotaManager.checkAndConsumeQuota(pkg)
        if (!status.isExceeded) { overlayManager.hideOverlay(); return }

        when {
            status.focusModeActive -> {
                overlayManager.showBlockOverlay(status)
                scheduleAutoExit(2000)
            }
            pkg in setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill") ->
                showToastAndExit(getString(R.string.toast_tiktok_blocked))
            pkg == "com.instagram.android" ->
                showToastAndExit(getString(R.string.toast_instagram_blocked))
            pkg == "com.google.android.youtube" ->
                showToastAndExit(getString(R.string.toast_youtube_blocked))
            pkg == "com.facebook.katana" ->
                showToastAndExit(getString(R.string.toast_facebook_blocked))
            pkg == "com.snapchat.android" ->
                showToastAndExit(getString(R.string.toast_snapchat_blocked))
            else -> { overlayManager.showBlockOverlay(status); scheduleAutoExit(2000) }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // UTILITAIRES SYSTÈME
    // ────────────────────────────────────────────────────────────────────────────

    private fun showToastAndExit(message: String) {
        // Terminer la session AVANT la navigation : arrête le timer et vide l'état.
        // Sans cela, le timer continuerait à appeler checkAndBlock() toutes les 5s
        // même après que l'utilisateur ait quitté l'app → boucle de blocage.
        exitReelsSection()
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        // HOME (et non BACK) pour garantir une sortie de l'app cible,
        // même si GLOBAL_ACTION_BACK resterait dans la section Reels.
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun scheduleAutoExit(delayMs: Long) {
        cancelAutoExit()
        val runnable = Runnable { overlayManager.hideOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
        autoExitRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelAutoExit() {
        autoExitRunnable?.let { handler.removeCallbacks(it) }
        autoExitRunnable = null
    }

    override fun onInterrupt() { exitReelsSection(); cancelAutoExit(); stopSessionTimer() }

    override fun onDestroy() {
        super.onDestroy()
        exitReelsSection()
        cancelAutoExit()
        stopSessionTimer()
        try { unregisterReceiver(backReceiver) } catch (_: Exception) {}
    }
}
