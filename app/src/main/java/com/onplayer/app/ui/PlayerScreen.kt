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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.onplayer.app.data.MediaFile
import com.onplayer.app.ui.UserPlaylist


private fun extractArtworkAccent(bitmap: Bitmap?): Color {
    if (bitmap == null || bitmap.isRecycled) return Color(0xFFE34B62)
    val w = bitmap.width; val h = bitmap.height
    val step = maxOf(1, minOf(w, h) / 24)
    var best = 0; var bestScore = -1f
    for (y in step until h step step) for (x in step until w step step) {
        val pixel = bitmap.getPixel(x, y)
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(pixel, hsv)
        if (hsv[1] < 0.25f || hsv[2] < 0.18f || hsv[2] > 0.96f) continue
        val score = hsv[1] * (0.5f + hsv[2] * 0.5f)
        if (score > bestScore) { bestScore = score; best = pixel }
    }
    if (bestScore < 0f) return Color(0xFFE34B62)
    return Color(best).copy(alpha = 1f)
}

private data class TrackArtwork(val bitmap: Bitmap?, val title: String, val artist: String, val album: String)

@Composable
private fun rememberTrackArtwork(file: MediaFile): TrackArtwork {
    val context = LocalContext.current
    val initial = TrackArtwork(null, file.displayTitle, file.displayArtist, file.displayAlbum)
    val data by produceState(initial, file.uri) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, file.uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: file.displayTitle
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: file.displayArtist
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: file.displayAlbum
                val bitmap = retriever.embeddedPicture?.let { bytes ->
                    // Decode album art to a bounded size to avoid large allocations on UI transitions.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 640)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    })
                }
                TrackArtwork(bitmap, title, artist, album)
            } catch (_: Exception) {
                initial
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }
    return data
}

