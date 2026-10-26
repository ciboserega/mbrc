package com.kelsos.mbrc.feature.content.playlists

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.kelsos.mbrc.core.common.mvvm.BaseViewModel
import com.kelsos.mbrc.core.common.mvvm.UiMessageBase
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.playlist.PlaylistRepository
import com.kelsos.mbrc.core.networking.api.QueueApi
import com.kelsos.mbrc.core.networking.dto.QueuePayload
import com.kelsos.mbrc.core.networking.protocol.base.Protocol
import com.kelsos.mbrc.core.queue.Queue
import com.kelsos.mbrc.core.networking.protocol.usecases.UserActionUseCase
import com.kelsos.mbrc.core.networking.protocol.usecases.performUserAction
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class PlaylistUiMessages : UiMessageBase {
  data object RefreshFailed : PlaylistUiMessages()

  data object RefreshSuccess : PlaylistUiMessages()

  data object NetworkUnavailable : PlaylistUiMessages()

  data object PlayFailed : PlaylistUiMessages()

  data object SelectedSongStarted : PlaylistUiMessages()
}

interface IPlaylistActions {
  fun play(url: String)
  fun reload()
  fun reload(showUserMessage: Boolean)
  fun navigateToFolder(path: String)
  fun navigateUp(): Boolean
}

class PlaylistActions(
  private val scope: CoroutineScope,
  private val dispatchers: AppCoroutineDispatchers,
  private val repository: PlaylistRepository,
  private val userActionUseCase: UserActionUseCase,
  private val connectionStateFlow: ConnectionStateFlow,
  private val emit: suspend (uiMessage: PlaylistUiMessages) -> Unit,
  private val getCurrentPath: () -> String,
  private val setCurrentPath: (String) -> Unit
) : IPlaylistActions {
  override fun play(url: String) {
    scope.launch(dispatchers.network) {
      val result =
        if (!connectionStateFlow.isConnected) {
          PlaylistUiMessages.NetworkUnavailable
        } else {
          try {
            // Start the full playlist, then immediately stop playback as requested.
            // Keep both remote commands in this coroutine so they are sent in order.
            userActionUseCase.performUserAction(Protocol.PlaylistPlay, url)
            userActionUseCase.stop()
            return@launch // Don't emit anything on success for play action
          } catch (e: IOException) {
            Timber.e(e)
            PlaylistUiMessages.PlayFailed
          }
        }
      emit(result)
    }
  }

  override fun reload() {
    reload(showUserMessage = true)
  }

  override fun reload(showUserMessage: Boolean) {
    scope.launch(dispatchers.network) {
      if (!connectionStateFlow.isConnected) {
        if (showUserMessage) {
          emit(PlaylistUiMessages.NetworkUnavailable)
        }
        return@launch
      }

      val result =
        try {
          repository.getRemote()
          if (showUserMessage) PlaylistUiMessages.RefreshSuccess else null
        } catch (e: IOException) {
          Timber.e(e)
          if (showUserMessage) PlaylistUiMessages.RefreshFailed else null
        }

      result?.let { emit(it) }
    }
  }

  override fun navigateToFolder(path: String) {
    setCurrentPath(path)
  }

  override fun navigateUp(): Boolean {
    val parentPath = getParentPath(getCurrentPath())
    return if (parentPath != null) {
      setCurrentPath(parentPath)
      true
    } else {
      false
    }
  }
}

data class PlaylistDetailState(
  val name: String,
  val url: String,
  val loading: Boolean = true,
  val tracks: List<PlaylistSong> = emptyList(),
  val unsupported: Boolean = false,
  val failed: Boolean = false,
  val playingSelection: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(
  repository: PlaylistRepository,
  private val dispatchers: AppCoroutineDispatchers,
  userActionsUseCase: UserActionUseCase,
  private val connectionStateFlow: ConnectionStateFlow,
  private val trackReader: PlaylistTrackReader,
  private val queueApi: QueueApi
) : BaseViewModel<PlaylistUiMessages>() {

  private val _detail = MutableStateFlow<PlaylistDetailState?>(null)
  val detail: StateFlow<PlaylistDetailState?> = _detail.asStateFlow()
  private var browseJob: kotlinx.coroutines.Job? = null

  fun openPlaylist(name: String, url: String) {
    browseJob?.cancel()
    _detail.value = PlaylistDetailState(name = name, url = url)
    browseJob = viewModelScope.launch(dispatchers.network) {
      try {
        if (!connectionStateFlow.isConnected) throw IOException("Server disconnected")
        val result = trackReader.read(url)
        _detail.value = _detail.value?.takeIf { it.url == url }
          ?.copy(loading = false, tracks = result)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: PlaylistBrowseUnsupportedException) {
        _detail.value = _detail.value?.takeIf { it.url == url }
          ?.copy(loading = false, unsupported = true)
      } catch (e: IOException) {
        Timber.e(e)
        _detail.value = _detail.value?.takeIf { it.url == url }
          ?.copy(loading = false, failed = true)
      }
    }
  }

  fun closePlaylist() {
    browseJob?.cancel()
    _detail.value = null
  }

  /** No queue mutation until the user explicitly selects a song. */
  fun playSong(song: PlaylistSong) {
    val detail = _detail.value ?: return
    if (detail.loading || detail.playingSelection || song !in detail.tracks) return
    _detail.value = detail.copy(playingSelection = true)
    viewModelScope.launch(dispatchers.network) {
      try {
        if (!connectionStateFlow.isConnected) {
          emit(PlaylistUiMessages.NetworkUnavailable)
          return@launch
        }
        // AddAndPlay replaces the queue with this playlist and starts from
        // the selected song, not from its first entry.
        val response = queueApi.queue(
          QueuePayload(Queue.AddAll.action, detail.tracks.map { it.path }, song.path)
        )
        emit(if (response.code == 200) {
          PlaylistUiMessages.SelectedSongStarted
        } else {
          PlaylistUiMessages.PlayFailed
        })
      } catch (e: IOException) {
        Timber.e(e)
        emit(PlaylistUiMessages.PlayFailed)
      } finally {
        _detail.value = _detail.value?.takeIf { it.url == detail.url }
          ?.copy(playingSelection = false)
      }
    }
  }

  private val _currentPath = MutableStateFlow("")
  val currentPath: StateFlow<String> = _currentPath.asStateFlow()

  /** Paginated browser items that react to path changes */
  val items = _currentPath
    .flatMapLatest { path -> repository.getBrowserItemsAtPath(path) }
    .cachedIn(viewModelScope)

  val actions: PlaylistActions =
    PlaylistActions(
      scope = viewModelScope,
      dispatchers = dispatchers,
      repository = repository,
      userActionUseCase = userActionsUseCase,
      connectionStateFlow = connectionStateFlow,
      emit = this::emit,
      getCurrentPath = { _currentPath.value },
      setCurrentPath = { _currentPath.value = it }
    )
}
