package com.kelsos.mbrc.feature.playback.nowplaying.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kelsos.mbrc.feature.playback.R
import com.kelsos.mbrc.core.ui.compose.DynamicScreenScaffold
import com.kelsos.mbrc.core.ui.compose.TopBarState

/**
 * First Slots layout pass: 20 independent empty tiles arranged in four columns and five rows.
 * Track assignment will be added as a separate step; these tiles do not modify MusicBee Queue.
 */
@Composable
fun SlotsScreen(
  onOpenDrawer: () -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState
) {
  val title = androidx.compose.ui.res.stringResource(R.string.nav_slots)
  val topBar = remember(title) { TopBarState.WithTitle(title) }

  DynamicScreenScaffold(
    topBarState = topBar,
    snackbarHostState = snackbarHostState,
    defaultTitle = title,
    onOpenDrawer = onOpenDrawer,
    actionItems = emptyList(),
    menuItems = emptyList()
  ) { padding ->
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
      contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items((1..20).toList()) {
        Surface(
          modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
          tonalElevation = 0.dp,
          shadowElevation = 0.dp
        ) {}
      }
    }
  }
}
