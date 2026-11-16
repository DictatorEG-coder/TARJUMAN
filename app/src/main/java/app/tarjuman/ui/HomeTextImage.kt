package app.tarjuman.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.tarjuman.MainActivity
import app.tarjuman.R
import app.tarjuman.core.Prefs
import app.tarjuman.data.HistoryStore
import app.tarjuman.engine.Engine
import app.tarjuman.engine.ModelMissing
import app.tarjuman.engine.Pipeline
import app.tarjuman.overlay.TarjumanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun HomeScreen(a: MainActivity, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(TarjumanService.running) }
    LaunchedEffect(Unit) { while (true) { running = TarjumanService.running; delay(700) } }

    val tick = a.tick
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    val projLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == Activity.RESULT_OK && d != null) {
            TarjumanService.start(ctx, r.resultCode, d)
        } else {
            Toast.makeText(ctx, R.string.projection_denied, Toast.LENGTH_SHORT).show()
        }
    }
    fun launchProjection() {
        projLauncher.launch(ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        a.tick++
        launchProjection()
    }
    fun startFlow() {
        if (!Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            return
        }
        if (!notifOk) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else launchProjection()
    }
    LaunchedEffect(a.autostart) {
        if (a.autostart) { a.autostart = false; startFlow() }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), color = TextC, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("TARJUMAN", color = Burgundy, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tagline), color = TextC, fontSize = 17.sp)
            Text(stringResource(R.string.subtitle), color = Muted, fontSize = 13.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            PermRow(R.string.perm_overlay, overlayOk) { startFlow() }
            PermRow(R.string.perm_notif, notifOk) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            Spacer(Modifier.height(8.dp))
            if (running) {
                Text(stringResource(R.string.bubble_running), color = Soft, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { TarjumanService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bubble_stop))
                }
            } else {
                Button(onClick = { startFlow() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bubble_start))
                }
            }
        }
        Text(stringResource(R.string.first_run_hint), color = Muted, fontSize = 12.sp)
        listOf(
            listOf("text" to R.string.home_text, "image" to R.string.home_image),
            listOf("doc" to R.string.home_doc, "history" to R.string.home_history),
            listOf("models" to R.string.home_models, "settings" to R.string.home_settings),
            listOf("about" to R.string.home_about)
        ).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (route, label) ->
                    Panel(Modifier.weight(1f), onClick = { go(route) }) {
                        Text(stringResource(label), color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PermRow(label: Int, ok: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), color = TextC, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (ok) Text(stringResource(R.string.perm_granted), color = Soft, fontSize = 13.sp)
        else TextButton(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
    }
}

class TtsHolder(ctx: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { ready = (it == TextToSpeech.SUCCESS) }

    fun speak(text: String, lang: String): Boolean {
        if (!ready) return false
        val r = tts.setLanguage(Locale.forLanguageTag(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tarjuman")
        return true
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}

@Composable
fun TextScreen(a: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var src by remember { mutableStateOf(Prefs.sourceLang) }
    var tgt by remember { mutableStateOf(Prefs.targetLang) }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<String?>(null) }
    var engine by remember { mutableStateOf("") }
    val tts = remember { TtsHolder(ctx) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(a.sharedText) { a.sharedText?.let { input = it; a.sharedText = null } }

    fun doTranslate() {
        if (input.isBlank() || busy) return
        busy = true; error = null; missing = false
        Prefs.sourceLang = src; Prefs.targetLang = tgt
        scope.launch {
            try {
                val r = Engine.translate(input, src, tgt)
                output = r.text; detected = r.detected; engine = r.engine
                if (Prefs.historyText) HistoryStore.add(input, r.text, r.detected, tgt, r.engine)
            } catch (e: ModelMissing) {
                missing = true
                error = ctx.getString(R.string.model_missing, e.langs.joinToString { app.tarjuman.core.Languages.label(it) })
            } catch (e: Exception) {
                error = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }

    fun swap() {
        val newSrc = tgt
        val newTgt = if (src == "auto") (detected ?: "en") else src
        src = newSrc; tgt = newTgt
        if (output.isNotBlank()) { val t = input; input = output; output = t }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LangPicker(src, true, Modifier.weight(1f)) { src = it }
            TextButton(onClick = { swap() }) { Text("⇄", fontSize = 20.sp) }
            LangPicker(tgt, false, Modifier.weight(1f)) { tgt = it }
        }
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
            placeholder = { Text(stringResource(R.string.input_hint)) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { doTranslate() }, enabled = !busy && input.isNotBlank()) {
                Text(if (busy) stringResource(R.string.processing) else stringResource(R.string.translate))
            }
            OutlinedButton(onClick = { input = ""; output = ""; error = null }) { Text(stringResource(R.string.clear)) }
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.let { input = it.toString() }
            }) { Text(stringResource(R.string.paste)) }
        }
        error?.let {
            Panel(Modifier.fillMaxWidth()) {
                Text(it, color = TextC, fontSize = 14.sp)
                if (missing) TextButton(onClick = { a.route = "models" }) { Text(stringResource(R.string.open_models)) }
            }
        }
        if (output.isNotBlank()) {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.translation) + (detected?.let { " · " + app.tarjuman.core.Languages.label(it) + " → " + app.tarjuman.core.Languages.label(tgt) } ?: ""),
                    color = Muted, fontSize = 12.sp
                )
                SelectionContainer { Text(output, color = TextC, fontSize = 18.sp) }
                Text(stringResource(R.string.engine_used, engine), color = Muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { copyText(ctx, output) }) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = { shareText(ctx, output) }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = {
                        if (!tts.speak(output, tgt)) Toast.makeText(ctx, R.string.tts_unavailable, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.speak)) }
                }
            }
        }
    }
}

