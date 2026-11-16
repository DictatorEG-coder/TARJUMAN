package app.tarjuman.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tarjuman.R
import app.tarjuman.core.Languages

val Navy = Color(0xFF0B1530)
val SurfaceC = Color(0xFF14213F)
val Burgundy = Color(0xFF9B1B30)
val TextC = Color(0xFFE8ECF7)
val Muted = Color(0xFF9AA6C4)
val Soft = Color(0xFF7C9BD1)

@Composable
fun TarjumanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Burgundy, onPrimary = Color.White,
            secondary = Soft, background = Navy, onBackground = TextC,
            surface = SurfaceC, onSurface = TextC,
            surfaceVariant = Color(0xFF1B2A4D), onSurfaceVariant = Muted,
            outline = Color(0xFF3A4A70)
        ),
        content = content
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var m = modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceC)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), content = content)
}

@Composable
fun LangPicker(value: String, includeAuto: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (value == "auto") stringResource(R.string.lang_auto) else Languages.label(value), maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (includeAuto) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lang_auto)) },
                    onClick = { onChange("auto"); open = false }
                )
            }
            Languages.all.forEach { l ->
                DropdownMenuItem(
                    text = { Text(Languages.label(l.code)) },
                    onClick = { onChange(l.code); open = false }
                )
            }
        }
    }
}

fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("tarjuman", text))
    Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, null))
}