@Composable
fun PlayerScreen(
    file: MediaFile?,
    files: List<MediaFile>,
    vm: OnPlayerViewModel,
    player: MediaController?,
    playlists: List<UserPlaylist>,
    onBack: () -> Unit,
    onAddToPlaylist: (String, MediaFile) -> Unit,
    onRenameTrack: (MediaFile, String) -> Unit,
    onDeleteTrack: (MediaFile) -> Unit
) {
    val foreground = Color.White
    val secondary = Color(0xFFD1C9E4)
    val accent = MaterialTheme.colorScheme.primary

    if (file == null) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF090A12)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(58.dp))
                Text("Choose a song", color = foreground, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        return
    }
    if (player == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF090A12)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent)
        }
        return
    }

    val artwork = rememberTrackArtwork(file)
    val artworkAccent = remember(artwork.bitmap) { extractArtworkAccent(artwork.bitmap) }
    val dynamicAccent by androidx.compose.animation.animateColorAsState(artworkAccent, animationSpec = tween(450), label = "cover accent")
    val dynamicDeep = remember(dynamicAccent) { dynamicAccent.copy(red = dynamicAccent.red * 0.30f, green = dynamicAccent.green * 0.30f, blue = dynamicAccent.blue * 0.30f) }
    val dynamicSoft = remember(dynamicAccent) { dynamicAccent.copy(alpha = 0.55f) }
    val favorites by vm.favorites.collectAsState()
    val isFavorite = file.uri.toString() in favorites
    val queue = remember(files, file.uri) {
        files.filter { it.isPlayable }.let { playable ->
            if (playable.any { it.uri == file.uri }) playable else listOf(file) + playable
        }
    }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatOne by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var moreExpanded by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember(file.uri) { mutableStateOf(file.displayTitle) }
    var newPlaylistName by remember { mutableStateOf("") }
    val discRotation = rememberInfiniteTransition(label = "disc rotation")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)),
            label = "disc rotation"
        ).value

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                position = 0L
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, file.uri, queue) {
        if (player.currentMediaItem?.localConfiguration?.uri != file.uri) {
            val items = queue.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.displayTitle)
                            .setArtist(song.displayArtist)
                            .setAlbumTitle(song.displayAlbum)
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(items, queue.indexOfFirst { it.uri == file.uri }.coerceAtLeast(0), 0L)
            player.prepare()
            player.play()
        } else {
            // ExoPlayer requests permanent music audio focus before output begins.
            player.play()
        }
    }

    LaunchedEffect(player, playing) {
        while (playing) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            delay(600)
        }
        position = player.currentPosition.coerceAtLeast(0L)
        duration = player.duration.takeIf { it > 0L } ?: 0L
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("You have no playlists yet. Create one to save this song.")
                } else {
                    Column(Modifier.heightIn(max = 360.dp)) {
                        playlists.forEach { playlist ->
                            val alreadyAdded = file.uri.toString() in playlist.songUris
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (!alreadyAdded) onAddToPlaylist(playlist.id, file)
                                        showPlaylistPicker = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.QueueMusic, null, tint = Color(0xFFFFA0B1))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(playlist.songUris.size.toString() + " songs", color = Color(0xFFD1C9E4), fontSize = 11.sp)
                                }
                                Icon(
                                    if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = if (alreadyAdded) Color(0xFF69C99B) else Color(0xFFD6B8FF)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPlaylistPicker = false
                    newPlaylistName = ""
                    showCreatePlaylist = true
                }) { Text("New playlist") }
            },
            containerColor = Color(0xFF242033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD1C9E4)
        )
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = vm.createPlaylist(newPlaylistName)
                    if (id != null) onAddToPlaylist(id, file)
                    showCreatePlaylist = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF242033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD1C9E4)
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename in On Player") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Song name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameTrack(file, renameText)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF242033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD1C9E4)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this song?") },
            text = { Text("The audio file will be deleted from your device. Android may ask you to confirm this action.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteTrack(file)
                }) { Text("Delete", color = Color(0xFFFF8796)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF242033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD1C9E4)
        )
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(dynamicDeep, Color(0xFF10131C), Color(0xFF050609))))) {

        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    description = "Close player",
                    onClick = onBack,
                    diameter = 46.dp
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ON PLAYER", color = dynamicAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                    Text("Now Playing", color = secondary, fontSize = 12.sp)
                }
                Box {
                    GlassIconButton(
                        icon = Icons.Default.MoreHoriz,
                        description = "More options",
                        onClick = { moreExpanded = true },
                        diameter = 46.dp
                    )
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = Color(0xFF242033)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = Color(0xFFD6B8FF)) },
                            onClick = { moreExpanded = false; showPlaylistPicker = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename song", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFFD6B8FF)) },
                            onClick = { moreExpanded = false; renameText = file.displayTitle; showRenameDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete from device", color = Color(0xFFFFA0AA)) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF8796)) },
                            onClick = { moreExpanded = false; showDeleteDialog = true }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.14f))

            Box(
                Modifier.fillMaxWidth().aspectRatio(0.96f).padding(horizontal = 5.dp)
                    .pointerInput(file.uri) {
                        var dragX = 0f
                        detectDragGestures(
                            onDragStart = { dragX = 0f },
                            onDrag = { change, delta -> dragX += delta.x; change.consume() },
                            onDragEnd = {
                                if (dragX < -65f) player.seekToNextMediaItem()
                                else if (dragX > 65f) player.seekToPreviousMediaItem()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxSize(0.88f)
                        .graphicsLayer { rotationZ = if (playing) discRotation else 0f }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(dynamicSoft, dynamicDeep, Color(0xFF08090D))))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        for (i in 1..14) {
                            val radius = size.minDimension * (0.28f + i * 0.025f)
                            drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.13f else 0.035f), radius, center, style = Stroke(width = size.minDimension * 0.003f))
                        }
                        drawCircle(dynamicAccent.copy(alpha = 0.32f), size.minDimension * 0.49f, center, style = Stroke(width = size.minDimension * 0.012f))
                    }
                    Box(
                        Modifier.fillMaxSize(0.42f).clip(CircleShape)
                            .border(2.dp, Color(0xFF9B9DA5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        artwork.bitmap?.let { bitmap ->
                            Image(bitmap.asImageBitmap(), contentDescription = "Album cover",
                                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } ?: Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF111217)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape))
                    }
                }
            }
            Spacer(Modifier.weight(0.13f))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.displayTitle,
                        color = foreground,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(artwork.artist, artwork.album).filter { it.isNotBlank() }.joinToString(" • "),
                        color = secondary.copy(alpha = 0.84f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                GlassIconButton(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    description = if (isFavorite) "Remove favorite" else "Add favorite",
                    onClick = { vm.toggleFavorite(file) },
                    diameter = 46.dp,
                    active = isFavorite
                )
            }

            Spacer(Modifier.height(19.dp))

            LiquidSeekBar(
                positionMs = position,
                durationMs = duration,
                accent = dynamicAccent,
                onSeek = { target -> position = target },
                onSeekFinished = { player.seekTo(it) }
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(timeText(position), color = secondary.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(timeText(duration), color = secondary.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(19.dp))

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(38.dp))
                    .background(Color.White.copy(alpha = 0.085f))
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.2f))
                        ),
                        RoundedCornerShape(38.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlassIconButton(
                    icon = Icons.Default.Shuffle,
                    description = "Shuffle",
                    onClick = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                    diameter = 42.dp,
                    active = shuffle
                )
                GlassIconButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "Previous track",
                    onClick = { player.seekToPreviousMediaItem() },
                    diameter = 50.dp
                )
                GlassPlayButton(
                    playing = playing,
                    accent = dynamicAccent,
                    onClick = { if (player.isPlaying) player.pause() else player.play() }
                )
                GlassIconButton(
                    icon = Icons.Default.SkipNext,
                    description = "Next track",
                    onClick = { player.seekToNextMediaItem() },
                    diameter = 50.dp
                )
                GlassIconButton(
                    icon = Icons.Default.RepeatOne,
                    description = "Repeat one",
                    onClick = {
                        repeatOne = !repeatOne
                        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    },
                    diameter = 42.dp,
                    active = repeatOne
                )
            }

            Spacer(Modifier.weight(0.12f))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
                Text(
                    "  PLAYING FROM YOUR LIBRARY",
                    color = secondary.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    diameter: Dp,
    active: Boolean = false
) {
    val shape = CircleShape
    Box(
        Modifier.size(diameter)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (active) 0.30f else 0.19f),
                        Color.White.copy(alpha = 0.055f),
                        Color(0xFF26243A).copy(alpha = 0.34f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.28f))
                ),
                shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) Color(0xFFFF6B83) else Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(if (diameter >= 50.dp) 28.dp else 21.dp)
        )
    }
}

