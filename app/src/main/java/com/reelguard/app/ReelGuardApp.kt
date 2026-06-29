package com.reelguard.app

import android.app.Application
import com.reelguard.app.data.AppDatabase
import com.reelguard.app.manager.QuotaManager

class ReelGuardApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val quotaManager by lazy { QuotaManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // Reset quotas si nécessaire au démarrage de l'app
        quotaManager.checkAndResetDailyQuotas()
    }
}
