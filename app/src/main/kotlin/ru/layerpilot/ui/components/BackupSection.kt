package ru.layerpilot.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.layerpilot.ui.AppViewModel

/**
 * Резервная копия профилей и настроек. Файл всегда шифруется AES-256-GCM,
 * пароль нигде не сохраняется.
 */
@Composable
fun BackupSection(vm: AppViewModel) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val pass = pendingExport
        pendingExport = null
        if (uri == null || pass == null) return@rememberLauncherForActivityResult
        val payload = vm.exportBackup(pass)
        status = if (payload == null) "Не удалось создать копию" else runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("no stream")
            "Копия сохранена · пароль понадобится для импорта"
        }.getOrElse { "Не удалось записать файл" }
    }
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (vm.importBackup(text, password)) "Настройки восстановлены"
            else "Неверный пароль или файл повреждён"
        }.getOrElse { "Не удалось прочитать файл" }
    }

    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Резервная копия настроек", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("Профили принтеров и настройки · шифрование AES-256",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(64) },
            label = { Text("Пароль копии") },
            placeholder = { Text("минимум 6 символов") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = password.length >= 6,
                onClick = {
                    status = null
                    pendingExport = password
                    createFile.launch("BambukHome-backup.bmb")
                },
            ) { Text("Экспорт") }
            TextButton(
                enabled = password.isNotBlank(),
                onClick = { status = null; openFile.launch(arrayOf("*/*")) },
            ) { Text("Импорт") }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
