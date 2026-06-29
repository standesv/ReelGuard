package com.reelguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.reelguard.app.manager.OverlayManager
import com.reelguard.app.manager.QuotaManager

class ReelBlockerAccessibilityService : AccessibilityService() {

    private lateinit var quotaManager: QuotaManager
    private lateinit var overlayManager: OverlayManager
    private val handler = Handler(Looper.getMainLooper())
    private var autoExitRunnable: Runnable? = null

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
    }

    private val backReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Quand l'utilisateur clique "Retour" sur l'overlay → aller à l'accueil
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
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 150
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
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ignorer notre propre app et les packages système
        if (pkg == packageName || pkg in IGNORE_PACKAGES || pkg.startsWith("com.android.")) return

        // Quitter une app cible → annuler le blocage
        if (pkg !in TARGET_PACKAGES) {
            cancelAutoExit()
            overlayManager.hideOverlay()
            return
        }

        if (!quotaManager.isBlockingEnabledForApp(pkg)) return
        if (isMessagingContext(event, pkg)) {
            cancelAutoExit()
            overlayManager.hideOverlay()
            return
        }

        val status = quotaManager.checkAndConsumeQuota(pkg)
        if (!status.isExceeded) {
            cancelAutoExit()
            overlayManager.hideOverlay()
            return
        }

        val className = event.className?.toString()?.lowercase() ?: ""

        when {
            // MODE FOCUS : bloquer toute l'app → overlay + sortie auto dans 2s
            status.focusModeActive -> {
                overlayManager.showBlockOverlay(status)
                scheduleAutoExit(2000)
            }

            // TikTok : toute l'app est des reels → sortie immédiate
            pkg in setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill") -> {
                showToastAndExit("Reels bloqués — quota atteint")
            }

            // Instagram Reels / Clips
            pkg == "com.instagram.android" && isReelClass(className) -> {
                showToastAndExit("Instagram Reels bloqués")
            }

            // YouTube Shorts
            pkg == "com.google.android.youtube" && isShortClass(className) -> {
                showToastAndExit("YouTube Shorts bloqués")
            }

            // Facebook Reels
            pkg == "com.facebook.katana" && isReelClass(className) -> {
                showToastAndExit("Facebook Reels bloqués")
            }

            // Snapchat Spotlight
            pkg == "com.snapchat.android" && className.contains("spotlight") -> {
                showToastAndExit("Spotlight bloqué")
            }
        }
    }

    private fun isReelClass(className: String) =
        className.contains("reel") || className.contains("clip")

    private fun isShortClass(className: String) =
        className.contains("short")

    private fun showToastAndExit(message: String) {
        Toast.makeText(applicationContext, "🛑 $message", Toast.LENGTH_SHORT).show()
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

    private fun isMessagingContext(event: AccessibilityEvent, pkg: String): Boolean {
        val keywords = MESSAGING_KEYWORDS[pkg] ?: return false
        val className = event.className?.toString()?.lowercase() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""
        return keywords.any { className.contains(it) || text.contains(it) }
    }

    override fun onInterrupt() {
        cancelAutoExit()
        overlayManager.hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoExit()
        overlayManager.hideOverlay()
        try { unregisterReceiver(backReceiver) } catch (_: Exception) {}
    }
}
