package com.onplayer.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.onplayer.app.data.MediaCategory
import com.onplayer.app.data.MediaFile
import com.onplayer.app.ui.OnPlayerViewModel
import com.onplayer.app.ui.PlayerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF8B5CF6)
private val DarkBg = Color(0xFF090911)
private val DarkCard = Color(0xFF191925)
private val MutedDark = Color(0xFF9B9BAF)
private val MutedLight = Color(0xFF666778)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { OnPlayerApp() } }
}


@Composable fun OnPlayerApp(vm: OnPlayerViewModel = viewModel()) {
    val nav = rememberNavController()
    val files by vm.files.collectAsState()
    val current by vm.current.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val light = themeMode == "Light" || (themeMode == "System" && !systemDark)
    val bg = if (light) Color(0xFFF4F4F8) else if (themeMode == "AMOLED Black") Color.Black else DarkBg
    val card = if (light) Color.White else DarkCard
    val textColor = if (light) Color(0xFF20202A) else Color.White
    val muted = if (light) MutedLight else MutedDark
    val colors = if (light) lightColorScheme(primary=Accent, background=bg, surface=card, onSurface=textColor, onBackground=textColor)
                else darkColorScheme(primary=Accent, background=bg, surface=card, onSurface=textColor, onBackground=textColor)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if (result.values.any { it }) vm.scan() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES))
        else permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }
    MaterialTheme(colorScheme=colors) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(if (light) Color(0xFFF7F5FF) else Color(0xFF11101D), bg, if (light) Color(0xFFEDE9FF) else Color(0xFF08080F))))) {
            NavHost(navController=nav, startDestination="home", modifier=Modifier.fillMaxSize()) {
                composable("home") { HomeScreen(files, scanning, vm, nav, favorites, light, textColor, muted, card) }
                composable("library") { LibraryScreen(files, vm, nav, favorites, light, textColor, muted) }
                composable("player") { PlayerScreen(current, files, vm, nav) }
                composable("playlist") { PlaylistScreen(files, favorites, vm, nav, light, textColor, muted) }
                composable("settings") { SettingsScreen(vm, themeMode, nav, light, textColor, muted) }
                composable("about") { AboutScreen(nav, light, textColor, muted) }
                composable("category/{category}") { entry ->
                    val cat = MediaCategory.valueOf(entry.arguments?.getString("category") ?: "Others")
                    CategoryScreen(files.filter { it.category == cat }, cat, vm, nav, favorites, light, textColor, muted)
                }
                composable("search") { SearchScreen(files, vm, nav, favorites, light, textColor, muted) }
            }
            BottomBar(nav, Modifier.align(Alignment.BottomCenter), light) { route -> nav.navigate(route) { launchSingleTop=true } }
        }
    }
}


@Composable private fun BottomBar(nav: NavHostController, modifier: Modifier, light: Boolean, onNav: (String)->Unit) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val inactive = if (light) Color(0xFF777783) else MutedDark
    Row(modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp).background(Brush.horizontalGradient(if(light) listOf(Color(0xEFFFFFFF),Color(0xDDF0EDFF),Color(0xEEFFFFFF)) else listOf(Color(0xCC26243A),Color(0xCC171722),Color(0xCC26243A))), RoundedCornerShape(28.dp)).border(1.dp, if(light) Color.White.copy(alpha=.8f) else Color.White.copy(alpha=.12f), RoundedCornerShape(28.dp)).padding(8.dp), horizontalArrangement=Arrangement.SpaceAround, verticalAlignment=Alignment.CenterVertically) {
        BottomItem("Home", Icons.Default.Home, route=="home", inactive, onNav, "home")
        BottomItem("Files", Icons.Default.Folder, route=="library", inactive, onNav, "library")
        IconButton(onClick={onNav("player")}, modifier=Modifier.size(56.dp).background(Brush.linearGradient(listOf(Accent,Color(0xFF5D36CC))),RoundedCornerShape(50))) { Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(32.dp)) }
        BottomItem("Favorites", Icons.Default.Favorite, route=="playlist", inactive, onNav, "playlist")
        BottomItem("Settings", Icons.Default.Settings, route=="settings", inactive, onNav, "settings")
    }
}
@Composable private fun BottomItem(label:String, icon:androidx.compose.ui.graphics.vector.ImageVector, active:Boolean, inactive:Color, onNav:(String)->Unit, route:String) {
    Column(Modifier.clickable { onNav(route) }.padding(horizontal=6.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon,null,tint=if(active) Accent else inactive,modifier=Modifier.size(22.dp)); Text(label,color=if(active) Accent else inactive,fontSize=10.sp)
    }
}

