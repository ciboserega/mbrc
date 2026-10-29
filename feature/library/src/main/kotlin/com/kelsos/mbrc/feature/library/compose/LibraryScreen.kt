package com.kelsos.mbrc.feature.library.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kelsos.mbrc.core.common.utilities.AppError
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.album.Album
import com.kelsos.mbrc.core.data.library.artist.Artist
import com.kelsos.mbrc.core.data.library.genre.Genre
import com.kelsos.mbrc.core.ui.compose.ActionItem
import com.kelsos.mbrc.core.ui.compose.DynamicScreenScaffold
import com.kelsos.mbrc.core.ui.compose.MenuItem
import com.kelsos.mbrc.core.ui.compose.TopBarState
import com.kelsos.mbrc.feature.library.LibraryViewModel
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.albums.BrowseAlbumViewModel
import com.kelsos.mbrc.feature.library.compose.tabs.TracksTab
import com.kelsos.mbrc.feature.library.data.LibraryStats
import com.kelsos.mbrc.feature.library.domain.LibrarySyncProgress
import com.kelsos.mbrc.feature.library.ui.LibraryUiEvent
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import org.koin.androidx.compose.koinViewModel

/** A focused title-search library: tracks are the only visible category. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun LibraryScreen(
  onOpenDrawer: () -> Unit,
  onNavigateToGenreArtists: (Genre) -> Unit,
  onNavigateToGenreAlbums: (Genre) -> Unit,
  onNavigateToArtistAlbums: (Artist) -> Unit,
  onNavigateToAlbumTracks: (Album) -> Unit,
  onNavigateToPlayer: () -> Unit,
  onNavigateToQueue: () -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: LibraryViewModel = koinViewModel(),
  albumViewModel: BrowseAlbumViewModel = koinViewModel(),
  initialSearchActive: Boolean = false
) {
  var isSearchActive by rememberSaveable { mutableStateOf(initialSearchActive) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var statsToShow by remember { mutableStateOf<LibraryStats?>(null) }
  var showSortSheet by rememberSaveable { mutableStateOf(false) }

  val syncProgress by viewModel.progress.collectAsStateWithLifecycle(
    initialValue = LibrarySyncProgress.Idle
  )

  val searchPlaceholder = stringResource(R.string.library_search_hint)
  val syncStateLabel = stringResource(R.string.library_menu__sync_state)
  val syncProgressTemplate = stringResource(R.string.library__sync_progress)
  val libraryTitle = stringResource(R.string.common_library)
  val categoryName = if (syncProgress.running && syncProgress.category.titleRes != 0) {
    stringResource(syncProgress.category.titleRes)
  } else {
    ""
  }

  val topBarState = when {
    isSearchActive -> TopBarState.Search(
      query = searchQuery,
      placeholder = searchPlaceholder,
      onQueryChange = { query ->
        searchQuery = query
        viewModel.search(query)
      },
      onSearch = {
        // Library tracks filter as text is typed; no submit required.
      },
      onClose = {
        isSearchActive = false
        searchQuery = ""
        viewModel.search("")
      },
      autoFocus = true
    )

    syncProgress.running -> {
      val progress = if (syncProgress.total > 0) {
        syncProgress.current.toFloat() / syncProgress.total.toFloat()
      } else {
        -1f
      }
      TopBarState.WithProgress(
        title = libraryTitle,
        progress = progress,
        progressText = syncProgressTemplate.format(
          syncProgress.current,
          syncProgress.total,
          categoryName
        )
      )
    }

    else -> TopBarState.WithTitle(libraryTitle)
  }

  val playAllLabel = stringResource(R.string.menu_play_all)
  val shuffleAllLabel = stringResource(R.string.menu_shuffle_all)
  val menuItems = remember(isSearchActive, syncProgress.running) {
    if (isSearchActive || syncProgress.running) {
      emptyList()
    } else {
      listOf(
        MenuItem(
          label = playAllLabel,
          onClick = { viewModel.playAll(shuffle = false) }
        ),
        MenuItem(
          label = shuffleAllLabel,
          onClick = { viewModel.playAll(shuffle = true) }
        ),
        MenuItem(
          label = syncStateLabel,
          onClick = { viewModel.displayLibraryStats() }
        )
      )
    }
  }

  val searchDescription = stringResource(R.string.library_search_hint)
  val sortDescription = stringResource(R.string.sort_button_description)
  val syncDescription = stringResource(R.string.library_action__sync)
  val actionItems = remember(isSearchActive, syncProgress.running) {
    if (!isSearchActive && !syncProgress.running) {
      listOf(
        ActionItem(
          icon = Icons.Default.Sync,
          contentDescription = syncDescription,
          onClick = { viewModel.sync() }
        ),
        ActionItem(
          icon = Icons.AutoMirrored.Filled.Sort,
          contentDescription = sortDescription,
          onClick = { showSortSheet = true }
        ),
        ActionItem(
          icon = Icons.Default.Search,
          contentDescription = searchDescription,
          onClick = { isSearchActive = true },
          iconSize = 48.dp
        )
      )
    } else {
      emptyList()
    }
  }

  LibraryEventsEffect(
    viewModel = viewModel,
    snackbarHostState = snackbarHostState,
    onStatsReady = { statsToShow = it }
  )

  statsToShow?.let { stats ->
    LibraryStatsDialog(stats = stats, onDismiss = { statsToShow = null })
  }

  DynamicScreenScaffold(
    topBarState = topBarState,
    snackbarHostState = snackbarHostState,
    defaultTitle = libraryTitle,
    onOpenDrawer = onOpenDrawer,
    actionItems = actionItems,
    menuItems = menuItems,
    modifier = modifier
  ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
    LibraryContent(
      snackbarHostState = snackbarHostState,
      isSyncing = syncProgress.running,
      showSortSheet = showSortSheet,
      onDismissSortSheet = { showSortSheet = false },
      onNavigateToPlayer = onNavigateToPlayer,
      onSync = { viewModel.sync() },
      modifier = Modifier.fillMaxSize()
    )
    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 320.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      LibraryShortcutButton(label = "Q", onClick = onNavigateToQueue)
      Spacer(modifier = Modifier.height(12.dp))
      LibraryShortcutButton(iconPlay = true, onClick = onNavigateToPlayer)
    }
    }
  }
}

@Composable
private fun LibraryShortcutButton(
  label: String? = null,
  iconPlay: Boolean = false,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .size(56.dp)
      .clickable(onClick = onClick),
    shape = CircleShape,
    color = Color.DarkGray.copy(alpha = 0.72f),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = androidx.compose.foundation.BorderStroke(2.dp, Color.LightGray.copy(alpha = 0.45f))
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (iconPlay) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "Now Playing",
          tint = Color.LightGray,
          modifier = Modifier.size(34.dp)
        )
      } else {
        Text(
          text = label.orEmpty(),
          color = Color.LightGray,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun LibraryEventsEffect(
  viewModel: LibraryViewModel,
  snackbarHostState: SnackbarHostState,
  onStatsReady: (LibraryStats) -> Unit
) {
  val networkUnavailableMessage = stringResource(R.string.connection_error_network_unavailable)
  val playAllSuccessMessage = stringResource(R.string.library__play_all_success)
  val playAllFailedMessage = stringResource(R.string.library__play_all_failed)

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is LibraryUiEvent.LibraryStatsReady -> onStatsReady(event.stats)

        is LibraryUiEvent.UpdateAlbumArtistOnly -> Unit

        is LibraryUiEvent.NetworkUnavailable -> snackbarHostState.showSnackbar(
          message = networkUnavailableMessage,
          duration = SnackbarDuration.Short
        )

        is LibraryUiEvent.PlayAllSuccess -> snackbarHostState.showSnackbar(
          message = playAllSuccessMessage,
          duration = SnackbarDuration.Short
        )

        is LibraryUiEvent.PlayAllFailed -> snackbarHostState.showSnackbar(
          message = playAllFailedMessage,
          duration = SnackbarDuration.Short
        )
      }
    }
  }

  SyncResultEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)
}

@Composable
private fun SyncResultEffect(viewModel: LibraryViewModel, snackbarHostState: SnackbarHostState) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val syncFailedMessage = stringResource(R.string.library__sync_failed)

  LaunchedEffect(Unit) {
    viewModel.syncResults.collect { result ->
      val message = when (result) {
        is Outcome.Success -> buildSyncCompleteMessage(context, result.data)

        is Outcome.Failure -> when (result.error) {
          is AppError.NoOp -> null
          else -> syncFailedMessage
        }
      }
      if (message != null) {
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
      }
    }
  }
}

private fun buildSyncCompleteMessage(
  context: android.content.Context,
  stats: LibraryStats
): String {
  val resources = context.resources
  return context.getString(
    R.string.library__sync_complete,
    resources.getQuantityString(R.plurals.genre, stats.genres.toInt(), stats.genres.toInt()),
    resources.getQuantityString(R.plurals.artist, stats.artists.toInt(), stats.artists.toInt()),
    resources.getQuantityString(R.plurals.album, stats.albums.toInt(), stats.albums.toInt()),
    resources.getQuantityString(R.plurals.track, stats.tracks.toInt(), stats.tracks.toInt()),
    resources.getQuantityString(
      R.plurals.playlist,
      stats.playlists.toInt(),
      stats.playlists.toInt()
    )
  )
}

/**
 * A single tracks list replaces the category pager and tab strip.
 * Keep the mini-player and the list's normal sync/sort behavior.
 */
