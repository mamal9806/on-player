package com.onplayer.app.ui

import android.content.Intent
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
    if (file.category == MediaCategory.Images) { ImagePreview(file, nav); return }
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

@Composable private fun ImagePreview(file: MediaFile, nav: NavHostController) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().padding(bottom = 90.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text(file.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { rotation += 90f }) { Icon(Icons.Default.RotateRight, null, tint = Color.White) }
            IconButton(onClick = {
                try { val share = Intent(Intent.ACTION_SEND).apply { type = file.mime; putExtra(Intent.EXTRA_STREAM, file.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(share, "Share image")) } catch (_: Exception) { }
            }) { Icon(Icons.Default.Share, null, tint = Color.White) }
        }
        AndroidView(factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; setImageURI(file.uri) } }, modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTransformGestures { _, _, zoom, rot -> scale = (scale * zoom).coerceIn(1f, 6f); rotation += rot } }.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation })
    }
}
