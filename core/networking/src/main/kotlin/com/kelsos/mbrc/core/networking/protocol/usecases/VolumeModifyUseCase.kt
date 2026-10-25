package com.kelsos.mbrc.core.networking.protocol.usecases

import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.networking.client.MessageQueue
import com.kelsos.mbrc.core.networking.client.SocketMessage
import com.kelsos.mbrc.core.networking.protocol.base.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Handles the step based volume changes.
 */
interface VolumeModifyUseCase {
  /**
   * Increases the volume by a step up to a maximum of 100
   */
  suspend fun increase()

  /**
   * Decreases the volume by a step down to a minimum of 0
   */
  suspend fun decrease()

  /**
   * Reduces volume to 20% of the original volume
   */
  suspend fun reduceVolume()

  /**
   * Fire-and-forget [increase], for callers on the main thread.
   *
   * Sending is a suspending queue write that parks until a collector drains it, so a main-thread
   * caller must never wait for it. See [UserActionUseCase.tryPerform].
   */
  fun tryIncrease()

  /**
   * Fire-and-forget [decrease], for callers on the main thread. See [tryIncrease].
   */
  fun tryDecrease()
}

class VolumeModifyUseCaseImpl(
  private val appState: AppStateFlow,
  private val queue: MessageQueue,
  dispatchers: AppCoroutineDispatchers
) : VolumeModifyUseCase {
  private val job = SupervisorJob()
  private val scope = CoroutineScope(job + dispatchers.network)

  override fun tryIncrease() {
    scope.launch { increase() }
  }

  override fun tryDecrease() {
    scope.launch { decrease() }
  }

  private suspend fun currentVolume() = appState.playerStatus.firstOrNull()?.volume ?: 0

  private suspend fun isMute() = appState.playerStatus.firstOrNull()?.mute == true

  override suspend fun increase() {
    val current = currentVolume().coerceIn(MIN_VOLUME, MAX_VOLUME)
    val nextStep = (current * VOLUME_STEPS / MAX_VOLUME) + 1
    val volume = if (nextStep >= VOLUME_STEPS) {
      MAX_VOLUME
    } else {
      (nextStep * MAX_VOLUME + VOLUME_STEPS - 1) / VOLUME_STEPS
    }
    send(volume)
  }

  override suspend fun decrease() {
    val current = currentVolume().coerceIn(MIN_VOLUME, MAX_VOLUME)
    val previousStep = ((current * VOLUME_STEPS + MAX_VOLUME - 1) / MAX_VOLUME) - 1
    val volume = if (previousStep <= 0) {
      MIN_VOLUME
    } else {
      previousStep * MAX_VOLUME / VOLUME_STEPS
    }
    send(volume)
  }

  override suspend fun reduceVolume() {
    val volume = currentVolume()
    val mute = isMute()
    if (mute || volume == 0) {
      return
    }
    send((volume * VOLUME_PERCENTAGE).toInt())
  }

  /**
   * Sends a messages with the new volume value through the active socket
   *
   * @param volume The new volume value that will be send to the plugin.
   */
  private suspend fun send(volume: Int) {
    queue.queue(SocketMessage.create(Protocol.PlayerVolume, volume))
  }

  companion object {
    const val VOLUME_STEPS = 22
    const val MAX_VOLUME = 100
    const val MIN_VOLUME = 0
    const val VOLUME_PERCENTAGE = 0.2
  }
}
