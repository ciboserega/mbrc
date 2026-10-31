package com.kelsos.mbrc.feature.playback.player.compose

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TempoPitchPanelTest {
  @Test fun pitchUsesExactHalfSemitoneSteps() {
    assertThat(nextPitchHalfStep(0, 1)).isEqualTo(1)
    assertThat(nextPitchHalfStep(1, 1)).isEqualTo(2)
    assertThat(nextPitchHalfStep(0, -1)).isEqualTo(-1)
  }

  @Test fun pitchClampsToTwelveSemitones() {
    assertThat(nextPitchHalfStep(24, 1)).isEqualTo(24)
    assertThat(nextPitchHalfStep(-24, -1)).isEqualTo(-24)
  }

  @Test fun tempoChangesByOnePercent() {
    assertThat(nextTempoPercent(100, 1)).isEqualTo(101)
    assertThat(nextTempoPercent(100, -1)).isEqualTo(99)
  }

  @Test fun tempoClampsToSafeRange() {
    assertThat(nextTempoPercent(150, 1)).isEqualTo(150)
    assertThat(nextTempoPercent(50, -1)).isEqualTo(50)
  }
}
