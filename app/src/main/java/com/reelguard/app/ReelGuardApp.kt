package com.reelguard.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.reelguard.app.data.AppDatabase
import com.reelguard.app.manager.QuotaManager

class ReelGuardApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val quotaManager by lazy { QuotaManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        quotaManager.checkAndResetDailyQuotas()
        // Initialisation AdMob (obligatoire avant d'afficher des publicités)
        MobileAds.initialize(this)
    }
}
