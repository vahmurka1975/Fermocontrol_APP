package ru.fermocontrol.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ru.fermocontrol.data.BatchPdf
import ru.fermocontrol.data.PdfRequest
import ru.fermocontrol.model.Batch
import ru.fermocontrol.model.WeightLog
import ru.fermocontrol.model.computeMetrics

class BatchPdfActions(
    val save: (PdfRequest) -> Unit,
    val share: (PdfRequest) -> Unit,
)

@Composable
fun rememberBatchPdfActions(pdf: BatchPdf, onMessage: (String) -> Unit): BatchPdfActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PdfRequest?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runTask(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val message = block()
                if (message.isNotEmpty()) onMessage(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage(e.message ?: "Не удалось создать PDF")
            } finally {
                busy = false
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val req = pending
        pending = null
        if (uri != null && req != null) runTask {
            context.contentResolver.openOutputStream(uri, "wt")?.use { pdf.write(req, it) }
                ?: throw IOException("Не удалось открыть файл для записи")
            "PDF сохранён"
        }
    }

    return BatchPdfActions(
        save = { req ->
            pending = req
            saveLauncher.launch(pdf.fileName(req.batch))
        },
        share = { req ->
            runTask {
                val uri = pdf.prepareShare(req)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Партия №${req.batch.number} ${req.batch.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Поделиться карточкой"))
                ""
            }
        },
    )
}