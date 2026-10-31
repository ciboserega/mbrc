package com.kelsos.mbrc.feature.playback.player.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kelsos.mbrc.feature.playback.R
import java.util.Locale

/** Pitch is stored in integer half-steps to avoid floating-point drift. */
internal fun nextPitchHalfStep(current: Int, direction: Int): Int =
  (current + direction.coerceIn(-1, 1)).coerceIn(-24, 24)

/** Tempo is a playback multiplier in percent, NOT a BPM tag. */
internal fun nextTempoPercent(current: Int, direction: Int): Int =
  (current + direction.coerceIn(-1, 1)).coerceIn(50, 150)

/**
 * UI only until a real MusicBee server operation can read and write these values.
 *
 * Never call onPitchChange/onTempoChange or display optimistic values when disabled:
 * the current MusicBee Remote plugin does not implement tempo/pitch operations.
 */
@Composable
fun TempoPitchPanel(
  pitchHalfSteps: Int,
  tempoPercent: Int,
  enabled: Boolean,
  onPitchChange: (Int) -> Unit,
  onTempoChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    AdjustmentRow(
      title = stringResource(R.string.player_pitch_label),
      value = String.format(Locale.ROOT, "%+.1f", pitchHalfSteps / 2.0),
      enabled = enabled,
      canDecrease = pitchHalfSteps > -24,
      canIncrease = pitchHalfSteps < 24,
      onDecrease = { onPitchChange(nextPitchHalfStep(pitchHalfSteps, -1)) },
      onIncrease = { onPitchChange(nextPitchHalfStep(pitchHalfSteps, 1)) }
    )
    AdjustmentRow(
      title = stringResource(R.string.player_tempo_label),
      value = "$tempoPercent%",
      enabled = enabled,
      canDecrease = tempoPercent > 50,
      canIncrease = tempoPercent < 150,
      onDecrease = { onTempoChange(nextTempoPercent(tempoPercent, -1)) },
      onIncrease = { onTempoChange(nextTempoPercent(tempoPercent, 1)) }
    )
    if (!enabled) {
      Text(
        text = stringResource(R.string.player_tempo_pitch_requires_server),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp)
      )
    }
  }
}

@Composable
private fun AdjustmentRow(
  title: String,
  value: String,
  enabled: Boolean,
  canDecrease: Boolean,
  canIncrease: Boolean,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = title, style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onDecrease, enabled = enabled && canDecrease) {
        Text("−", style = MaterialTheme.typography.titleLarge)
      }
      Text(text = value, style = MaterialTheme.typography.titleMedium)
      IconButton(onClick = onIncrease, enabled = enabled && canIncrease) {
        Text("+", style = MaterialTheme.typography.titleLarge)
      }
    }
  }
}
