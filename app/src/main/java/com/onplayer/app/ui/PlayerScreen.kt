package com.onplayer.app.ui

import android.content.Intent
import kotlin.math.abs
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.onplayer.app.data.MediaFile
import com.onplayer.app.data.MediaCategory

@Composable
fun PlayerScreen(file: MediaFile?, files: List<MediaFile>, vm: OnPlayerViewModel, nav: NavHostController) {
    val context = LocalContext.current
    if (file == null) {
        Column(Modifier.fillMaxSize().padding(bottom = 90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF9B4DFF), modifier = Modifier.size(72.dp))
            Text("Choose a media file", color = Color.White)
            Button(onClick = { nav.navigate("library") }) { Text("Browse Library") }
        }
        return
    }
    if (file.category == MediaCategory.Images) { ImagePreview(file, files, vm, nav); return }
    val player = remember(file.uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(file.uri)); prepare(); playWhenReady = false } }
    DisposableEffect(player) { onDispose { player.release() } }
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var repeat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(bottom = 90.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text(file.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { repeat = !repeat; player.repeatMode = if (repeat) ExoPlayer.REPEAT_MODE_ONE else ExoPlayer.REPEAT_MODE_OFF }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Color(0xFF9B4DFF) else Color.Gray) }
        }
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); useController = true } }, modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(22.dp)))
        Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(file.category.label + " • " + file.sizeText, color = Color.Gray)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }) { Icon(Icons.Default.Replay10, null, tint = Color.White) }
            IconButton(onClick = { isPlaying = !isPlaying; if (isPlaying) player.play() else player.pause() }, modifier = Modifier.size(68.dp)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF9B4DFF), modifier = Modifier.size(48.dp)) }
            IconButton(onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration.coerceAtLeast(0))) }) { Icon(Icons.Default.Forward10, null, tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Playback speed", color = Color.Gray)
            Row { listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { v -> Text("${v}x", color = if (speed == v) Color(0xFF9B4DFF) else Color.Gray, modifier = Modifier.padding(5.dp).clickable { speed = v; player.setPlaybackSpeed(v) }) } }
        }
        if (file.category == MediaCategory.Videos) {
            Button(onClick = {
                val seconds = (player.currentPosition / 1000).coerceAtLeast(0)
                val query = "${file.name} scene at ${seconds}s"
                val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
                try { context.startActivity(intent) } catch (_: Exception) {
                    val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query)))
                    context.startActivity(fallback)
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33205D))) {
                Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search This Scene / Title")
            }
        }
    }
}

@Composable private fun ImagePreview(file: MediaFile, files: List<MediaFile>, vm: OnPlayerViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val imageFiles = remember(files) { files.filter { it.category == MediaCategory.Images } }
    var current by remember(file.uri) { mutableStateOf(file) }
    var scale by remember(file.uri) { mutableFloatStateOf(1f) }
    var rotation by remember(file.uri) { mutableFloatStateOf(0f) }
    var panX by remember(file.uri) { mutableFloatStateOf(0f) }
    var panY by remember(file.uri) { mutableFloatStateOf(0f) }
    var swipeHandled by remember(file.uri) { mutableStateOf(false) }
    val index = imageFiles.indexOfFirst { it.uri == current.uri }
    fun move(delta: Int) {
        if (imageFiles.isEmpty() || index < 0) return
        val next = (index + delta).coerceIn(0, imageFiles.lastIndex)
        if (next != index && next >= 0) {
            current = imageFiles[next]
            vm.open(current)
            scale = 1f; rotation = 0f; panX = 0f; panY = 0f; swipeHandled = false
        }
    }
    Column(Modifier.fillMaxSize().padding(bottom = 90.dp)) {
        Row(Modifier.fillMaxWidth().background(Color(0xAA211D36), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(current.name, color = Color.White, maxLines = 1)
                Text("${index + 1} / ${imageFiles.size} • ${current.sizeText}", color = Color(0xFFBDB6D6), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { scale = 1f; rotation = 0f; panX = 0f; panY = 0f }) { Icon(Icons.Default.FitScreen, null, tint = Color.White) }
            IconButton(onClick = { rotation += 90f }) { Icon(Icons.Default.RotateRight, null, tint = Color.White) }
            IconButton(onClick = {
                try { val share = Intent(Intent.ACTION_SEND).apply { type = current.mime; putExtra(Intent.EXTRA_STREAM, current.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(share, "Share image")) } catch (_: Exception) { }
            }) { Icon(Icons.Default.Share, null, tint = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } }, update = { it.setImageURI(current.uri) }, modifier = Modifier.fillMaxSize().pointerInput(current.uri) {
                detectTransformGestures { _, pan, zoom, rot ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    rotation += rot
                    if (scale > 1.05f) { panX += pan.x; panY += pan.y }
                    else if (abs(pan.x) > 65f && !swipeHandled) { move(if (pan.x > 0) -1 else 1); swipeHandled = true }
                    if (abs(pan.x) < 8f) swipeHandled = false
                }
            }.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation; translationX = panX; translationY = panY })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { move(-1) }, enabled = index > 0) { Icon(Icons.Default.ChevronLeft, "Previous image", tint = if (index > 0) Color.White else Color.Gray) }
            Text(current.name, color = Color(0xFFC7C0DF), maxLines = 1, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { move(1) }, enabled = index >= 0 && index < imageFiles.lastIndex) { Icon(Icons.Default.ChevronRight, "Next image", tint = if (index >= 0 && index < imageFiles.lastIndex) Color.White else Color.Gray) }
        }
    }
}
