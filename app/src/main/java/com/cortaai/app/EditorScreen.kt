@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.cortaai.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private enum class Tool(val label: String) {
    TRIM("✂️ Cortar"),
    SPEED("⚡ Velocidade"),
    VOLUME("🔊 Volume"),
    FILTER("🎨 Filtros"),
    TEXT("🔤 Texto"),
}

private data class Preset(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
)

private val presets = listOf(
    Preset("Normal", 0f, 0f, 0f, 0f),
    Preset("Vívido", 0.05f, 0.2f, 0.45f, 0f),
    Preset("P&B", 0f, 0.15f, -1f, 0f),
    Preset("Quente", 0.03f, 0.05f, 0.15f, 0.7f),
    Preset("Frio", 0f, 0.05f, 0.05f, -0.7f),
    Preset("Cinema", -0.05f, 0.35f, -0.2f, 0.25f),
)

@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(player) { onDispose { player.release() } }

    var curIndex by remember { mutableIntStateOf(0) }
    var curPos by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf<Tool?>(null) }
    var showExport by remember { mutableStateOf(false) }

    val clips = vm.clips
    val selected = vm.selected

    SideEffect { view.keepScreenOn = vm.exportState is ExportState.Running }

    fun applyClip(index: Int) {
        val c = vm.clips.getOrNull(index) ?: return
        player.setPlaybackSpeed(c.speed)
        player.volume = c.volume.coerceAtMost(1f)
        try {
            player.setVideoEffects(videoEffectsFor(c, false))
        } catch (_: Exception) {
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyClip(player.currentMediaItemIndex)
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // Reconstrói a playlist só quando a estrutura (clipes/cortes) muda.
    val structure = clips.map { listOf(it.id, it.startMs, it.endMs) }
    LaunchedEffect(structure) {
        if (vm.clips.isEmpty()) {
            player.clearMediaItems()
        } else {
            val idx = vm.clips.indexOfFirst { it.id == vm.selectedId }.coerceAtLeast(0)
            player.setMediaItems(vm.clips.map { mediaItemFor(it) }, idx, 0L)
            player.prepare()
            applyClip(idx)
        }
    }
    // Ajustes (velocidade, filtros, volume, texto) valem na hora no clipe atual.
    LaunchedEffect(clips) { applyClip(player.currentMediaItemIndex) }

    LaunchedEffect(player) {
        while (true) {
            curIndex = player.currentMediaItemIndex
            curPos = player.currentPosition.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(100)
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) vm.addMedia(uris) }

    val pickMusic = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.setMusic(uri) }

    val total = clips.sumOf { it.trimmedMs }
    val before = clips.take(curIndex).sumOf { it.trimmedMs }
    val globalPos = (before + curPos).coerceIn(0L, maxOf(total, 0L))

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Barra superior
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CortaAi", fontSize = 20.sp, color = Accent)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showExport = true }, enabled = clips.isNotEmpty()) {
                Text("Exportar")
            }
        }

        // Pré-visualização
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (clips.isEmpty()) {
                Text(
                    "Toque em + para importar vídeos ou fotos",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Reprodução e navegação
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (clips.isEmpty()) return@TextButton
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
                        player.play()
                    }
                }
            ) { Text(if (playing) "⏸" else "▶", fontSize = 22.sp) }
            Text(
                "${formatTime(globalPos)} / ${formatTime(total)}",
                fontSize = 12.sp,
                color = Color.LightGray
            )
            Slider(
                value = if (total > 0) globalPos.toFloat() / total else 0f,
                onValueChange = { f ->
                    var target = (f * total).toLong()
                    var idx = 0
                    for ((i, c) in clips.withIndex()) {
                        if (target <= c.trimmedMs || i == clips.lastIndex) {
                            idx = i
                            break
                        }
                        target -= c.trimmedMs
                    }
                    player.seekTo(idx, target.coerceAtLeast(0L))
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }

        // Linha do tempo
        LazyRow(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(clips, key = { it.id }) { c ->
                ClipCard(
                    c = c,
                    thumb = vm.thumbs[c.id],
                    selected = c.id == vm.selectedId,
                    onClick = {
                        vm.select(c.id)
                        val i = vm.clips.indexOfFirst { it.id == c.id }
                        if (i >= 0) player.seekTo(i, 0L)
                    }
                )
            }
            item {
                Box(
                    Modifier
                        .width(64.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Accent)
                        .clickable {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Text("+", fontSize = 30.sp, color = Color.Black) }
            }
        }

        if (vm.musicUri != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎵 Música", fontSize = 12.sp, color = Color.LightGray)
                Slider(
                    value = vm.musicVolume,
                    onValueChange = { vm.musicVolume = it },
                    valueRange = 0f..2f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                TextButton(onClick = { vm.setMusic(null) }) { Text("Remover") }
            }
        }

        // Painel da ferramenta
        if (selected != null && tool != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                when (tool) {
                    Tool.TRIM -> TrimPanel(selected, vm)
                    Tool.SPEED -> SpeedPanel(selected, vm)
                    Tool.VOLUME -> VolumePanel(selected, vm)
                    Tool.FILTER -> FilterPanel(selected, vm)
                    Tool.TEXT -> TextPanel(selected, vm)
                    null -> {}
                }
            }
        }

        // Barra de ferramentas
        LazyRow(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(Tool.entries.toList()) { t ->
                ToolButton(t.label, active = tool == t) {
                    tool = if (tool == t) null else t
                }
            }
            item {
                ToolButton("🔪 Dividir") {
                    val c = vm.clips.getOrNull(curIndex) ?: return@ToolButton
                    vm.split(c.id, c.startMs + curPos)
                }
            }
            item {
                ToolButton("🔄 Girar") {
                    selected?.let { s -> vm.update(s.id) { it.copy(rotation = (it.rotation + 90) % 360) } }
                }
            }
            item { ToolButton("⬅️") { selected?.let { vm.move(it.id, -1) } } }
            item { ToolButton("➡️") { selected?.let { vm.move(it.id, 1) } } }
            item { ToolButton("📑 Duplicar") { selected?.let { vm.duplicate(it.id) } } }
            item { ToolButton("🗑 Excluir") { selected?.let { vm.delete(it.id) } } }
            item { ToolButton("🎵 Música") { pickMusic.launch(arrayOf("audio/*")) } }
        }
    }

    if (showExport) {
        ExportDialog(vm) { showExport = false }
    }
}

