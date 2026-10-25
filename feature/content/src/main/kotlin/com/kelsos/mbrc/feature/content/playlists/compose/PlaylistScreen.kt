package com.kelsos.mbrc.feature.content.playlists.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.data.playlist.PlaylistBrowserItem
import com.kelsos.mbrc.core.ui.R as CoreUiR
import com.kelsos.mbrc.core.ui.compose.NavigationIconType
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.core.ui.compose.SingleLineRow
import com.kelsos.mbrc.core.ui.compose.SwipeRefreshScreen
import com.kelsos.mbrc.feature.content.R
import com.kelsos.mbrc.feature.content.playlists.PlaylistUiMessages
import com.kelsos.mbrc.feature.content.playlists.PlaylistViewModel
import com.kelsos.mbrc.feature.content.playlists.getPathDisplayName
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import org.koin.androidx.compose.koinViewModel

@Composable
fun PlaylistScreen(
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  onOpenDrawer: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PlaylistViewModel = koinViewModel()
) {
  val items = viewModel.items.collectAsLazyPagingItems()
  val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
  val detail by viewModel.detail.collectAsStateWithLifecycle()
  var isRefreshing by remember { mutableStateOf(false) }

  val refreshFailedMessage = stringResource(R.string.playlists_load_failed)
  val refreshSuccessMessage = stringResource(R.string.playlists_load_success)
  val networkUnavailableMessage =
    stringResource(CoreUiR.string.connection_error_network_unavailable)
  val playFailedMessage = stringResource(R.string.radio__play_failed)
  val baseTitle = stringResource(R.string.nav_playlists)

  // Dynamic title based on current path
  val title = detail?.name ?: if (currentPath.isEmpty()) {
    baseTitle
  } else {
    getPathDisplayName(currentPath)
  }

  // Navigation icon: back arrow when in folder, drawer when at root
  val navigationIcon = when {
    detail != null -> NavigationIconType.Back { viewModel.closePlaylist() }
    currentPath.isEmpty() -> NavigationIconType.Drawer
    else -> NavigationIconType.Back { viewModel.actions.navigateUp() }
  }

  // Handle system back navigation
  BackHandler(enabled = detail != null || currentPath.isNotEmpty()) {
    if (detail != null) viewModel.closePlaylist() else viewModel.actions.navigateUp()
  }

  LaunchedEffect(Unit) {
    // Trigger initial load without user message
    viewModel.actions.reload(showUserMessage = false)
  }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      val message = when (event) {
        is PlaylistUiMessages.RefreshFailed -> {
          isRefreshing = false
          refreshFailedMessage
        }

        is PlaylistUiMessages.RefreshSuccess -> {
          isRefreshing = false
          refreshSuccessMessage
        }

        is PlaylistUiMessages.NetworkUnavailable -> {
          isRefreshing = false
          networkUnavailableMessage
        }

        is PlaylistUiMessages.PlayFailed -> playFailedMessage

        PlaylistUiMessages.SelectedSongStarted -> {
          onNavigateToPlayer()
          null
        }


      }

      if (message != null) {
        snackbarHostState.showSnackbar(
          message = message,
          duration = SnackbarDuration.Short
        )
      }
    }
  }

  ScreenScaffold(
    title = title,
    snackbarHostState = snackbarHostState,
    navigationIcon = navigationIcon,
    onOpenDrawer = onOpenDrawer,
    modifier = modifier
  ) { paddingValues ->
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      val selected = detail
      if (selected != null) {
        PlaylistDetailContent(
          state = selected,
          onPlay = viewModel::playSong,
          onRetry = { viewModel.openPlaylist(selected.name, selected.url) },
          modifier = Modifier.weight(1f)
        )
      } else {
      SwipeRefreshScreen(
        items = items,
        listId = "playlists",
        isRefreshing = isRefreshing,
        onRefresh = {
          isRefreshing = true
          viewModel.actions.reload()
        },
        modifier = Modifier.weight(1f),
        emptyMessage = stringResource(R.string.playlists_list_empty),
        emptyIcon = Icons.AutoMirrored.Filled.QueueMusic,
        key = { item ->
          if (item.isFolder) "folder:${item.path}" else "playlist:${item.id}"
        }
      ) { item ->
        BrowserItemRow(
          item = item,
          onFolderClick = { viewModel.actions.navigateToFolder(it) },
          // Preserve the original behavior: selecting a playlist starts it
          // in MusicBee and opens the Queue/player screen.
          onPlaylistClick = { url ->
            viewModel.actions.play(url)
            onNavigateToPlayer()
          },
          onPlaylistPlay = { viewModel.actions.play(it) }
        )
      }

      }
      MiniControl(
        onNavigateToPlayer = onNavigateToPlayer,
        snackbarHostState = snackbarHostState
      )
    }
  }
}


/**
 * Displays the selected playlist without touching MusicBee playback or Queue.
 * Tracks are read separately through the V6 playlist_tracks operation.
 */
@Composable
private fun PlaylistDetailContent(
  state: com.kelsos.mbrc.feature.content.playlists.PlaylistDetailState,
  onPlay: (com.kelsos.mbrc.feature.content.playlists.PlaylistSong) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    when {
      state.loading -> CircularProgressIndicator()
      state.unsupported -> Text(
        text = stringResource(R.string.playlists_v6_required),
        modifier = Modifier.padding(24.dp)
      )
      state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.playlists_tracks_failed))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.playlists_try_again)) }
      }
      state.tracks.isEmpty() -> Text(text = stringResource(R.string.playlists_no_tracks))
      else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.tracks, key = { index, _ -> index }) { _, song ->
          ListItem(
            headlineContent = {
              Text(text = song.title, maxLines = Int.MAX_VALUE, softWrap = true)
            },
            modifier = Modifier.clickable(enabled = !state.playingSelection) { onPlay(song) }
          )
        }
      }
    }
  }
}

@Composable
fun BrowserItemRow(
  item: PlaylistBrowserItem,
  onFolderClick: (path: String) -> Unit,
  onPlaylistClick: (url: String) -> Unit,
  onPlaylistPlay: (url: String) -> Unit,
  modifier: Modifier = Modifier
) {
  if (item.isFolder) {
    SingleLineRow(
      text = item.name,
      onClick = { onFolderClick(item.path) },
      modifier = modifier,
      leadingContent = {
        Icon(
          imageVector = Icons.Default.Folder,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.secondary
        )
      }
    )
  } else {
    SingleLineRow(
      text = item.name,
      onClick = { onPlaylistClick(item.path) },
      modifier = modifier,
      leadingContent = {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.QueueMusic,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.primary
        )
      },
      trailingContent = {
        IconButton(onClick = { onPlaylistPlay(item.path) }) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = stringResource(CoreUiR.string.action_play),
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }
    )
  }
}
