package com.qemuglass.app


import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { QemuGlassApp(this) } }
}
private val night = Color(0xFF090D1C); private val panel = Color(0x22FFFFFF); private val violet = Color(0xFF9D8CFF); private val aqua = Color(0xFF67E5ED)
@Composable fun QemuGlassApp(context: Context) {
 var name by remember { mutableStateOf("My Virtual Machine") }; var os by remember { mutableStateOf("Linux") }; var arch by remember { mutableStateOf("x86_64") }; var ram by remember { mutableStateOf(2048f) }; var cores by remember { mutableStateOf(2f) }; var disk by remember { mutableStateOf(20f) }; var accel by remember { mutableStateOf(false) }; var output by remember { mutableStateOf(false) }
 val cmd = remember(name, os, arch, ram, cores, disk, accel, output) { buildCommand(name, os, arch, ram.toInt(), cores.toInt(), disk.toInt(), accel, output) }
 MaterialTheme(colorScheme = darkColorScheme(background=night, surface=night, primary=violet)) {
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF20274C), night, Color(0xFF070A13))))) {
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(aqua.copy(.35f),violet.copy(.35f))),CircleShape), contentAlignment=Alignment.Center){ Icon(Icons.Default.Memory,null,tint=Color.White,modifier=Modifier.size(26.dp)) }; Column(Modifier.padding(start=12.dp)) { Text("QEMU GLASS",color=Color.White,fontWeight=FontWeight.Bold,letterSpacing=2.sp); Text("Virtual machine command builder",color=Color.LightGray,fontSize=12.sp) } }
    Spacer(Modifier.height(24.dp)); Text("Create a machine",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold); Text("Configure a VM and generate its launch command.",color=Color(0xFFBEC5DE),modifier=Modifier.padding(top=5.dp))
    Spacer(Modifier.height(18.dp)); GlassCard { Column(Modifier.padding(16.dp)) {
      Text("MACHINE PROFILE",color=aqua,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp); Spacer(Modifier.height(12.dp)); OutlinedTextField(name,{name=it},label={Text("VM name")},singleLine=true,modifier=Modifier.fillMaxWidth(),colors=fieldColors()); Spacer(Modifier.height(12.dp)); Text("Guest OS",color=Color.White); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Linux","Windows","Other").forEach { ChoiceChip(it,os==it){os=it} } }; Spacer(Modifier.height(10.dp)); Text("Architecture",color=Color.White); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("x86_64","aarch64").forEach { ChoiceChip(it,arch==it){arch=it} } }
    } }
    Spacer(Modifier.height(12.dp)); GlassCard { Column(Modifier.padding(16.dp)) { Text("RESOURCES",color=aqua,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp); Spacer(Modifier.height(10.dp)); SliderRow("Memory",ram,512f,8192f,"${ram.toInt()} MB"){ram=it}; SliderRow("CPU cores",cores,1f,8f,cores.toInt().toString()){cores=it}; SliderRow("Disk image",disk,4f,128f,"${disk.toInt()} GB"){disk=it} } }
    Spacer(Modifier.height(12.dp)); GlassCard { Column(Modifier.padding(16.dp)) { Text("LAUNCH OPTIONS",color=aqua,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp); SwitchLine("Enable KVM acceleration",accel){accel=it}; SwitchLine("Use graphical display",output){output=it} } }
    Spacer(Modifier.height(12.dp)); GlassCard { Column(Modifier.padding(16.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Text("Generated command",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); IconButton(onClick={ val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("QEMU command",cmd)) }) { Icon(Icons.Default.ContentCopy,null,tint=aqua) } }; Text(cmd,color=Color(0xFFDAE0F7),fontSize=12.sp,lineHeight=18.sp) } }
    Spacer(Modifier.height(16.dp)); Text("Important",color=aqua,fontWeight=FontWeight.Bold); Text("This app generates commands only. QEMU is not bundled, so run the command on a computer or Android environment where QEMU is installed. The KVM option may not be available on your device.",color=Color(0xFFB9C0D9),fontSize=12.sp,lineHeight=17.sp); Spacer(Modifier.height(18.dp))
   }
  }
 }
}
@Composable fun GlassCard(content:@Composable ()->Unit) { Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color.White.copy(.12f),Color.White.copy(.035f))),RoundedCornerShape(24.dp)).border(1.dp,Brush.linearGradient(listOf(Color.White.copy(.2f),Color.White.copy(.04f))),RoundedCornerShape(24.dp))) { content() } }
@Composable fun ChoiceChip(label:String,selected:Boolean,onClick:()->Unit) { Box(Modifier.background(if(selected)violet.copy(.28f) else Color.White.copy(.05f),RoundedCornerShape(14.dp)).border(1.dp,if(selected)violet.copy(.5f) else Color.White.copy(.08f),RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(horizontal=12.dp,vertical=10.dp) { Text(label,color=if(selected)Color.White else Color.LightGray,fontSize=12.sp) } }
@Composable fun SliderRow(title:String,value:Float,min:Float,max:Float,readout:String,onChange:(Float)->Unit) { Text(title,color=Color.White); Row(verticalAlignment=Alignment.CenterVertically) { Slider(value,onChange,valueRange=min..max,modifier=Modifier.weight(1f),colors=SliderDefaults.colors(thumbColor=aqua,activeTrackColor=violet,inactiveTrackColor=Color.White.copy(.15f))); Text(readout,color=aqua,fontSize=12.sp,modifier=Modifier.width(64.dp)) } }
@Composable fun SwitchLine(title:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) { Text(title,color=Color.White,modifier=Modifier.weight(1f),fontSize=13.sp); Switch(checked,onChange,colors=SwitchDefaults.colors(checkedThumbColor=aqua,checkedTrackColor=violet)) } }
@Composable fun fieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=aqua, unfocusedBorderColor=Color.White.copy(.2f), focusedLabelColor=aqua, unfocusedLabelColor=Color.LightGray, focusedTextColor=Color.White, unfocusedTextColor=Color.White)
fun buildCommand(name:String,os:String,arch:String,ram:Int,cores:Int,disk:Int,kvm:Boolean,gui:Boolean):String { val safe=name.lowercase().replace(Regex("[^a-z0-9_-]"),"-").ifBlank{"vm"}; val machine=if(arch=="aarch64") "virt" else "q35"; val cpu=if(kvm) "host" else "max"; val display=if(gui) "-display gtk" else "-nographic"; val drive="${safe}.qcow2"; return "qemu-system-${arch} \\\n  -name \"${name}\" \\\n  -machine ${machine} \\\n  -cpu ${cpu} \\\n  -smp ${cores} \\\n  -m ${ram} \\\n  -drive file=${drive},format=qcow2,if=virtio \\\n  -boot order=d \\\n  -netdev user,id=net0 -device virtio-net-pci,netdev=net0 \\\n  ${display} \\\n  # Create disk first (one-time): qemu-img create -f qcow2 ${drive} ${disk}G \\\n  # Attach installer ISO with: -cdrom path/to/installer.iso \\\n  # Guest profile: ${os} / ${arch} / ${ram}MB RAM" }