@Composable
private fun LibraryContent(
  snackbarHostState: SnackbarHostState,
  isSyncing: Boolean,
  showSortSheet: Boolean,
  onDismissSortSheet: () -> Unit,
  onNavigateToPlayer: () -> Unit,
  onSync: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxSize()) {
    TracksTab(
      snackbarHostState = snackbarHostState,
      onNavigateToPlayer = onNavigateToPlayer,
      isSyncing = isSyncing,
      showSortSheet = showSortSheet,
      onDismissSortSheet = onDismissSortSheet,
      onSync = onSync,
      modifier = Modifier.weight(1f)
    )

    MiniControl(
      onNavigateToPlayer = onNavigateToPlayer,
      snackbarHostState = snackbarHostState,
      showCover = false
    )
  }
}

@Composable
private fun LibraryStatsDialog(stats: LibraryStats, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.library_stats__title)) },
    text = {
      Column {
        Text(
          text = stringResource(R.string.library_stats__description),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(bottom = 16.dp)
        )
        StatsRow(label = stringResource(R.string.media__genres), value = stats.genres)
        StatsRow(label = stringResource(R.string.media__artists), value = stats.artists)
        StatsRow(label = stringResource(R.string.media__albums), value = stats.albums)
        StatsRow(label = stringResource(R.string.media__tracks), value = stats.tracks)
        StatsRow(label = stringResource(R.string.media__playlists), value = stats.playlists)
        StatsRow(label = stringResource(R.string.media__covers), value = stats.covers)
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(android.R.string.ok))
      }
    }
  )
}

@Composable
private fun StatsRow(label: String, value: Long) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = value.toString(),
      style = MaterialTheme.typography.bodyMedium
    )
  }
}
