package com.reelguard.app.ui.dashboard

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reelguard.app.manager.QuotaManager
import com.reelguard.app.manager.QuotaStatus
import com.reelguard.app.service.ReelBlockerAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val quota: QuotaStatus = QuotaStatus(),
    val isFocusActive: Boolean = false,
    val focusTimeRemaining: String = "",
    val streakDays: Int = 0,
    val timeUsedTodayMin: Float = 0f,
    val appStates: Map<String, Boolean> = emptyMap()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val quotaManager = QuotaManager.getInstance(application)
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(2000) // Refresh toutes les 2 secondes
            }
        }
    }

    fun refresh() {
        val ctx = getApplication<Application>().applicationContext
        val quota = quotaManager.getCurrentQuotaStatus()
        _state.value = DashboardState(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(ctx),
            isOverlayPermissionGranted = Settings.canDrawOverlays(ctx),
            quota = quota,
            isFocusActive = quotaManager.isFocusModeActive(),
            focusTimeRemaining = quotaManager.getFocusEndTimeDisplay(),
            streakDays = quotaManager.getStreakDays(),
            timeUsedTodayMin = quotaManager.getTimeUsedMs() / 60000f,
            appStates = ReelBlockerAccessibilityService.TARGET_PACKAGES.associateWith {
                quotaManager.isBlockingEnabledForApp(it)
            }
        )
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        quotaManager.setBlockingEnabledForApp(packageName, enabled)
        refresh()
    }

    fun startFocus(durationMin: Int) {
        quotaManager.startFocusMode(durationMin)
        refresh()
    }

    fun stopFocus() {
        quotaManager.stopFocusMode()
        refresh()
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/${ReelBlockerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
    }
}
