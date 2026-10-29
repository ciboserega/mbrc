package com.kelsos.mbrc.feature.playback.nowplaying.compose

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.TrackSortField
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.ui.compose.DynamicScreenScaffold
import com.kelsos.mbrc.core.ui.compose.TopBarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Slots is a locally persisted list. It intentionally never calls queue mutation actions:
 * adding, removing, and reordering here cannot modify MusicBee's live Queue.
 */
@Composable
fun SlotsScreen(
  onOpenDrawer: () -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  trackRepository: TrackRepository = koinInject()
) {
  val context = LocalContext.current
  val preferences = remember(context) {
    context.getSharedPreferences(SLOTS_PREFERENCES, Context.MODE_PRIVATE)
  }
  val slotPaths = remember { mutableStateListOf<String>() }
  val slotTracks = remember { mutableStateListOf<Track>() }
  var showLibrary by remember { mutableStateOf(false) }
  val library = trackRepository.getAll(TrackSortField.TITLE, SortOrder.ASC)
    .collectAsLazyPagingItems()

  LaunchedEffect(preferences, trackRepository) {
    val saved = preferences.getString(SLOTS_PATHS, "").orEmpty()
      .split(SLOTS_SEPARATOR).filter { it.isNotBlank() }
    slotPaths.clear()
    slotPaths.addAll(saved)
    slotTracks.clear()
    saved.forEach { path ->
      val track = withContext(Dispatchers.IO) { trackRepository.getByPath(path) }
      if (track != null) slotTracks.add(track)
    }
    // Drop paths that no longer exist in the library.
    if (slotTracks.size != saved.size) {
      slotPaths.clear()
      slotPaths.addAll(slotTracks.map { it.src })
      persistSlots(preferences, slotPaths)
    }
  }

  val title = stringResource(com.kelsos.mbrc.R.string.nav_slots)
  val addTrackLabel = stringResource(com.kelsos.mbrc.R.string.slots_add_tracks)
  val emptyLabel = stringResource(com.kelsos.mbrc.R.string.slots_empty)
  val topBar = TopBarState.WithTitle(title)

  DynamicScreenScaffold(
    topBarState = topBar,
    snackbarHostState = snackbarHostState,
    defaultTitle = title,
    onOpenDrawer = onOpenDrawer,
    actionItems = emptyList(),
    menuItems = emptyList()
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { showLibrary = !showLibrary }
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.LibraryMusic, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(addTrackLabel, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Add, contentDescription = addTrackLabel)
      }

      if (showLibrary) {
        LazyColumn(modifier = Modifier.weight(1f)) {
          items(count = library.itemCount) { index ->
            val track = library[index]
            if (track != null) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(Modifier.weight(1f)) {
                  Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                  Text(track.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = {
                  if (track.src !in slotPaths) {
                    slotPaths.add(track.src)
                    slotTracks.add(track)
                    persistSlots(preferences, slotPaths)
                  }
                }, enabled = track.src !in slotPaths) {
                  Icon(Icons.Default.Add, contentDescription = addTrackLabel)
                }
              }
            }
          }
        }
      } else if (slotTracks.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(emptyLabel, style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
          items(slotTracks.size, key = { slotTracks[it].src }) { index ->
            val track = slotTracks[index]
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
              }
              IconButton(onClick = {
                if (index > 0) {
                  val item = slotTracks.removeAt(index)
                  slotTracks.add(index - 1, item)
                  val path = slotPaths.removeAt(index)
                  slotPaths.add(index - 1, path)
                  persistSlots(preferences, slotPaths)
                }
              }, enabled = index > 0) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
              }
              IconButton(onClick = {
                if (index < slotTracks.lastIndex) {
                  val item = slotTracks.removeAt(index)
                  slotTracks.add(index + 1, item)
                  val path = slotPaths.removeAt(index)
                  slotPaths.add(index + 1, path)
                  persistSlots(preferences, slotPaths)
                }
              }, enabled = index < slotTracks.lastIndex) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
              }
              IconButton(onClick = {
                slotTracks.removeAt(index)
                slotPaths.removeAt(index)
                persistSlots(preferences, slotPaths)
              }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(com.kelsos.mbrc.R.string.menu_remove_track))
              }
            }
          }
        }
      }
    }
  }
}

private fun persistSlots(
  preferences: android.content.SharedPreferences,
  paths: List<String>
) {
  preferences.edit().putString(SLOTS_PATHS, paths.joinToString(SLOTS_SEPARATOR)).apply()
}

private const val SLOTS_PREFERENCES = "independent_slots"
private const val SLOTS_PATHS = "track_paths"
private const val SLOTS_SEPARATOR = "\n"
