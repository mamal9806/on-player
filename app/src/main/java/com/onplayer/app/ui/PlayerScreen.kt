package com.onplayer.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.onplayer.app.data.MediaFile
import com.onplayer.app.data.MediaCategory

private data class AudioMeta(val title:String,val artist:String,val album:String,val artwork:Bitmap?)

@Composable
private fun rememberAudioMeta(file:MediaFile):AudioMeta {
    val context=LocalContext.current
    val initial=AudioMeta(file.name,"Unknown artist","",null)
    val meta by produceState(initial,file.uri) {
        value=withContext(Dispatchers.IO) {
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,file.uri)
                val title=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?:file.name
                val artist=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?: "Unknown artist"
                val album=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?: ""
                val art=retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
                AudioMeta(title,artist,album,art)
            } catch (_:Exception) { initial } finally { try { retriever.release() } catch (_:Exception) {} }
        }
    }
    return meta
}

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
    val audioMeta = rememberAudioMeta(file)
    val visualizer = rememberInfiniteTransition(label="music-bars")
    val timerScope = rememberCoroutineScope()
    var sleepDialog by remember { mutableStateOf(false) }
    var sleepJob by remember(file.uri) { mutableStateOf<Job?>(null) }
    DisposableEffect(player) { onDispose { sleepJob?.cancel(); player.release() } }
    val playable = remember(files) { files.filter { it.isPlayable } }
    val currentIndex = playable.indexOfFirst { it.uri == file.uri }
    var isPlaying by remember(file.uri) { mutableStateOf(false) }
    var speed by remember(file.uri) { mutableStateOf(1f) }
    var repeat by remember(file.uri) { mutableStateOf(false) }
    var shuffle by remember(file.uri) { mutableStateOf(false) }
    var volume by remember(file.uri) { mutableFloatStateOf(1f) }
    var position by remember(file.uri) { mutableFloatStateOf(0f) }
    var duration by remember(file.uri) { mutableFloatStateOf(0f) }
    var aspect by remember(file.uri) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var rotation by remember(file.uri) { mutableFloatStateOf(0f) }
    var fullscreen by remember(file.uri) { mutableStateOf(false) }
    var queueOpen by remember(file.uri) { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L).toFloat()
            duration = player.duration.takeIf { it > 0L }?.toFloat() ?: 0f
            isPlaying = player.isPlaying
            delay(450)
        }
    }
    fun playAt(index: Int) {
        if (index in playable.indices) {
            vm.open(playable[index])
        }
    }
    Column(Modifier.fillMaxSize().padding(bottom = 90.dp).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Brush.horizontalGradient(listOf(Color(0xAA312A50),Color(0xAA171722))),RoundedCornerShape(22.dp)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(file.name, color = Color.White, maxLines = 1)
                Text(if(file.category==MediaCategory.Audio) "Music player" else "Video player", color = Color(0xFFBDB6D6), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { queueOpen = true }) { Icon(Icons.Default.QueueMusic, null, tint = Color.White) }
            IconButton(onClick = { sleepDialog = true }) { Icon(Icons.Default.Timer, null, tint = Color.White) }
            IconButton(onClick = { fullscreen = true }) { Icon(Icons.Default.Fullscreen, null, tint = Color.White) }
            IconButton(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) }
        }
        if(file.category==MediaCategory.Audio) {
            Column(Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(Color(0xFF29213F),Color(0xFF090910))),RoundedCornerShape(26.dp)).padding(18.dp),horizontalAlignment=Alignment.Center,verticalArrangement=Arrangement.Center) {
                if(audioMeta.artwork!=null) Image(audioMeta.artwork!!.asImageBitmap(),contentDescription="Album cover",modifier=Modifier.size(190.dp).clip(RoundedCornerShape(28.dp)),contentScale=ContentScale.Crop)
                else Box(Modifier.size(190.dp).background(Brush.linearGradient(listOf(Color(0xFF8B5CF6),Color(0xFF3035B8))),RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=Color.White,modifier=Modifier.size(86.dp))}
                Text(audioMeta.title,color=Color.White,style=MaterialTheme.typography.titleLarge,maxLines=1,modifier=Modifier.padding(top=18.dp))
                Text(audioMeta.artist+(if(audioMeta.album.isNotBlank()) " • "+audioMeta.album else ""),color=Color(0xFFBDB6D6),maxLines=1)
                Row(Modifier.height(90.dp).padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Bottom) {
                    repeat(18) { i ->
                        val bar=visualizer.animateFloat(initialValue=.18f,targetValue=.28f+(i%5)*.12f,animationSpec=infiniteRepeatable(tween(550+i*35,delayMillis=i*45),RepeatMode.Reverse),label="bar-${i}")
                        Box(Modifier.width(7.dp).height((bar.value*88).dp).background(Brush.verticalGradient(listOf(Color(0xFFB99AFF),Color(0xFF6041C9))),RoundedCornerShape(6.dp)))
                    }
                }
            }
        } else AndroidView(factory = { ctx -> PlayerView(ctx).apply { playerView=this; this.player=player; useController=true; resizeMode=aspect } }, update = { it.player=player; it.resizeMode=aspect }, modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black,RoundedCornerShape(24.dp)).graphicsLayer { rotationZ=rotation })
        Column(Modifier.fillMaxWidth().padding(top=12.dp).background(Brush.verticalGradient(listOf(Color(0xAA29243E),Color(0xAA171722))),RoundedCornerShape(24.dp)).padding(14.dp)) {
            Text(file.name, color=Color.White, style=MaterialTheme.typography.titleMedium, maxLines=1)
            Text(file.category.label+" • "+file.sizeText, color=Color(0xFFBDB6D6), style=MaterialTheme.typography.labelSmall)
            Slider(value=if(duration>0) position.coerceIn(0f,duration) else 0f, onValueChange={position=it}, onValueChangeFinished={player.seekTo(position.toLong())}, valueRange=0f..duration.coerceAtLeast(1f), enabled=duration>0)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatPlayerTime(position.toLong()),color=Color(0xFFBDB6D6),fontSize=11.sp);Text(formatPlayerTime(duration.toLong()),color=Color(0xFFBDB6D6),fontSize=11.sp)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={shuffle=!shuffle;player.shuffleModeEnabled=shuffle}) { Icon(Icons.Default.Shuffle,null,tint=if(shuffle) Color(0xFFC5A6FF) else Color.Gray) }
                IconButton(onClick={playAt(currentIndex-1)},enabled=currentIndex>0) { Icon(Icons.Default.SkipPrevious,null,tint=if(currentIndex>0) Color.White else Color.Gray,modifier=Modifier.size(32.dp)) }
                IconButton(onClick={if(isPlaying)player.pause() else player.play();isPlaying=!isPlaying},modifier=Modifier.size(64.dp)) { Icon(if(isPlaying)Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,null,tint=Color(0xFFC5A6FF),modifier=Modifier.size(56.dp)) }
                IconButton(onClick={playAt(currentIndex+1)},enabled=currentIndex>=0&&currentIndex<playable.lastIndex) { Icon(Icons.Default.SkipNext,null,tint=if(currentIndex>=0&&currentIndex<playable.lastIndex) Color.White else Color.Gray,modifier=Modifier.size(32.dp)) }
                IconButton(onClick={repeat=!repeat;player.repeatMode=if(repeat)ExoPlayer.REPEAT_MODE_ONE else ExoPlayer.REPEAT_MODE_OFF}) { Icon(Icons.Default.Repeat,null,tint=if(repeat) Color(0xFFC5A6FF) else Color.Gray) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeDown,null,tint=Color(0xFFBDB6D6))
                Slider(value=volume,onValueChange={volume=it;player.volume=it},modifier=Modifier.weight(1f))
                Icon(Icons.Default.VolumeUp,null,tint=Color(0xFFBDB6D6))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Speed",color=Color(0xFFBDB6D6),fontSize=12.sp)
                Row { listOf(.75f,1f,1.25f,1.5f,2f).forEach { v -> Text("${v}x",color=if(speed==v)Color(0xFFC5A6FF)else Color.Gray,modifier=Modifier.padding(horizontal=4.dp).clickable{speed=v;player.setPlaybackSpeed(v)},fontSize=12.sp) } }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Aspect ratio",color=Color(0xFFBDB6D6),fontSize=12.sp)
                Row { listOf("Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,"Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,"Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM).forEach { (label,mode) -> Text(label,color=if(aspect==mode)Color(0xFFC5A6FF)else Color.Gray,modifier=Modifier.padding(horizontal=5.dp).clickable{aspect=mode;playerView?.resizeMode=mode},fontSize=12.sp) } }
            }
            if (file.category == MediaCategory.Videos) {
                Button(onClick = {
                    val seconds=(player.currentPosition/1000).coerceAtLeast(0)
                    val query="${file.name} scene at ${seconds}s"
                    val intent=Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY,query)
                    try { context.startActivity(intent) } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com/search?q="+android.net.Uri.encode(query))))
                    }
                },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF33205D))) { Icon(Icons.Default.Search,null);Spacer(Modifier.width(6.dp));Text("Search This Scene / Title") }
            }
        }
    }
    if(sleepDialog) AlertDialog(onDismissRequest={sleepDialog=false},title={Text("Sleep timer")},text={Column {
        listOf(15,30,45,60,90).forEach { minutes -> TextButton(onClick={sleepJob?.cancel();sleepJob=timerScope.launch{delay(minutes*60_000L);player.pause();isPlaying=false};sleepDialog=false},modifier=Modifier.fillMaxWidth()) { Text("Pause playback in ${minutes} minutes") } }
        TextButton(onClick={sleepJob?.cancel();sleepJob=null;sleepDialog=false},modifier=Modifier.fillMaxWidth()) { Text("Cancel timer") }
    }},confirmButton={TextButton(onClick={sleepDialog=false}){Text("Close")}})
    if(queueOpen) AlertDialog(onDismissRequest={queueOpen=false},title={Text("Play queue")},text={Column(Modifier.heightIn(max=420.dp)){playable.forEachIndexed { index, item -> Text(item.name,maxLines=1,modifier=Modifier.fillMaxWidth().clickable{playAt(index);queueOpen=false}.padding(vertical=10.dp),color=if(item.uri==file.uri)Color(0xFF9B4DFF)else MaterialTheme.colorScheme.onSurface) } }},confirmButton={TextButton(onClick={queueOpen=false}){Text("Close")}})
    if(fullscreen) Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=true;resizeMode=aspect}},modifier=Modifier.fillMaxSize())
            IconButton(onClick={fullscreen=false},modifier=Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color(0x88202020),RoundedCornerShape(50))) { Icon(Icons.Default.FullscreenExit,null,tint=Color.White) }
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val seconds=(ms/1000).coerceAtLeast(0)
    val hours=seconds/3600
    val minutes=(seconds%3600)/60
    val remainder=seconds%60
    return if(hours>0) "${hours}:${minutes.toString().padStart(2,'0')}:${remainder.toString().padStart(2,'0')}" else "${minutes}:${remainder.toString().padStart(2,'0')}"
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
