package com.mkveditor.app

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import org.json.JSONObject

object Engine {

    /** Convierte un Uri elegido por el usuario en un parámetro que FFmpeg entiende. */
    fun safParam(ctx: Context, uri: Uri): String =
        FFmpegKitConfig.getSafParameterForRead(ctx, uri)

    private fun tag(tags: JSONObject?, key: String): String {
        if (tags == null) return ""
        val a = tags.optString(key, "")
        return if (a.isNotBlank()) a else tags.optString(key.uppercase(), "")
    }

    /** Lee las pistas de un archivo con FFprobe. */
    fun probe(param: String): ProbeResult? {
        val session = FFprobeKit.getMediaInformation(param)
        val info = session.mediaInformation ?: return null
        val list = (info.streams ?: emptyList()).map { s ->
            val type = s.type ?: "data"
            val tags = s.tags
            val detail = when (type) {
                "video" -> "${s.width?.toString() ?: "?"}x${s.height?.toString() ?: "?"}"
                "audio" -> listOfNotNull(
                    s.channelLayout,
                    s.sampleRate?.let { "$it Hz" }
                ).joinToString(" · ")
                else -> ""
            }
            val title = if (type == "attachment") tag(tags, "filename") else tag(tags, "title")
            val disp = s.allProperties?.optJSONObject("disposition")
            StreamInfo(
                index = (s.index ?: 0L).toInt(),
                type = type,
                codec = s.codec ?: "?",
                title = title,
                language = tag(tags, "language"),
                isDefault = (disp?.optInt("default", 0) ?: 0) == 1,
                detail = detail
            )
        }
        return ProbeResult(list, info.duration?.toDoubleOrNull() ?: 0.0)
    }

    /** "01:30:05", "02:10" o "95.5" -> segundos. Devuelve null si no es válido. */
    fun parseTime(text: String): Double? {
        val parts = text.trim().split(":")
        if (parts.isEmpty() || parts.size > 3) return null
        var total = 0.0
        for (p in parts) {
            val v = p.toDoubleOrNull() ?: return null
            total = total * 60 + v
        }
        return total
    }

    /** Ejecuta FFmpeg (bloqueante). Devuelve (éxito, registro). */
    fun run(args: List<String>): Pair<Boolean, String> {
        val s = FFmpegKit.executeWithArguments(args.toTypedArray())
        val ok = ReturnCode.isSuccess(s.returnCode)
        return ok to (s.allLogsAsString ?: "")
    }

    /** Construye el comando para editar pistas/metadatos (sin recodificar). */
    fun buildEdit(
        mainParam: String,
        extras: List<String>,
        tracks: List<Track>,
        opts: EditOptions,
        outPath: String
    ): List<String> {
        val a = mutableListOf("-y", "-hide_banner")
        if (opts.start.isNotBlank()) a += listOf("-ss", opts.start)
        if (opts.end.isNotBlank()) a += listOf("-to", opts.end)
        a += listOf("-i", mainParam)
        extras.forEach { a += listOf("-i", it) }

        val kept = tracks.filter { it.keep }
        kept.forEach { a += listOf("-map", "${it.inputIdx}:${it.streamIndex}") }

        a += listOf("-c", "copy")
        a += listOf("-map_metadata", if (opts.stripMetadata) "-1" else "0")
        if (opts.removeChapters) a += listOf("-map_chapters", "-1")
        if (opts.globalTitle.isNotBlank()) a += listOf("-metadata", "title=${opts.globalTitle}")

        val counters = mutableMapOf<String, Int>()
        for (t in kept) {
            val letter = when (t.type) {
                "video" -> "v"
                "audio" -> "a"
                "subtitle" -> "s"
                else -> null
            } ?: continue
            val n = counters.getOrDefault(letter, 0)
            a += listOf("-metadata:s:$letter:$n", "title=${t.newTitle}")
            if (t.newLanguage.isNotBlank()) {
                a += listOf("-metadata:s:$letter:$n", "language=${t.newLanguage}")
            }
            if (letter != "v") {
                a += listOf("-disposition:$letter:$n", if (t.newDefault) "default" else "0")
            }
            counters[letter] = n + 1
        }
        a += outPath
        return a
    }
}
