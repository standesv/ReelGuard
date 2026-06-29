package com.reelguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reelguard.app.manager.OverlayManager
import com.reelguard.app.manager.QuotaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReelBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var quotaManager: QuotaManager
    private lateinit var overlayManager: OverlayManager

    private var currentPackage: String = ""
    private var isReelActive = false
    private var reelStartTime = 0L

    // Packages ciblés et mots-clés de détection dans le content-description / view-id
    companion object {
        val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",   // TikTok international
            "com.ss.android.ugc.trill",   // TikTok alternatif
            "com.facebook.katana",
            "com.snapchat.android",
            "com.pinterest",
            "com.twitter.android",
            "com.X.android"
        )

        // Mots-clés dans les content descriptions / class names indiquant un Reel
        private val REEL_KEYWORDS = listOf(
            "reel", "reels", "short", "shorts", "spotlight",
            "clip", "clips", "idea pin", "idea pins"
        )

        // View IDs connus pour les zones Reels (obfusqués mais stables entre versions)
        private val REEL_VIEW_IDS = listOf(
            // Instagram
            "clips_viewer_container", "reel_viewer", "clips_tab",
            // YouTube Shorts
            "reel_player_page", "shorts_container", "shorts_video_cell",
            // Facebook
            "reels_viewer", "fb_reels",
            // Snapchat
            "spotlight_video"
        )

        // Packages de messagerie : si l'utilisateur est dans les DMs, on ne bloque pas
        private val MESSAGING_ACTIVITIES = mapOf(
            "com.instagram.android" to listOf("DirectThread", "DirectInbox", "message", "inbox"),
            "com.facebook.katana" to listOf("MessengerThread", "inbox", "message"),
            "com.snapchat.android" to listOf("ChatActivity", "ConversationActivity")
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        quotaManager = QuotaManager.getInstance(applicationContext)
        overlayManager = OverlayManager(applicationContext)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = TARGET_PACKAGES.toTypedArray()
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val event = event ?: return
        val pkg = event.packageName?.toString() ?: return

        if (pkg !in TARGET_PACKAGES) return
        if (!quotaManager.isBlockingEnabledForApp(pkg)) return

        currentPackage = pkg

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                serviceScope.launch {
                    processEvent(event, pkg)
                }
            }
        }
    }

    private fun processEvent(event: AccessibilityEvent, pkg: String) {
        // 1. Vérifier si on est dans la messagerie -> pas de blocage
        if (isInMessagingContext(event, pkg)) {
            if (isReelActive) stopReelTracking()
            overlayManager.hideOverlay()
            return
        }

        // 2. Détecter si un Reel est à l'écran
        val reelDetected = detectReel(event, pkg)

        if (reelDetected) {
            if (!isReelActive) startReelTracking(pkg)

            // Vérifier si le quota est dépassé
            val quotaStatus = quotaManager.checkAndConsumeQuota(pkg)
            when {
                quotaStatus.isExceeded -> overlayManager.showBlockOverlay(quotaStatus)
                quotaStatus.isWarning -> overlayManager.showWarningBadge(quotaStatus)
                else -> overlayManager.hideOverlay()
            }
        } else {
            if (isReelActive) stopReelTracking()
            overlayManager.hideOverlay()
        }
    }

    private fun detectReel(event: AccessibilityEvent, pkg: String): Boolean {
        return when (pkg) {
            "com.instagram.android" -> detectInstagramReel(event)
            "com.google.android.youtube" -> detectYouTubeShorts(event)
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> detectTikTok(event)
            "com.facebook.katana" -> detectFacebookReel(event)
            "com.snapchat.android" -> detectSnapchatSpotlight(event)
            "com.pinterest" -> detectPinterestIdeaPin(event)
            "com.twitter.android", "com.X.android" -> detectTwitterShorts(event)
            else -> false
        }
    }

    private fun detectInstagramReel(event: AccessibilityEvent): Boolean {
        // Méthode 1 : class name de l'activité
        val className = event.className?.toString() ?: ""
        if (className.contains("Reel", ignoreCase = true) ||
            className.contains("Clip", ignoreCase = true)) return true

        // Méthode 2 : parcours de l'arbre de vues
        val root = rootInActiveWindow ?: return false
        return nodeContainsKeywords(root, REEL_VIEW_IDS + listOf("reel_tray", "reels_surface")) ||
               nodeContainsContentDesc(root, listOf("reel", "reels"))
    }

    private fun detectYouTubeShorts(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("Short", ignoreCase = true)) return true

        val root = rootInActiveWindow ?: return false
        return nodeContainsKeywords(root, listOf("shorts_container", "reel_player", "shorts_video")) ||
               nodeContainsContentDesc(root, listOf("shorts", "short"))
    }

    private fun detectTikTok(event: AccessibilityEvent): Boolean {
        // TikTok EST essentiellement un flux de Reels — on bloque toute l'app
        // sauf si l'utilisateur est dans les messages
        return true
    }

    private fun detectFacebookReel(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("Reel", ignoreCase = true)) return true

        val root = rootInActiveWindow ?: return false
        return nodeContainsKeywords(root, listOf("reels_viewer", "fb_reels", "video_reels")) ||
               nodeContainsContentDesc(root, listOf("reel", "reels"))
    }

    private fun detectSnapchatSpotlight(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("Spotlight", ignoreCase = true)) return true

        val root = rootInActiveWindow ?: return false
        return nodeContainsKeywords(root, listOf("spotlight", "discover_feed")) ||
               nodeContainsContentDesc(root, listOf("spotlight"))
    }

    private fun detectPinterestIdeaPin(event: AccessibilityEvent): Boolean {
        val root = rootInActiveWindow ?: return false
        return nodeContainsContentDesc(root, listOf("idea pin", "story pin", "video pin"))
    }

    private fun detectTwitterShorts(event: AccessibilityEvent): Boolean {
        // Sur X/Twitter, on cible les vidéos courtes dans le fil "Pour toi"
        val root = rootInActiveWindow ?: return false
        return nodeContainsContentDesc(root, listOf("video", "clip"))
    }

    private fun isInMessagingContext(event: AccessibilityEvent, pkg: String): Boolean {
        val messagingKeywords = MESSAGING_ACTIVITIES[pkg] ?: return false
        val className = event.className?.toString()?.lowercase() ?: ""
        val contentText = event.text?.joinToString(" ")?.lowercase() ?: ""

        return messagingKeywords.any { keyword ->
            className.contains(keyword, ignoreCase = true) ||
            contentText.contains(keyword, ignoreCase = true)
        }
    }

    // ---------- Utilitaires d'inspection de l'arbre de vues ----------

    private fun nodeContainsKeywords(
        node: AccessibilityNodeInfo?,
        keywords: List<String>,
        depth: Int = 0
    ): Boolean {
        if (node == null || depth > 8) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (keywords.any { viewId.contains(it) }) return true

        for (i in 0 until node.childCount) {
            if (nodeContainsKeywords(node.getChild(i), keywords, depth + 1)) return true
        }
        return false
    }

    private fun nodeContainsContentDesc(
        node: AccessibilityNodeInfo?,
        keywords: List<String>,
        depth: Int = 0
    ): Boolean {
        if (node == null || depth > 8) return false

        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (keywords.any { contentDesc.contains(it) || text.contains(it) }) return true

        for (i in 0 until node.childCount) {
            if (nodeContainsContentDesc(node.getChild(i), keywords, depth + 1)) return true
        }
        return false
    }

    // ---------- Suivi du temps ----------

    private fun startReelTracking(pkg: String) {
        isReelActive = true
        reelStartTime = System.currentTimeMillis()
    }

    private fun stopReelTracking() {
        if (isReelActive && reelStartTime > 0) {
            val duration = System.currentTimeMillis() - reelStartTime
            quotaManager.recordReelSession(currentPackage, duration)
        }
        isReelActive = false
        reelStartTime = 0L
    }

    override fun onInterrupt() {
        if (isReelActive) stopReelTracking()
        overlayManager.hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReelActive) stopReelTracking()
        overlayManager.hideOverlay()
        serviceScope.cancel()
    }
}
