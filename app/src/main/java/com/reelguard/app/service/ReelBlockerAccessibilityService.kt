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
    private var currentReelStartTime = 0L   // début du reel EN COURS
    private var sessionTimeAccum = 0L       // temps cumulé dans la session

    // Anti-doublon pour les scrolls
    private var lastScrollTime = 0L
    private val SCROLL_DEBOUNCE_MS = 1500L  // 1,5s entre deux comptes

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

        private val REEL_VIEW_IDS = listOf(
            "clips_viewer_container", "reel_viewer", "clips_tab",
            "reel_player_page", "shorts_container", "shorts_video_cell",
            "reels_viewer", "fb_reels", "spotlight_video"
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
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
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
        if (pkg !in TARGET_PACKAGES) {
            onLeaveTargetApp()
            return
        }
        if (!quotaManager.isBlockingEnabledForApp(pkg)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(event, pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(event, pkg)
        }
    }

    // ── Changement d'activité ────────────────────────────────────────────────

    private fun handleWindowChange(event: AccessibilityEvent, pkg: String) {
        if (pkg != currentPackage) {
            // L'utilisateur change d'app cible
            flushCurrentReel()
            currentPackage = pkg
        }

        if (quotaManager.isMessagingExceptionEnabled() && isMessagingContext(event, pkg)) {
            flushCurrentReel()
            isInReelsSection = false
            overlayManager.hideOverlay()
            return
        }

        val inReels = isReelsContext(event, pkg)

        if (inReels && !isInReelsSection) {
            // Entrée dans la section Reels
            isInReelsSection = true
            currentReelStartTime = System.currentTimeMillis()
            sessionTimeAccum = 0L
            // Compter le premier reel
            countOneReel(pkg)
        } else if (!inReels && isInReelsSection) {
            // Sortie de la section Reels
            flushCurrentReel()
            isInReelsSection = false
            overlayManager.hideOverlay()
        }

        if (isInReelsSection) checkAndBlock(pkg)
    }

    // ── Scroll = nouveau reel ────────────────────────────────────────────────

    private fun handleScroll(event: AccessibilityEvent, pkg: String) {
        if (!isInReelsSection) return
        if (quotaManager.isMessagingExceptionEnabled() && isMessagingContext(event, pkg)) return

        val now = System.currentTimeMillis()
        if (now - lastScrollTime < SCROLL_DEBOUNCE_MS) return
        lastScrollTime = now

        // Enregistrer le temps passé sur le reel précédent
        val reelDuration = now - currentReelStartTime
        sessionTimeAccum += reelDuration
        quotaManager.recordReelSession(pkg, reelDuration)
        currentReelStartTime = now  // Démarrer le nouveau reel

        checkAndBlock(pkg)
    }

    // ── Vérification et blocage ──────────────────────────────────────────────

    private fun checkAndBlock(pkg: String) {
        val status = quotaManager.checkAndConsumeQuota(pkg)
        if (!status.isExceeded) {
            overlayManager.hideOverlay()
            return
        }

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
            else -> {
                overlayManager.showBlockOverlay(status)
                scheduleAutoExit(2000)
            }
        }
    }

    // ── Détection du contexte ────────────────────────────────────────────────

    private fun isReelsContext(event: AccessibilityEvent, pkg: String): Boolean {
        val className = event.className?.toString()?.lowercase() ?: ""

        // Par nom de classe (fiable si la classe change)
        val classMatch = when (pkg) {
            "com.instagram.android" ->
                className.contains("reel") || className.contains("clip")
            "com.google.android.youtube" ->
                className.contains("short")
            "com.facebook.katana" ->
                className.contains("reel")
            "com.snapchat.android" ->
                className.contains("spotlight")
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" ->
                true  // TikTok = toujours des reels
            else -> false
        }
        if (classMatch) return true

        // Par view IDs dans l'arbre de vues (fallback)
        val root = rootInActiveWindow ?: return false
        return nodeContainsViewIds(root, REEL_VIEW_IDS)
    }

    private fun isMessagingContext(event: AccessibilityEvent, pkg: String): Boolean {
        val keywords = MESSAGING_KEYWORDS[pkg] ?: return false
        val className = event.className?.toString()?.lowercase() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""
        return keywords.any { className.contains(it) || text.contains(it) }
    }

    // ── Comptage ─────────────────────────────────────────────────────────────

    private fun countOneReel(pkg: String) {
        // Enregistre 1 reel avec 0ms (le temps sera ajouté au prochain scroll/exit)
        quotaManager.recordReelSession(pkg, 0L)
    }

    private fun flushCurrentReel() {
        if (isInReelsSection && currentReelStartTime > 0) {
            val duration = System.currentTimeMillis() - currentReelStartTime
            if (duration > 500) {
                // Enregistrer seulement le temps du dernier reel (sans incrémenter le count)
                // On passe duration négative pour signaler "temps seulement"
                // → en réalité on ajoute le temps accumulé final
                quotaManager.recordReelSession(currentPackage, duration)
            }
            currentReelStartTime = 0L
            sessionTimeAccum = 0L
        }
    }

    private fun onLeaveTargetApp() {
        flushCurrentReel()
        isInReelsSection = false
        cancelAutoExit()
        overlayManager.hideOverlay()
        currentPackage = ""
    }

    // ── Utilitaires d'arbre de vues ──────────────────────────────────────────

    private fun nodeContainsViewIds(node: AccessibilityNodeInfo?, ids: List<String>, depth: Int = 0): Boolean {
        if (node == null || depth > 6) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (ids.any { viewId.contains(it) }) return true
        for (i in 0 until node.childCount) {
            if (nodeContainsViewIds(node.getChild(i), ids, depth + 1)) return true
        }
        return false
    }

    // ── Actions système ──────────────────────────────────────────────────────

    private fun showToastAndExit(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun scheduleAutoExit(delayMs: Long) {
        cancelAutoExit()
        val runnable = Runnable {
            overlayManager.hideOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        autoExitRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelAutoExit() {
        autoExitRunnable?.let { handler.removeCallbacks(it) }
        autoExitRunnable = null
    }

    override fun onInterrupt() {
        flushCurrentReel()
        cancelAutoExit()
        overlayManager.hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        flushCurrentReel()
        cancelAutoExit()
        overlayManager.hideOverlay()
        try { unregisterReceiver(backReceiver) } catch (_: Exception) {}
    }
}
