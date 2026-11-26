package com.onplayer.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.app.Activity
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.compose.ui.platform.LocalContext
import com.onplayer.app.data.MediaCategory
import com.onplayer.app.data.MediaFile
import com.onplayer.app.ui.OnPlayerViewModel
import com.onplayer.app.ui.PlayerScreen
import com.onplayer.app.work.ACTION_LIBRARY_CHANGED
import com.onplayer.app.work.AutoLibraryScanScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Purple = Color(0xFFB18AFF)
private val Deep = Color(0xFF090A12)
private val CardDark = Color(0xFF151723)
private val Subtle = Color(0xFF9B9BAF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OnPlayerApp() }
    }
}

private enum class Tab(val title: String) { Home("Home"), Library("Library"), Playlists("Playlists"), Favorites("Favorites"), Search("Search"), Settings("Settings") }

@Composable
fun OnPlayerApp(vm: OnPlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val files by vm.files.collectAsState()
    val current by vm.current.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val scanError by vm.scanError.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val lastPlayed by vm.lastPlayed.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    var tab by remember { mutableStateOf(Tab.Home) }
    var playerOpen by remember { mutableStateOf(false) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaFile?>(null) }
    val controller = rememberRootController()
    BackHandler(enabled = playerOpen || (tab == Tab.Playlists && selectedPlaylistId != null)) {
        if (playerOpen) playerOpen = false else selectedPlaylistId = null
    }
    LaunchedEffect(playlists, selectedPlaylistId) {
        if (selectedPlaylistId != null && playlists.none { it.id == selectedPlaylistId }) selectedPlaylistId = null
    }

    DisposableEffect(context, vm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: Intent?) {
                vm.scan()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_LIBRARY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isAmoled = themeMode == "AMOLED Black"
    val dark = isAmoled || (themeMode != "Light" && (themeMode != "System" || systemDark))
    val background = when { isAmoled -> Color.Black; dark -> Deep; else -> Color(0xFFF5F4FA) }
    val foreground = if (dark) Color.White else Color(0xFF22232E)
    val muted = if (dark) Subtle else Color(0xFF6E7081)
    val surface = if (isAmoled) Color(0xFF090909) else if (dark) CardDark else Color.White
    val colors = if (dark) darkColorScheme(primary=Purple, background=background, surface=surface, onSurface=foreground, onBackground=foreground)
        else lightColorScheme(primary=Color(0xFF7B50D7), background=background, surface=surface, onSurface=foreground, onBackground=foreground)

    DisposableEffect(controller, files) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = vm.setPlaybackActive(isPlaying)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.localConfiguration?.uri ?: return
                files.firstOrNull { it.uri == uri }?.let(vm::open)
            }
        }
        controller?.addListener(listener)
        controller?.currentMediaItem?.localConfiguration?.uri?.let { uri -> files.firstOrNull { it.uri == uri }?.let(vm::rememberLastPlayed) }
        vm.setPlaybackActive(controller?.isPlaying == true)
        onDispose { controller?.removeListener(listener) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val audioPermissionGranted = if (Build.VERSION.SDK_INT >= 33) {
            result[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioPermissionGranted) {
            vm.scan()
            AutoLibraryScanScheduler.ensureScheduled(context)
        } else {
            vm.setScanError("Music permission denied. You can still open a song from the file picker.")
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val name = try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: uri.lastPathSegment ?: "Audio"
            } catch (_: Exception) { uri.lastPathSegment ?: "Audio" }
            vm.open(MediaFile(uri.hashCode().toLong(), name, uri, context.contentResolver.getType(uri) ?: "audio/*", 0L, System.currentTimeMillis()/1000, MediaCategory.Audio))
            playerOpen = true
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val deletedFile = pendingDelete
        if (result.resultCode == Activity.RESULT_OK && deletedFile != null) {
            if (controller?.currentMediaItem?.localConfiguration?.uri == deletedFile.uri) controller.stop()
            vm.removeDeletedTrack(deletedFile)
            vm.scan()
        }
        pendingDelete = null
    }
    val requestDelete: (MediaFile) -> Unit = { target ->
        pendingDelete = target
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(target.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                val removed = context.contentResolver.delete(target.uri, null, null)
                if (removed > 0) {
                    if (controller?.currentMediaItem?.localConfiguration?.uri == target.uri) controller.stop()
                    vm.removeDeletedTrack(target)
                    vm.scan()
                } else {
                    vm.setScanError("Android could not delete this audio file.")
                }
                pendingDelete = null
            }
        } catch (error: Exception) {
            pendingDelete = null
            vm.setScanError(error.message ?: "Could not delete this audio file.")
        }
    }

    MaterialTheme(colorScheme=colors) {
        if (playerOpen) {
            PlayerScreen(
                file = current,
                files = files,
                vm = vm,
                player = controller,
                playlists = playlists,
                onBack = { playerOpen = false },
                onAddToPlaylist = { playlistId, song -> vm.addToPlaylist(playlistId, song) },
                onRenameTrack = { song, title -> vm.renameTrack(song, title) },
                onDeleteTrack = requestDelete
            )
        } else {
            Scaffold(
                containerColor=background,
                bottomBar={
                    Column {
                        if (current != null) MiniPlayer(current!!, playing, dark, onClick={playerOpen=true}, onToggle={ if (controller?.isPlaying==true) controller.pause() else controller?.play() })
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=7.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(if(dark) Color(0xE51A1A27) else Color(0xF2FFFFFF))
                                .border(
                                    1.dp,
                                    Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark)0.30f else 0.85f),Color.White.copy(alpha=0.05f))),
                                    RoundedCornerShape(28.dp)
                                )
                                .padding(horizontal=3.dp, vertical=6.dp),
                            horizontalArrangement=Arrangement.spacedBy(2.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Tab.entries.forEach { item ->
                                val icon = when(item) {
                                    Tab.Home -> Icons.Default.Home
                                    Tab.Library -> Icons.Default.LibraryMusic
                                    Tab.Playlists -> Icons.Default.QueueMusic
                                    Tab.Favorites -> Icons.Default.Favorite
                                    Tab.Search -> Icons.Default.Search
                                    Tab.Settings -> Icons.Default.Settings
                                }
                                val selected = tab == item
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                                        .background(if(selected) Purple.copy(alpha=if(dark)0.22f else 0.12f) else Color.Transparent)
                                        .clickable { tab = item }
                                        .padding(vertical=6.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally
                                ) {
                                    Icon(icon,null,tint=if(selected) Purple else muted,modifier=Modifier.size(19.dp))
                                    Text(item.title,color=if(selected) Purple else muted,fontSize=8.sp,maxLines=1)
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when(tab) {
                        Tab.Home -> HomeTab(files, lastPlayed, playing, scanning, scanError, dark, foreground, muted, surface, onRefresh={vm.scan()}, onPick={audioPicker.launch(arrayOf("audio/*"))}, onOpenSong={vm.open(it);playerOpen=true}, onLibrary={tab=Tab.Library})
                        Tab.Library -> SongListTab("Your library",files,scanning,scanError,dark,foreground,muted,onRefresh={vm.scan()},onOpenSong={vm.open(it);playerOpen=true},onPick={audioPicker.launch(arrayOf("audio/*"))})
                        Tab.Playlists -> PlaylistsTab(
                            playlists = playlists,
                            files = files,
                            selectedPlaylistId = selectedPlaylistId,
                            dark = dark,
                            fg = foreground,
                            muted = muted,
                            onSelect = { selectedPlaylistId = it },
                            onBack = { selectedPlaylistId = null },
                            onCreate = { vm.createPlaylist(it) },
                            onDelete = { vm.deletePlaylist(it) },
                            onAddSong = { id, song -> vm.addToPlaylist(id, song) },
                            onRemoveSong = { id, song -> vm.removeFromPlaylist(id, song) },
                            onOpenSong = { vm.open(it); playerOpen = true }
                        )
                        Tab.Favorites -> SongListTab("Favorites",files.filter{it.uri.toString() in favorites},scanning,null,dark,foreground,muted,onRefresh={vm.scan()},onOpenSong={vm.open(it);playerOpen=true},onPick={audioPicker.launch(arrayOf("audio/*"))},emptyText="No favorites yet")
                        Tab.Search -> SearchTab(files,favorites,dark,foreground,muted,onOpenSong={vm.open(it);playerOpen=true},onFavorite={vm.toggleFavorite(it)})
                        Tab.Settings -> SettingsTab(themeMode,dark,foreground,muted,vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberRootController(): MediaController? {
    val context=LocalContext.current
    val future=remember(context){MediaController.Builder(context,SessionToken(context,ComponentName(context,OnPlayerService::class.java))).buildAsync()}
    var controller by remember{mutableStateOf<MediaController?>(null)}
    DisposableEffect(future) {
        future.addListener({controller=try{future.get()}catch(_:Exception){null}},context.mainExecutor)
        onDispose{MediaController.releaseFuture(future)}
    }
    return controller
}

@Composable
private fun HomeTab(files:List<MediaFile>,last:MediaFile?,playing:Boolean,loading:Boolean,error:String?,dark:Boolean,fg:Color,muted:Color,surface:Color,onRefresh:()->Unit,onPick:()->Unit,onOpenSong:(MediaFile)->Unit,onLibrary:()->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=24.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ON PLAYER",color=Purple,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                    Text("Good music.\nNo distractions.",color=fg,fontSize=28.sp,fontWeight=FontWeight.Bold,lineHeight=33.sp)
                }
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF754DCA),Color(0xFF252C5C)))),contentAlignment=Alignment.Center) { Icon(Icons.Default.Headphones,contentDescription=null,modifier=Modifier.size(28.dp),tint=Color.White) }
            }
        }
        item {
            last?.let { FeaturedTrack(it,playing,dark,onOpenSong) } ?: Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.Start) {
                    Icon(Icons.Default.MusicNote,contentDescription=null,tint=Purple,modifier=Modifier.size(42.dp))
                    Text("Your music awaits",color=fg,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
                    Text("Choose a song to start listening.",color=muted)
                    Button(onClick=onPick,modifier=Modifier.padding(top=14.dp)) { Icon(Icons.Default.FolderOpen,null); Spacer(Modifier.width(6.dp)); Text("Choose music") }
                }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                QuickAction("All songs",files.size,Icons.Default.LibraryMusic,Modifier.weight(1f),dark,fg,onLibrary)
                QuickAction("Recently added",files.take(12).size,Icons.Default.History,Modifier.weight(1f),dark,fg,onLibrary)
            }
        }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Text("Recently added",color=fg,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); Text("See all",color=Purple,modifier=Modifier.clickable(onClick=onLibrary)) } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error!=null) item { Text(error,color=muted) }
        items(files.take(12),key={it.uri.toString()}) { song -> SongRow(song,dark,fg,muted,onClick={onOpenSong(song)}) }
        item { OutlinedButton(onClick=onRefresh,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("Rescan music") } }
    }
}

