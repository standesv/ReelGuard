package com.reelguard.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelguard.app.manager.QuotaManager
import com.reelguard.app.manager.ScheduleRule

// ---- ViewModel ----

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val qm = QuotaManager.getInstance(application)

    var countEnabled by mutableStateOf(qm.isCountQuotaEnabled())
    var countLimit by mutableIntStateOf(qm.getCountLimit())
    var timeEnabled by mutableStateOf(qm.isTimeQuotaEnabled())
    var timeLimitMin by mutableIntStateOf(qm.getTimeLimitMin())
    var scheduleEnabled by mutableStateOf(qm.isScheduleEnabled())
    var scheduleRules by mutableStateOf(qm.getScheduleRules())
    var pinEnabled by mutableStateOf(qm.isPinEnabled())
    var resetHour by mutableIntStateOf(qm.prefs.getInt(QuotaManager.KEY_RESET_HOUR, 0))

    fun save() {
        qm.setCountQuotaEnabled(countEnabled)
        qm.setCountLimit(countLimit)
        qm.setTimeQuotaEnabled(timeEnabled)
        qm.setTimeLimitMin(timeLimitMin)
        qm.setScheduleEnabled(scheduleEnabled)
        qm.saveScheduleRules(scheduleRules)
        qm.prefs.edit().putInt(QuotaManager.KEY_RESET_HOUR, resetHour).apply()
    }

    fun setPin(pin: String) {
        qm.setPin(pin)
        pinEnabled = pin.isNotEmpty()
    }
}

// ---- Screen ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showAddSchedule by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parametres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.save() }) {
                        Text("Sauvegarder", fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Quota en nombre ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Quota par nombre de Reels", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Activer le quota par nombre")
                        Switch(checked = vm.countEnabled, onCheckedChange = { vm.countEnabled = it })
                    }
                    if (vm.countEnabled) {
                        Text("Maximum par jour : ${vm.countLimit} reels")
                        Slider(
                            value = vm.countLimit.toFloat(),
                            onValueChange = { vm.countLimit = it.toInt() },
                            valueRange = 1f..50f,
                            steps = 48
                        )
                    }
                }
            }

            // --- Quota en temps ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Quota par duree", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Activer le quota en temps")
                        Switch(checked = vm.timeEnabled, onCheckedChange = { vm.timeEnabled = it })
                    }
                    if (vm.timeEnabled) {
                        Text("Maximum par jour : ${vm.timeLimitMin} minutes")
                        Slider(
                            value = vm.timeLimitMin.toFloat(),
                            onValueChange = { vm.timeLimitMin = it.toInt() },
                            valueRange = 5f..120f,
                            steps = 23
                        )
                    }
                }
            }

            // --- Plages horaires ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Plages horaires bloquees", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Activer les plages horaires")
                        Switch(checked = vm.scheduleEnabled, onCheckedChange = { vm.scheduleEnabled = it })
                    }
                    if (vm.scheduleEnabled) {
                        vm.scheduleRules.forEachIndexed { idx, rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rule.label.ifEmpty { "${rule.startHour}h-${rule.endHour}h" })
                                IconButton(onClick = {
                                    vm.scheduleRules = vm.scheduleRules.toMutableList().also { it.removeAt(idx) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showAddSchedule = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Ajouter une plage")
                        }
                    }
                }
            }

            // --- Heure de reset ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Reinitialisation quotidienne", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Heure de reset : ${"%02d".format(vm.resetHour)}h00")
                    Slider(
                        value = vm.resetHour.toFloat(),
                        onValueChange = { vm.resetHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                    Text(
                        "Les quotas se remettent a zero chaque jour a ${"%02d".format(vm.resetHour)}h00.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- PIN ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Anti-contournement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PIN de protection")
                            Text(
                                "Demander un PIN pour desactiver ReelGuard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vm.pinEnabled,
                            onCheckedChange = {
                                if (it) showPinDialog = true
                                else vm.setPin("")
                            }
                        )
                    }
                    if (vm.pinEnabled) {
                        TextButton(onClick = { showPinDialog = true }) {
                            Text("Changer le PIN")
                        }
                    }
                    HorizontalDivider()
                    Text(
                        "Un delai de 30 secondes s'applique avant toute desactivation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                vm.setPin(pin)
                showPinDialog = false
            }
        )
    }

    if (showAddSchedule) {
        AddScheduleDialog(
            onDismiss = { showAddSchedule = false },
            onConfirm = { rule ->
                vm.scheduleRules = vm.scheduleRules + rule
                showAddSchedule = false
            }
        )
    }
}

@Composable
fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Definir un PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("PIN (4-6 chiffres)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Confirmer le PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error,
                    supportingText = { if (error) Text("Les PINs ne correspondent pas") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = pin.length >= 4 && pin == confirm
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun AddScheduleDialog(onDismiss: () -> Unit, onConfirm: (ScheduleRule) -> Unit) {
    var startHour by remember { mutableIntStateOf(22) }
    var endHour by remember { mutableIntStateOf(8) }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une plage horaire") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("De ${"%02d".format(startHour)}h a ${"%02d".format(endHour)}h")
                Text("Heure de debut : ${"%02d".format(startHour)}h")
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.toInt() },
                    valueRange = 0f..23f, steps = 22
                )
                Text("Heure de fin : ${"%02d".format(endHour)}h")
                Slider(
                    value = endHour.toFloat(),
                    onValueChange = { endHour = it.toInt() },
                    valueRange = 0f..23f, steps = 22
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (ex: Nuit)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(ScheduleRule(startHour = startHour, endHour = endHour, label = label))
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
