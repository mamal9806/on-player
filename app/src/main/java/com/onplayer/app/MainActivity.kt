package com.onplayer.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.onplayer.app.data.MediaCategory
import com.onplayer.app.data.MediaFile
import com.onplayer.app.data.MediaRepository
import com.onplayer.app.ui.OnPlayerViewModel
import com.onplayer.app.ui.PlayerScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg=Color(0xFF080810); private val Surface=Color(0xFF171725); private val Purple=Color(0xFF9B4DFF); private val Muted=Color(0xFF9B9BB6)
class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); setContent { OnPlayerApp() } } }

@Composable fun OnPlayerApp(vm:OnPlayerViewModel= viewModel()) {
    val nav=rememberNavController(); val files by vm.files.collectAsState(); val current by vm.current.collectAsState(); val search by vm.search.collectAsState(); val scanning by vm.scanning.collectAsState(); val favorites by vm.favorites.collectAsState(); val themeMode by vm.themeMode.collectAsState()
    var hasPermissions by remember { mutableStateOf(false) }
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ results -> hasPermissions=results.values.any{it} || results.values.all{it}; if(hasPermissions) vm.scan() }
    LaunchedEffect(Unit){ if(Build.VERSION.SDK_INT>=33) { val p=listOf(Manifest.permission.READ_MEDIA_AUDIO,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_IMAGES); permissionLauncher.launch(p.toTypedArray()) } else permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }
    MaterialTheme(colorScheme=darkColorScheme(primary=Purple,background=Bg,surface=Surface,onBackground=Color.White,onSurface=Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF08070F), Color(0xFF0B0717), Color(0xFF05060B))))) {
            NavHost(navController=nav,startDestination="home",modifier=Modifier.fillMaxSize()) {
                composable("home"){ HomeScreen(files,scanning,search,vm,nav,favorites) }
                composable("library"){ LibraryScreen(files,vm,nav) }
                composable("player"){ PlayerScreen(current,files,vm,nav) }
                composable("playlist"){ PlaylistScreen(files,favorites,vm,nav) }
                composable("settings"){ SettingsScreen(vm,themeMode,nav) }
                composable("about"){ AboutScreen(nav) }
                composable("category/{category}"){ entry -> val cat=MediaCategory.valueOf(entry.arguments?.getString("category") ?: "Others"); CategoryScreen(files.filter{it.category==cat},cat,vm,nav) }
                composable("search"){ SearchScreen(files,vm,nav) }
            }
            BottomBar(nav,current!=null,Modifier.align(Alignment.BottomCenter)) { route -> nav.navigate(route){ launchSingleTop=true } }
        }
    }
}