@Composable
private fun ToolButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = if (active) Accent else Color.White)
    }
}

@Composable
private fun ClipCard(c: Clip, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val w = (c.outputMs / 1000f * 36f).coerceAtLeast(64f).dp
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .width(w)
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF26262C))
            .border(if (selected) 2.dp else 0.dp, Accent, shape)
            .clickable(onClick = onClick)
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f
            )
        }
        val label = buildString {
            append(formatTime(c.outputMs))
            if (c.speed != 1f) append(" ⚡${c.speed}x")
            if (c.text.isNotBlank()) append(" 🔤")
        }
        Text(
            label,
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp)
        )
    }
}

// ---------- painéis ----------

@Composable
private fun TrimPanel(c: Clip, vm: EditorViewModel) {
    if (c.isImage) {
        var d by remember(c.id, c.endMs) { mutableFloatStateOf(c.trimmedMs / 1000f) }
        Text("Duração da foto: %.1fs".format(d), fontSize = 13.sp)
        Slider(
            value = d,
            onValueChange = { d = it },
            valueRange = 1f..15f,
            onValueChangeFinished = {
                vm.update(c.id) { x -> x.copy(endMs = (d * 1000).toLong()) }
            }
        )
    } else {
        var r by remember(c.id, c.startMs, c.endMs) {
            mutableStateOf(c.startMs.toFloat()..c.endMs.toFloat())
        }
        Text(
            "Trecho: ${formatTime(r.start.toLong())} – ${formatTime(r.endInclusive.toLong())}",
            fontSize = 13.sp
        )
        RangeSlider(
            value = r,
            onValueChange = { r = it },
            valueRange = 0f..c.sourceMs.toFloat(),
            onValueChangeFinished = {
                val s = r.start.toLong()
                val e = maxOf(r.endInclusive.toLong(), s + 200L)
                vm.update(c.id) { x -> x.copy(startMs = s, endMs = minOf(e, x.sourceMs)) }
            }
        )
    }
}

