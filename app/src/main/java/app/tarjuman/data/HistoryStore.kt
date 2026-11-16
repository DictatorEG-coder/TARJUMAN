package app.tarjuman.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val id: Long,
    val original: String,
    val translated: String,
    val src: String,
    val tgt: String,
    val engine: String,
    val ts: Long,
    val fav: Boolean
)

/** Local JSON history. Nothing leaves the device. */
object HistoryStore {
    private lateinit var file: File
    private val items = ArrayList<HistoryItem>()

    @Synchronized
    fun init(ctx: Context) {
        file = File(ctx.filesDir, "history.json")
        items.clear()
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (n in 0 until arr.length()) {
                    val o = arr.getJSONObject(n)
                    items.add(
                        HistoryItem(
                            o.getLong("id"), o.getString("o"), o.getString("t"),
                            o.optString("s"), o.optString("g"), o.optString("e"),
                            o.getLong("ts"), o.optBoolean("f")
                        )
                    )
                }
            } catch (e: Exception) {
                items.clear()
            }
        }
    }

    @Synchronized
    fun all(): List<HistoryItem> = items.sortedByDescending { it.ts }

    @Synchronized
    fun add(original: String, translated: String, src: String?, tgt: String, engine: String) {
        val now = System.currentTimeMillis()
        var id = now
        while (items.any { it.id == id }) id++
        items.add(HistoryItem(id, original, translated, src ?: "", tgt, engine, now, false))
        while (items.size > 2000) items.removeAt(0)
        save()
    }

    @Synchronized
    fun delete(id: Long) { items.removeAll { it.id == id }; save() }

    @Synchronized
    fun clear() { items.clear(); save() }

    @Synchronized
    fun toggleFav(id: Long) {
        val n = items.indexOfFirst { it.id == id }
        if (n >= 0) { items[n] = items[n].copy(fav = !items[n].fav); save() }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("o", it.original).put("t", it.translated)
                    .put("s", it.src).put("g", it.tgt).put("e", it.engine)
                    .put("ts", it.ts).put("f", it.fav)
            )
        }
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
