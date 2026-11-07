package com.mkveditor.app

enum class Filter { ALL, AUDIO, SUB }

/** Pista detectada en un archivo (principal o externo). */
data class Track(
    val uid: Int,
    val inputIdx: Int,          // 0 = MKV principal, 1.. = archivos externos añadidos
    val streamIndex: Int,       // índice de la pista dentro de su archivo
    val type: String,           // video, audio, subtitle, attachment, data
    val codec: String,
    val detail: String,
    val title: String,
    val language: String,
    val isDefault: Boolean,
    val origin: String,         // "" para el principal, nombre para externos
    val keep: Boolean = true,
    val newTitle: String = title,
    val newLanguage: String = language,
    val newDefault: Boolean = isDefault
)

data class StreamInfo(
    val index: Int,
    val type: String,
    val codec: String,
    val title: String,
    val language: String,
    val isDefault: Boolean,
    val detail: String
)

data class ProbeResult(val streams: List<StreamInfo>, val durationSec: Double)

data class EditOptions(
    val start: String,
    val end: String,
    val globalTitle: String,
    val stripMetadata: Boolean,
    val removeChapters: Boolean
)
