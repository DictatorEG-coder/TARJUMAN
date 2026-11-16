package app.tarjuman.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tarjuman.R
import app.tarjuman.core.Languages
import app.tarjuman.core.Links
import app.tarjuman.core.Prefs
import app.tarjuman.data.HistoryStore
import app.tarjuman.engine.Engine
import app.tarjuman.engine.ModelMissing
import app.tarjuman.engine.ModelStore
import app.tarjuman.engine.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- documents

@Composable
fun DocScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tgt by remember { mutableStateOf(Prefs.targetLang) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun process(u: Uri) {
        busy = true; error = null; original = ""; translated = ""
        scope.launch {
            try {
                val type = ctx.contentResolver.getType(u) ?: ""
                val o = StringBuilder()
                val t = StringBuilder()
                if (type == "application/pdf") {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openFileDescriptor(u, "r")!!.use { pfd ->
                            PdfRenderer(pfd).use { pr ->
                                val n = minOf(pr.pageCount, 30)
                                for (i in 0 until n) {
                                    progress = ctx.getString(R.string.page_progress, i + 1, n)
                                    pr.openPage(i).use { page ->
                                        val w = 1600
                                        val h = (page.height * (w.toFloat() / page.width)).toInt()
                                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        val out = Pipeline.bitmap(bmp, tgt)
                                        bmp.recycle()
                                        if (out.original.isNotBlank()) {
                                            o.append("— ${i + 1} —\n").append(out.original).append("\n\n")
                                            t.append("— ${i + 1} —\n").append(out.translated).append("\n\n")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    progress = ctx.getString(R.string.processing)
                    val bytes = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() }
                    }
                    val text = bytes.take(2_000_000).toByteArray().toString(Charsets.UTF_8)
                    o.append(text)
                    t.append(Engine.translate(text, null, tgt).text)
                }
                original = o.toString(); translated = t.toString()
                if (translated.isBlank()) error = ctx.getString(R.string.no_text)
            } catch (e: ModelMissing) {
                error = ctx.getString(R.string.model_missing, e.langs.joinToString { Languages.label(it) })
            } catch (e: Exception) {
                error = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            } finally {
                busy = false; progress = ""
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) process(u) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        if (u != null) {
            try {
                ctx.contentResolver.openOutputStream(u)?.use { it.write(translated.toByteArray()) }
                Toast.makeText(ctx, R.string.saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { picker.launch(arrayOf("text/plain", "application/pdf")) }, enabled = !busy) {
                Text(stringResource(R.string.pick_doc))
            }
            LangPicker(tgt, false, Modifier.weight(1f)) { tgt = it; Prefs.targetLang = it }
        }
        Text(stringResource(R.string.doc_note), color = Muted, fontSize = 12.sp)
        if (busy) Text(progress.ifEmpty { stringResource(R.string.processing) }, color = Soft)
        error?.let { Panel(Modifier.fillMaxWidth()) { Text(it, color = TextC, fontSize = 14.sp) } }
        if (translated.isNotBlank()) {
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.translation), color = Muted, fontSize = 12.sp)
                SelectionContainer { Text(translated, color = TextC, fontSize = 16.sp) }
                Row {
                    TextButton(onClick = { copyText(ctx, translated) }) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = { saver.launch("tarjuman-translation.txt") }) { Text(stringResource(R.string.export)) }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.original), color = Muted, fontSize = 12.sp)
                SelectionContainer { Text(original, color = Muted, fontSize = 13.sp) }
            }
        }
    }
}

// ---------------------------------------------------------------- models

