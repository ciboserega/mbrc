package com.kelsos.mbrc.feature.library.tracks

import com.kelsos.mbrc.core.common.mvvm.UiMessageBase

sealed class TrackUiMessage : UiMessageBase {
  // Only a successful direct tap on a track should open the player.
  // Context-menu queue actions must not navigate.
  data class QueueSuccess(
    val tracksCount: Int,
    val openPlayer: Boolean = false
  ) : TrackUiMessage()

  object QueueFailed : TrackUiMessage()

  object NetworkUnavailable : TrackUiMessage()
}
