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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
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

private val Purple = Color(0xFF8B5CF6)
private val LocalAppLanguage = compositionLocalOf { "English" }
@Composable private fun tr(value: String): String = if (LocalAppLanguage.current == "فارسی") mapOf("Settings" to "تنظیمات","LANGUAGE" to "زبان","APPEARANCE" to "ظاهر","LIBRARY" to "کتابخانه","PLAYBACK" to "پخش","ABOUT" to "درباره","Dark" to "تیره","Light" to "روشن","System" to "سیستم","AMOLED Black" to "مشکی AMOLED","Music library" to "کتابخانه موسیقی","Rescan" to "اسکن مجدد","Favorites" to "علاقه‌مندی‌ها","Clear" to "پاک کردن","Audio focus" to "تمرکز صوتی","Your library" to "کتابخانه من","No favorites yet" to "هنوز آهنگ محبوبی ندارید","Pick a song" to "انتخاب آهنگ","Search music" to "جستجوی موسیقی","Song, artist or album" to "نام آهنگ، خواننده یا آلبوم","Recently added" to "اخیراً اضافه‌شده","See all" to "مشاهده همه","Choose music" to "انتخاب موسیقی","Your music awaits" to "موسیقی شما منتظر است","Choose a song to start listening." to "برای شروع، یک آهنگ انتخاب کنید","Now Playing" to "در حال پخش","Close player" to "بستن پخش‌کننده","More options" to "گزینه‌های بیشتر","Add to playlist" to "افزودن به فهرست پخش","Rename song" to "تغییر نام آهنگ","Delete from device" to "حذف از دستگاه","ON PLAYER" to "آن پلیر","On Player" to "آن پلیر","Lightweight. Ad-free. Just music." to "سبک، بدون تبلیغ؛ فقط موسیقی.","Rescan music" to "اسکن دوباره موسیقی","Open file" to "باز کردن فایل","All songs" to "همه آهنگ‌ها","On Player requests Android audio focus when playback starts." to "آن پلیر هنگام شروع پخش، تمرکز صوتی را از اندروید دریافت می‌کند.","Version 1.0 • Music, beautifully simple" to "نسخه ۱٫۰ • موسیقی، ساده و زیبا","Clear favorites?" to "علاقه‌مندی‌ها پاک شوند؟","This removes all saved favorites from On Player." to "این کار همه آهنگ‌های ذخیره‌شده در آن پلیر را پاک می‌کند.","Cancel" to "لغو","Make it yours" to "شخصی‌سازی موسیقی","Create a playlist and collect your favorite tracks." to "فهرست پخش بساز و آهنگ‌های محبوبت را جمع کن.","Create playlist" to "ساخت فهرست پخش","Add songs" to "افزودن آهنگ‌ها","Delete playlist" to "حذف فهرست پخش","This playlist is empty. Add some music." to "این فهرست خالی است؛ چند آهنگ به آن اضافه کن.","New playlist" to "فهرست پخش جدید","Playlist name" to "نام فهرست پخش","e.g. Late night" to "مثلاً شبانه","Create" to "ساخت","Your library is empty." to "کتابخانه موسیقی خالی است.","Done" to "تمام","Home" to "خانه","Library" to "کتابخانه","Playlists" to "فهرست‌های پخش","Search" to "جستجو","Your playlists" to "فهرست‌های پخش شما","playlists saved on this device" to "فهرست پخش ذخیره‌شده","songs" to "آهنگ","tracks scanned" to "آهنگ بررسی‌شده","saved tracks" to "آهنگ ذخیره‌شده","Add music to" to "افزودن موسیقی به").getOrDefault(value, value) else value

private val Deep = Color(0xFF090A12)
private val CardDark = Color(0xFF151723)
private val Subtle = Color(0xFF9B9BAF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OnPlayerApp() }
    }
}

