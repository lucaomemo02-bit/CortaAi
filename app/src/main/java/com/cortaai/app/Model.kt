package com.cortaai.app

import android.net.Uri

/** Um clipe na linha do tempo (vídeo ou foto) com todos os ajustes. */
data class Clip(
    val id: Long,
    val uri: Uri,
    val isImage: Boolean,
    val mime: String,
    val sourceMs: Long,
    val startMs: Long = 0L,
    val endMs: Long = sourceMs,
    val speed: Float = 1f,
    val volume: Float = 1f,      // 0..2
    val rotation: Int = 0,       // 0, 90, 180, 270 (horário)
    val brightness: Float = 0f,  // -1..1
    val contrast: Float = 0f,    // -1..1
    val saturation: Float = 0f,  // -1..1
    val warmth: Float = 0f,      // -1 (frio) .. 1 (quente)
    val text: String = "",
) {
    /** Duração depois do corte, antes da velocidade. */
    val trimmedMs: Long get() = endMs - startMs

    /** Duração final no vídeo exportado. */
    val outputMs: Long get() = (trimmedMs / speed).toLong()
}

enum class Aspect(val label: String) {
    ORIGINAL("Original"),
    PORTRAIT("9:16"),
    LANDSCAPE("16:9"),
    SQUARE("1:1"),
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val saved: Boolean) : ExportState
    data class Failed(val message: String) : ExportState
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val d = (ms % 1000) / 100
    return if (h > 0) "%d:%02d:%02d.%d".format(h, m, s, d) else "%d:%02d.%d".format(m, s, d)
}
