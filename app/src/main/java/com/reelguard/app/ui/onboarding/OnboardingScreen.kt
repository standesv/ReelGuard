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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelguard.app.R

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
            title = stringResource(R.string.onboarding_welcome_title),
            steps = listOf(
                stringResource(R.string.onboarding_welcome_step1),
                stringResource(R.string.onboarding_welcome_step2),
                stringResource(R.string.onboarding_welcome_step3)
            )
        ),
        OnboardingPage(
            emoji = "♿",
            title = stringResource(R.string.onboarding_accessibility_title),
            steps = listOf(
                stringResource(R.string.onboarding_accessibility_step1),
                stringResource(R.string.onboarding_accessibility_step2),
                stringResource(R.string.onboarding_accessibility_step3),
                stringResource(R.string.onboarding_accessibility_step4)
            ),
            note = stringResource(R.string.onboarding_accessibility_note),
            actionLabel = stringResource(R.string.onboarding_accessibility_button),
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ),
        OnboardingPage(
            emoji = "🪟",
            title = stringResource(R.string.onboarding_overlay_title),
            steps = listOf(
                stringResource(R.string.onboarding_overlay_step1),
                stringResource(R.string.onboarding_overlay_step2),
                stringResource(R.string.onboarding_overlay_step3)
            ),
            note = stringResource(R.string.onboarding_overlay_note),
            actionLabel = stringResource(R.string.onboarding_overlay_button),
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
            title = stringResource(R.string.onboarding_config_title),
            steps = listOf(
                stringResource(R.string.onboarding_config_step1),
                stringResource(R.string.onboarding_config_step2),
                stringResource(R.string.onboarding_config_step3),
                stringResource(R.string.onboarding_config_step4)
            ),
            note = stringResource(R.string.onboarding_config_note)
        ),
        OnboardingPage(
            emoji = "✅",
            title = stringResource(R.string.onboarding_ready_title),
            steps = listOf(
                stringResource(R.string.onboarding_ready_step1),
                stringResource(R.string.onboarding_ready_step2),
                stringResource(R.string.onboarding_ready_step3)
            ),
            note = stringResource(R.string.onboarding_ready_note)
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
                TextButton(onClick = { currentPage-- }) {
                    Text(stringResource(R.string.btn_previous))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (currentPage < pages.lastIndex) {
                Button(onClick = { currentPage++ }) {
                    Text(stringResource(R.string.btn_next))
                }
            } else {
                Button(onClick = onComplete) {
                    Text(stringResource(R.string.btn_get_started))
                }
            }
        }
    }
}
