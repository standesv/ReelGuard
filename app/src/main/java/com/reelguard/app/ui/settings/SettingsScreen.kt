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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelguard.app.R
import com.reelguard.app.manager.QuotaManager
import com.reelguard.app.manager.ScheduleRule

// ---- ViewModel ----

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val qm = QuotaManager.getInstance(application)

    var timeEnabled by mutableStateOf(qm.isTimeQuotaEnabled())
    var timeLimitMin by mutableIntStateOf(qm.getTimeLimitMin())
    var scheduleEnabled by mutableStateOf(qm.isScheduleEnabled())
    var scheduleRules by mutableStateOf(qm.getScheduleRules())
    var pinEnabled by mutableStateOf(qm.isPinEnabled())
    var messagingException by mutableStateOf(qm.isMessagingExceptionEnabled())
    var resetHour by mutableIntStateOf(qm.prefs.getInt(QuotaManager.KEY_RESET_HOUR, 0))
    var parentalMode by mutableStateOf(qm.isParentalModeEnabled())

    fun save() {
        qm.setTimeQuotaEnabled(timeEnabled)
        qm.setTimeLimitMin(timeLimitMin)
        qm.setScheduleEnabled(scheduleEnabled)
        qm.saveScheduleRules(scheduleRules)
        qm.setMessagingExceptionEnabled(messagingException)
        qm.prefs.edit().putInt(QuotaManager.KEY_RESET_HOUR, resetHour).apply()
    }

    fun setPin(pin: String) {
        qm.setPin(pin)
        pinEnabled = pin.isNotEmpty()
    }

    fun verifyPin(pin: String): Boolean = qm.verifyPin(pin)

    fun setParentalModeEnabled(enabled: Boolean) {
        qm.setParentalModeEnabled(enabled)
        parentalMode = enabled
    }
}