@Composable
private fun FeaturedTrack(file:MediaFile,playing:Boolean,dark:Boolean,onOpen:(MediaFile)->Unit) {
    val artwork by rememberArtwork(file.uri)
    val shimmer=rememberInfiniteTransition(label="playing pulse").animateFloat(initialValue=0.94f,targetValue=1f,animationSpec=infiniteRepeatable(tween(1100),RepeatMode.Reverse),label="scale").value
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF2E244A),Color(0xFF141521),Color(0xFF11121A)))).clickable{onOpen(file)}) {
        artwork?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xE9090A12),Color(0xFF090A12))))) {}
        Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.Bottom) {
            Text(if(playing)"NOW PLAYING" else "LAST PLAYED",color=Purple,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
            Text(file.displayTitle,color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(file.displayArtist,color=Color.White.copy(alpha=.75f),maxLines=1)
            Box(Modifier.padding(top=12.dp).size(54.dp).clip(CircleShape).background(Color.White).graphicsLayer{scaleX=shimmer;scaleY=shimmer},contentAlignment=Alignment.Center) { Icon(Icons.Default.PlayArrow,null,tint=Color(0xFF171520),modifier=Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun QuickAction(title:String,count:Int,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,dark:Boolean,fg:Color,onClick:()->Unit) {
    Card(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(dark)CardDark else Color.White)) {
        Column(Modifier.padding(16.dp)) { Icon(icon,contentDescription=null,tint=Purple,modifier=Modifier.size(23.dp)); Text(count.toString(),color=fg,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp)); Text(title,color=Subtle,fontSize=12.sp) }
    }
}

@Composable
private fun SongListTab(title:String,songs:List<MediaFile>,loading:Boolean,error:String?,dark:Boolean,fg:Color,muted:Color,onRefresh:()->Unit,onOpenSong:(MediaFile)->Unit,onPick:()->Unit,emptyText:String="No music found") {
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Text(title,color=fg,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=14.dp))
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error!=null) Text(error,color=muted,modifier=Modifier.padding(bottom=8.dp))
        if(songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.MusicNote,contentDescription=null,tint=Purple,modifier=Modifier.size(48.dp)); Text(emptyText,color=muted,modifier=Modifier.padding(top=12.dp)); Button(onClick=onPick,modifier=Modifier.padding(top=12.dp)){Text("Pick a song")} } }
        } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { items(songs,key={it.uri.toString()}) { SongRow(it,dark,fg,muted,onClick={onOpenSong(it)}) } }
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onRefresh,modifier=Modifier.weight(1f)) { Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(4.dp));Text("Rescan") }
            Button(onClick=onPick,modifier=Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen,null);Spacer(Modifier.width(4.dp));Text("Open file") }
        }
    }
}

