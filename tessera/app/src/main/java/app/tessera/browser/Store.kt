package app.tessera.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/** Durable app state in one JSON file, written atomically and debounced. */
class Store(context: Context) {
    private val file = File(context.filesDir, "tessera_state.json")
    private val tmp = File(context.filesDir, "tessera_state.json.tmp")
    private val handler = Handler(Looper.getMainLooper())
    private val writer = Runnable { saveNow() }

    val state: AppState = load()

    private fun load(): AppState =
        runCatching { if (file.exists()) StateCodec.decode(file.readText()) else null }.getOrNull()
            ?: AppState().also { it.ensureValid() }

    fun save() {
        handler.removeCallbacks(writer)
        handler.postDelayed(writer, 400)
    }

    fun saveNow() {
        handler.removeCallbacks(writer)
        runCatching {
            tmp.writeText(StateCodec.encode(state))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
