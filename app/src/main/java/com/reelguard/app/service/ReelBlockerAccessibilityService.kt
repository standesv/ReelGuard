package com.reelguard.app.service

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
import android.widget.Toast
import com.reelguard.app.manager.OverlayManager
import com.reelguard.app.manager.QuotaManager

class ReelBlockerAccessibilityService : AccessibilityService() {

    private lateinit var quotaManager: QuotaManager
    private lateinit var overlayManager: OverlayManager
    private val handler = Handler(Looper.getMainLooper())
    private var autoExitRunnable: Runnable? = null

    // Suivi du temps passé dans les apps cibles
    private var sessionStartTime = 0L
    private var sessionPkg = ""
    // Anti-doublon pour le compteur (1 compte max toutes les 10s par app)
    private var lastCountTime = 0L
    private var lastCountPkg = ""

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

        if (pkg == packageName || pkg in IGNORE_PACKAGES || pkg.startsWith("com.android.")) return

        // L'utilisateur quitte une app cible → enregistrer la session
        if (pkg !in TARGET_PACKAGES) {
            endSession()
            cancelAutoExit()
            overlayManager.hideOverlay()
            return
        }

        if (!quotaManager.isBlockingEnabledForApp(pkg)) {
            endSession()
            return
        }

        if (isMessagingContext(event, pkg)) {
            endSession()
            overlayManager.hideOverlay()
            return
        }

        // Démarrer le suivi du temps si nouvelle session
        if (sessionPkg != pkg) {
            endSession()
            sessionStartTime = System.currentTimeMillis()
            sessionPkg = pkg
        }

        // Vérifier quota
        val status = quotaManager.checkAndConsumeQuota(pkg)

        if (status.isExceeded) {
            // Bloquer
            val className = event.className?.toString()?.lowercase() ?: ""
            when {
                status.focusModeActive -> {
                    overlayManager.showBlockOverlay(status)
                    scheduleAutoExit(2000)
                }
                pkg in setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill") -> {
                    showToastAndExit("TikTok bloqué — quota atteint")
                }
                pkg == "com.instagram.android" && isReelClass(className) -> {
                    showToastAndExit("Instagram Reels bloqués")
                }
                pkg == "com.google.android.youtube" && isShortClass(className) -> {
                    showToastAndExit("YouTube Shorts bloqués")
                }
                pkg == "com.facebook.katana" && isReelClass(className) -> {
                    showToastAndExit("Facebook Reels bloqués")
                }
                pkg == "com.snapchat.android" && isSpotlightClass(className) -> {
                    showToastAndExit("Spotlight bloqué")
                }
                // Si quota dépassé mais pas dans section Reels détectée :
                // bloquer quand même avec overlay
                status.countExceeded || status.timeExceeded -> {
                    overlayManager.showBlockOverlay(status)
                    scheduleAutoExit(2000)
                }
            }
        } else {
            // Quota pas encore atteint → enregistrer la visite
            val now = System.currentTimeMillis()
            if (pkg != lastCountPkg || now - lastCountTime > 10_000L) {
                quotaManager.recordReelSession(pkg, 0L)
                lastCountPkg = pkg
                lastCountTime = now
            }
            cancelAutoExit()
            overlayManager.hideOverlay()
        }
    }

    // Enregistre la durée de la session quand l'utilisateur quitte l'app
    private fun endSession() {
        if (sessionStartTime > 0 && sessionPkg.isNotEmpty()) {
            val duration = System.currentTimeMillis() - sessionStartTime
            if (duration > 2000) { // Ignorer les sessions < 2 secondes
                quotaManager.recordReelSession(sessionPkg, duration)
            }
        }
        sessionStartTime = 0L
        sessionPkg = ""
    }

    private fun isReelClass(cls: String) = cls.contains("reel") || cls.contains("clip")
    private fun isShortClass(cls: String) = cls.contains("short")
    private fun isSpotlightClass(cls: String) = cls.contains("spotlight")

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
        endSession()
        cancelAutoExit()
        overlayManager.hideOverlay()
        try { unregisterReceiver(backReceiver) } catch (_: Exception) {}
    }
}
