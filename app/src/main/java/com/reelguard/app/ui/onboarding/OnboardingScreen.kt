package com.reelguard.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val actionLabel: String?,
    val action: ((android.content.Context) -> Unit)? = null
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }

    val pages = listOf(
        OnboardingPage(
            emoji = "🛑",
            title = "Bienvenue sur ReelGuard",
            description = "ReelGuard vous aide à reprendre le contrôle de votre temps en bloquant les Reels sur Instagram, YouTube Shorts, TikTok et d'autres apps.",
            actionLabel = null
        ),
        OnboardingPage(
            emoji = "♿",
            title = "Service d'Accessibilité",
            description = "Pour détecter les Reels à l'écran, ReelGuard a besoin du Service d'Accessibilité.\n\nVos données restent 100% locales — aucune information n'est envoyée à des serveurs.",
            actionLabel = "Activer le service",
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ),
        OnboardingPage(
            emoji = "🪟",
            title = "Affichage par-dessus les apps",
            description = "Pour afficher l'écran de blocage par-dessus Instagram ou YouTube, ReelGuard a besoin d'une permission d'affichage système.",
            actionLabel = "Accorder la permission",
            action = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${ctx.packageName}")
                    }
                )
            }
        ),
        OnboardingPage(
            emoji = "⏱️",
            title = "Définissez votre quota",
            description = "Commencez par un quota de 15 minutes par jour. Vous pourrez ajuster à tout moment dans les Paramètres.\n\nReelGuard vous avertira à 80% du quota atteint.",
            actionLabel = null
        ),
        OnboardingPage(
            emoji = "✅",
            title = "Vous êtes prêt !",
            description = "ReelGuard surveille maintenant vos Reels.\n\nConsultez le tableau de bord pour voir votre consommation et vos statistiques.",
            actionLabel = null
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { i ->
                val isActive = i == currentPage
                Surface(
                    modifier = Modifier.size(if (isActive) 10.dp else 6.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ) {}
            }
        }

        // Content
        AnimatedContent(targetState = currentPage, label = "onboarding_page") { page ->
            val p = pages[page]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(p.emoji, fontSize = 80.sp)
                Text(
                    p.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    p.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (p.action != null && p.actionLabel != null) {
                    Button(
                        onClick = { p.action.invoke(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(p.actionLabel)
                    }
                }
            }
        }

        // Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentPage > 0) {
                TextButton(onClick = { currentPage-- }) { Text("Précédent") }
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (currentPage < pages.lastIndex) {
                Button(onClick = { currentPage++ }) { Text("Suivant") }
            } else {
                Button(onClick = onComplete) { Text("Commencer !") }
            }
        }
    }
}
