package ru.layerpilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.model.PrinterModel
import ru.layerpilot.data.printer.PrinterDiscovery
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.theme.NdotFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(vm: AppViewModel, onConnected: () -> Unit, onBack: (() -> Unit)? = null) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var host by rememberSaveable(settings.host) { mutableStateOf(settings.host) }
    var code by rememberSaveable(settings.accessCode) { mutableStateOf(settings.accessCode) }
    var serial by rememberSaveable(settings.serial) { mutableStateOf(settings.serial) }
    var name by rememberSaveable(settings.printerName) { mutableStateOf(settings.printerName) }
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var demoAms by rememberSaveable { mutableStateOf(settings.demoAms) }
    val ctx = LocalContext.current
    val discovery = remember { PrinterDiscovery(ctx) }
    val foundPrinters by discovery.found.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { discovery.stop() } }

    val canConnect = host.isNotBlank() && code.isNotBlank() && serial.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(62.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Print, null, Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column {
                Text("BambukHome", fontSize = 34.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
                Text("локальное управление принтером",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            "Один раз укажите адрес принтера — дальше всё работает по домашнему Wi-Fi, без облака и аккаунтов.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TonalCard(Modifier.fillMaxWidth()) {
            Text("ПОДКЛЮЧЕНИЕ", fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP-адрес принтера") },
                placeholder = { Text("192.168.1.50") },
                leadingIcon = { Icon(Icons.Rounded.Dns, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { discovery.start() },
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Wifi, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Найти в сети")
                }
                Text("mDNS", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            foundPrinters.forEach { found ->
                TextButton(onClick = {
                    host = found.host
                    if (name.isBlank()) name = found.name
                    discovery.stop()
                }) {
                    Text("${found.name} · ${found.host}")
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isLetterOrDigit() } },
                label = { Text("Код доступа") },
                placeholder = { Text("8 символов") },
                leadingIcon = { Icon(Icons.Rounded.Password, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Показать код",
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = serial,
                onValueChange = { serial = it.trim().uppercase() },
                label = { Text("Серийный номер") },
                placeholder = { Text("01P00A123456789") },
                leadingIcon = { Icon(Icons.Rounded.QrCode, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ExposedDropdownMenuBox(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = !menuOpen },
            ) {
                OutlinedTextField(
                    value = model.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Модель") },
                    leadingIcon = { Icon(Icons.Rounded.Print, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    PrinterModel.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                model = item
                                if (name.isBlank() || PrinterModel.entries.any { it.label == name }) {
                                    name = item.label
                                }
                                menuOpen = false
                            },
                            trailingIcon = {
                                if (item == model) Icon(Icons.Rounded.Check, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название принтера") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.saveConnection(host, code, serial, model, name, demo = false)
                    onConnected()
                },
                enabled = canConnect,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Wifi, null)
                Spacer(Modifier.size(8.dp))
                Text("Подключиться по Wi-Fi", fontWeight = FontWeight.Bold)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Text("или", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }

        TonalCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Демо-режим", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Посмотреть приложение без принтера",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(
                    selected = demoAms,
                    onClick = { demoAms = !demoAms },
                    label = { Text("AMS") },
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    vm.setDemoAms(demoAms)
                    vm.setDemo(true)
                    onConnected()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Открыть демо с печатью")
            }
        }

        TonalCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text("ГДЕ ЭТО ВЗЯТЬ", fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Tip("IP-адрес", "Экран принтера → Настройки → Сеть → IP. Лучше закрепить адрес в роутере.")
            Tip("Код доступа", "Экран принтера → Настройки → Сеть → Код доступа (8 символов).")
            Tip("Серийный номер", "На наклейке сзади или в Bambu Handy → Настройки устройства.")
        }

        Text(
            "Приложение общается с принтером напрямую: MQTT 8883, FTPS 990, камера 6000. Данные не покидают дом.",
            fontSize = 12.sp, lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Tip(title: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(6.dp).padding(top = 6.dp).background(
                MaterialTheme.colorScheme.primary,
                androidx.compose.foundation.shape.CircleShape,
            ),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

