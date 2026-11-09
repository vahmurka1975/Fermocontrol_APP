package ru.fermocontrol.fermocontrol.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.fermocontrol.fermocontrol.data.BackupManager

class BackupActions(
    val saveBackup: () -> Unit,
    val restoreBackup: () -> Unit,
    val shareBackup: () -> Unit,
    val saveCsv: () -> Unit,
)

@Composable
fun rememberBackupActions(
    manager: BackupManager,
    onMessage: (String) -> Unit,
    onRestored: () -> Unit,
): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val message = block()
                if (message.isNotEmpty()) onMessage(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage(e.message ?: "Операция не удалась")
            } finally {
                busy = false
            }
        }
    }

    val saveZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) run {
            val n = context.contentResolver.openOutputStream(uri, "wt")
                ?.use { manager.writeBackup(it) }
                ?: throw IOException("Не удалось создать файл")
            "Копия сохранена. Партий: $n"
        }
    }

    val saveCsvFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) run {
            val n = context.contentResolver.openOutputStream(uri, "wt")
                ?.use { manager.writeCsv(it) }
                ?: throw IOException("Не удалось создать файл")
            "CSV сохранён. Партий: $n"
        }
    }

    val pickZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingRestore = uri }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Заменить данные из копии?") },
            text = {
                Text(
                    "Текущий журнал будет заменён содержимым копии. " +
                        "Перед этим приложение сохранит страховочную копию текущих данных.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    run {
                        val n = context.contentResolver.openInputStream(uri)
                            ?.use { manager.restore(it) }
                            ?: throw IOException("Не удалось открыть файл")
                        onRestored()
                        "Восстановлено. Партий: $n"
                    }
                }) { Text("Заменить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Отмена") }
            },
        )
    }

    return BackupActions(
        saveBackup = { saveZip.launch(manager.backupFileName()) },
        restoreBackup = { pickZip.launch(arrayOf("*/*")) },
        shareBackup = {
            run {
                val uri = manager.prepareShare()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Поделиться копией"))
                ""
            }
        },
        saveCsv = { saveCsvFile.launch(manager.csvFileName()) },
    )
}