private enum class Tab(val title: String) { Home("Home"), Library("Library"), Playlists("Playlists"), Favorites("Favorites"), Settings("Settings") }

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
    val languagePrefs = remember(context) { context.getSharedPreferences("on_player_settings", android.content.Context.MODE_PRIVATE) }
    var appLanguage by remember { mutableStateOf(languagePrefs.getString("app_language", "English") ?: "English") }
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
    val isAccentTheme = false
    val themeTrack = current ?: lastPlayed
    val accent = Purple
    val dark = isAccentTheme || isAmoled || (themeMode != "Light" && (themeMode != "System" || systemDark))
    val background = when { isAmoled -> Color.Black; dark -> Deep; else -> Color(0xFFF5F4FA) }
    val foreground = if (dark) Color.White else Color(0xFF22232E)
    val muted = if (dark) Subtle else Color(0xFF6E7081)
    val surface = if (isAmoled) Color(0xFF090909) else if (dark) CardDark else Color.White
    val colors = if (dark) darkColorScheme(primary=accent, background=background, surface=surface, onSurface=foreground, onBackground=foreground)
        else lightColorScheme(primary=accent, background=background, surface=surface, onSurface=foreground, onBackground=foreground)

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
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val alreadyGranted = ContextCompat.checkSelfPermission(context, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            // Open straight into a ready library: scan automatically without prompting again.
            vm.scan()
            AutoLibraryScanScheduler.ensureScheduled(context)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        } else {
            if (Build.VERSION.SDK_INT >= 33) {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
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
        CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
        if (playerOpen) {
            PlayerScreen(
                file = current,
                appLanguage = appLanguage,
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
                        val miniTrack = current ?: lastPlayed
                        if (tab in listOf(Tab.Library, Tab.Playlists, Tab.Favorites) && miniTrack != null) MiniPlayer(miniTrack, playing, dark, controller, accent, onClick={ vm.open(miniTrack); playerOpen=true }, onToggle={ if (controller?.isPlaying==true) controller.pause() else controller?.play() })
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
                                    Tab.Settings -> Icons.Default.Settings
                                }
                                val selected = tab == item
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                                         .background(if(selected) Brush.linearGradient(listOf(Purple.copy(alpha=0.25f),Color.White.copy(alpha=0.07f))) else Brush.linearGradient(listOf(Color.Transparent,Color.Transparent)))
                                         .clickable { tab = item }
                                        .padding(vertical=6.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally
                                ) {
                                    Icon(icon,null,tint=if(selected) accent else muted,modifier=Modifier.size(19.dp))
                                    Text(tr(item.title),color=if(selected) accent else muted,fontSize=8.sp,maxLines=1)
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInHorizontally(initialOffsetX = { it / 7 })) togetherWith
                            (fadeOut(tween(120)) + slideOutHorizontally(targetOffsetX = { -it / 10 }))
                    },
                    label = "page transition"
                ) { activeTab ->
                    Box(Modifier.fillMaxSize().padding(padding).background(if(activeTab==Tab.Library) Color.Black else Color.Transparent)) {
                        when(activeTab) {
                            Tab.Home -> HomeTab(files, lastPlayed, playing, scanning, scanError, dark, foreground, muted, surface, accent, controller, onRefresh={vm.scan()}, onPick={audioPicker.launch(arrayOf("audio/*"))}, onOpenSong={vm.open(it);playerOpen=true}, onLibrary={tab=Tab.Library})
                            Tab.Library -> SongListTab(tr("Your library"),files,scanning,scanError,dark,foreground,muted,onRefresh={vm.scan()},onOpenSong={vm.open(it);playerOpen=true},onPick={audioPicker.launch(arrayOf("audio/*"))},searchable=true)
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
                            Tab.Favorites -> SongListTab(tr("Favorites"),files.filter{it.uri.toString() in favorites},scanning,null,dark,foreground,muted,onRefresh={vm.scan()},onOpenSong={vm.open(it);playerOpen=true},onPick={audioPicker.launch(arrayOf("audio/*"))},emptyText="No favorites yet")
                            Tab.Settings -> SettingsTab(themeMode,dark,foreground,muted,vm,onLanguageChanged={ appLanguage=it })
                        }
                    }
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
private fun HomeTab(files:List<MediaFile>,last:MediaFile?,playing:Boolean,loading:Boolean,error:String?,dark:Boolean,fg:Color,muted:Color,surface:Color,accent:Color,controller:MediaController?,onRefresh:()->Unit,onPick:()->Unit,onOpenSong:(MediaFile)->Unit,onLibrary:()->Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Purple.copy(alpha=if(dark)0.14f else 0.08f), if(dark)Deep.copy(alpha=0.72f) else Color(0xFFF5F4FA).copy(alpha=0.58f), if(dark)Deep else Color(0xFFF5F4FA))))) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=24.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("On Player"),color=fg,fontSize=32.sp,fontWeight=FontWeight.Bold,lineHeight=36.sp)
                    Text(tr("Lightweight. Ad-free. Just music."),color=muted,fontSize=14.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=5.dp))
                }
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(accent,accent.copy(alpha=0.55f)))),contentAlignment=Alignment.Center) { Icon(Icons.Default.Headphones,contentDescription=null,modifier=Modifier.size(28.dp),tint=Color.White) }
            }
        }
        item {
            last?.let { FeaturedTrack(it,playing,dark,accent,controller,onOpenSong,onOpenSong) } ?: Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.Start) {
                    Icon(Icons.Default.MusicNote,contentDescription=null,tint=Purple,modifier=Modifier.size(42.dp))
                    Text(tr("Your music awaits"),color=fg,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
                    Text(tr("Choose a song to start listening."),color=muted)
                    Button(onClick=onPick,modifier=Modifier.padding(top=14.dp),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color.White)) { Icon(Icons.Default.FolderOpen,null); Spacer(Modifier.width(6.dp)); Text(tr("Choose music"),color=Color.White,fontWeight=FontWeight.SemiBold) }
                }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                QuickAction(tr("All songs"),files.size,Icons.Default.LibraryMusic,Modifier.weight(1f),dark,fg,accent,onLibrary)
                QuickAction(tr("Recently added"),files.take(12).size,Icons.Default.History,Modifier.weight(1f),dark,fg,accent,onLibrary)
            }
        }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Text(tr("Recently added"),color=fg,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); Text(tr("See all"),color=accent,modifier=Modifier.clickable(onClick=onLibrary)) } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error!=null) item { Text(error,color=muted) }
        items(files.take(12),key={it.uri.toString()}) { song -> SongRow(song,dark,fg,muted,onClick={onOpenSong(song)}) }
        item { OutlinedButton(onClick=onRefresh,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text(tr("Rescan music")) } }
    }
    }
}

