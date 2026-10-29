package com.kelsos.mbrc.feature.playback.nowplaying.compose

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
  var activeSlot by remember { mutableStateOf<Int?>(null) }
  val scope = rememberCoroutineScope()
  val title = stringResource(R.string.nav_slots)
  val topBar = remember(title) { TopBarState.WithTitle(title) }

  LaunchedEffect(preferences, trackRepository) {
    repeat(SLOT_COUNT) { index ->
      val path = preferences.getString(slotKey(index), null)
      slotTracks[index] = path?.let { withContext(Dispatchers.IO) { trackRepository.getByPath(it) } }
    }
  }

  val searchFlow = remember(searchText) {
    if (searchText.isBlank()) trackRepository.getAll(TrackSortField.TITLE, SortOrder.ASC)
    else trackRepository.search(searchText, TrackSortField.TITLE, SortOrder.ASC)
  }
  val searchResults = searchFlow.collectAsLazyPagingItems()
  val baseViewConfiguration = LocalViewConfiguration.current
  val fourSecondLongPressConfiguration = remember(baseViewConfiguration) {
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
    androidx.compose.runtime.CompositionLocalProvider(LocalViewConfiguration provides fourSecondLongPressConfiguration) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items((0 until SLOT_COUNT).toList(), key = { it }) { index ->
          val track = slotTracks[index]
          Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.82f).combinedClickable(
              onClick = {
                if (track == null) {
                  scope.launch { snackbarHostState.showSnackbar("Hold a slot for 4 seconds to assign a track") }
                } else {
                  scope.launch {
                    if (activeSlot == index) {
                      // Stop playback; MusicBee's Stop command returns the track to its start.
                      userActionUseCase.stop()
                      activeSlot = null
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
                        if (response.code == 0) {
                          activeSlot = index
                        } else {
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
            color = if (track == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
              else MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
          ) {
            Column(
              modifier = Modifier.fillMaxSize().padding(5.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              if (track != null) {
                Text(track.title, style = MaterialTheme.typography.labelMedium,
                  textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (track.artist.isNotBlank()) {
                  Text(track.artist, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
              }
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
                        preferences.edit().putString(slotKey(index), track.src).apply()
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
            preferences.edit().remove(slotKey(index)).apply()
          }
          slotToAssign = null
        }) { Text("Clear slot") }
      }
    )
  }
}

private fun slotKey(index: Int) = "slot_${index + 1}"
private const val SLOTS_PREFERENCES = "independent_slots"
private const val SLOT_COUNT = 20
private const val SLOT_LONG_PRESS_MS = 4000L
