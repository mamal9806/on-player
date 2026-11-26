package com.onplayer.app.ui

import android.content.Intent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.onplayer.app.data.MediaCategory
import com.onplayer.app.data.MediaFile
import kotlinx.coroutines.delay
import java.util.Locale

private val GlassBg = Color(0xFF080B18)
private val GlassAccent = Color(0xFF9B8CFF)
private val GlassCyan = Color(0xFF6DE4E8)
private val GlassText = Color(0xFFF4F3FF)
private val GlassMuted = Color(0xFFAAAFC5)

@Composable
fun PlayerScreen(file: MediaFile?, files: List<MediaFile>, vm: OnPlayerViewModel, nav: NavHostController) {
    if (file == null) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171B35), GlassBg))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassIcon(Icons.Default.PlayCircle, 76)
                Spacer(Modifier.height(18.dp))
                Text("Your next favorite moment", color = GlassText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Choose a video, song or photo from your library", color = GlassMuted, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(22.dp))
                Button(onClick = { nav.navigate("library") }, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Explore library")
                }
            }
        }
        return
    }
    if (file.category == MediaCategory.Images) {
        GlassImageReader(file, nav)
        return
    }

    val context = LocalContext.current
    val player = remember(file.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            duration = player.duration.coerceAtLeast(0L)
            if (!dragging && duration > 0L) progress = (player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF20264A), Color(0xFF10152B), GlassBg)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                GlassRoundButton(Icons.Default.ArrowBack) { nav.popBackStack() }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("NOW PLAYING", color = GlassCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(file.name, color = GlassText, maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
                GlassRoundButton(if (repeat) Icons.Default.RepeatOne else Icons.Default.Repeat) {
                    repeat = !repeat
                    player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }
                Spacer(Modifier.width(7.dp))
                GlassRoundButton(Icons.Default.MoreHoriz) {
                    try {
                        val send = Intent(Intent.ACTION_SEND).apply { type = file.mime; putExtra(Intent.EXTRA_STREAM, file.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        context.startActivity(Intent.createChooser(send, "Share media"))
                    } catch (_: Exception) { }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(28.dp))
                    ) {
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = .48f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(if (file.category == MediaCategory.Audio) "AUDIO" else "ON PLAYER", color = GlassText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                            Text(file.name, color = GlassText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(file.category.label + "  •  " + file.sizeText, color = GlassMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                            Slider(
                                value = progress,
                                onValueChange = { dragging = true; progress = it },
                                onValueChangeFinished = { player.seekTo((progress * duration).toLong()); dragging = false },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                colors = SliderDefaults.colors(thumbColor = GlassCyan, activeTrackColor = GlassAccent, inactiveTrackColor = Color.White.copy(alpha = .15f))
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime((progress * duration).toLong()), color = GlassMuted, fontSize = 11.sp)
                                Text(formatTime(duration), color = GlassMuted, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                GlassRoundButton(Icons.Default.Replay10, 48) { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }
                                Box(Modifier.size(70.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GlassCyan, GlassAccent))).clickable {
                                    if (player.isPlaying) player.pause() else player.play()
                                    playing = player.isPlaying
                                }, contentAlignment = Alignment.Center) {
                                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF10152B), modifier = Modifier.size(38.dp))
                                }
                                GlassRoundButton(Icons.Default.Forward10, 48) {
                                    val end = if (duration > 0L) duration else Long.MAX_VALUE
                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(end))
                                }
                            }
                            Spacer(Modifier.height(15.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, null, tint = GlassCyan, modifier = Modifier.size(18.dp))
                                    Text("  Playback speed", color = GlassMuted, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                                        val selected = speed == value
                                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) GlassAccent.copy(alpha = .28f) else Color.White.copy(alpha = .06f))
                                            .clickable { speed = value; player.setPlaybackSpeed(value) }.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                            Text(value.toString() + "x", color = if (selected) GlassText else GlassMuted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (file.category == MediaCategory.Videos) {
                Spacer(Modifier.height(10.dp))
                GlassPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable {
                        val seconds = (player.currentPosition / 1000).coerceAtLeast(0)
                        val query = file.name + " scene at " + seconds + "s"
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
                        try { context.startActivity(intent) } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query))))
                        }
                    }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = GlassCyan)
                        Text("  Search this scene on Google", color = GlassText, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = GlassMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassImageReader(file: MediaFile, nav: NavHostController) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF20264A), GlassBg)))) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassRoundButton(Icons.Default.ArrowBack) { nav.popBackStack() }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("PHOTO READER", color = GlassCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(file.name, color = GlassText, maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
                GlassRoundButton(Icons.Default.RotateRight) { rotation += 90f }
                Spacer(Modifier.width(7.dp))
                GlassRoundButton(Icons.Default.Share) {
                    try {
                        val share = Intent(Intent.ACTION_SEND).apply { type = file.mime; putExtra(Intent.EXTRA_STREAM, file.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        context.startActivity(Intent.createChooser(share, "Share image"))
                    } catch (_: Exception) { }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = .28f))
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx -> android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; setImageURI(file.uri) } },
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, rotate ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            rotation += rotate
                        }
                    }.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation }
                )
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassPanel {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ZoomIn, null, tint = GlassCyan, modifier = Modifier.size(17.dp))
                            Text("  Pinch to zoom  •  " + (scale * 100).toInt().toString() + "%", color = GlassText, fontSize = 11.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("Reset", color = GlassCyan, modifier = Modifier.clickable { scale = 1f; rotation = 0f }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(25.dp))
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .13f), Color.White.copy(alpha = .045f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .32f), Color.White.copy(alpha = .07f))), RoundedCornerShape(25.dp))) {
        content()
    }
}

@Composable
private fun GlassRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 42, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape)
        .background(Color.White.copy(alpha = .09f))
        .border(1.dp, Color.White.copy(alpha = .17f), CircleShape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = GlassText, modifier = Modifier.size((size * .48f).dp))
    }
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GlassCyan.copy(alpha = .3f), GlassAccent.copy(alpha = .35f))))
        .border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = GlassText, modifier = Modifier.size((size * .52f).dp))
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
