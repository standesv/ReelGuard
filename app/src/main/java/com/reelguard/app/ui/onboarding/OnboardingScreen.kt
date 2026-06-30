package com.reelguard.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val steps: List<String> = emptyList(),
    val note: String = "",
    val actionLabel: String? = null,
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
            steps = listOf(
                "ReelGuard bloque les Reels sur Instagram, YouTube Shorts, TikTok, Facebook, Snapchat et plus.",
                "Définissez un quota journalier (nombre ou durée) et l'app bloque automatiquement quand il est atteint.",
                "Toutes vos données restent sur votre téléphone — aucun serveur, aucun compte requis."
            )
        ),
        OnboardingPage(
            emoji = "♿",
            title = "Autorisation 1 / 2 — Service d'Accessibilité",
            steps = listOf(
                "Appuyez sur le bouton ci-dessous pour ouvrir les paramètres d'accessibilité.",
                "Cherchez la section « Applications téléchargées » ou « Services installés ».",
                "Appuyez sur « ReelGuard ».",
                "Activez le bouton en haut de l'écran et confirmez en appuyant sur « Autoriser »."
            ),
            note = "⚠️ Sur Samsung, le chemin peut être : Paramètres → Accessibilité → Applications installées → ReelGuard. Sur Pixel : Paramètres → Accessibilité → ReelGuard.",
            actionLabel = "Ouvrir les paramètres d'accessibilité",
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ),
        OnboardingPage(
            emoji = "🪟",
            title = "Autorisation 2 / 2 — Affichage par-dessus les apps",
            steps = listOf(
                "Appuyez sur le bouton ci-dessous.",
                "Trouvez « ReelGuard » dans la liste et appuyez dessus.",
                "Activez « Autoriser l'affichage par-dessus d'autres applis »."
            ),
            note = "⚠️ Sur certains appareils, cette option s'appelle « Apparaître au premier plan » ou « Dessiner par-dessus d'autres applications ».",
            actionLabel = "Ouvrir les paramètres d'affichage",
            action = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${ctx.packageName}")
                    }
                )
            }
        ),
        OnboardingPage(
            emoji = "⚙️",
            title = "Configurez votre quota",
            steps = listOf(
                "Ouvrez les Paramètres (icône ⚙️ en haut à droite du tableau de bord).",
                "Activez « Quota par durée » et réglez-le sur 15 minutes — c'est un bon point de départ.",
                "Ou activez « Quota par nombre » si vous préférez limiter le nombre de Reels.",
                "Appuyez sur « Sauvegarder »."
            ),
            note = "Vous recevrez un avertissement à 80% du quota atteint. Vous pouvez modifier ces réglages à tout moment."
        ),
        OnboardingPage(
            emoji = "✅",
            title = "C'est parti !",
            steps = listOf(
                "ReelGuard surveille maintenant vos applications.",
                "Le tableau de bord affiche votre consommation du jour.",
                "Activez le Mode Focus pour bloquer totalement les Reels pendant une durée définie."
            ),
            note = "Si le blocage ne fonctionne pas, vérifiez que les deux autorisations sont bien accordées (bandeau rouge = permission manquante)."
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicateur de page
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

        // Contenu
        AnimatedContent(targetState = currentPage, label = "onboarding_page") { page ->
            val p = pages[page]
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(p.emoji, fontSize = 64.sp)
                Text(
                    p.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Étapes numérotées
                if (p.steps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.steps.forEachIndexed { index, step ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Note contextuelle
                if (p.note.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            p.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Bouton action
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
