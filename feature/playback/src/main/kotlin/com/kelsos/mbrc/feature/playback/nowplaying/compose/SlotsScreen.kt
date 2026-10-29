package com.kelsos.mbrc.feature.playback.nowplaying.compose

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kelsos.mbrc.core.common.state.PlayerState
import com.kelsos.mbrc.feature.playback.player.PlayerViewModel
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.feature.playback.R
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.TrackSortField
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.ui.compose.DynamicScreenScaffold
import com.kelsos.mbrc.core.ui.compose.TopBarState
import com.kelsos.mbrc.core.networking.api.QueueApi
import com.kelsos.mbrc.core.networking.dto.QueuePayload
import com.kelsos.mbrc.core.networking.protocol.usecases.UserActionUseCase
import com.kelsos.mbrc.core.networking.protocol.usecases.stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Twenty independent, locally persisted soundboard slots. Assigning or clearing a slot never
 * mutates MusicBee's queue. Playing is sent as a play-by-position command when the assigned track
 * exists in the current MusicBee queue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlotsScreen(
  onOpenDrawer: () -> Unit,
  onNavigateToPlayer: () -> Unit,
  onNavigateToQueue: () -> Unit,
  onNavigateToLibrary: () -> Unit,
  snackbarHostState: SnackbarHostState,
  trackRepository: TrackRepository = koinInject(),
  queueApi: QueueApi = koinInject(),
  userActionUseCase: UserActionUseCase = koinInject()
) {
  val context = LocalContext.current
  val preferences = remember(context) {
    context.getSharedPreferences(SLOTS_PREFERENCES, Context.MODE_PRIVATE)
  }
  val slotTracks = remember { mutableStateListOf<Track?>().apply { repeat(SLOT_COUNT) { add(null) } } }
  var slotToAssign by remember { mutableStateOf<Int?>(null) }
  var searchText by remember { mutableStateOf("") }
  val playerViewModel: PlayerViewModel = koinViewModel()
  val currentTrack by playerViewModel.playingTrack.collectAsStateWithLifecycle()
  val playingPosition by playerViewModel.playingPosition.collectAsStateWithLifecycle()
  val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
  val slotDurations = remember { mutableStateListOf<Long>().apply { repeat(SLOT_COUNT) { add(0L) } } }
  val scope = rememberCoroutineScope()
  val title = stringResource(R.string.nav_slots)
  val topBar = remember(title) { TopBarState.WithTitle(title) }

  LaunchedEffect(preferences, trackRepository) {
    repeat(SLOT_COUNT) { index ->
      val path = preferences.getString(slotKey(index), null)
      slotTracks[index] = path?.let { withContext(Dispatchers.IO) { trackRepository.getByPath(it) } }
      slotDurations[index] = preferences.getLong(slotDurationKey(index), 0L)
    }
  }

  // Cache each assigned track duration when MusicBee reports it, so every populated slot can
  // show its full duration while idle and the real remaining time while that slot is playing.
  LaunchedEffect(currentTrack.path, playingPosition.total, slotTracks.toList()) {
    if (currentTrack.path.isNotBlank() && playingPosition.total > 0L) {
      val index = (0 until SLOT_COUNT).firstOrNull { slotTracks[it]?.src == currentTrack.path }
      if (index != null && slotDurations[index] != playingPosition.total) {
        slotDurations[index] = playingPosition.total
        preferences.edit().putLong(slotDurationKey(index), playingPosition.total).apply()
      }
    }
  }

  val searchFlow = remember(searchText) {
    if (searchText.isBlank()) trackRepository.getAll(TrackSortField.TITLE, SortOrder.ASC)
    else trackRepository.search(searchText, TrackSortField.TITLE, SortOrder.ASC)
  }
  val searchResults = searchFlow.collectAsLazyPagingItems()
  val baseViewConfiguration = LocalViewConfiguration.current
  val twoSecondLongPressConfiguration = remember(baseViewConfiguration) {
    object : ViewConfiguration by baseViewConfiguration {
      override val longPressTimeoutMillis: Long = SLOT_LONG_PRESS_MS
    }
  }

  DynamicScreenScaffold(
    topBarState = topBar,
    snackbarHostState = snackbarHostState,
    defaultTitle = title,
    onOpenDrawer = onOpenDrawer,
    actionItems = emptyList(),
    menuItems = emptyList()
  ) { padding ->
    androidx.compose.runtime.CompositionLocalProvider(LocalViewConfiguration provides twoSecondLongPressConfiguration) {
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items((0 until SLOT_VISIBLE_COUNT).toList(), key = { it }) { index ->
          val track = slotTracks[index]
          val isPlaying = track != null && track.src == currentTrack.path &&
            playbackState.playerState == PlayerState.Playing
          val remainingMillis = if (track != null && track.src == currentTrack.path && playingPosition.total > 0L) {
            (playingPosition.total - playingPosition.current).coerceAtLeast(0L)
          } else {
            slotDurations[index]
          }
          Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.0f).combinedClickable(
              onClick = {
                if (track == null) {
                  scope.launch { snackbarHostState.showSnackbar("Hold a slot for 2 seconds to assign a track") }
                } else {
                  scope.launch {
                    if (track.src == currentTrack.path && playbackState.playerState == PlayerState.Playing) {
                      // Tapping the currently playing slot again stops playback.
                      userActionUseCase.stop()
                    } else {
                      try {
                        // Queue this library file as NOW and start it immediately.
                        val response = withContext(Dispatchers.IO) {
                          queueApi.queue(
                            QueuePayload(
                              type = "now",
                              data = listOf(track.src),
                              play = track.src
                            )
                          )
                        }
                        if (response.code != 0) {
                          snackbarHostState.showSnackbar("MusicBee could not add this track to Queue")
                        }
                      } catch (error: Exception) {
                        snackbarHostState.showSnackbar("Could not play track: ${error.message ?: "connection error"}")
                      }
                    }
                  }
                }
              },
              onLongClick = {
                slotToAssign = index
                searchText = ""
              }
            ),
            shape = RoundedCornerShape(8.dp),
            color = when {
              track == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
              isPlaying -> Color(0xFFD0C3B8)
              else -> Color(0xFF454545)
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              Column(
                modifier = Modifier.fillMaxSize().padding(5.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                if (track != null) {
                  Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                    color = if (isPlaying) Color(0xFF292522) else Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (track.artist.isNotBlank()) {
                    Text(
                      text = track.artist,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                      color = if (isPlaying) Color(0xFF51473F) else Color(0xFFD0D0D0),
                      textAlign = TextAlign.Center,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
              if (track != null) {
                Box(
                  modifier = Modifier.align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = formatSlotTime(remainingMillis),
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 12.sp
                  )
                }
              }
            }
          }
        }
      }
      Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 314.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        SlotNavigationButton(label = "Q", onClick = onNavigateToQueue)
        Spacer(modifier = Modifier.height(12.dp))
        SlotNavigationButton(label = "L", onClick = onNavigateToLibrary)
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          modifier = Modifier.size(56.dp).border(2.dp, Color.LightGray.copy(alpha = 0.45f), CircleShape)
            .clip(CircleShape).clickable(onClick = onNavigateToPlayer),
          shape = CircleShape, color = Color.DarkGray.copy(alpha = 0.72f),
          tonalElevation = 0.dp, shadowElevation = 0.dp
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Now playing",
              tint = Color.LightGray, modifier = Modifier.size(30.dp))
          }
        }
      }
      }
    }
  }

  if (slotToAssign != null) {
    AlertDialog(
      onDismissRequest = { slotToAssign = null },
      title = { Text("Choose track for Slot ${(slotToAssign ?: 0) + 1}") },
      text = {
        Column {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
          ) {
            BasicTextField(
              value = searchText,
              onValueChange = { searchText = it },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              decorationBox = { inner ->
                if (searchText.isBlank()) {
                  Text("Search Library…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
              }
            )
          }
          LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(searchResults.itemCount) { resultIndex ->
              val track = searchResults[resultIndex]
              if (track != null) {
                Row(
                  modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                      val index = slotToAssign
                      if (index != null) {
                        slotTracks[index] = track
                        slotDurations[index] = 0L
                        preferences.edit()
                          .putString(slotKey(index), track.src)
                          .remove(slotDurationKey(index))
                          .apply()
                        slotToAssign = null
                      }
                    },
                    onLongClick = {}
                  ).padding(vertical = 10.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { slotToAssign = null }) { Text("Cancel") }
      },
      dismissButton = {
        TextButton(onClick = {
          val index = slotToAssign
          if (index != null) {
            slotTracks[index] = null
            slotDurations[index] = 0L
            preferences.edit().remove(slotKey(index)).remove(slotDurationKey(index)).apply()
          }
          slotToAssign = null
        }) { Text("Clear slot") }
      }
    )
  }
}

private fun slotKey(index: Int) = "slot_${index + 1}"
private fun slotDurationKey(index: Int) = "slot_duration_${index + 1}"
private fun formatSlotTime(milliseconds: Long): String {
  val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return "%02d:%02d".format(minutes, seconds)
}
private const val SLOTS_PREFERENCES = "independent_slots"
private const val SLOT_COUNT = 20
private const val SLOT_VISIBLE_COUNT = 15
private const val SLOT_LONG_PRESS_MS = 2000L


@Composable
private fun SlotNavigationButton(label: String, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.size(56.dp).border(2.dp, Color.LightGray.copy(alpha = 0.45f), CircleShape)
      .clip(CircleShape).clickable(onClick = onClick),
    shape = CircleShape,
    color = Color.DarkGray.copy(alpha = 0.72f),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(label, color = Color.LightGray, style = MaterialTheme.typography.titleLarge)
    }
  }
}
