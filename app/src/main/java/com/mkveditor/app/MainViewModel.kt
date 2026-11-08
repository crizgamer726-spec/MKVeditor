package com.mkveditor.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>().applicationContext

    var fileName by mutableStateOf<String?>(null)
    var durationSec by mutableStateOf(0.0)
    var filter by mutableStateOf(Filter.ALL)
    var globalTitle by mutableStateOf("")
    var stripMetadata by mutableStateOf(false)
    var removeChapters by mutableStateOf(false)
    var trimStart by mutableStateOf("")
    var trimEnd by mutableStateOf("")
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var message by mutableStateOf("")
    var saveRequest by mutableStateOf(0)
    var suggestedName = "editado.mkv"
    val tracks = mutableStateListOf<Track>()

    private var mainParam: String? = null
    private val extras = mutableListOf<String>()
    private var nextUid = 1
    private var resultFile: File? = null

    @Volatile
    private var totalMs = 0.0

    init {
        FFmpegKitConfig.enableStatisticsCallback { s ->
            if (totalMs > 0) {
                progress = (s.time.toDouble() / totalMs).coerceIn(0.0, 1.0).toFloat()
            }
        }
    }

    private fun queryName(uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun StreamInfo.toTrack(inputIdx: Int, origin: String) = Track(
        uid = nextUid++, inputIdx = inputIdx, streamIndex = index, type = type, codec = codec,
        detail = detail, title = title, language = language, isDefault = isDefault, origin = origin
    )

    private fun cleanCache() {
        ctx.cacheDir.listFiles()?.forEach {
            if (it.name.startsWith("out_") || it.name.startsWith("merge_") || it.name == "list.txt") {
                it.delete()
            }
        }
    }

    // ---------- Agregar MKV ----------
    fun loadMain(uri: Uri) {
        viewModelScope.launch {
            busy = true
            message = "Leyendo pistas…"
            try {
                val param = Engine.safParam(ctx, uri)
                val r = withContext(Dispatchers.IO) { Engine.probe(param) }
                if (r == null || r.streams.isEmpty()) {
                    message = "No se pudo leer el archivo."
                } else {
                    mainParam = param
                    extras.clear()
                    tracks.clear()
                    r.streams.forEach { tracks.add(it.toTrack(0, "")) }
                    fileName = queryName(uri) ?: "video.mkv"
                    durationSec = r.durationSec
                    globalTitle = ""
                    stripMetadata = false
                    removeChapters = false
                    trimStart = ""
                    trimEnd = ""
                    filter = Filter.ALL
                    val base = (fileName ?: "video").substringBeforeLast(".")
                    suggestedName = "${base}_editado.mkv"
                    message = "Archivo cargado: ${tracks.size} pistas."
                }
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    // ---------- Añadir subtítulo / audio externo ----------
    fun addExternal(uri: Uri) {
        if (mainParam == null) {
            message = "Primero agrega un MKV."
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val param = Engine.safParam(ctx, uri)
                val r = withContext(Dispatchers.IO) { Engine.probe(param) }
                val usable = r?.streams?.filter { it.type == "audio" || it.type == "subtitle" }.orEmpty()
                if (usable.isEmpty()) {
                    message = "El archivo no contiene audio ni subtítulos válidos."
                } else {
                    extras.add(param)
                    val idx = extras.size
                    val name = queryName(uri) ?: "externo"
                    usable.forEach { tracks.add(it.toTrack(idx, name)) }
                    message = "Se añadieron ${usable.size} pista(s) de $name."
                }
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun updateTrack(uid: Int, change: (Track) -> Track) {
        val i = tracks.indexOfFirst { it.uid == uid }
        if (i >= 0) tracks[i] = change(tracks[i])
    }

    // ---------- Guardar (editar pistas, metadatos, recorte) ----------
    fun export() {
        val main = mainParam ?: return
        if (busy) return
        if (tracks.none { it.keep && (it.type == "video" || it.type == "audio" || it.type == "subtitle") }) {
            message = "Debes conservar al menos una pista."
            return
        }
        val opts = EditOptions(trimStart.trim(), trimEnd.trim(), globalTitle.trim(), stripMetadata, removeChapters)
        val startSec = if (opts.start.isBlank()) 0.0 else Engine.parseTime(opts.start)
        val endSec = if (opts.end.isBlank()) durationSec else Engine.parseTime(opts.end)
        if (startSec == null || endSec == null) {
            message = "Tiempo inválido. Usa el formato HH:MM:SS."
            return
        }
        totalMs = ((if (endSec > 0) endSec else durationSec) - startSec).coerceAtLeast(1.0) * 1000
        val snapshot = tracks.toList()
        val extraCopy = extras.toList()

        viewModelScope.launch {
            busy = true
            progress = 0f
            message = "Procesando… no cierres la app."
            try {
                val (ok, log, out) = withContext(Dispatchers.IO) {
                    cleanCache()
                    val outFile = File(ctx.cacheDir, "out_${System.currentTimeMillis()}.mkv")
                    val args = Engine.buildEdit(main, extraCopy, snapshot, opts, outFile.absolutePath)
                    val res = Engine.run(args)
                    Triple(res.first, res.second, outFile)
                }
                finish(ok, log, out)
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    // ---------- Combinar varios MKV ----------
    fun merge(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size < 2) {
            message = "Selecciona al menos 2 archivos MKV."
            return
        }
        if (busy) return
        viewModelScope.launch {
            busy = true
            progress = 0f
            message = "Preparando archivos…"
            try {
                val (ok, log, out) = withContext(Dispatchers.IO) {
                    cleanCache()
                    val files = uris.mapIndexed { i, u ->
                        val f = File(ctx.cacheDir, "merge_$i.mkv")
                        ctx.contentResolver.openInputStream(u)!!.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        f
                    }
                    totalMs = files.sumOf { Engine.probe(it.absolutePath)?.durationSec ?: 0.0 } * 1000
                    val list = File(ctx.cacheDir, "list.txt")
                    list.writeText(files.joinToString("\n") { "file '${it.absolutePath}'" })
                    val outFile = File(ctx.cacheDir, "out_${System.currentTimeMillis()}.mkv")
                    withContext(Dispatchers.Main) { message = "Combinando… no cierres la app." }
                    val res = Engine.run(
                        listOf(
                            "-y", "-hide_banner", "-f", "concat", "-safe", "0",
                            "-i", list.absolutePath, "-map", "0", "-c", "copy", outFile.absolutePath
                        )
                    )
                    files.forEach { it.delete() }
                    Triple(res.first, res.second, outFile)
                }
                suggestedName = "combinado.mkv"
                finish(ok, log, out)
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun finish(ok: Boolean, log: String, out: File) {
        if (ok && out.exists() && out.length() > 0) {
            resultFile = out
            progress = 1f
            message = "Listo. Elige dónde guardar el archivo."
            saveRequest++
        } else {
            message = "FFmpeg falló:\n" + log.takeLast(400)
        }
    }

    // ---------- Copiar resultado a la ubicación elegida ----------
    fun saveTo(uri: Uri) {
        val f = resultFile ?: return
        viewModelScope.launch {
            busy = true
            message = "Guardando…"
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { o ->
                        f.inputStream().use { it.copyTo(o) }
                    }
                }
                message = "Guardado correctamente ✓"
            } catch (e: Exception) {
                message = "Error al guardar: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun canSaveAgain() = resultFile?.exists() == true
    fun requestSaveAgain() { if (canSaveAgain()) saveRequest++ }
}
