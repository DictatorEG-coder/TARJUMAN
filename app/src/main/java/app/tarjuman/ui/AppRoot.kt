package app.tarjuman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tarjuman.MainActivity
import app.tarjuman.R

@Composable
fun AppRoot(a: MainActivity) {
    var screen by remember { mutableStateOf("home") }
    LaunchedEffect(a.route) {
        if (a.route != "home") { screen = a.route; a.route = "home" }
    }
    BackHandler(enabled = screen != "home") { screen = "home" }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val title = when (screen) {
        "text" -> R.string.home_text
        "image" -> R.string.home_image
        "doc" -> R.string.home_doc
        "history" -> R.string.home_history
        "models" -> R.string.home_models
        "settings" -> R.string.home_settings
        "about" -> R.string.home_about
        else -> R.string.app_name
    }
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding()) {
        if (screen != "home") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clickable { screen = "home" }.padding(12.dp)) {
                    Text(if (rtl) "→" else "←", color = TextC, fontSize = 22.sp)
                }
                Text(stringResource(title), color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            when (screen) {
                "text" -> TextScreen(a)
                "image" -> ImageScreen(a)
                "doc" -> DocScreen()
                "history" -> HistoryScreen()
                "models" -> ModelsScreen()
                "settings" -> SettingsScreen()
                "about" -> AboutScreen()
                else -> HomeScreen(a) { screen = it }
            }
        }
    }
}