@Composable
private fun GlassCardColors(dark:Boolean, accent:Color):CardColors = CardDefaults.cardColors(containerColor=if(dark) Color(0xB91B1B27) else Color(0xCFFFFFFF))

private fun Modifier.roundGlass(dark:Boolean,accent:Color,shape:RoundedCornerShape=RoundedCornerShape(24.dp)):Modifier = this
    .clip(shape)
    .background(if(dark) Brush.linearGradient(listOf(Color.White.copy(alpha=0.075f),accent.copy(alpha=0.045f),Color(0xFF14141F).copy(alpha=0.76f))) else Brush.linearGradient(listOf(Color.White.copy(alpha=0.78f),accent.copy(alpha=0.08f),Color(0xFFF4F5FA).copy(alpha=0.78f))))
    .border(1.dp,if(dark) Brush.linearGradient(listOf(Color.White.copy(alpha=0.19f),accent.copy(alpha=0.20f),Color.White.copy(alpha=0.07f))) else Brush.linearGradient(listOf(Color.White.copy(alpha=0.96f),accent.copy(alpha=0.18f),Color(0xFFD6DCE8)),),shape)

@Composable
private fun FeaturedTrack(file:MediaFile,playing:Boolean,dark:Boolean,accent:Color,controller:MediaController?,onOpen:(MediaFile)->Unit,onOpenCard:(MediaFile)->Unit) {
    val artwork by rememberArtwork(file.uri)
    val discRotation=rememberInfiniteTransition(label="featured disc").animateFloat(initialValue=0f,targetValue=360f,animationSpec=infiniteRepeatable(tween(12000,easing=androidx.compose.animation.core.LinearEasing)),label="rotation").value
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controller,playing) { while(true) { pos=controller?.currentPosition?.coerceAtLeast(0L)?:0L; dur=controller?.duration?.takeIf{it>0L}?:0L; kotlinx.coroutines.delay(if(playing)500L else 1000L) } }
    Box(Modifier.fillMaxWidth().height(390.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha=0.8f),Color(0xFF141521),Color(0xFF11121A)))).clickable { onOpenCard(file) }) {
        artwork?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xE9090A12),Color(0xFF090A12))))) {}
        Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.Bottom) {
            Text(if(playing)"NOW PLAYING" else "LAST PLAYED",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
            Text(file.displayTitle,color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(file.displayArtist,color=Color.White.copy(alpha=.75f),maxLines=1)
            Slider(value=if(dur>0L)pos.toFloat().coerceIn(0f,dur.toFloat())else 0f,onValueChange={v->if(dur>0L)controller?.seekTo(v.toLong())},valueRange=0f..(dur.coerceAtLeast(1L).toFloat()),modifier=Modifier.fillMaxWidth().padding(top=6.dp).height(20.dp),colors=SliderDefaults.colors(thumbColor=accent,activeTrackColor=accent,inactiveTrackColor=Color.White.copy(alpha=.30f)))
            Row(Modifier.fillMaxWidth().padding(top=2.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(formatMiniTime(pos),color=Color.White.copy(alpha=.7f),fontSize=10.sp);Text(formatMiniTime(dur),color=Color.White.copy(alpha=.7f),fontSize=10.sp) }
            Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={controller?.seekToPreviousMediaItem()}) { Icon(Icons.Default.SkipPrevious,null,tint=Color.White,modifier=Modifier.size(30.dp)) }
                IconButton(onClick={if(controller?.isPlaying==true)controller.pause() else controller?.play()}) { Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=accent,modifier=Modifier.size(42.dp)) }
                IconButton(onClick={controller?.seekToNextMediaItem()}) { Icon(Icons.Default.SkipNext,null,tint=Color.White,modifier=Modifier.size(30.dp)) }
            }
        }
    }
}

