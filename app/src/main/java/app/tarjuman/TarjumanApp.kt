package app.tarjuman

import android.app.Application
import app.tarjuman.core.Prefs
import app.tarjuman.data.HistoryStore

class TarjumanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        HistoryStore.init(this)
    }
}
