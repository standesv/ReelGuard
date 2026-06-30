package com.reelguard.app.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.reelguard.app.ui.theme.BlockRed
import com.reelguard.app.ui.theme.FocusBlue
import com.reelguard.app.ui.theme.StreakGold
import com.reelguard.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFocusDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛑 ", style = MaterialTheme.typography.headlineSmall)
                        Text("ReelGuard", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                }
            )
        },
        bottomBar = { AdBanner() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avertissements de permissions manquantes
            if (!state.isAccessibilityEnabled) {
                PermissionWarningCard(
                    title = "Service d'accessibilité requis",
                    description = "Activez ReelGuard dans les paramètres d'accessibilité pour détecter les Reels.",
                    buttonText = "Activer",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
            if (!state.isOverlayPermissionGranted) {
                PermissionWarningCard(
                    title = "Permission d'affichage requise",
                    description = "ReelGuard a besoin d'afficher un écran de blocage par-dessus les autres apps.",
                    buttonText = "Autoriser",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
            }

            // Streak
            if (state.streakDays > 0) {
                StreakCard(state.streakDays)
            }

            // Mode Focus actif
            if (state.isFocusActive) {
                FocusActiveCard(
                    timeRemaining = state.focusTimeRemaining,
                    onStop = { viewModel.stopFocus() }
                )
            }

            // Quota du jour
            QuotaCard(state = state)

            // Bouton Mode Focus
            if (!state.isFocusActive) {
                OutlinedButton(
                    onClick = { showFocusDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FocusBlue)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activer le Mode Focus")
                }
            }

            // Apps activées
            AppToggleCard(
                appStates = state.appStates,
                onToggle = { pkg, enabled -> viewModel.toggleApp(pkg, enabled) }
            )

            // Statut global
            StatusBadge(
                isActive = state.isAccessibilityEnabled && state.isOverlayPermissionGranted
            )
        }
    }

    // Dialog Mode Focus
    if (showFocusDialog) {
        FocusDurationDialog(
            onDismiss = { showFocusDialog = false },
            onConfirm = { minutes ->
                viewModel.startFocus(minutes)
                showFocusDialog = false
            }
        )
    }
}

@Composable
fun PermissionWarningCard(
    title: String,
    description: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun StreakCard(days: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = StreakGold.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔥", style = MaterialTheme.typography.headlineMedium)
            Column {
                Text(
                    "$days jours de suite !",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StreakGold
                )
                Text(
                    "Tu respectes tes quotas — continue comme ça !",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FocusActiveCard(timeRemaining: String, onStop: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FocusBlue.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = FocusBlue)
                Column {
                    Text("Mode Focus actif", fontWeight = FontWeight.Bold, color = FocusBlue)
                    Text("Fin dans $timeRemaining", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onStop) { Text("Arrêter") }
        }
    }
}

@Composable
fun QuotaCard(state: DashboardState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quotas du jour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Quota en nombre
            if (state.quota.countLimit > 0) {
                QuotaProgressRow(
                    label = "Reels visionnés",
                    used = state.quota.countUsed,
                    limit = state.quota.countLimit,
                    unit = "reels"
                )
            }

            // Quota en temps
            if (state.quota.timeLimitMin > 0) {
                val usedMin = (state.quota.timeUsedMs / 60000f).toInt()
                QuotaProgressRow(
                    label = "Temps de Reels",
                    used = usedMin,
                    limit = state.quota.timeLimitMin,
                    unit = "min"
                )
            }

            if (state.quota.countLimit <= 0 && state.quota.timeLimitMin <= 0) {
                Text(
                    "Aucun quota configuré — allez dans Paramètres pour en définir un.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuotaProgressRow(label: String, used: Int, limit: Int, unit: String) {
    val progress = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val animProgress by animateFloatAsState(progress, label = "quota_progress")
    val color = when {
        progress >= 1f -> BlockRed
        progress >= 0.8f -> MaterialTheme.colorScheme.error
        else -> SuccessGreen
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$used / $limit $unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { animProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color
        )
    }
}

@Composable
fun AppToggleCard(
    appStates: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit
) {
    val appLabels = mapOf(
        "com.instagram.android" to "Instagram Reels",
        "com.google.android.youtube" to "YouTube Shorts",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok (alt.)",
        "com.facebook.katana" to "Facebook Reels",
        "com.snapchat.android" to "Snapchat Spotlight",
        "com.pinterest" to "Pinterest",
        "com.twitter.android" to "X / Twitter",
        "com.X.android" to "X"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Applications bloquées", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            appStates.forEach { (pkg, enabled) ->
                val label = appLabels[pkg] ?: pkg
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle(pkg, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) SuccessGreen.copy(alpha = 0.12f)
                else BlockRed.copy(alpha = 0.12f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isActive) SuccessGreen else BlockRed
        )
        Text(
            if (isActive) "ReelGuard est actif et protège votre téléphone"
            else "ReelGuard est inactif — activez les permissions",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) SuccessGreen else BlockRed
        )
    }
}

// ── Bandeau publicitaire AdMob ───────────────────────────────────────────────
// Remplacer l'ID ci-dessous par votre vrai ID après création du compte AdMob
// ID de test Google (affiche des publicités de test, aucun revenu réel) :
private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun AdBanner() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER) // 320×50 dp, format standard accepté par toutes les régies
                adUnitId = AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
fun FocusDurationDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(30) }
    val options = listOf(15, 30, 60, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode Focus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bloquer tous les Reels pendant :")
                options.forEach { min ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == min,
                            onClick = { selected = min }
                        )
                        Text(
                            if (min < 60) "$min minutes"
                            else "${min / 60}h${if (min % 60 > 0) "${min % 60}min" else ""}",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) { Text("Démarrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