@Composable private fun BottomBar(nav:NavHostController,playing:Boolean,modifier:Modifier=Modifier,onNav:(String)->Unit){
    val route=nav.currentBackStackEntryAsState().value?.destination?.route
    Box(modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp).background(Color(0xE8171727),RoundedCornerShape(32.dp)).padding(horizontal=8.dp,vertical=8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceAround,verticalAlignment=Alignment.Center) {
            BottomItem("Home",Icons.Default.Home,route=="home"){onNav("home")}; BottomItem("Library",Icons.Default.Folder,route=="library"){onNav("library")}
            Box(Modifier.size(64.dp).background(Brush.linearGradient(listOf(Purple,Color(0xFF5D2CDA))),RoundedCornerShape(50)).clickable{onNav("player")},contentAlignment=Alignment.Center){ Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(36.dp)) }
            BottomItem("Playlist",Icons.Default.QueueMusic,route=="playlist"){onNav("playlist")}; BottomItem("Settings",Icons.Default.Settings,route=="settings"){onNav("settings")}
        }
    }
}
@Composable private fun BottomItem(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean,click:()->Unit){Column(Modifier.clickable{click()}.padding(horizontal=7.dp),horizontalAlignment=Alignment.Center){Icon(icon,null,tint=if(active) Purple else Muted,modifier=Modifier.size(24.dp));Text(label,color=if(active) Purple else Muted,fontSize=11.sp)} }
@Composable private fun HomeScreen(files:List<MediaFile>,loading:Boolean,search:String,vm:OnPlayerViewModel,nav:NavHostController,favorites:Set<String>){
    val counts=MediaCategory.values().associateWith{cat->files.count{it.category==cat}}
    Column(Modifier.fillMaxSize().padding(bottom=92.dp).padding(horizontal=16.dp)) {
        Row(Modifier.padding(top=24.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically){ Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF5144FF),Color(0xFF9C29E8))),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(34.dp))}; Column(Modifier.weight(1f).padding(start=14.dp)){Text("On Player",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Your all-in-one media viewer",color=Muted,fontSize=13.sp)}; IconButton(onClick={nav.navigate("search")}){Icon(Icons.Default.Search,null,tint=Color.White)}; IconButton(onClick={nav.navigate("settings")}){Icon(Icons.Default.MoreVert,null,tint=Color.White)} }
        Card(Modifier.fillMaxWidth().height(190.dp),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xAA171525)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x557D56FF))) { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF17152A),Color(0xFF281044),Color(0xFF121426))))){Column(Modifier.align(Alignment.CenterStart).padding(22.dp)){Text("Play. Browse. Enjoy.",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("All your media, in one place.",color=Muted,modifier=Modifier.padding(top=7.dp));Spacer(Modifier.height(18.dp));Button(onClick={nav.navigate("library")},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF262338))){Text("Explore",color=Color.White);Icon(Icons.Default.ChevronRight,null)} }; Icon(Icons.Default.Movie,null,tint=Purple,modifier=Modifier.align(Alignment.CenterEnd).padding(end=24.dp).size(80.dp)) } }
        Spacer(Modifier.height(16.dp)); val categories=listOf(MediaCategory.Images,MediaCategory.Videos,MediaCategory.Audio,MediaCategory.Documents,MediaCategory.Archives,MediaCategory.Others)
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){ categories.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{cat-> CategoryTile(cat,counts[cat]?:0,Modifier.weight(1f)){nav.navigate("category/${cat.name}")} } } } }
        Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){Text("Recent Files",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.weight(1f));Text("See All  ›",color=Purple,modifier=Modifier.clickable{nav.navigate("library")})}
        if(loading) Text("Scanning media…",color=Muted)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(files.take(8),key={it.uri.toString()}){FileRow(it,vm,nav,favorites)}}
    }
}
@Composable private fun CategoryTile(cat:MediaCategory,count:Int,modifier:Modifier=Modifier,onClick:()->Unit){val (ico,col)=when(cat){MediaCategory.Images->Icons.Default.Image to Color(0xFF9D52FF);MediaCategory.Videos->Icons.Default.VideoLibrary to Color(0xFFFF4F91);MediaCategory.Audio->Icons.Default.MusicNote to Color(0xFF4C9DFF);MediaCategory.Documents->Icons.Default.Description to Color(0xFF37D9A2);MediaCategory.Archives->Icons.Default.FolderZip to Color(0xFFFFBE39);else->Icons.Default.GridView to Purple}; Column(modifier.height(124.dp).background(Brush.linearGradient(listOf(col.copy(alpha=.20f),Color(0xAA151522),Color(0xFF0D0D17))),RoundedCornerShape(24.dp)).then(Modifier).clickable{onClick()}.padding(8.dp),horizontalAlignment=Alignment.Center,verticalArrangement=Arrangement.Center){Icon(ico,null,tint=col,modifier=Modifier.size(36.dp));Text(cat.label,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=6.dp));Text("$count items",color=Muted,fontSize=12.sp)} }
@Composable private fun FileRow(file:MediaFile,vm:OnPlayerViewModel,nav:NavHostController,favorites:Set<String>){val context=androidx.compose.ui.platform.LocalContext.current; val icon=when(file.category){MediaCategory.Images->Icons.Default.Image;MediaCategory.Videos->Icons.Default.Movie;MediaCategory.Audio->Icons.Default.MusicNote;MediaCategory.Documents->Icons.Default.Description;MediaCategory.Archives->Icons.Default.FolderZip;else->Icons.Default.InsertDriveFile}; Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xAA1A1829),Color(0xFF11111A))),RoundedCornerShape(20.dp)).then(Modifier).clickable{ if(file.category==MediaCategory.Audio || file.category==MediaCategory.Videos || file.category==MediaCategory.Images){ vm.open(file); nav.navigate("player") } else { try { val intent=Intent(Intent.ACTION_VIEW).apply{setDataAndType(file.uri,file.mime);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}; context.startActivity(Intent.createChooser(intent,"Open file with")) } catch(_:Exception){} } }.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(64.dp).background(Color(0xFF28203E),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=Purple,modifier=Modifier.size(28.dp))};Column(Modifier.weight(1f).padding(start=12.dp)){Text(file.name,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${file.sizeText} • ${file.category.label}",color=Muted,fontSize=12.sp);Text(SimpleDateFormat("MMM d",Locale.getDefault()).format(Date(file.modified*1000)),color=Muted,fontSize=11.sp)};IconButton(onClick={vm.toggleFavorite(file)}){Icon(if(file.uri.toString() in favorites)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=Purple)} }
@Composable private fun LibraryScreen(files:List<MediaFile>,vm:OnPlayerViewModel,nav:NavHostController){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text("Library",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("${files.size} files found",color=Muted,modifier=Modifier.padding(bottom=14.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(files,key={it.uri.toString()}){FileRow(it,vm,nav,vm.favorites.collectAsState().value)}}}}
@Composable private fun CategoryScreen(files:List<MediaFile>,cat:MediaCategory,vm:OnPlayerViewModel,nav:NavHostController){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text(cat.label,fontSize=28.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("${files.size} items",color=Muted,modifier=Modifier.padding(bottom=12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(files,key={it.uri.toString()}){FileRow(it,vm,nav,vm.favorites.collectAsState().value)}}}}
@Composable private fun SearchScreen(files:List<MediaFile>,vm:OnPlayerViewModel,nav:NavHostController){var q by remember{mutableStateOf("")};val found=files.filter{it.name.contains(q,true)||it.category.label.contains(q,true)};Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text("Search",fontSize=26.sp,color=Color.White,fontWeight=FontWeight.Bold);OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search files…")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true);LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(found,key={it.uri.toString()}){FileRow(it,vm,nav,vm.favorites.collectAsState().value)}}}}
@Composable private fun PlaylistScreen(files:List<MediaFile>,favorites:Set<String>,vm:OnPlayerViewModel,nav:NavHostController){val favoriteFiles=files.filter{it.uri.toString() in favorites};Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(16.dp)){Text("Playlist",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Favorites • ${favoriteFiles.size} files",color=Muted,modifier=Modifier.padding(bottom=12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(favoriteFiles,key={it.uri.toString()}){FileRow(it,vm,nav,favorites)}}} }
@Composable private fun SettingsScreen(vm:OnPlayerViewModel,mode:String,nav:NavHostController){Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(20.dp)){Text("Settings",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Appearance",color=Muted,modifier=Modifier.padding(top=20.dp));listOf("Dark","Light","AMOLED Black","System").forEach{item->Row(Modifier.fillMaxWidth().clickable{vm.setTheme(item)}.padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Text(item,color=Color.White,modifier=Modifier.weight(1f));RadioButton(mode==item,onClick={vm.setTheme(item)},colors=RadioButtonDefaults.colors(selectedColor=Purple))} };Divider(color=Muted.copy(alpha=.3f));Row(Modifier.fillMaxWidth().clickable{vm.scan()}.padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Refresh,null,tint=Purple);Text("Refresh Library",color=Color.White,modifier=Modifier.padding(start=12.dp))};Text("Library",color=Muted,modifier=Modifier.padding(top=24.dp));Text("Refresh Library",color=Color.White,modifier=Modifier.fillMaxWidth().clickable{vm.scan()}.padding(vertical=12.dp));Divider(color=Color(0x334D4D66));Text("About",color=Muted,modifier=Modifier.padding(top=20.dp));Row(Modifier.fillMaxWidth().clickable{nav.navigate("about")}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null,tint=Purple);Text("About On Player",color=Color.White,modifier=Modifier.weight(1f).padding(start=12.dp));Icon(Icons.Default.ChevronRight,null,tint=Muted)}} }

@Composable private fun AboutScreen(nav:NavHostController){
    Column(Modifier.fillMaxSize().padding(bottom=90.dp).padding(22.dp),horizontalAlignment=Alignment.Center){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={nav.popBackStack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)};Text("About On Player",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)}
        Spacer(Modifier.height(28.dp));Box(Modifier.size(100.dp).background(Brush.linearGradient(listOf(Color(0xFF5144FF),Color(0xFF9C29E8))),RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(58.dp))}
        )
        Text("On Player",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp));Text("Play. Browse. Enjoy.",color=Muted,modifier=Modifier.padding(top=6.dp))
        Spacer(Modifier.height(24.dp));Card(colors=CardDefaults.cardColors(containerColor=Color(0xAA171525)),shape=RoundedCornerShape(24.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x557D56FF))){Column(Modifier.fillMaxWidth().padding(18.dp)){Text("Your all-in-one media viewer",color=Color.White,fontWeight=FontWeight.SemiBold);Text("Music • Video • Photos • Files",color=Muted,modifier=Modifier.padding(top=8.dp));Text("Version 1.0",color=Muted,modifier=Modifier.padding(top=12.dp))}}
        Spacer(Modifier.height(18.dp));Text("Designed for a simple, beautiful media experience. Browse your library and enjoy your files in one place.",color=Color.White,style=MaterialTheme.typography.bodyMedium)
    }
}