@Composable
private fun QuickAction(title:String,count:Int,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,dark:Boolean,fg:Color,accent:Color,onClick:()->Unit) {
    Card(modifier.clickable(onClick=onClick).roundGlass(dark,accent,RoundedCornerShape(22.dp)),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent)) {
        Column(Modifier.padding(16.dp)) { Icon(icon,contentDescription=null,tint=accent,modifier=Modifier.size(23.dp)); Text(count.toString(),color=fg,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp)); Text(title,color=if(dark) Color(0xFFB8B8C8) else Color(0xFF555967),fontSize=12.sp) }
    }
}

@Composable
private fun SongListTab(title:String,songs:List<MediaFile>,loading:Boolean,error:String?,dark:Boolean,fg:Color,muted:Color,onRefresh:()->Unit,onOpenSong:(MediaFile)->Unit,onPick:()->Unit,emptyText:String="No music found",searchable:Boolean=false) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchReveal by animateFloatAsState(if(searchOpen) 1f else 0f, animationSpec=tween(240), label="library search reveal")
    val visibleSongs=remember(songs,query){ if(query.isBlank()) songs else songs.filter { it.displayTitle.contains(query,true) || it.displayArtist.contains(query,true) || it.displayAlbum.contains(query,true) } }
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
            if(searchOpen) { OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.weight(1f).graphicsLayer { alpha=searchReveal; scaleX=0.96f+0.04f*searchReveal; scaleY=0.96f+0.04f*searchReveal },placeholder={Text(tr("Song, artist or album"))},singleLine=true,shape=RoundedCornerShape(18.dp),leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Clear,null)}}) }
            else Text(tr(title),color=fg,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            if(searchable) IconButton(onClick={ if(searchOpen){query="";searchOpen=false}else searchOpen=true },modifier=Modifier.graphicsLayer { scaleX=if(searchOpen)0.94f else 1f; scaleY=if(searchOpen)0.94f else 1f }) { Icon(if(searchOpen)Icons.Default.Close else Icons.Default.Search,contentDescription="Search in library",tint=fg) }
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error!=null) Text(error,color=muted,modifier=Modifier.padding(bottom=8.dp))
        if(songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.MusicNote,contentDescription=null,tint=Purple,modifier=Modifier.size(48.dp)); Text(tr(emptyText),color=muted,modifier=Modifier.padding(top=12.dp)); Button(onClick=onPick,modifier=Modifier.padding(top=12.dp)){Text(tr("Pick a song"))} } }
        } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { items(visibleSongs,key={it.uri.toString()}) { SongRow(it,dark,fg,muted,onClick={onOpenSong(it)}) } }

    }
}

