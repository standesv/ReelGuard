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

    // Cooldown post-blocage : empêche enterReelsSection pendant 3s après showToastAndExit.
    // Sans cela, les events AccessibilityService bufferisés qui arrivent après GLOBAL_ACTION_HOME
    // peuvent rappeler enterReelsSection et relancer la boucle de blocage.
    private var blockCooldownUntil = 0L

    // Instagram : poller arbre de vues (toutes les 1,5s) + détection par clic (réponse immédiate).
    private var instagramPollerRunnable: Runnable? = null
    private val INSTAGRAM_POLL_MS = 1500L
    private val INSTAGRAM_REEL_IDS = listOf(
        // Conteneurs du lecteur Reels plein-écran uniquement.
        // EXCLU "clips_tab" : c'est le bouton de l'onglet Reels dans la barre de navigation —
        // il est présent dans l'arbre d'accessibilité sur TOUS les écrans Instagram (feed, DM,
        // Explore, profil…) → causerait un faux positif systématique sur tout Instagram.
        // EXCLU "reel_player_page" : peut apparaître pour les Reels intégrés dans le feed.
        "clips_viewer_container", "reel_viewer"
    )

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

        // Mots-clés des onglets Reels/Shorts (content description du bouton nav — résistant à l'obfuscation).
        // Facebook : "reels" (EN) + "vidéos" (FR) — le label de l'onglet Reels en français est "Vidéos".
        // On évite "vidéo" (singulier) et "watch" qui matchent les boutons de posts dans le feed.
        private val REELS_TAB_KEYWORDS = mapOf(
            "com.google.android.youtube" to listOf("shorts"),
            "com.facebook.katana"        to listOf("reels", "vidéos"),
            "com.snapchat.android"       to listOf("spotlight")
        )

        private val YOUTUBE_NON_SHORTS_TABS = listOf("home", "accueil", "explore",
            "subscriptions", "abonnements", "library", "bibliothèque", "you")

        // IDs de vues : fallback si les autres méthodes échouent.
        // NE PAS inclure shorts_shelf_cell / shorts_video_cell : ce sont les cartes
        // de la shelf "Shorts" sur l'accueil YouTube (faux-positif garanti).
        // Pour Facebook : noms obfusqués, on inclut tous les IDs potentiels connus.
        private val REEL_VIEW_IDS = listOf(
            // Instagram (utilisé uniquement pour le tree-scan Facebook — les IDs Instagram
            // sont dans INSTAGRAM_REEL_IDS ; clips_tab exclu car présent sur tous les écrans)
            "clips_viewer_container", "reel_viewer", "reel_player_page",
            // YouTube
            "shorts_container", "shorts_player",
            // Facebook (plusieurs variantes selon la version — obfusqués en pratique)
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

        // Instagram : on démarre le poller (détection passive toutes les 1,5s via arbre de vues)
        // ET on conserve la détection par clic sur l'onglet Reels (réponse immédiate).
        // On ignore scroll/window-change car les classes "reel"/"clip" apparaissent aussi sur le feed.
        if (pkg == "com.instagram.android") {
            startInstagramPoller()
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                handleClick(event, pkg)
            }
            return
        }

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
        val combined = "$text $desc".trim()

        // Instagram : correspondance stricte sur l'onglet Reels de la barre de navigation.
        // Le label de l'onglet nav est court et précis ("reels" ou "reels, onglet 3").
        // Un clic sur un post/story mentionnant "reel" a une description bien plus longue.
        // On exclut ainsi les clics sur le contenu du feed qui mentionnent les Reels.
        if (pkg == "com.instagram.android") {
            if (combined.startsWith("reels") && combined.length <= 25 && !isInReelsSection) {
                enterReelsSection(pkg)
            }
            return
        }

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
        }
        // Pas de sortie sur window change pour Instagram : trop de changements d'activité
        // légitimes (commentaires, profil créateur…) provoqueraient des sorties prématurées.
        // La sortie est gérée par le timer de session via rootInActiveWindow.

        // checkAndBlock uniquement pour YouTube/Facebook : chaque video = window change.
        // Pour Instagram/Snapchat/TikTok : le timer de session (5s) suffit.
        // Appeler checkAndBlock sur TOUT window change Instagram causait des blocages
        // sur le feed normal (window changes fréquents hors section Reels).
        if (isInReelsSection && pkg in setOf("com.google.android.youtube", "com.facebook.katana")) {
            softFlushTime()
            checkAndBlock(pkg)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // SCROLL / CHANGEMENT DE CONTENU
    // ────────────────────────────────────────────────────────────────────────────

    private fun handleScrollOrContent(event: AccessibilityEvent, pkg: String) {
        // TYPE_WINDOW_CONTENT_CHANGED ne doit PAS servir à détecter l'entrée en section Reels.
        // Instagram a des IDs de vues contenant "reel" dans le feed normal (reel_ring_container,
        // reel_tray pour les stories). Un changement de contenu du feed déclencherait un faux positif.
        // On l'ignore totalement : seul TYPE_VIEW_SCROLLED est utilisé pour l'entrée.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        if (!isInReelsSection) {
            if (!tryEnterViaScroll(event, pkg)) return
            return
        }

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

        // Facebook : IDs et classes obfusqués.
        // Scan par IDs (peu fiable) + scan par texte visible "reels"/"vidéos".
        // Le scan texte sur scroll est acceptable car si "reels" est visible à l'écran
        // pendant un swipe, l'utilisateur est probablement dans la section Reels.
        if (pkg == "com.facebook.katana") {
            val root = rootInActiveWindow ?: return false
            if (nodeContainsViewIds(root, REEL_VIEW_IDS)) { enterReelsSection(pkg); return true }
            if (nodeContainsText(root, "reels") || nodeContainsText(root, "vidéos")) {
                enterReelsSection(pkg); return true
            }
            return false
        }

        val scrollClass = event.className?.toString()?.lowercase() ?: ""
        val sourceId    = event.source?.viewIdResourceName?.lowercase() ?: ""

        val hit = when (pkg) {
            "com.google.android.youtube" ->
                scrollClass.contains("short") || sourceId.contains("short")
            "com.instagram.android" ->
                // Détection scroll désactivée pour Instagram : les classes "reel" et "clip"
                // apparaissent aussi sur le feed normal (ReelTray, ClipVideoView inline…).
                // Entrée uniquement via handleClick (tap onglet Reels, label court et exact).
                false
            "com.snapchat.android" ->
                scrollClass.contains("spotlight") || sourceId.contains("spotlight")
            else -> false
        }

        if (hit) { enterReelsSection(pkg); return true }

        // PAS de fallback nodeContainsViewIds générique ici : le feed Instagram affiche
        // des previews de Reels avec des IDs "reel_*" → faux positifs sur le feed normal.
        // Le tree-scan reste réservé à Facebook (classe obfusquée) ci-dessus.
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
                // Désactivé : Instagram charge ClipVideoView, ReelTrayFragment etc. même sur
                // le feed normal → className "reel"/"clip" déclenchait enterReelsSection()
                // sur l'accueil. Détection via handleClick (onglet nav) + tryEnterViaScroll.
                false
            "com.google.android.youtube" ->
                // eventText retiré : risque de faux-positif si le titre de la fenêtre inclut
                // du contenu visible (section "Shorts" dans le feed de l'accueil).
                className.contains("short") || contentDesc.contains("short")
            "com.facebook.katana" ->
                // className obfusqué → peu fiable. On check eventText + contentDesc.
                // "vidéos" ajouté : en français, l'onglet Reels est parfois nommé "Vidéos".
                className.contains("reel") ||
                eventText.contains("reel") || eventText.contains("vidéos") ||
                contentDesc.contains("reel") || contentDesc.contains("vidéos")
            "com.snapchat.android" ->
                className.contains("spotlight") || eventText.contains("spotlight")
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> true
            else -> false
        }
        if (hit) return true

        // Tree-scan uniquement pour Facebook : IDs/classes obfusqués, impossible autrement.
        // 1. Scan par IDs (peu fiable — obfusqués, gardé en dernier recours)
        // 2. Scan par texte visible (non-obfusqué) : cherche "reels" ou "vidéos" dans les nœuds
        //    de l'arbre. Appelé uniquement sur TYPE_WINDOW_STATE_CHANGED pour éviter les
        //    faux-positifs du feed (posts mentionnant "reels" dans leur caption).
        if (pkg == "com.facebook.katana") {
            val root = rootInActiveWindow ?: return false
            if (nodeContainsViewIds(root, REEL_VIEW_IDS)) return true
            return nodeContainsText(root, "reels") || nodeContainsText(root, "vidéos")
        }
        return false
    }

    private fun isMessagingContext(event: AccessibilityEvent, pkg: String): Boolean {
        val keywords = MESSAGING_KEYWORDS[pkg] ?: return false
        val className = event.className?.toString()?.lowercase() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""
        return keywords.any { className.contains(it) || text.contains(it) }
    }

    private fun nodeContainsViewIds(node: AccessibilityNodeInfo?, ids: List<String>, depth: Int = 0): Boolean {
        if (node == null || depth > 8) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (ids.any { viewId.contains(it) }) return true
        for (i in 0 until node.childCount) {
            if (nodeContainsViewIds(node.getChild(i), ids, depth + 1)) return true
        }
        return false
    }

    /**
     * Scan l'arbre par TEXTE VISIBLE (pas par ID) — résistant à l'obfuscation de Facebook.
     * Utilisé uniquement sur TYPE_WINDOW_STATE_CHANGED pour éviter les faux-positifs
     * liés aux posts du feed qui mentionneraient "reels" dans leur description.
     * Depth limité à 6 pour la performance.
     */
    private fun nodeContainsText(node: AccessibilityNodeInfo?, keyword: String, depth: Int = 0): Boolean {
        if (node == null || depth > 6) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains(keyword) || desc.contains(keyword)) return true
        for (i in 0 until node.childCount) {
            if (nodeContainsText(node.getChild(i), keyword, depth + 1)) return true
        }
        return false
    }

    // ────────────────────────────────────────────────────────────────────────────
    // GESTION DE SESSION
    // ────────────────────────────────────────────────────────────────────────────

    private fun enterReelsSection(pkg: String) {
        // Cooldown actif : on vient de bloquer, on ignore les events résiduels de l'app.
        if (System.currentTimeMillis() < blockCooldownUntil) return
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

    private fun startInstagramPoller() {
        if (instagramPollerRunnable != null) return  // déjà actif
        val runnable = object : Runnable {
            override fun run() {
                val root = rootInActiveWindow
                val fg = root?.packageName?.toString()
                if (fg != "com.instagram.android") {
                    // Instagram n'est plus au premier plan
                    if (isInReelsSection) exitReelsSection()
                    stopInstagramPoller()
                    return
                }
                val reelsVisible = nodeContainsViewIds(root!!, INSTAGRAM_REEL_IDS)
                when {
                    reelsVisible && !isInReelsSection ->
                        enterReelsSection("com.instagram.android")
                    !reelsVisible && isInReelsSection ->
                        exitReelsSection()
                }
                handler.postDelayed(this, INSTAGRAM_POLL_MS)
            }
        }
        instagramPollerRunnable = runnable
        handler.postDelayed(runnable, INSTAGRAM_POLL_MS)
    }

    private fun stopInstagramPoller() {
        instagramPollerRunnable?.let { handler.removeCallbacks(it) }
        instagramPollerRunnable = null
    }

    private fun exitReelsSection() {
        flushCurrentReel()
        isInReelsSection = false
        stopSessionTimer()
        cancelAutoExit()  // annule l'auto-exit en attente → évite HOME/overlay sur écran d'accueil
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
        // Ne bloquer QUE si une app cible est réellement au premier plan.
        // ?: return → si rootInActiveWindow est null (home screen sur certains launchers),
        // on ne bloque pas non plus : on ne peut pas confirmer que l'app est visible.
        val foregroundPkg = rootInActiveWindow?.packageName?.toString() ?: return
        if (foregroundPkg !in TARGET_PACKAGES) return

        val status = quotaManager.checkAndConsumeQuota(pkg)
        if (!status.isExceeded) { overlayManager.hideOverlay(); return }

        when {
            status.focusModeActive -> {
                // showToastAndExit au lieu de l'overlay + scheduleAutoExit(2000) :
                // L'overlay TYPE_APPLICATION_OVERLAY restait visible sur l'écran d'accueil
                // si l'utilisateur appuyait sur HOME avant les 2 secondes → "message en background".
                // Avec showToastAndExit : exitReelsSection() immédiat + HOME immédiat, pas d'overlay.
                showToastAndExit(getString(R.string.overlay_focus_active, quotaManager.getFocusEndTimeDisplay()))
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
        // Cooldown 3s : bloque enterReelsSection pendant la transition HOME.
        // Les events AccessibilityService arrivent en différé après GLOBAL_ACTION_HOME
        // et pourraient sinon relancer la session immédiatement.
        blockCooldownUntil = System.currentTimeMillis() + 3000L
        exitReelsSection()
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun scheduleAutoExit(delayMs: Long) {
        cancelAutoExit()
        val runnable = Runnable {
            blockCooldownUntil = System.currentTimeMillis() + 3000L
            exitReelsSection()  // stoppe timer + overlay + annule auto-exit lui-même
            // Ne presser HOME que si l'utilisateur est encore dans une app cible.
            // Si il a déjà quitté manuellement, inutile de le renvoyer sur home.
            val current = rootInActiveWindow?.packageName?.toString()
            if (current != null && current in TARGET_PACKAGES) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        autoExitRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelAutoExit() {
        autoExitRunnable?.let { handler.removeCallbacks(it) }
        autoExitRunnable = null
    }

    override fun onInterrupt() { exitReelsSection(); cancelAutoExit(); stopSessionTimer(); stopInstagramPoller() }

    override fun onDestroy() {
        super.onDestroy()
        exitReelsSection()
        cancelAutoExit()
        stopSessionTimer()
        stopInstagramPoller()
        try { unregisterReceiver(backReceiver) } catch (_: Exception) {}
    }
}
