@file:OptIn(UnstableApi::class)

package com.cortaai.app

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    var clips by mutableStateOf<List<Clip>>(emptyList())
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var musicUri by mutableStateOf<Uri?>(null)
        private set
    var musicDurationMs by mutableStateOf(0L)
        private set
    var musicVolume by mutableFloatStateOf(1f)
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    val thumbs = mutableStateMapOf<Long, ImageBitmap>()

    private var nextId = 1L
    private var transformer: Transformer? = null
    private var outFile: File? = null

    val selected: Clip? get() = clips.firstOrNull { it.id == selectedId }

    // ---------- importação ----------

    fun addMedia(uris: List<Uri>) {
        viewModelScope.launch {
            for (u in uris) {
                val clip = withContext(Dispatchers.IO) { probe(u) } ?: continue
                clips = clips + clip
                selectedId = clip.id
                loadThumb(clip)
            }
        }
    }

    private fun probe(uri: Uri): Clip? {
        val ctx = getApplication<Application>()
        val type = ctx.contentResolver.getType(uri) ?: ""
        val id = nextId++
        if (type.startsWith("image/")) {
            return Clip(id, uri, true, type, sourceMs = 15_000L, endMs = 3_000L)
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            Clip(id, uri, false, type, sourceMs = d)
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    private fun loadThumb(c: Clip) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { makeThumb(c) }
            if (bmp != null) thumbs[c.id] = bmp.asImageBitmap()
        }
    }

    private fun makeThumb(c: Clip): Bitmap? {
        val ctx = getApplication<Application>()
        return try {
            if (c.isImage) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                ctx.contentResolver.openInputStream(c.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, c.uri)
                    r.getFrameAtTime(c.startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, 160, 160 * it.height / it.width.coerceAtLeast(1), true) }
                } finally {
                    r.release()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- edição ----------

    fun select(id: Long) {
        selectedId = id
    }

    fun update(id: Long, f: (Clip) -> Clip) {
        clips = clips.map { if (it.id == id) f(it) else it }
    }

    fun split(id: Long, atSourceMs: Long) {
        val c = clips.firstOrNull { it.id == id } ?: return
        if (c.isImage) return
        if (atSourceMs <= c.startMs + 300 || atSourceMs >= c.endMs - 300) return
        val a = c.copy(endMs = atSourceMs)
        val b = c.copy(id = nextId++, startMs = atSourceMs)
        clips = clips.flatMap { if (it.id == id) listOf(a, b) else listOf(it) }
        selectedId = b.id
        loadThumb(b)
    }

    fun duplicate(id: Long) {
        val c = clips.firstOrNull { it.id == id } ?: return
        val copy = c.copy(id = nextId++)
        val i = clips.indexOfFirst { it.id == id }
        clips = clips.toMutableList().apply { add(i + 1, copy) }
        selectedId = copy.id
        thumbs[c.id]?.let { thumbs[copy.id] = it }
    }

    fun delete(id: Long) {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return
        clips = clips.filter { it.id != id }
        thumbs.remove(id)
        selectedId = clips.getOrNull(i.coerceAtMost(clips.lastIndex))?.id
    }

    fun move(id: Long, delta: Int) {
        val i = clips.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in clips.indices) return
        clips = clips.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun setMusic(uri: Uri?) {
        musicUri = uri
        musicDurationMs = 0L
        if (uri == null) return
        viewModelScope.launch {
            musicDurationMs = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(getApplication<Application>(), uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                } finally {
                    r.release()
                }
            }
        }
    }

    // ---------- exportação ----------

    fun export(presentation: Presentation?) {
        if (clips.isEmpty() || exportState is ExportState.Running) return
        val ctx = getApplication<Application>()
        val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        outFile = out

        val composition = try {
            buildComposition(clips, musicUri, musicDurationMs, musicVolume, presentation)
        } catch (e: Exception) {
            exportState = ExportState.Failed(e.message ?: "Falha ao montar o projeto")
            return
        }

        exportState = ExportState.Running(0f)
        val t = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    viewModelScope.launch {
                        val ok = saveToGallery(out)
                        out.delete()
                        exportState = ExportState.Done(ok)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    out.delete()
                    exportState = ExportState.Failed(exportException.message ?: "Erro na exportação")
                }
            })
            .build()
        transformer = t
        t.start(composition, out.absolutePath)

        viewModelScope.launch {
            val holder = ProgressHolder()
            while (exportState is ExportState.Running) {
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    exportState = ExportState.Running(holder.progress / 100f)
                }
                delay(250)
            }
        }
    }

    fun cancelExport() {
        transformer?.cancel()
        outFile?.delete()
        exportState = ExportState.Idle
    }

    fun resetExport() {
        if (exportState !is ExportState.Running) exportState = ExportState.Idle
    }

    private suspend fun saveToGallery(file: File): Boolean = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "CortaAi_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CortaAi")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { os ->
                file.inputStream().use { it.copyTo(os, 1 shl 20) }
            } ?: return@withContext false
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    override fun onCleared() {
        transformer?.cancel()
        super.onCleared()
    }
}
