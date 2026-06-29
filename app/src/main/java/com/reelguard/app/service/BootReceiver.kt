package com.reelguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reelguard.app.manager.QuotaManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // Réinitialiser les quotas du jour si nécessaire
            QuotaManager.getInstance(context).checkAndResetDailyQuotas()
        }
    }
}
