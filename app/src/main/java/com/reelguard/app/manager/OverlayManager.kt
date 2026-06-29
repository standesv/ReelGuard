package com.reelguard.app.manager

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var warningView: View? = null

    private val overlayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private val warningParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = 200
    }

    fun showBlockOverlay(status: QuotaStatus) {
        if (overlayView != null) {
            // Mettre à jour le message si déjà affiché
            overlayView?.findViewById<TextView>(android.R.id.message)?.text =
                buildBlockMessage(status)
            return
        }

        val view = buildBlockView(status)
        overlayView = view

        try {
            windowManager.addView(view, overlayParams)
        } catch (e: Exception) {
            overlayView = null
        }
    }

    fun showWarningBadge(status: QuotaStatus) {
        hideWarning()
        val view = buildWarningView(status)
        warningView = view
        try {
            windowManager.addView(view, warningParams)
        } catch (e: Exception) {
            warningView = null
        }
    }

    fun hideOverlay() {
        hideBlock()
        hideWarning()
    }

    private fun hideBlock() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun hideWarning() {
        warningView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        warningView = null
    }

    // ---------- Construction des vues ----------

    private fun buildBlockView(status: QuotaStatus): View {
        val view = View(context)

        // Fond semi-opaque foncé
        val rootLayout = android.widget.FrameLayout(context).apply {
            setBackgroundColor(Color.argb(240, 18, 18, 18))
        }

        val inner = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
        }

        // Icône (texte emoji)
        val iconText = TextView(context).apply {
            text = "🛑"
            textSize = 64f
            gravity = Gravity.CENTER
        }

        // Titre
        val title = TextView(context).apply {
            text = "Quota de Reels atteint"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Message détaillé
        val message = TextView(context).apply {
            id = android.R.id.message
            text = buildBlockMessage(status)
            textSize = 16f
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        // Bouton retour
        val backButton = Button(context).apply {
            text = "← Retour"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                // Simuler le bouton back système
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                hideBlock()
            }
        }

        // Streak / motivation
        val streakText = TextView(context).apply {
            text = if (status.streakDays > 0) "🔥 ${status.streakDays} jours de suite respectés !" else ""
            textSize = 14f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        inner.addView(iconText)
        inner.addView(title)
        inner.addView(message)
        inner.addView(backButton)
        if (status.streakDays > 0) inner.addView(streakText)

        rootLayout.addView(inner, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        return rootLayout
    }

    private fun buildWarningView(status: QuotaStatus): View {
        return TextView(context).apply {
            val remaining = when {
                status.countRemaining >= 0 -> "${status.countRemaining} reels restants"
                status.timeRemainingMs >= 0 -> "${status.timeRemainingMs / 60000} min restantes"
                else -> ""
            }
            text = "⚠️ $remaining"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 200, 100, 0))
            setPadding(32, 16, 32, 16)
        }
    }

    private fun buildBlockMessage(status: QuotaStatus): String {
        return when {
            status.countExceeded -> "Tu as regardé tous tes Reels du jour (${status.countLimit} maximum).\nReviens demain ! 💪"
            status.timeExceeded -> "Tu as utilisé tout ton temps de Reels aujourd'hui (${status.timeLimitMin} min).\nReviens demain ! 💪"
            status.scheduledBlocked -> "Les Reels sont bloqués pendant cette plage horaire.\n${status.scheduleMessage}"
            status.focusModeActive -> "Mode Focus actif jusqu'à ${status.focusEndTime}.\nTu peux le faire ! 🎯"
            else -> "Accès aux Reels bloqué."
        }
    }
}

// Hack pour accéder à performGlobalAction depuis l'overlay
// Le vrai appel se fait via le service
private val accessibilityServiceRef = java.lang.ref.WeakReference<android.accessibilityservice.AccessibilityService?>(null)

private fun View.performGlobalAction(action: Int) {
    // En pratique, c'est le service qui appelle hideBlock et pressBack
    // On envoie un broadcast ou on utilise la référence faible au service
    context.sendBroadcast(android.content.Intent("com.reelguard.action.PRESS_BACK"))
}
