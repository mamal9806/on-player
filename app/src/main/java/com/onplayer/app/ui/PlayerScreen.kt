package com.onplayer.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.onplayer.app.data.MediaFile

private data class TrackArtwork(val bitmap:Bitmap?,val title:String,val artist:String,val album:String)

@Composable
private fun rememberTrackArtwork(file:MediaFile):TrackArtwork {
    val context=LocalContext.current
    val initial=TrackArtwork(null,file.displayTitle,file.displayArtist,file.displayAlbum)
    val data by produceState(initial,file.uri) {
        value=withContext(Dispatchers.IO) {
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,file.uri)
                val title=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf{it.isNotBlank()&&it!="<unknown>"} ?: file.displayTitle
                val artist=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf{it.isNotBlank()&&it!="<unknown>"} ?: file.displayArtist
                val album=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf{it.isNotBlank()&&it!="<unknown>"} ?: file.displayAlbum
                val bitmap=retriever.embeddedPicture?.let{bytes->BitmapFactory.decodeByteArray(bytes,0,bytes.size)}
                TrackArtwork(bitmap,title,artist,album)
            }catch(_:Exception){initial}finally{try{retriever.release()}catch(_:Exception){}}
        }
    }
    return data
}

@Composable
fun PlayerScreen(file:MediaFile?,files:List<MediaFile>,vm:OnPlayerViewModel,player:MediaController?,onBack:()->Unit) {
    val fg=Color.White
    val muted=Color(0xFFB6B2C8)
    val purple=Color(0xFFB18AFF)
    if(file==null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF090A12)),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.Center) { Icon(Icons.Default.MusicNote,tint=purple,modifier=Modifier.size(58.dp)); Text("Choose a song",color=fg,modifier=Modifier.padding(top=12.dp)); TextButton(onClick=onBack){Text("Back")} }
        }
        return
    }
    if(player==null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF090A12)),contentAlignment=Alignment.Center){CircularProgressIndicator(color=purple)}
        return
    }
    val art=rememberTrackArtwork(file)
    val queue=remember(files,file.uri){(files.filter{it.isPlayable}.let{list->if(list.any{it.uri==file.uri})list else listOf(file)+list})}
    var playing by remember{mutableStateOf(player.isPlaying)}
    var position by remember{mutableLongStateOf(0L)}
    var duration by remember{mutableLongStateOf(0L)}
    var shuffle by remember{mutableStateOf(player.shuffleModeEnabled)}
    var repeatOne by remember{mutableStateOf(player.repeatMode==Player.REPEAT_MODE_ONE)}
    val pulse=rememberInfiniteTransition(label="artwork pulse").animateFloat(initial=1f,targetValue=1.025f,animationSpec=infiniteRepeatable(tween(2200),RepeatMode.Reverse),label="art scale").value
    DisposableEffect(player) {
        val listener=object:Player.Listener {
            override fun onIsPlayingChanged(isPlaying:Boolean){playing=isPlaying}
            override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){position=0L;duration=player.duration.coerceAtLeast(0L)}
        }
        player.addListener(listener)
        onDispose{player.removeListener(listener)}
    }
    LaunchedEffect(player,file.uri,queue) {
        if(player.currentMediaItem?.localConfiguration?.uri!=file.uri) {
            val items=queue.map{song->MediaItem.Builder().setUri(song.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(song.displayTitle).setArtist(song.displayArtist).setAlbumTitle(song.displayAlbum).build()).build()}
            player.setMediaItems(items,queue.indexOfFirst{it.uri==file.uri}.coerceAtLeast(0),0L)
            player.prepare()
            player.play()
        }
    }
    LaunchedEffect(player,playing) {
        while(playing) {
            position=player.currentPosition.coerceAtLeast(0L)
            duration=player.duration.takeIf{it>0L}?:0L
            delay(450)
        }
        position=player.currentPosition.coerceAtLeast(0L)
        duration=player.duration.takeIf{it>0L}?:0L
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF090A12))) {
        art.bitmap?.let { bitmap ->
            Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize().graphicsLayer{scaleX=1.45f;scaleY=1.45f}.blur(72.dp),contentScale=ContentScale.Crop)
            Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize().graphicsLayer{scaleX=1.22f;scaleY=1.22f}.blur(36.dp),contentScale=ContentScale.Crop)
        } ?: Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF4B347F),Color(0xFF232949),Color(0xFF090A12)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC080810),Color(0x44202030),Color(0xEE080810)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=22.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack,modifier=Modifier.size(46.dp).background(Color.White.copy(alpha=.09f),CircleShape)){Icon(Icons.Default.KeyboardArrowDown,null,tint=fg,modifier=Modifier.size(30.dp))}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("NOW PLAYING",color=purple,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                    Text("On Player",color=muted,fontSize=12.sp)
                }
                IconButton(onClick={},modifier=Modifier.size(46.dp).background(Color.White.copy(alpha=.09f),CircleShape)){Icon(Icons.Default.MoreHoriz,null,tint=fg)}
            }
            Spacer(Modifier.weight(.18f))
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal=8.dp),contentAlignment=Alignment.Center) {
                art.bitmap?.let{bitmap->Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize().graphicsLayer{scaleX=pulse;scaleY=pulse}.clip(RoundedCornerShape(30.dp)).border(1.dp,Color.White.copy(alpha=.18f),RoundedCornerShape(30.dp)),contentScale=ContentScale.Crop)}
                ?:Box(Modifier.fillMaxSize().graphicsLayer{scaleX=pulse;scaleY=pulse}.clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF8463CA),Color(0xFF293A79)))),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=Color.White,modifier=Modifier.size(92.dp))}
            }
            Spacer(Modifier.weight(.20f))
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.Start) {
                Text(art.title,color=fg,fontSize=26.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(art.artist+(if(art.album.isNotBlank())" • "+art.album else ""),color=muted,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=5.dp))
            }
            Slider(value=if(duration>0)position.toFloat().coerceIn(0f,duration.toFloat()) else 0f,onValueChange={position=it.toLong()},onValueChangeFinished={player.seekTo(position)},valueRange=0f..duration.toFloat().coerceAtLeast(1f),enabled=duration>0,colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color.White,inactiveTrackColor=Color.White.copy(alpha=.28f)))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(timeText(position),color=muted,fontSize=11.sp);Text(timeText(duration),color=muted,fontSize=11.sp) }
            Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                IconButton(onClick={shuffle=!shuffle;player.shuffleModeEnabled=shuffle},modifier=Modifier.size(48.dp)){Icon(Icons.Default.Shuffle,null,tint=if(shuffle)purple else muted,modifier=Modifier.size(22.dp))}
                IconButton(onClick={player.seekToPreviousMediaItem()},modifier=Modifier.size(60.dp)){Icon(Icons.Default.SkipPrevious,null,tint=fg,modifier=Modifier.size(36.dp))}
                Box(Modifier.size(82.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color.White,Color(0xFFE5D8FF)))),contentAlignment=Alignment.Center) {
                    IconButton(onClick={if(player.isPlaying)player.pause()else player.play()},modifier=Modifier.size(82.dp)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color(0xFF191521),modifier=Modifier.size(42.dp))}
                }
                IconButton(onClick={player.seekToNextMediaItem()},modifier=Modifier.size(60.dp)){Icon(Icons.Default.SkipNext,null,tint=fg,modifier=Modifier.size(36.dp))}
                IconButton(onClick={repeatOne=!repeatOne;player.repeatMode=if(repeatOne)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF},modifier=Modifier.size(48.dp)){Icon(Icons.Default.RepeatOne,null,tint=if(repeatOne)purple else muted,modifier=Modifier.size(22.dp))}
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun timeText(ms:Long):String {
    val sec=(ms/1000).coerceAtLeast(0)
    return "%d:%02d".format(sec/60,sec%60)
}
