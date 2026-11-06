@file:OptIn(UnstableApi::class)

package com.cortaai.app

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList

/** Item de mídia (com corte) usado tanto na pré-visualização quanto na exportação. */
fun mediaItemFor(c: Clip): MediaItem {
    val b = MediaItem.Builder().setUri(c.uri)
    if (c.isImage) {
        if (c.mime.isNotEmpty()) b.setMimeType(c.mime)
        b.setImageDurationMs(c.trimmedMs)
    } else {
        b.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(c.startMs)
                .setEndPositionMs(c.endMs)
                .build()
        )
    }
    return b.build()
}

private fun textOverlay(text: String): Effect {
    val s = SpannableString(text)
    s.setSpan(ForegroundColorSpan(Color.WHITE), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    s.setSpan(AbsoluteSizeSpan(72), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val settings = OverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, -0.7f)
        .build()
    return OverlayEffect(
        ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(s, settings))
    )
}

/** Efeitos de vídeo do clipe. Na pré-visualização a velocidade é aplicada pelo player. */
fun videoEffectsFor(c: Clip, forExport: Boolean): List<Effect> {
    val l = mutableListOf<Effect>()
    if (forExport && !c.isImage && c.speed != 1f) l += SpeedChangeEffect(c.speed)
    if (c.rotation != 0) {
        l += ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(-c.rotation.toFloat())
            .build()
    }
    if (c.brightness != 0f) l += Brightness(c.brightness)
    if (c.contrast != 0f) l += Contrast(c.contrast)
    if (c.saturation != 0f) {
        l += HslAdjustment.Builder().adjustSaturation(c.saturation * 100f).build()
    }
    if (c.warmth != 0f) {
        l += RgbAdjustment.Builder()
            .setRedScale(1f + 0.25f * c.warmth)
            .setBlueScale(1f - 0.25f * c.warmth)
            .build()
    }
    if (c.text.isNotBlank()) l += textOverlay(c.text)
    return l
}

private fun volumeProcessor(volume: Float): AudioProcessor =
    ChannelMixingAudioProcessor().apply {
        for (ch in 1..2) {
            putChannelMixingMatrix(ChannelMixingMatrix.create(ch, ch).scaleBy(volume))
        }
    }

fun audioProcessorsFor(c: Clip): List<AudioProcessor> {
    val l = mutableListOf<AudioProcessor>()
    if (c.speed != 1f) l += SonicAudioProcessor().apply { setSpeed(c.speed) }
    if (c.volume != 1f) l += volumeProcessor(c.volume)
    return l
}

private fun editedItemFor(c: Clip): EditedMediaItem {
    val muted = c.volume <= 0.01f
    val b = EditedMediaItem.Builder(mediaItemFor(c))
    if (c.isImage) {
        b.setDurationUs(c.trimmedMs * 1000L).setFrameRate(30)
    } else if (muted) {
        b.setRemoveAudio(true)
    }
    val audio = if (c.isImage || muted) emptyList() else audioProcessorsFor(c)
    b.setEffects(Effects(audio, videoEffectsFor(c, true)))
    return b.build()
}

/** Tamanho de saída. Em "Original" não mexe na resolução de cada clipe. */
fun presentationFor(shortSide: Int, aspect: Aspect): Presentation? {
    fun even(x: Int) = x / 2 * 2
    return when (aspect) {
        Aspect.ORIGINAL -> null
        Aspect.PORTRAIT -> Presentation.createForWidthAndHeight(
            even(shortSide), even(shortSide * 16 / 9), Presentation.LAYOUT_SCALE_TO_FIT
        )
        Aspect.LANDSCAPE -> Presentation.createForWidthAndHeight(
            even(shortSide * 16 / 9), even(shortSide), Presentation.LAYOUT_SCALE_TO_FIT
        )
        Aspect.SQUARE -> Presentation.createForWidthAndHeight(
            even(shortSide), even(shortSide), Presentation.LAYOUT_SCALE_TO_FIT
        )
    }
}

fun buildComposition(
    clips: List<Clip>,
    music: Uri?,
    musicDurationMs: Long,
    musicVolume: Float,
    presentation: Presentation?,
): Composition {
    val items = clips.map { editedItemFor(it) }.toTypedArray()
    val video = EditedMediaItemSequence.Builder(*items)
        .experimentalSetForceAudioTrack(true)
        .build()

    val sequences = mutableListOf(video)
    if (music != null) {
        val total = clips.sumOf { it.outputMs }
        val end = if (musicDurationMs > 0) minOf(total, musicDurationMs) else total
        val mi = MediaItem.Builder()
            .setUri(music)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(end).build()
            )
            .build()
        val ei = EditedMediaItem.Builder(mi)
            .setRemoveVideo(true)
            .setEffects(Effects(listOf(volumeProcessor(musicVolume)), emptyList()))
            .build()
        sequences += EditedMediaItemSequence.Builder(ei).build()
    }

    val b = Composition.Builder(sequences)
    if (presentation != null) {
        b.setEffects(Effects(emptyList(), listOf<Effect>(presentation)))
    }
    return b.build()
}
