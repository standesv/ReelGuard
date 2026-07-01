package com.reelguard.app.ui.stats

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelguard.app.data.AppDatabase
import com.reelguard.app.data.entity.DailyStats
import com.reelguard.app.manager.QuotaManager
import com.reelguard.app.ui.theme.StreakGold
import com.reelguard.app.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val qm = QuotaManager.getInstance(application)

    val last30Days = db.dailyStatsDao().getLast30Days()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streakDays get() = qm.getStreakDays()
    val todayTimeMs get() = qm.getTimeUsedMs()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, vm: StatsViewModel = viewModel()) {
    val stats by vm.last30Days.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Résumé du jour
            SummaryRow(
                todayTimeMs = vm.todayTimeMs,
                streakDays = vm.streakDays
            )

            // Graphique 7 derniers jours
            WeeklyChart(stats.take(7).reversed())

            // Tableau des 30 derniers jours
            RecentDaysTable(stats)

            // Temps total récupéré
            val totalTimeSaved = stats.sumOf { it.totalTimeMs }
            TimeSavedCard(totalTimeSaved)
        }
    }
}

@Composable
fun SummaryRow(todayTimeMs: Long, streakDays: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Schedule,
            value = "${todayTimeMs / 60000} min",
            label = "Temps\naujourd'hui"
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.EmojiEvents,
            value = "$streakDays 🔥",
            label = "Jours de\nstreak",
            color = StreakGold
        )
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WeeklyChart(days: List<DailyStats>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("7 derniers jours — Temps (min)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            if (days.isEmpty()) {
                Text("Pas encore de données.", style = MaterialTheme.typography.bodySmall)
                return@Column
            }

            val maxTime = days.maxOf { it.totalTimeMs }.coerceAtLeast(1L)

            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    val fraction = (day.totalTimeMs.toFloat() / maxTime).coerceIn(0f, 1f)
                    val animFraction by animateFloatAsState(fraction, label = "bar")
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((animFraction * 100).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            try {
                                LocalDate.parse(day.date)
                                    .dayOfWeek
                                    .getDisplayName(TextStyle.SHORT, Locale.FRENCH)
                                    .take(2)
                            } catch (_: Exception) { day.date.takeLast(2) },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentDaysTable(days: List<DailyStats>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Historique", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
                Text("Durée", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.5f))
                Text("Quota", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()

            if (days.isEmpty()) {
                Text("Aucune donnée pour le moment.", style = MaterialTheme.typography.bodySmall)
            }

            days.forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        try {
                            LocalDate.parse(day.date)
                                .format(DateTimeFormatter.ofPattern("dd/MM"))
                        } catch (_: Exception) { day.date },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(2f)
                    )
                    Text("${day.totalTimeMs / 60000} min", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                    Text(
                        if (day.quotaMetTime) "✅" else "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun TimeSavedCard(savedMs: Long) {
    val hours = savedMs / 3600000
    val minutes = (savedMs % 3600000) / 60000

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⏱️", style = MaterialTheme.typography.headlineMedium)
            Column {
                Text("Temps utilisé (30j)", fontWeight = FontWeight.Bold)
                Text(
                    if (hours > 0) "${hours}h ${minutes}min de Reels regardés"
                    else "${minutes} min de Reels regardés",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