@Composable
fun ImageScreen(a: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var tgt by remember { mutableStateOf(Prefs.targetLang) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var original by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var noText by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) uri = u }
    LaunchedEffect(a.sharedImage) { a.sharedImage?.let { uri = it; a.sharedImage = null } }

    fun run() {
        val u = uri ?: return
        busy = true; error = null; noText = false; original = ""; translated = ""
        scope.launch {
            try {
                val bmp = thumb ?: withContext(Dispatchers.IO) { Pipeline.loadBitmap(ctx, u) }
                thumb = bmp
                val out = Pipeline.bitmap(bmp, tgt)
                if (out.original.isBlank()) noText = true
                original = out.original; translated = out.translated; engine = out.engine
            } catch (e: ModelMissing) {
                error = ctx.getString(R.string.model_missing, e.langs.joinToString { app.tarjuman.core.Languages.label(it) })
            } catch (e: Exception) {
                error = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(uri) { if (uri != null) { thumb = null; run() } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(stringResource(R.string.pick_image)) }
            LangPicker(tgt, false, Modifier.weight(1f)) { tgt = it; Prefs.targetLang = it; if (uri != null) run() }
        }
        thumb?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
        }
        if (busy) Text(stringResource(R.string.processing), color = Muted)
        error?.let { Panel(Modifier.fillMaxWidth()) { Text(it, color = TextC, fontSize = 14.sp) } }
        if (noText) Text(stringResource(R.string.no_text), color = Muted)
        if (translated.isNotBlank()) {
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.translation), color = Muted, fontSize = 12.sp)
                SelectionContainer { Text(translated, color = TextC, fontSize = 18.sp) }
                Text(stringResource(R.string.engine_used, engine), color = Muted, fontSize = 11.sp)
                Row {
                    TextButton(onClick = { copyText(ctx, translated) }) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = { shareText(ctx, translated) }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = {
                        HistoryStore.add(original, translated, null, tgt, engine)
                        Toast.makeText(ctx, R.string.saved, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.save_history)) }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.original), color = Muted, fontSize = 12.sp)
                SelectionContainer { Text(original, color = Muted, fontSize = 14.sp) }
                TextButton(onClick = { copyText(ctx, original) }) { Text(stringResource(R.string.copy)) }
            }
        }
    }
}