@Composable private fun HomeScreen(files:List<MediaFile>, loading:Boolean, vm:OnPlayerViewModel, nav:NavHostController, favorites:Set<String>, light:Boolean, fg:Color, muted:Color, card:Color) {
    val counts=MediaCategory.values().associateWith { c -> files.count { it.category==c } }
    Column(Modifier.fillMaxSize().padding(bottom=88.dp).padding(horizontal=16.dp)) {
        Row(Modifier.padding(top=24.dp,bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
            AppLogo(52.dp); Column(Modifier.weight(1f).padding(start=12.dp)){ Text("On Player",fontSize=24.sp,fontWeight=FontWeight.Bold,color=fg); Text("Your media, neatly organized",fontSize=12.sp,color=muted) }
            IconButton(onClick={nav.navigate("search")}){Icon(Icons.Default.Search,null,tint=fg)}; IconButton(onClick={nav.navigate("settings")}){Icon(Icons.Default.Settings,null,tint=fg)}
        }
        Card(Modifier.fillMaxWidth().height(148.dp),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=card)) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Accent.copy(alpha=.18f),card)))) {
                Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) { Text("Everything you love",fontSize=22.sp,fontWeight=FontWeight.Bold,color=fg); Text("Browse photos, videos and files",color=muted,modifier=Modifier.padding(top=5.dp)); Button(onClick={nav.navigate("library")},modifier=Modifier.padding(top=10.dp)){Text("Open library")} }
                Icon(Icons.Default.PlayCircle,null,tint=Accent.copy(alpha=.45f),modifier=Modifier.align(Alignment.CenterEnd).padding(end=18.dp).size(68.dp))
            }
        }
        Text("Browse",fontSize=18.sp,fontWeight=FontWeight.Bold,color=fg,modifier=Modifier.padding(top=18.dp,bottom=10.dp))
        val cats=listOf(MediaCategory.Images,MediaCategory.Videos,MediaCategory.Audio,MediaCategory.Documents,MediaCategory.Archives,MediaCategory.APK,MediaCategory.Others)
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){ cats.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach { c -> CategoryTile(c,counts[c]?:0,Modifier.weight(1f),light,fg,muted){nav.navigate("category/${c.name}")} } } } }
        Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){ Text("Recent files",fontSize=19.sp,fontWeight=FontWeight.Bold,color=fg,modifier=Modifier.weight(1f)); Text("View all  ›",color=Accent,modifier=Modifier.clickable{nav.navigate("library")}) }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){ items(files.take(10),key={it.uri.toString()}) { FileRow(it,vm,nav,favorites,light,fg,muted) } }
    }
}
@Composable private fun AppLogo(size: androidx.compose.ui.unit.Dp) { Box(Modifier.size(size).background(Brush.linearGradient(listOf(Color(0xFF5144FF),Color(0xFF9C29E8))),RoundedCornerShape(size*.28f)),contentAlignment=Alignment.Center){Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(size*.58f))} }
@Composable private fun CategoryTile(cat:MediaCategory,count:Int,modifier:Modifier,light:Boolean,fg:Color,muted:Color,onClick:()->Unit) {
    val (icon,color)=when(cat){MediaCategory.Images->Icons.Default.Image to Color(0xFF9D52FF);MediaCategory.Videos->Icons.Default.VideoLibrary to Color(0xFFFF4F91);MediaCategory.Audio->Icons.Default.MusicNote to Color(0xFF4C9DFF);MediaCategory.Documents->Icons.Default.Description to Color(0xFF37B98D);MediaCategory.Archives->Icons.Default.FolderZip to Color(0xFFFFBE39);else->Icons.Default.FolderOpen to Accent}
    val transition = rememberInfiniteTransition(label="tile-glow")
    val glow = transition.animateFloat(initialValue=.12f,targetValue=.25f,animationSpec=infiniteRepeatable(tween(1800),RepeatMode.Reverse),label="glow")
    Column(modifier.animateContentSize().height(112.dp).background(Brush.linearGradient(if(light) listOf(Color.White,Color(0xFFF0EBFF)) else listOf(Color(0xCC252236),Color(0xCC171722))),RoundedCornerShape(20.dp)).border(1.dp,if(light) Color.White.copy(alpha=.9f) else Color.White.copy(alpha=glow.value),RoundedCornerShape(20.dp)).clickable{onClick()}.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,null,tint=color,modifier=Modifier.size(30.dp));Text(cat.label,color=fg,fontSize=13.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=5.dp));Text("$count items",color=muted,fontSize=11.sp)}
}
@Composable private fun FilePreview(file:MediaFile,icon:androidx.compose.ui.graphics.vector.ImageVector) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(null,file.uri,file.category) {
        value=withContext(Dispatchers.IO) { try {
            if(file.category==MediaCategory.Images) context.contentResolver.loadThumbnail(file.uri,android.util.Size(160,160),null)
            else if(file.category==MediaCategory.Videos) { val r=MediaMetadataRetriever(); try {r.setDataSource(context,file.uri);r.getFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)} finally {r.release()} }
            else null
        } catch(_:Exception){null} }
    }
    Box(Modifier.size(64.dp).background(Color(0x332D2946),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){ if(bitmap!=null) Image(bitmap!!.asImageBitmap(),file.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Icon(icon,null,tint=Accent,modifier=Modifier.size(28.dp)); if(file.category==MediaCategory.Videos) Icon(Icons.Default.PlayCircle,null,tint=Color.White,modifier=Modifier.size(23.dp).align(Alignment.Center)) }
}
@Composable private fun FileRow(file:MediaFile,vm:OnPlayerViewModel,nav:NavHostController,favorites:Set<String>,light:Boolean,fg:Color,muted:Color) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val icon=when(file.category){MediaCategory.Images->Icons.Default.Image;MediaCategory.Videos->Icons.Default.Movie;MediaCategory.Audio->Icons.Default.MusicNote;MediaCategory.Documents->Icons.Default.Description;MediaCategory.Archives->Icons.Default.FolderZip;else->Icons.Default.InsertDriveFile}
    Row(Modifier.fillMaxWidth().background(if(light) Color.White else Color(0xFF171722),RoundedCornerShape(18.dp)).clickable {
        if(file.category==MediaCategory.Audio||file.category==MediaCategory.Videos||file.category==MediaCategory.Images){vm.open(file);nav.navigate("player")}
        else try { val i=Intent(Intent.ACTION_VIEW).apply{setDataAndType(file.uri,file.mime);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};context.startActivity(Intent.createChooser(i,"Open file")) } catch(_:Exception){}
    }.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        FilePreview(file,icon); Column(Modifier.weight(1f).padding(start=10.dp)){ Text(file.name,color=fg,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium); Text("${file.sizeText} · ${file.category.label}",color=muted,fontSize=11.sp); Text(SimpleDateFormat("MMM d · HH:mm",Locale.getDefault()).format(Date(file.modified*1000)),color=muted,fontSize=10.sp) }
        IconButton(onClick={vm.toggleFavorite(file)}){Icon(if(file.uri.toString() in favorites)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=Accent)}
    }
}
@Composable private fun LibraryScreen(files:List<MediaFile>,vm:OnPlayerViewModel,nav:NavHostController,favorites:Set<String>,light:Boolean,fg:Color,muted:Color){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text("Files",fontSize=26.sp,fontWeight=FontWeight.Bold,color=fg);Text("${files.size} files · sorted by newest",color=muted,modifier=Modifier.padding(bottom=12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(files,key={it.uri.toString()}){FileRow(it,vm,nav,favorites,light,fg,muted)}}}}
@Composable private fun CategoryScreen(files:List<MediaFile>,cat:MediaCategory,vm:OnPlayerViewModel,nav:NavHostController,favorites:Set<String>,light:Boolean,fg:Color,muted:Color){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text(cat.label,fontSize=26.sp,fontWeight=FontWeight.Bold,color=fg);Text("${files.size} items",color=muted,modifier=Modifier.padding(bottom=12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(files,key={it.uri.toString()}){FileRow(it,vm,nav,favorites,light,fg,muted)}}}}
@Composable private fun SearchScreen(files:List<MediaFile>,vm:OnPlayerViewModel,nav:NavHostController,favorites:Set<String>,light:Boolean,fg:Color,muted:Color){var query by remember{mutableStateOf("")};val found=files.filter{it.name.contains(query,true)||it.category.label.contains(query,true)};Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text("Search",fontSize=26.sp,color=fg,fontWeight=FontWeight.Bold);OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search files…")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true);LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(found,key={it.uri.toString()}){FileRow(it,vm,nav,favorites,light,fg,muted)}}}}
@Composable private fun PlaylistScreen(files:List<MediaFile>,favorites:Set<String>,vm:OnPlayerViewModel,nav:NavHostController,light:Boolean,fg:Color,muted:Color){val fav=files.filter{it.uri.toString() in favorites};Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text("Favorites",fontSize=26.sp,fontWeight=FontWeight.Bold,color=fg);Text("${fav.size} saved items",color=muted,modifier=Modifier.padding(bottom=12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(fav,key={it.uri.toString()}){FileRow(it,vm,nav,favorites,light,fg,muted)}}}}
@Composable private fun SettingsScreen(vm:OnPlayerViewModel,mode:String,nav:NavHostController,light:Boolean,fg:Color,muted:Color){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(20.dp)){Text("Settings",fontSize=27.sp,fontWeight=FontWeight.Bold,color=fg);Text("Appearance",color=muted,modifier=Modifier.padding(top=18.dp,bottom=4.dp));listOf("Dark","Light","AMOLED Black","System").forEach{option->Row(Modifier.fillMaxWidth().clickable{vm.setTheme(option)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Text(option,color=fg,modifier=Modifier.weight(1f));RadioButton(mode==option,onClick={vm.setTheme(option)})}};HorizontalDivider();Row(Modifier.fillMaxWidth().clickable{vm.scan()}.padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Refresh,null,tint=Accent);Text("Refresh media library",color=fg,modifier=Modifier.padding(start=12.dp))};HorizontalDivider();Row(Modifier.fillMaxWidth().clickable{nav.navigate("about")}.padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null,tint=Accent);Text("About On Player",color=fg,modifier=Modifier.weight(1f).padding(start=12.dp));Icon(Icons.Default.ChevronRight,null,tint=muted)}}}
@Composable private fun AboutScreen(nav:NavHostController,light:Boolean,fg:Color,muted:Color){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.ArrowBack,null,tint=fg)};Text("About On Player",color=fg,fontSize=22.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(28.dp));AppLogo(100.dp);Text("On Player",color=fg,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp));Text("Play. Browse. Enjoy.",color=muted,modifier=Modifier.padding(top=5.dp));Spacer(Modifier.height(24.dp));Card(colors=CardDefaults.cardColors(containerColor=if(light) Color.White else DarkCard),shape=RoundedCornerShape(24.dp)){Column(Modifier.fillMaxWidth().padding(18.dp)){Text("Your all-in-one media viewer",color=fg,fontWeight=FontWeight.SemiBold);Text("Music · Video · Photos · Files",color=muted,modifier=Modifier.padding(top=8.dp));Text("Version 1.0",color=muted,modifier=Modifier.padding(top=12.dp))}}}}