@Composable
private fun SpeedPanel(c: Clip, vm: EditorViewModel) {
    if (c.isImage) {
        Text("Fotos não têm velocidade.", fontSize = 13.sp, color = Color.Gray)
        return
    }
    Text("Velocidade: ${c.speed}x", fontSize = 13.sp)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)) { v ->
            FilterChip(
                selected = c.speed == v,
                onClick = { vm.update(c.id) { x -> x.copy(speed = v) } },
                label = { Text("${v}x") }
            )
        }
    }
}

@Composable
private fun VolumePanel(c: Clip, vm: EditorViewModel) {
    if (c.isImage) {
        Text("Fotos não têm áudio.", fontSize = 13.sp, color = Color.Gray)
        return
    }
    LabeledSlider("Volume", c.volume, 0f..2f) { v -> vm.update(c.id) { x -> x.copy(volume = v) } }
}

@Composable
private fun FilterPanel(c: Clip, vm: EditorViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(presets) { p ->
            FilterChip(
                selected = false,
                onClick = {
                    vm.update(c.id) { x ->
                        x.copy(
                            brightness = p.brightness,
                            contrast = p.contrast,
                            saturation = p.saturation,
                            warmth = p.warmth
                        )
                    }
                },
                label = { Text(p.name) }
            )
        }
    }
    LabeledSlider("Brilho", c.brightness, -1f..1f) { v -> vm.update(c.id) { x -> x.copy(brightness = v) } }
    LabeledSlider("Contraste", c.contrast, -1f..1f) { v -> vm.update(c.id) { x -> x.copy(contrast = v) } }
    LabeledSlider("Saturação", c.saturation, -1f..1f) { v -> vm.update(c.id) { x -> x.copy(saturation = v) } }
    LabeledSlider("Temperatura", c.warmth, -1f..1f) { v -> vm.update(c.id) { x -> x.copy(warmth = v) } }
}

@Composable
private fun TextPanel(c: Clip, vm: EditorViewModel) {
    OutlinedTextField(
        value = c.text,
        onValueChange = { t -> vm.update(c.id) { x -> x.copy(text = t) } },
        label = { Text("Texto na tela (durante todo o clipe)") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onFinish: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Slider(
            value = v,
            onValueChange = { v = it },
            valueRange = range,
            onValueChangeFinished = { onFinish(v) },
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------- exportação ----------

@Composable
private fun ExportDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    var aspect by remember { mutableStateOf(Aspect.ORIGINAL) }
    var res by remember { mutableIntStateOf(1080) }
    val st = vm.exportState

    AlertDialog(
        onDismissRequest = { if (st !is ExportState.Running) onDismiss() },
        title = { Text("Exportar vídeo") },
        text = {
            Column {
                if (st is ExportState.Failed) {
                    Text("Erro: ${st.message}", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
                when (st) {
                    is ExportState.Running -> {
                        LinearProgressIndicator(
                            progress = { st.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${(st.progress * 100).toInt()}% — mantenha o app aberto", fontSize = 12.sp)
                    }
                    is ExportState.Done -> Text(
                        if (st.saved) "Pronto! Salvo na galeria, pasta Filmes/CortaAi."
                        else "A exportação terminou, mas não consegui salvar na galeria."
                    )
                    else -> {
                        Text("Formato", fontSize = 12.sp, color = Color.Gray)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(Aspect.entries.toList()) { a ->
                                FilterChip(
                                    selected = aspect == a,
                                    onClick = { aspect = a },
                                    label = { Text(a.label) }
                                )
                            }
                        }
                        if (aspect == Aspect.ORIGINAL) {
                            Text(
                                "Mantém a resolução original dos clipes.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        } else {
                            Text("Resolução", fontSize = 12.sp, color = Color.Gray)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf(480 to "480p", 720 to "720p", 1080 to "1080p", 2160 to "4K")) { (v, l) ->
                                    FilterChip(
                                        selected = res == v,
                                        onClick = { res = v },
                                        label = { Text(l) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (st) {
                is ExportState.Done -> Button(onClick = { vm.resetExport(); onDismiss() }) { Text("OK") }
                is ExportState.Running -> {}
                else -> Button(onClick = { vm.export(presentationFor(res, aspect)) }) { Text("Exportar") }
            }
        },
        dismissButton = {
            if (st is ExportState.Running) {
                TextButton(onClick = { vm.cancelExport() }) { Text("Cancelar") }
            } else if (st !is ExportState.Done) {
                TextButton(onClick = { vm.resetExport(); onDismiss() }) { Text("Fechar") }
            }
        }
    )
}