@Composable
private fun SearchTab(files:List<MediaFile>,favorites:Set<String>,dark:Boolean,fg:Color,muted:Color,onOpenSong:(MediaFile)->Unit,onFavorite:(MediaFile)->Unit) {
    var query by remember{mutableStateOf("")}
    val filtered=remember(files,query){files.filter{it.displayTitle.contains(query,true)||it.displayArtist.contains(query,true)||it.displayAlbum.contains(query,true)}}
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Text(tr("Search music"),color=fg,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=12.dp))
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),placeholder={Text(tr("Song, artist or album"))},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Clear,null)}},singleLine=true,shape=RoundedCornerShape(18.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { items(filtered,key={it.uri.toString()}) { SongRow(it,dark,fg,muted,onClick={onOpenSong(it)},favorite=it.uri.toString() in favorites,onFavorite={onFavorite(it)}) } }
    }
}

@Composable
private fun SongRow(file:MediaFile,dark:Boolean,fg:Color,muted:Color,onClick:()->Unit,favorite:Boolean=false,onFavorite:()->Unit={}) {
    val art by rememberArtwork(file.uri)
    Row(Modifier.fillMaxWidth().roundGlass(dark,Purple,RoundedCornerShape(20.dp)).clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF6747AD),Color(0xFF1D355E)))),contentAlignment=Alignment.Center) { if(art!=null) Image(art!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Icon(Icons.Default.MusicNote,contentDescription=null,tint=Color.White) }
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(file.displayTitle,color=fg,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(file.displayArtist,color=muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }
        if(favorite) IconButton(onClick=onFavorite) { Icon(Icons.Default.Favorite,contentDescription="Remove favorite",tint=Color(0xFFEE79A8)) }
        else Icon(Icons.Default.PlayCircleOutline,tint=Purple,contentDescription="Play")
    }
}

