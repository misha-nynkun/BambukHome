@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.layerpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.model.PrinterModel
import ru.layerpilot.data.settings.PrinterProfile
import ru.layerpilot.data.settings.currentProfileId
import ru.layerpilot.data.settings.profileList
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ExpressiveSpinner
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.pressScale
import ru.layerpilot.ui.locale.Text
import ru.layerpilot.ui.theme.NdotFamily

@Composable
fun PrintersScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val profiles = remember(settings.printersJson, settings.host, settings.activePrinterId) {
        settings.profileList()
    }
    val activeId = settings.currentProfileId()
    var editor by remember { mutableStateOf<PrinterProfile?>(null) }
    var pendingDelete by remember { mutableStateOf<PrinterProfile?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            ScreenHeader("Принтеры", "несколько принтеров на одном телефоне", onBack = onBack)
            Column(
                Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (profiles.isEmpty()) {
                    Surface(shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Пока нет принтеров", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Добавьте первый принтер — и при необходимости ещё несколько.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                profiles.forEach { profile ->
                    val active = profile.id == activeId
                    PrinterProfileCard(
                        profile = profile,
                        active = active,
                        connected = active && connection is ru.layerpilot.data.printer.ConnectionStatus.Connected,
                        onActivate = { vm.switchPrinter(profile.id) },
                        onEdit = { editor = profile },
                        onDelete = { pendingDelete = profile },
                    )
                }
                Spacer(Modifier.height(80.dp))
            }
        }
        FloatingActionButton(
            onClick = {
                editor = PrinterProfile(
                    id = PrinterProfile.newId(),
                    name = "Новый принтер",
                    host = "",
                    accessCode = "",
                    serial = "",
                    model = PrinterModel.P1S,
                    demo = false,
                    demoAms = true,
                )
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp),
        ) {
            Icon(Icons.Rounded.Add, "Добавить принтер")
        }
    }

    editor?.let { profile ->
        PrinterEditorDialog(
            initial = profile,
            isNew = profiles.none { it.id == profile.id },
            onDismiss = { editor = null },
            onSave = { saved, activate ->
                editor = null
                vm.savePrinterProfile(saved, activate)
            },
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить принтер?") },
            text = {
                Text("«${profile.name}» исчезнет из списка. Настройки самого принтера не изменятся.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.deletePrinterProfile(profile.id)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun PrinterProfileCard(
    profile: PrinterProfile,
    active: Boolean,
    connected: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp),
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Print, null, Modifier.size(21.dp),
                            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(profile.model.label)
                            if (profile.demo) append(" · демо")
                            else if (profile.host.isNotBlank()) append(" · ${profile.host}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (active) {
                    Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(4.dp))
                            Text(if (connected) "онлайн" else "активен", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!active) {
                    Button(onClick = onActivate, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.pressScale()) {
                        Text("Подключиться")
                    }
                } else {
                    Text("Активный принтер", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, "Изменить", Modifier.size(19.dp),
                        tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, "Удалить", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PrinterEditorDialog(
    initial: PrinterProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (PrinterProfile, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var code by remember { mutableStateOf(initial.accessCode) }
    var serial by remember { mutableStateOf(initial.serial) }
    var model by remember { mutableStateOf(initial.model) }
    var demo by remember { mutableStateOf(initial.demo) }
    var demoAms by remember { mutableStateOf(initial.demoAms) }
    var activate by remember { mutableStateOf(isNew) }
    var modelMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новый принтер" else "Принтер") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    Surface(
                        onClick = { modelMenu = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(model.label, modifier = Modifier.weight(1f))
                            Text("▼", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        PrinterModel.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.label) },
                                onClick = { model = entry; modelMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP-адрес принтера") },
                    placeholder = { Text("192.168.3.50") },
                    singleLine = true,
                    enabled = !demo,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Код доступа") },
                    singleLine = true,
                    enabled = !demo,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Серийный номер") },
                    singleLine = true,
                    enabled = !demo,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchLine("Демо-режим", demo) { demo = it }
                if (demo) SwitchLine("AMS в демо", demoAms) { demoAms = it }
                SwitchLine("Сделать активным", activate) { activate = it }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                if (name.isBlank()) {
                    error = "Введите название"
                    return@Button
                }
                if (!demo && (host.isBlank() || code.isBlank() || serial.isBlank())) {
                    error = "Для принтера нужны IP, код доступа и серийный номер"
                    return@Button
                }
                onSave(
                    initial.copy(
                        name = name.trim(),
                        host = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/'),
                        accessCode = code.trim(),
                        serial = serial.trim(),
                        model = model,
                        demo = demo,
                        demoAms = demoAms,
                    ),
                    activate,
                )
            }, modifier = Modifier.pressScale()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
