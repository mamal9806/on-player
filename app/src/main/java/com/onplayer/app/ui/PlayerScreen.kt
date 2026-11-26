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
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
    val accent = Color(0xFFD6B8FF)

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
    val pulse = rememberInfiniteTransition(label = "album glass pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 1.018f,
            animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
            label = "album scale"
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
            delay(350)
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
                                Icon(Icons.Default.QueueMusic, null, tint = Color(0xFFD6B8FF))
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

    Box(Modifier.fillMaxSize().background(Color(0xFF080912))) {
        artwork.bitmap?.let { bitmap ->
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.blur(82.dp),
                contentScale = ContentScale.Crop
            )
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.28f; scaleY = 1.28f }.blur(42.dp),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF48306F), Color(0xFF242846), Color(0xFF090A12)))
            )
        )

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xCC080810),
                        Color(0x4A171426),
                        Color(0xAA171321),
                        Color(0xF5080912)
                    )
                )
            )
        )

        // Soft, translucent highlights create a layered Liquid Glass atmosphere.
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 85.dp, end = 8.dp)
                .size(170.dp).blur(70.dp)
                .background(Color(0x665B4B9B), CircleShape)
        )
        Box(
            Modifier.align(Alignment.BottomStart).padding(bottom = 150.dp, start = 0.dp)
                .size(190.dp).blur(82.dp)
                .background(Color(0x554D7BB7), CircleShape)
        )

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
                    Text("ON PLAYER", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
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
                Modifier.fillMaxWidth().aspectRatio(0.96f).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                // Diffuse halo behind the album cover.
                Box(
                    Modifier.fillMaxSize(0.82f).blur(34.dp)
                        .background(Color(0x667E6CC0), RoundedCornerShape(42.dp))
                )
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .shadow(28.dp, RoundedCornerShape(34.dp), clip = false)
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8C75C8), Color(0xFF29385E), Color(0xFF17192A))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.28f))
                            ),
                            RoundedCornerShape(34.dp)
                        )
                ) {
                    artwork.bitmap?.let { bitmap ->
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = "Album artwork",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(104.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth().height(76.dp).align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)
                                )
                            )
                    )
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
            tint = if (active) Color(0xFFE0C9FF) else Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(if (diameter >= 50.dp) 28.dp else 21.dp)
        )
    }
}

@Composable
private fun GlassPlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp)
            .shadow(20.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFDFBFF), Color(0xFFDCCBFA), Color(0xFFA993D7))
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
            tint = Color(0xFF211A31),
            modifier = Modifier.size(39.dp)
        )
    }
}

@Composable
private fun LiquidSeekBar(
    positionMs: Long,
    durationMs: Long,
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
                    listOf(Color(0xFFF8F2FF), Color(0xFFD9B9FF), Color(0xFFBDA0F2))
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
