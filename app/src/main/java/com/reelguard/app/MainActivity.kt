package com.reelguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.reelguard.app.ui.navigation.AppNavigation
import com.reelguard.app.ui.theme.ReelGuardTheme
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitialized = AtomicBoolean(false)

    // Receiver pour que l'overlay puisse déclencher "Retour" via le service
    private val backPressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Vérifier si c'est le premier lancement
        val prefs = getSharedPreferences("reelguard_prefs", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("first_launch", true)
        if (isFirstLaunch) {
            prefs.edit().putBoolean("first_launch", false).apply()
        }

        // ── Consentement RGPD via UMP ──────────────────────────────────────────
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                // Mise à jour du statut réussie : afficher le formulaire si nécessaire
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Log.w("UMP", "Consent form error: ${formError.message}")
                    }
                    // Initialiser AdMob après que le consentement est traité
                    initMobileAdsIfNeeded()
                }
            },
            { requestError ->
                // Erreur réseau / config : initialiser quand même (mode dégradé)
                Log.w("UMP", "Consent request error: ${requestError.message}")
                initMobileAdsIfNeeded()
            }
        )

        // Si le consentement était déjà accordé lors d'une session précédente
        if (consentInformation.canRequestAds()) {
            initMobileAdsIfNeeded()
        }
        // ────────────────────────────────────────────────────────────────────────

        setContent {
            ReelGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(showOnboarding = isFirstLaunch)
                }
            }
        }
    }

    private fun initMobileAdsIfNeeded() {
        if (isMobileAdsInitialized.getAndSet(true)) return
        MobileAds.initialize(this) {}
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.reelguard.action.PRESS_BACK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(backPressReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(backPressReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(backPressReceiver) } catch (_: Exception) {}
    }
}