@Composable
private fun SearchTab(files:List<MediaFile>,favorites:Set<String>,dark:Boolean,fg:Color,muted:Color,onOpenSong:(MediaFile)->Unit,onFavorite:(MediaFile)->Unit) {
    var query by remember{mutableStateOf("")}
    val filtered=remember(files,query){files.filter{it.displayTitle.contains(query,true)||it.displayArtist.contains(query,true)||it.displayAlbum.contains(query,true)}}
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Text("Search music",color=fg,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=12.dp))
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Song, artist or album")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Clear,null)}},singleLine=true,shape=RoundedCornerShape(18.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { items(filtered,key={it.uri.toString()}) { SongRow(it,dark,fg,muted,onClick={onOpenSong(it)},favorite=it.uri.toString() in favorites,onFavorite={onFavorite(it)}) } }
    }
}

@Composable
private fun SongRow(file:MediaFile,dark:Boolean,fg:Color,muted:Color,onClick:()->Unit,favorite:Boolean=false,onFavorite:()->Unit={}) {
    val art by rememberArtwork(file.uri)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if(dark)CardDark else Color.White).clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF6747AD),Color(0xFF1D355E)))),contentAlignment=Alignment.Center) { if(art!=null) Image(art!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Icon(Icons.Default.MusicNote,contentDescription=null,tint=Color.White) }
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(file.displayTitle,color=fg,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(file.displayArtist,color=muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }
        if(favorite) IconButton(onClick=onFavorite) { Icon(Icons.Default.Favorite,contentDescription="Remove favorite",tint=Color(0xFFEE79A8)) }
        else Icon(Icons.Default.PlayCircleOutline,tint=Purple,contentDescription="Play")
    }
}