@Composable
private fun MiniPlayer(file:MediaFile,playing:Boolean,dark:Boolean,controller:MediaController?,accent:Color,onClick:()->Unit,onToggle:()->Unit) {
    val art by rememberArtwork(file.uri)
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controller, playing) {
        while (true) {
            pos = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            dur = controller?.duration?.takeIf { it > 0L } ?: 0L
            kotlinx.coroutines.delay(if (playing) 500L else 1000L)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp).clip(RoundedCornerShape(17.dp))) {
        if (art != null) Image(art!!.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize().blur(10.dp).graphicsLayer { alpha=0.24f; scaleX=1.08f; scaleY=1.08f },contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxWidth().roundGlass(dark,Purple,RoundedCornerShape(17.dp))) {
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Purple),contentAlignment=Alignment.Center) { if(art!=null)Image(art!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Default.MusicNote,null,tint=Color.White) }
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)) { Text(file.displayTitle,color=if(dark)Color.White else Color(0xFF20202A),fontWeight=FontWeight.SemiBold,maxLines=1);Text(file.displayArtist,color=if(dark) Color(0xFFB8B8C8) else Color(0xFF555967),fontSize=11.sp,maxLines=1) }
                    IconButton(onClick={controller?.seekToPreviousMediaItem()}) { Icon(Icons.Default.SkipPrevious,null,tint=accent) }
                    IconButton(onClick=onToggle) { Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=accent) }
                    IconButton(onClick={controller?.seekToNextMediaItem()}) { Icon(Icons.Default.SkipNext,null,tint=accent) }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal=10.dp).padding(bottom=6.dp)) {
                    Slider(value=if(dur>0L)pos.toFloat().coerceIn(0f,dur.toFloat())else 0f,onValueChange={v->if(dur>0L)controller?.seekTo(v.toLong())},valueRange=0f..(dur.coerceAtLeast(1L).toFloat()),modifier=Modifier.fillMaxWidth().height(18.dp),colors=SliderDefaults.colors(thumbColor=accent,activeTrackColor=accent,inactiveTrackColor=if(dark)Color.White.copy(alpha=0.15f)else Color.Black.copy(alpha=0.10f)))
                    Row(Modifier.fillMaxWidth().padding(top=2.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(formatMiniTime(pos),fontSize=9.sp,color=if(dark) Color(0xFFB8B8C8) else Color(0xFF555967))
                        Text(formatMiniTime(dur),fontSize=9.sp,color=if(dark) Color(0xFFB8B8C8) else Color(0xFF555967))
                    }
                }
            }
        }
    }
}
private fun formatMiniTime(ms:Long):String {
    val total=(ms.coerceAtLeast(0L)/1000L)
    return String.format(Locale.getDefault(),"%d:%02d",total/60,total%60)
}