@Composable
fun ModelsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wifiOnly by remember { mutableStateOf(true) }
    var confirmCore by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        downloaded = try { ModelStore.downloaded() } catch (e: Exception) { msg = e.message; emptySet() }
    }
    LaunchedEffect(Unit) { refresh() }

    fun download(code: String) {
        if (code in busy) return
        busy = busy + code
        scope.launch {
            try { ModelStore.download(code, wifiOnly) } catch (e: Exception) {
                msg = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
            busy = busy - code
            refresh()
        }
    }

    if (confirmCore) {
        AlertDialog(
            onDismissRequest = { confirmCore = false },
            title = { Text(stringResource(R.string.confirm_download_title)) },
            text = { Text(stringResource(R.string.confirm_download_msg, Languages.corePack.size * 30)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCore = false
                    Languages.corePack.filter { it !in downloaded }.forEach { download(it) }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmCore = false }) { Text(stringResource(R.string.no)) } }
        )
    }

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.models_note), color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            Text(stringResource(R.string.wifi_only), color = TextC, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
        }
        Button(onClick = { confirmCore = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.download_core)) }
        msg?.let { Text(it, color = Soft, fontSize = 12.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Languages.all, key = { it.code }) { l ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(Languages.label(l.code), color = TextC, fontSize = 16.sp)
                            Text(
                                if (l.code in downloaded) stringResource(R.string.model_ready) else stringResource(R.string.model_size),
                                color = Muted, fontSize = 12.sp
                            )
                        }
                        when {
                            l.code in busy -> Text(stringResource(R.string.processing), color = Soft, fontSize = 12.sp)
                            l.code in downloaded -> TextButton(onClick = {
                                scope.launch { try { ModelStore.delete(l.code) } catch (e: Exception) { msg = e.message }; refresh() }
                            }) { Text(stringResource(R.string.model_delete)) }
                            else -> OutlinedButton(onClick = { download(l.code) }) { Text(stringResource(R.string.model_download)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- history

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(HistoryStore.all()) }
    var q by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = all.filter {
        (!favOnly || it.fav) && (q.isBlank() || it.original.contains(q, true) || it.translated.contains(q, true))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_all)) },
            confirmButton = {
                TextButton(onClick = { HistoryStore.clear(); all = HistoryStore.all(); confirmClear = false }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.no)) } }
        )
    }

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.history_search)) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { favOnly = !favOnly }) {
                Text((if (favOnly) "★ " else "☆ ") + stringResource(R.string.favorites_only))
            }
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_all)) }
        }
        if (shown.isEmpty()) Text(stringResource(R.string.history_empty), color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { h ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(h.original, color = Muted, fontSize = 13.sp, maxLines = 3)
                    Spacer(Modifier.height(4.dp))
                    Text(h.translated, color = TextC, fontSize = 16.sp, maxLines = 5)
                    Text(
                        listOf(h.src.ifBlank { "?" } + " → " + h.tgt, h.engine, DateFormat.getDateTimeInstance().format(Date(h.ts)))
                            .joinToString(" · "),
                        color = Muted, fontSize = 11.sp
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { HistoryStore.toggleFav(h.id); all = HistoryStore.all() }) { Text(if (h.fav) "★" else "☆") }
                        TextButton(onClick = { copyText(ctx, h.translated) }) { Text(stringResource(R.string.copy)) }
                        TextButton(onClick = { shareText(ctx, h.translated) }) { Text(stringResource(R.string.share)) }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val mode = if (h.engine.startsWith("libre")) "offline_only" else "online_only"
                                    val r = Engine.translate(h.original, h.src.ifBlank { null }, h.tgt, mode)
                                    HistoryStore.add(h.original, r.text, r.detected, h.tgt, r.engine)
                                    all = HistoryStore.all()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, e.message ?: "error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) { Text(stringResource(R.string.retranslate_other)) }
                        TextButton(onClick = { HistoryStore.delete(h.id); all = HistoryStore.all() }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun Choice(options: List<Pair<String, Int>>, value: String, onChange: (String) -> Unit) {
    options.forEach { (k, label) ->
        Row(Modifier.fillMaxWidth().clickable { onChange(k) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == k, onClick = { onChange(k) })
            Text(stringResource(label), color = TextC, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Head(res: Int) = Text(stringResource(res), color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Bold)

@Composable
fun SettingsScreen() {
    var tgt by remember { mutableStateOf(Prefs.targetLang) }
    var ocr by remember { mutableStateOf(Prefs.ocrScript) }
    var mode by remember { mutableStateOf(Prefs.engineMode) }
    var url by remember { mutableStateOf(Prefs.libreUrl) }
    var key by remember { mutableStateOf(Prefs.libreKey) }
    var autoDl by remember { mutableStateOf(Prefs.autoDownload) }
    var display by remember { mutableStateOf(Prefs.displayMode) }
    var size by remember { mutableStateOf(Prefs.bubbleSizeDp.toFloat()) }
    var alpha by remember { mutableStateOf(Prefs.bubbleAlpha.toFloat()) }
    var hText by remember { mutableStateOf(Prefs.historyText) }
    var hScreen by remember { mutableStateOf(Prefs.historyScreen) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Head(R.string.set_target)
            LangPicker(tgt, false) { tgt = it; Prefs.targetLang = it }
        }
        Panel(Modifier.fillMaxWidth()) {
            Head(R.string.set_ocr)
            Choice(
                listOf("auto" to R.string.ocr_auto, "latin" to R.string.ocr_latin, "zh" to R.string.ocr_zh, "ja" to R.string.ocr_ja, "ko" to R.string.ocr_ko),
                ocr
            ) { ocr = it; Prefs.ocrScript = it }
            Text(stringResource(R.string.ocr_note), color = Muted, fontSize = 12.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Head(R.string.set_engine)
            Choice(
                listOf(
                    "offline_first" to R.string.eng_offline_first, "online_first" to R.string.eng_online_first,
                    "offline_only" to R.string.eng_offline_only, "online_only" to R.string.eng_online_only
                ),
                mode
            ) { mode = it; Prefs.engineMode = it }
            OutlinedTextField(
                value = url, onValueChange = { url = it; Prefs.libreUrl = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.set_libre_url)) }
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it; Prefs.libreKey = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.set_libre_key)) }
            )
            Text(stringResource(R.string.online_warning), color = Muted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoDl, onCheckedChange = { autoDl = it; Prefs.autoDownload = it })
                Text(stringResource(R.string.set_auto_dl), color = TextC, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Head(R.string.set_display)
            Choice(listOf("overlay" to R.string.disp_overlay, "panel" to R.string.disp_panel), display) { display = it; Prefs.displayMode = it }
        }
        Panel(Modifier.fillMaxWidth()) {
            Head(R.string.set_bubble)
            Text(stringResource(R.string.set_bubble_size) + ": " + size.toInt() + " dp", color = TextC, fontSize = 13.sp)
            Slider(value = size, onValueChange = { size = it; Prefs.bubbleSizeDp = it.toInt() }, valueRange = 40f..96f)
            Text(stringResource(R.string.set_bubble_alpha) + ": " + alpha.toInt() + "%", color = TextC, fontSize = 13.sp)
            Slider(value = alpha, onValueChange = { alpha = it; Prefs.bubbleAlpha = it.toInt() }, valueRange = 30f..100f)
            Text(stringResource(R.string.set_bubble_note), color = Muted, fontSize = 12.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Head(R.string.set_history)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = hText, onCheckedChange = { hText = it; Prefs.historyText = it })
                Text(stringResource(R.string.set_hist_text), color = TextC, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = hScreen, onCheckedChange = { hScreen = it; Prefs.historyScreen = it })
                Text(stringResource(R.string.set_hist_screen), color = TextC, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- about

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), color = TextC, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("TARJUMAN  v$version", color = Burgundy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tagline), color = Muted, fontSize = 14.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_channel_title), color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Links.TELEGRAM)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(ctx, Links.TELEGRAM, Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_channel)) }
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_title), color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.privacy_body), color = TextC, fontSize = 13.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_credits), color = Muted, fontSize = 12.sp)
        }
        Text(
            stringResource(R.string.salawat), color = Soft, fontSize = 18.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        )
    }
}