@Composable
private fun MiniPlayer(file:MediaFile,playing:Boolean,dark:Boolean,onClick:()->Unit,onToggle:()->Unit) {
    val art by rememberArtwork(file.uri)
    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp).clip(RoundedCornerShape(17.dp)).background(if(dark)Color(0xFF242330)else Color(0xFFF0EDFA)).clickable(onClick=onClick).padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Purple),contentAlignment=Alignment.Center) { if(art!=null)Image(art!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Default.MusicNote,null,tint=Color.White) }
        Column(Modifier.weight(1f).padding(horizontal=10.dp)) { Text(file.displayTitle,color=if(dark)Color.White else Color(0xFF20202A),fontWeight=FontWeight.SemiBold,maxLines=1);Text(file.displayArtist,color=Subtle,fontSize=11.sp,maxLines=1) }
        IconButton(onClick=onToggle) { Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=if(dark)Color.White else Purple) }
    }
}

@Composable
private fun SettingsTab(theme:String,dark:Boolean,fg:Color,muted:Color,vm:OnPlayerViewModel) {
    val files by vm.files.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var confirmClearFavorites by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
        Text("Settings",color=fg,fontSize=30.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=20.dp))
        Text("APPEARANCE",color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp)
        Card(
            Modifier.fillMaxWidth().padding(top=10.dp),
            shape=RoundedCornerShape(24.dp),
            colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                listOf("Dark","Light","System","AMOLED Black").forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { vm.setTheme(mode) }.padding(vertical=13.dp,horizontal=4.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription=null,tint=Purple
                        )
                        Text(mode,color=fg,modifier=Modifier.weight(1f).padding(start=12.dp))
                        if(theme==mode) Icon(Icons.Default.CheckCircle,contentDescription="Selected",tint=Purple)
                    }
                }
            }
        }
        Text("LIBRARY",color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=24.dp))
        Card(
            Modifier.fillMaxWidth().padding(top=10.dp),
            shape=RoundedCornerShape(24.dp),
            colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryMusic,null,tint=Purple)
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text("Music library",color=fg,fontWeight=FontWeight.SemiBold)
                        Text(files.size.toString()+" tracks scanned",color=muted,fontSize=12.sp)
                    }
                    TextButton(onClick={vm.scan()}) { Text("Rescan") }
                }
                HorizontalDivider(color=muted.copy(alpha=0.18f))
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=12.dp)) {
                    Icon(Icons.Default.Favorite,null,tint=Color(0xFFEA79A8))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text("Favorites",color=fg,fontWeight=FontWeight.SemiBold)
                        Text(favorites.size.toString()+" saved tracks",color=muted,fontSize=12.sp)
                    }
                    TextButton(onClick={confirmClearFavorites=true},enabled=favorites.isNotEmpty()) { Text("Clear") }
                }
            }
        }
        Text("PLAYBACK",color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=24.dp))
        Card(
            Modifier.fillMaxWidth().padding(top=10.dp),
            shape=RoundedCornerShape(24.dp),
            colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)
        ) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq,null,tint=Purple)
                Column(Modifier.weight(1f).padding(start=12.dp)) {
                    Text("Audio focus",color=fg,fontWeight=FontWeight.SemiBold)
                    Text("On Player requests Android audio focus when playback starts.",color=muted,fontSize=12.sp)
                }
                Icon(Icons.Default.CheckCircle,color=Color(0xFF69C99B),contentDescription="Enabled")
            }
        }
        Text("ABOUT",color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=24.dp))
        Card(
            Modifier.fillMaxWidth().padding(top=10.dp,bottom=28.dp),
            shape=RoundedCornerShape(24.dp),
            colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF9B75EF),Color(0xFF323D74)))),contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.Headphones,null,tint=Color.White,modifier=Modifier.size(25.dp))
                    }
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text("On Player",color=fg,fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text("Version 1.0 • Music, beautifully simple",color=muted,fontSize=12.sp)
                    }
                }
                Text("An audio-only player with persistent playlists, favorites and background playback.",color=muted,fontSize=13.sp,modifier=Modifier.padding(top=14.dp))
            }
        }
    }
    if(confirmClearFavorites) {
        AlertDialog(
            onDismissRequest={confirmClearFavorites=false},
            title={Text("Clear favorites?")},
            text={Text("This removes all saved favorites from On Player.")},
            confirmButton={TextButton(onClick={vm.clearFavorites();confirmClearFavorites=false}){Text("Clear")}},
            dismissButton={TextButton(onClick={confirmClearFavorites=false}){Text("Cancel")}}
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists:List<com.onplayer.app.ui.UserPlaylist>,
    files:List<MediaFile>,
    selectedPlaylistId:String?,
    dark:Boolean,
    fg:Color,
    muted:Color,
    onSelect:(String)->Unit,
    onBack:()->Unit,
    onCreate:(String)->Unit,
    onDelete:(String)->Unit,
    onAddSong:(String,MediaFile)->Unit,
    onRemoveSong:(String,MediaFile)->Unit,
    onOpenSong:(MediaFile)->Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var showAddSongs by remember { mutableStateOf(false) }
    val selected = playlists.firstOrNull { it.id == selectedPlaylistId }
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
            if(selected != null) {
                IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,null,tint=fg) }
            }
            Column(Modifier.weight(1f)) {
                Text(if(selected == null) "Your playlists" else selected.name,color=fg,fontSize=27.sp,fontWeight=FontWeight.Bold)
                Text(
                    if(selected == null) playlists.size.toString()+" playlists saved on this device"
                    else selected.songUris.size.toString()+" songs",
                    color=muted,fontSize=12.sp
                )
            }
            if(selected == null) {
                IconButton(onClick={newName="";showCreate=true}) {
                    Icon(Icons.Default.AddCircle,null,tint=Purple,modifier=Modifier.size(28.dp))
                }
            } else {
                IconButton(onClick={showAddSongs=true}) { Icon(Icons.Default.Add,null,tint=Purple) }
                IconButton(onClick={onBack}) { Icon(Icons.Default.Close,null,tint=muted) }
            }
        }
        if(selected == null) {
            if(playlists.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QueueMusic,null,tint=Purple,modifier=Modifier.size(58.dp))
                        Text("Make it yours",color=fg,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp))
                        Text("Create a playlist and collect your favorite tracks.",color=muted,fontSize=13.sp)
                        Button(onClick={newName="";showCreate=true},modifier=Modifier.padding(top=16.dp)) {
                            Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Create playlist")
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(playlists,key={it.id}) { playlist ->
                        Card(
                            Modifier.fillMaxWidth().clickable{onSelect(playlist.id)},
                            shape=RoundedCornerShape(22.dp),
                            colors=CardDefaults.cardColors(containerColor=if(dark)CardDark else Color.White)
                        ) {
                            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF8863D6),Color(0xFF344B76)))),contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.QueueMusic,null,tint=Color.White,modifier=Modifier.size(26.dp))
                                }
                                Column(Modifier.weight(1f).padding(horizontal=13.dp)) {
                                    Text(playlist.name,color=fg,fontWeight=FontWeight.SemiBold,fontSize=16.sp)
                                    Text(playlist.songUris.size.toString()+" songs",color=muted,fontSize=12.sp)
                                }
                                Icon(Icons.Default.ChevronRight,null,tint=muted)
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={showAddSongs=true},modifier=Modifier.weight(1f)) {
                    Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Add songs")
                }
                OutlinedButton(onClick={onDelete(selected.id)}) {
                    Icon(Icons.Default.DeleteOutline,null);Spacer(Modifier.width(5.dp));Text("Delete playlist")
                }
            }
            val songs = selected.songUris.mapNotNull { uri -> files.firstOrNull { it.uri.toString() == uri } }
            if(songs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Text("This playlist is empty. Add some music.",color=muted)
                }
            } else {
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(songs,key={it.uri.toString()}) { song ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if(dark)CardDark else Color.White).padding(start=14.dp,end=4.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).clickable{onOpenSong(song)}.padding(vertical=13.dp)) {
                                Text(song.displayTitle,color=fg,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(song.displayArtist,color=muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            IconButton(onClick={onRemoveSong(selected.id,song)}) { Icon(Icons.Default.RemoveCircleOutline,null,tint=muted) }
                        }
                    }
                }
            }
        }
    }
    if(showCreate) {
        AlertDialog(
            onDismissRequest={showCreate=false},
            title={Text("New playlist")},
            text={
                OutlinedTextField(
                    value=newName,onValueChange={newName=it},singleLine=true,
                    label={Text("Playlist name")},placeholder={Text("e.g. Late night")}
                )
            },
            confirmButton={
                TextButton(onClick={if(newName.isNotBlank()){onCreate(newName);showCreate=false}}) { Text("Create") }
            },
            dismissButton={TextButton(onClick={showCreate=false}){Text("Cancel")}}
        )
    }
    if(showAddSongs && selected != null) {
        AlertDialog(
            onDismissRequest={showAddSongs=false},
            title={Text("Add music to " + selected.name)},
            text={
                if(files.isEmpty()) Text("Your library is empty.")
                else LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(files,key={it.uri.toString()}) { song ->
                        val alreadyAdded = song.uri.toString() in selected.songUris
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable{if(!alreadyAdded)onAddSong(selected.id,song)}
                                .padding(horizontal=8.dp,vertical=10.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(song.displayTitle,color=fg,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(song.displayArtist,color=muted,fontSize=11.sp)
                            }
                            Icon(
                                if(alreadyAdded) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                contentDescription=null,
                                tint=if(alreadyAdded) Color(0xFF69C99B) else Purple
                            )
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={showAddSongs=false}){Text("Done")}}
        )
    }
}

@Composable
private fun rememberArtwork(uri:android.net.Uri):State<android.graphics.Bitmap?> {
    val context=LocalContext.current
    return produceState<android.graphics.Bitmap?>(null,uri) {
        value=withContext(Dispatchers.IO) {
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,uri)
                retriever.embeddedPicture?.let{bytes->android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)}
            } catch(_:Exception){null} finally { try{retriever.release()}catch(_:Exception){} }
        }
    }
}