@Composable
private fun SettingsTab(theme:String,dark:Boolean,fg:Color,muted:Color,vm:OnPlayerViewModel,onLanguageChanged:(String)->Unit) {
    val files by vm.files.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var confirmClearFavorites by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("on_player_settings", android.content.Context.MODE_PRIVATE) }
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", "English") ?: "English") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
        Text(tr("Settings"),color=fg,fontSize=30.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp,bottom=20.dp))
        Text(tr("LANGUAGE"),color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp)
        Card(Modifier.fillMaxWidth().roundGlass(dark,Purple).padding(top=10.dp),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)) {
            Column(Modifier.padding(16.dp)) {
                listOf("English","فارسی").forEach { language ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        appLanguage = language
                        prefs.edit().putString("app_language", language).apply()
                        onLanguageChanged(language)
                    }.padding(vertical=13.dp,horizontal=4.dp), verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Language,null,tint=Purple)
                        Text(language, color=fg, modifier=Modifier.weight(1f).padding(start=12.dp))
                        if(appLanguage==language) Icon(Icons.Default.CheckCircle,null,tint=Purple)
                    }
                }
            }
        }
        Text(tr("APPEARANCE"),color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=22.dp))
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
        Text(tr("LIBRARY"),color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=24.dp))
        Card(
            Modifier.fillMaxWidth().padding(top=10.dp),
            shape=RoundedCornerShape(24.dp),
            colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryMusic,null,tint=Purple)
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(tr("Music library"),color=fg,fontWeight=FontWeight.SemiBold)
                        Text(files.size.toString()+" "+tr("tracks scanned"),color=muted,fontSize=12.sp)
                    }
                    TextButton(onClick={vm.scan()}) { Text(tr("Rescan")) }
                }
                HorizontalDivider(color=muted.copy(alpha=0.18f))
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=12.dp)) {
                    Icon(Icons.Default.Favorite,null,tint=Color(0xFFEA79A8))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(tr("Favorites"),color=fg,fontWeight=FontWeight.SemiBold)
                        Text(favorites.size.toString()+" "+tr("saved tracks"),color=muted,fontSize=12.sp)
                    }
                    TextButton(onClick={confirmClearFavorites=true},enabled=favorites.isNotEmpty()) { Text(tr("Clear")) }
                }
            }
        }
        Text(tr("PLAYBACK"),color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=24.dp))
        Card(
            Modifier.fillMaxWidth().padding(top=10.dp),
            shape=RoundedCornerShape(24.dp),
            colors=CardDefaults.cardColors(containerColor=if(dark) CardDark else Color.White)
        ) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq,null,tint=Purple)
                Column(Modifier.weight(1f).padding(start=12.dp)) {
                    Text(tr("Audio focus"),color=fg,fontWeight=FontWeight.SemiBold)
                    Text(tr("On Player requests Android audio focus when playback starts."),color=muted,fontSize=12.sp)
                }
                Icon(Icons.Default.CheckCircle,contentDescription="Enabled",tint=Color(0xFF69C99B))
            }
        }
        Text(tr("ABOUT"),color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,modifier=Modifier.padding(top=24.dp))
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
                        Text(tr("On Player"),color=fg,fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text(tr("Version 1.0 • Music, beautifully simple"),color=muted,fontSize=12.sp)
                    }
                }
                Text(
                    if(appLanguage=="فارسی")
                        "یک موزیک‌پلیر سبک با طراحی بنفش، دیسک متحرک، رنگ‌های هماهنگ با کاور، بلور پس‌زمینه و کنترل‌های روان."
                    else
                        "A lightweight music player with artwork-reactive colors, blurred album wallpaper, a symmetric rotating CD, and smooth playback controls.",
                    color=muted,fontSize=13.sp,modifier=Modifier.padding(top=14.dp)
                )
                Text(if(appLanguage=="فارسی") "ویژگی‌ها" else "FEATURES",color=Purple,fontWeight=FontWeight.Bold,fontSize=11.sp,letterSpacing=1.2.sp,modifier=Modifier.padding(top=16.dp,bottom=8.dp))
                val features = if(appLanguage=="فارسی") listOf(
                    "پخش موسیقی از حافظه دستگاه",
                    "ساخت و مدیریت پلی‌لیست",
                    "علاقه‌مندی‌ها و ذخیره آهنگ‌ها",
                    "پخش در پس‌زمینه و کنترل از اعلان‌ها",
                    "دیسک CD گرد و متقارن با چرخش هنگام پخش",
                    "پس‌زمینه بلور شده از کاور آهنگ",
                    "رنگ پویا هماهنگ با کاور",
                    "جستجو، تغییر نام و حذف آهنگ",
                    "پخش تصادفی و تکرار آهنگ",
                    "جابجایی بین آهنگ‌ها با کشیدن صفحه",
                    "پوسته روشن، تاریک و AMOLED",
                    "انتخاب زبان فارسی یا انگلیسی"
                ) else listOf(
                    "Local music library playback",
                    "Create and manage playlists",
                    "Favorites and saved tracks",
                    "Background playback and notification controls",
                    "Symmetric rotating CD while playing",
                    "Blurred album-art wallpaper",
                    "Artwork-reactive accent colors",
                    "Search, rename and delete tracks",
                    "Shuffle and repeat controls",
                    "Swipe to switch tracks",
                    "Light, Dark and AMOLED themes",
                    "Persian and English language option"
                )
                features.forEach { feature ->
                    Row(Modifier.padding(vertical=4.dp),verticalAlignment=Alignment.Top) {
                        Icon(Icons.Default.CheckCircle,null,tint=Purple,modifier=Modifier.size(16.dp))
                        Text(feature,color=fg,fontSize=12.sp,modifier=Modifier.padding(start=8.dp))
                    }
                }
                Text(if(appLanguage=="فارسی") "ساخته شده برای گوش دادن ساده، زیبا و بدون حواس‌پرتی." else "Made for focused listening: simple, beautiful, and distraction-free.",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
            }
        }
    }
    if(confirmClearFavorites) {
        AlertDialog(
            onDismissRequest={confirmClearFavorites=false},
            title={Text(tr("Clear favorites?"))},
            text={Text(tr("This removes all saved favorites from On Player."))},
            confirmButton={TextButton(onClick={vm.clearFavorites();confirmClearFavorites=false}){Text(tr("Clear"))}},
            dismissButton={TextButton(onClick={confirmClearFavorites=false}){Text(tr("Cancel"))}}
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
                Text(if(selected == null) tr("Your playlists") else selected.name,color=fg,fontSize=27.sp,fontWeight=FontWeight.Bold)
                Text(
                    if(selected == null) playlists.size.toString()+" "+tr("playlists saved on this device")
                    else selected.songUris.size.toString()+" "+tr("songs"),
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
                        Text(tr("Make it yours"),color=fg,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp))
                        Text(tr("Create a playlist and collect your favorite tracks."),color=muted,fontSize=13.sp)
                        Button(onClick={newName="";showCreate=true},modifier=Modifier.padding(top=16.dp)) {
                            Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text(tr("Create playlist"))
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
                                    Text(playlist.songUris.size.toString()+" "+tr("songs"),color=muted,fontSize=12.sp)
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
                    Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text(tr("Add songs"))
                }
                OutlinedButton(onClick={onDelete(selected.id)}) {
                    Icon(Icons.Default.DeleteOutline,null);Spacer(Modifier.width(5.dp));Text(tr("Delete playlist"))
                }
            }
            val songs = selected.songUris.mapNotNull { uri -> files.firstOrNull { it.uri.toString() == uri } }
            if(songs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Text(tr("This playlist is empty. Add some music."),color=muted)
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
            title={Text(tr("New playlist"))},
            text={
                OutlinedTextField(
                    value=newName,onValueChange={newName=it},singleLine=true,
                    label={Text(tr("Playlist name"))},placeholder={Text(tr("e.g. Late night"))}
                )
            },
            confirmButton={
                TextButton(onClick={if(newName.isNotBlank()){onCreate(newName);showCreate=false}}) { Text(tr("Create")) }
            },
            dismissButton={TextButton(onClick={showCreate=false}){Text(tr("Cancel"))}}
        )
    }
    if(showAddSongs && selected != null) {
        AlertDialog(
            onDismissRequest={showAddSongs=false},
            title={Text(tr("Add music to") + " " + selected.name)},
            text={
                if(files.isEmpty()) Text(tr("Your library is empty."))
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
            confirmButton={TextButton(onClick={showAddSongs=false}){Text(tr("Done"))}}
        )
    }
}