@Composable
private fun GlassPlayButton(playing: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp)
            .shadow(20.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.95f), accent.copy(alpha = 0.80f), accent)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.18f))),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.58f), Color.Transparent, Color.Transparent))
            )
        )
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = accent.copy(red = (accent.red * 0.28f).coerceIn(0f,1f), green = (accent.green * 0.28f).coerceIn(0f,1f), blue = (accent.blue * 0.28f).coerceIn(0f,1f),
            modifier = Modifier.size(39.dp)
        )
    }
}

@Composable
private fun LiquidSeekBar(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragPosition by remember { mutableLongStateOf(positionMs.coerceIn(0L, safeDuration)) }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnSeekFinished by rememberUpdatedState(onSeekFinished)

    fun positionAt(x: Float): Long {
        val normalized = (x / widthPx).coerceIn(0f, 1f)
        return (normalized * safeDuration.toFloat()).toLong().coerceIn(0L, safeDuration)
    }

    Canvas(
        Modifier.fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(safeDuration, widthPx) {
                detectTapGestures { offset ->
                    val target = positionAt(offset.x)
                    dragPosition = target
                    latestOnSeek(target)
                    latestOnSeekFinished(target)
                }
            }
            .pointerInput(safeDuration, widthPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragPosition = positionAt(offset.x)
                        latestOnSeek(dragPosition)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = positionAt(change.position.x)
                        latestOnSeek(dragPosition)
                    },
                    onDragEnd = { latestOnSeekFinished(dragPosition) }
                )
            }
    ) {
        val trackHeight = 4.5.dp.toPx()
        val centerY = size.height / 2f
        val progressWidth = (size.width * fraction).coerceIn(0f, size.width)
        val radius = trackHeight / 2f

        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius)
        )
        if (progressWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.White, accent.copy(alpha = 0.85f), accent)
                ),
                topLeft = Offset(0f, centerY - radius),
                size = Size(progressWidth, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
        val thumb = Offset(progressWidth, centerY)
        drawCircle(Color.Black.copy(alpha = 0.28f), radius = 10.dp.toPx(), center = thumb.copy(y = centerY + 1.5.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = thumb, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Color.White, radius = 6.5.dp.toPx(), center = thumb)
    }
}

private fun timeText(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