// ---- Screen ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showPinVerifyForDisable by remember { mutableStateOf(false) }
    var showAddSchedule by remember { mutableStateOf(false) }
    var showProtectionGuide by remember { mutableStateOf(false) }

    // If parental mode + PIN are both active, require PIN to enter settings
    var isUnlocked by remember { mutableStateOf(!(vm.parentalMode && vm.pinEnabled)) }
    var showUnlockDialog by remember { mutableStateOf(vm.parentalMode && vm.pinEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isUnlocked) {
                        TextButton(onClick = { vm.save() }) {
                            Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- Quota en temps ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.time_quota_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.time_quota_toggle))
                            Switch(checked = vm.timeEnabled, onCheckedChange = { vm.timeEnabled = it })
                        }
                        if (vm.timeEnabled) {
                            Text(stringResource(R.string.time_quota_max, vm.timeLimitMin))
                            Slider(
                                value = vm.timeLimitMin.toFloat(),
                                onValueChange = { vm.timeLimitMin = (Math.round(it / 5f) * 5).coerceIn(5, 120) },
                                valueRange = 5f..120f,
                                steps = 22
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
                            Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.schedule_toggle))
                            Switch(checked = vm.scheduleEnabled, onCheckedChange = { vm.scheduleEnabled = it })
                        }
                        if (vm.scheduleEnabled) {
                            vm.scheduleRules.forEachIndexed { idx, rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        rule.label.ifEmpty {
                                            stringResource(R.string.schedule_from_to, rule.startHour, rule.endHour)
                                        }
                                    )
                                    IconButton(onClick = {
                                        vm.scheduleRules = vm.scheduleRules.toMutableList().also { it.removeAt(idx) }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { showAddSchedule = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_add_schedule))
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
                            Text(stringResource(R.string.reset_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(stringResource(R.string.reset_hour_label, vm.resetHour))
                        Slider(
                            value = vm.resetHour.toFloat(),
                            onValueChange = { vm.resetHour = it.toInt() },
                            valueRange = 0f..23f,
                            steps = 22
                        )
                        Text(
                            stringResource(R.string.reset_explanation, vm.resetHour),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- Exception messagerie ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.messaging_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.messaging_toggle))
                                Text(
                                    stringResource(R.string.messaging_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = vm.messagingException, onCheckedChange = { vm.messagingException = it })
                        }
                    }
                }

                // --- Mode Parental ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.parental_mode_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.parental_mode_toggle))
                                Text(
                                    stringResource(R.string.parental_mode_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = vm.parentalMode,
                                onCheckedChange = { enabling ->
                                    if (enabling) {
                                        vm.setParentalModeEnabled(true)
                                        if (!vm.pinEnabled) showPinSetupDialog = true
                                    } else {
                                        if (vm.pinEnabled) showPinVerifyForDisable = true
                                        else vm.setParentalModeEnabled(false)
                                    }
                                }
                            )
                        }

                        if (vm.parentalMode && !vm.pinEnabled) {
                            Text(
                                stringResource(R.string.parental_no_pin_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (vm.pinEnabled) {
                                TextButton(onClick = { showPinSetupDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.parental_pin_change))
                                }
                                TextButton(onClick = { showPinVerifyDialog = true }) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.parental_pin_remove))
                                }
                            } else {
                                TextButton(onClick = { showPinSetupDialog = true }) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.parental_pin_set))
                                }
                            }
                        }

                        HorizontalDivider()

                        TextButton(
                            onClick = { showProtectionGuide = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.parental_guide_btn))
                        }
                    }
                }
            }
        }
    }

    // Unlock settings on entry (parental mode guard)
    if (showUnlockDialog) {
        PinUnlockDialog(
            title = stringResource(R.string.pin_unlock_title),
            message = stringResource(R.string.pin_unlock_desc),
            onDismiss = {
                showUnlockDialog = false
                onBack()
            },
            onUnlocked = {
                isUnlocked = true
                showUnlockDialog = false
            },
            onVerify = { pin -> vm.verifyPin(pin) }
        )
    }

    // Verify PIN to disable parental mode
    if (showPinVerifyForDisable) {
        PinUnlockDialog(
            title = stringResource(R.string.pin_unlock_title),
            message = stringResource(R.string.parental_pin_verify_disable),
            onDismiss = { showPinVerifyForDisable = false },
            onUnlocked = {
                vm.setParentalModeEnabled(false)
                showPinVerifyForDisable = false
            },
            onVerify = { pin -> vm.verifyPin(pin) }
        )
    }

    // Verify PIN to remove it entirely
    if (showPinVerifyDialog) {
        PinUnlockDialog(
            title = stringResource(R.string.pin_unlock_title),
            message = stringResource(R.string.parental_pin_verify_remove),
            onDismiss = { showPinVerifyDialog = false },
            onUnlocked = {
                vm.setPin("")
                if (vm.parentalMode) vm.setParentalModeEnabled(false)
                showPinVerifyDialog = false
            },
            onVerify = { pin -> vm.verifyPin(pin) }
        )
    }

    // Set / change PIN
    if (showPinSetupDialog) {
        PinDialog(
            onDismiss = {
                if (vm.parentalMode && !vm.pinEnabled) vm.setParentalModeEnabled(false)
                showPinSetupDialog = false
            },
            onConfirm = { pin ->
                vm.setPin(pin)
                showPinSetupDialog = false
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

    if (showProtectionGuide) {
        ProtectionGuideDialog(onDismiss = { showProtectionGuide = false })
    }
}

// ---- PIN unlock dialog (verify existing PIN) ----

@Composable
fun PinUnlockDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    onVerify: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(6)
                        error = false
                    },
                    label = { Text(stringResource(R.string.pin_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.pin_unlock_error)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onVerify(pin)) onUnlocked()
                    else { error = true; pin = "" }
                },
                enabled = pin.length >= 4
            ) { Text(stringResource(R.string.btn_validate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_unlock_cancel)) }
        }
    )
}

// ---- PIN setup dialog (set / change PIN) ----

@Composable
fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.pin_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.pin_confirm_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.pin_error)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = pin.length >= 4 && pin == confirm
            ) { Text(stringResource(R.string.btn_validate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

// ---- Family Link / data protection guide ----

@Composable
fun ProtectionGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null) },
        title = { Text(stringResource(R.string.protection_guide_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.protection_guide_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.protection_guide_family_link_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.protection_guide_family_link_body),
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.protection_guide_wellbeing_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.protection_guide_wellbeing_body),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.protection_guide_close)) }
        }
    )
}

// ---- Add schedule dialog ----

@Composable
fun AddScheduleDialog(onDismiss: () -> Unit, onConfirm: (ScheduleRule) -> Unit) {
    var startHour by remember { mutableIntStateOf(22) }
    var endHour by remember { mutableIntStateOf(8) }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.schedule_from_to, startHour, endHour))
                Text(stringResource(R.string.schedule_start_hour, startHour))
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.toInt() },
                    valueRange = 0f..23f, steps = 22
                )
                Text(stringResource(R.string.schedule_end_hour, endHour))
                Slider(
                    value = endHour.toFloat(),
                    onValueChange = { endHour = it.toInt() },
                    valueRange = 0f..23f, steps = 22
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.schedule_label_hint)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(ScheduleRule(startHour = startHour, endHour = endHour, label = label))
            }) { Text(stringResource(R.string.btn_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
