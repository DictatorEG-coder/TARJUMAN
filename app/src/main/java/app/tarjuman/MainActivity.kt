package app.tarjuman

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tarjuman.ui.AppRoot
import app.tarjuman.ui.TarjumanTheme

class MainActivity : ComponentActivity() {
    var route by mutableStateOf("home")
    var sharedText by mutableStateOf<String?>(null)
    var sharedImage by mutableStateOf<Uri?>(null)
    var autostart by mutableStateOf(false)
    var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { TarjumanTheme { AppRoot(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    private fun <T : Parcelable> Intent.parcel(name: String, cls: Class<T>): T? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(name, cls)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra<T>(name)
        }

    private fun handle(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_SEND -> {
                val type = i.type ?: ""
                if (type.startsWith("text/")) {
                    sharedText = i.getStringExtra(Intent.EXTRA_TEXT)
                    route = "text"
                } else if (type.startsWith("image/")) {
                    sharedImage = i.parcel(Intent.EXTRA_STREAM, Uri::class.java)
                    route = "image"
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                sharedText = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                route = "text"
            }
            else -> {
                i.getStringExtra("route")?.let { route = it }
                if (i.getBooleanExtra("autostart", false)) autostart = true
            }
        }
    }
}