@Composable
private fun rememberArtworkOrNull(uri:android.net.Uri?):State<android.graphics.Bitmap?> {
    val context=LocalContext.current
    return produceState<android.graphics.Bitmap?>(null,uri) {
        value=withContext(Dispatchers.IO) {
            if(uri==null) null else {
                val retriever=MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context,uri)
                    retriever.embeddedPicture?.let{bytes->android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)}
                } catch(_:Exception){null} finally { try{retriever.release()}catch(_:Exception){} }
            }
        }
    }
}

private fun sampleArtworkAccent(bitmap:android.graphics.Bitmap?):Color {
    if(bitmap==null) return Purple
    var red=0L; var green=0L; var blue=0L; var count=0
    val stepX=(bitmap.width/20).coerceAtLeast(1); val stepY=(bitmap.height/20).coerceAtLeast(1)
    for(y in 0 until bitmap.height step stepY) for(x in 0 until bitmap.width step stepX) {
        val px=bitmap.getPixel(x,y)
        val r=android.graphics.Color.red(px); val g=android.graphics.Color.green(px); val b=android.graphics.Color.blue(px)
        val hsv=FloatArray(3); android.graphics.Color.colorToHSV(px,hsv)
        if(hsv[1]>0.22f && hsv[2]>0.18f && hsv[2]<0.94f) { red+=r;green+=g;blue+=b;count++ }
    }
    if(count==0) return Purple
    val r=(red/count).toInt(); val g=(green/count).toInt(); val b=(blue/count).toInt()
    val hsv=FloatArray(3); android.graphics.Color.colorToHSV(android.graphics.Color.rgb(r,g,b),hsv)
    // Keep artwork-derived accents vivid enough to remain visible on dark glass.
    hsv[1]=(hsv[1]*1.15f).coerceIn(0.48f,0.78f)
    hsv[2]=0.92f
    return Color(android.graphics.Color.HSVToColor(hsv))
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
