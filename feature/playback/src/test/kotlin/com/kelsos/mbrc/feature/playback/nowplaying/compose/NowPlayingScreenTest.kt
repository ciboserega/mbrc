package com.kelsos.mbrc.feature.playback.nowplaying.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.data.nowplaying.NowPlaying
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `empty state shows the empty message`() {
    composeTestRule.setEmptyContent()

    composeTestRule.onNodeWithText("Your queue is empty").assertIsDisplayed()
  }

  @Test
  fun `empty state is wrapped in a scrollable container so pull-to-refresh works`() {
    composeTestRule.setEmptyContent()

    // PullToRefreshBox requires its child to be vertically scrollable to detect
    // the pull gesture; without it the empty state never triggers refresh.
    composeTestRule.onNode(hasScrollAction()).assertExists()
  }

  @Test
  fun `clicking a filtered result plays its original queue position`() {
    var playedPosition: Int? = null
    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = filteredQueue.collectAsLazyPagingItems(),
        trackCount = 20,
        isConnected = true,
        isFiltering = true,
        onTrackClick = { playedPosition = it }
      )
    }

    composeTestRule.onNodeWithText("Second match").performClick()

    assertThat(playedPosition).isEqualTo(8)
  }

  @Test
  fun `removing a filtered result removes its original queue position`() {
    var removedPosition: Int? = null
    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = filteredQueue.collectAsLazyPagingItems(),
        trackCount = 20,
        isConnected = true,
        isFiltering = true,
        onTrackRemove = { removedPosition = it }
      )
    }

    composeTestRule.onAllNodesWithContentDescription("More options")[1].performClick()
    composeTestRule.onNodeWithText("Remove track").performClick()

    // Removal uses the protocol's zero-based position, not the second visible row's index.
    assertThat(removedPosition).isEqualTo(7)
  }

  @Test
  fun `dragging filtered results reorders the original queue positions`() {
    val moves = mutableListOf<Pair<Int, Int>>()
    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = filteredQueue.collectAsLazyPagingItems(),
        trackCount = 20,
        isConnected = true,
        isFiltering = true,
        onTrackMove = { from, to -> moves += from to to }
      )
    }

    val handles = composeTestRule.onAllNodesWithContentDescription(
      "Drag to reorder",
      useUnmergedTree = true
    )
    val positions = handles.fetchSemanticsNodes().map { it.boundsInRoot.center.y }
    handles[0].performTouchInput {
      down(center)
      advanceEventTime(600)
      moveBy(Offset(0f, positions[1] - positions[0]), delayMillis = 100)
      up()
    }
    composeTestRule.waitForIdle()

    assertThat(moves).contains(2 to 7)
  }

  @Test
  fun `with placeholders enabled total item count equals dataset size before any load`() {
    // Regression for the snap-to-page-boundary bug. Without placeholders, the
    // LazyPagingItems item count was the number of loaded items (~100 after
    // initial load). After a dao.move invalidation the count would briefly
    // shrink to the new initial-load size, the LazyColumn would clamp the
    // scroll anchor to that smaller list, and the user would land on
    // position ~100 regardless of where they had been.
    //
    // With placeholders enabled, item count = total dataset size from the
    // moment the first page lands, so the LazyColumn never sees the list
    // shrink.
    val totalItems = 350
    val source = CountingPagingSource(totalItems = totalItems)
    val pager = Pager(
      config = PagingConfig(
        pageSize = 50,
        initialLoadSize = 100,
        prefetchDistance = 25,
        enablePlaceholders = true
      ),
      pagingSourceFactory = { source }
    )

    var observedItemCount = 0
    composeTestRule.setContent {
      val tracks = pager.flow.collectAsLazyPagingItems()
      observedItemCount = tracks.itemCount
      TestNowPlayingContent(
        tracks = tracks,
        trackCount = totalItems,
        isConnected = false
      )
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      observedItemCount == totalItems
    }
    assertThat(observedItemCount).isEqualTo(totalItems)
  }

  @Test
  fun `scrolling past the initial page triggers paging beyond the initial load size`() {
    val totalItems = 400
    val source = CountingPagingSource(totalItems = totalItems)
    val pager = Pager(
      config = PagingConfig(
        pageSize = 50,
        initialLoadSize = 100,
        prefetchDistance = 25,
        enablePlaceholders = false
      ),
      pagingSourceFactory = { source }
    )

    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = pager.flow.collectAsLazyPagingItems(),
        trackCount = totalItems,
        isConnected = false
      )
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) { source.loadCount >= 1 }
    val initialLoads = source.loadCount

    for (i in 0 until 20) {
      if (source.loadCount > initialLoads) break
      composeTestRule.onRoot().performTouchInput { swipeUp() }
      composeTestRule.waitForIdle()
    }

    assertThat(source.loadCount).isGreaterThan(initialLoads)
  }

  @Test
  fun `opening the queue scrolls the playing track into view`() {
    val totalItems = 350
    val playingIndex = 200
    val listState = LazyListState()
    val pager = pagerOf(totalItems)

    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = pager.flow.collectAsLazyPagingItems(),
        trackCount = totalItems,
        isConnected = false,
        playingTrackIndex = playingIndex,
        lazyListState = listState
      )
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      listState.firstVisibleItemIndex != 0
    }
    // Two tracks of context are kept above the playing one.
    assertThat(listState.firstVisibleItemIndex).isEqualTo(playingIndex - 2)
  }

  @Test
  fun `clearing a filter does not repeat auto-scroll after the user returned to the top`() {
    val totalItems = 350
    val playingIndex = 200
    val queueState = LazyListState()
    val searchState = LazyListState()
    val pager = pagerOf(totalItems)
    var isFiltering by mutableStateOf(false)

    composeTestRule.setContent {
      val queue = pager.flow.collectAsLazyPagingItems()
      val matches = filteredQueue.collectAsLazyPagingItems()
      TestNowPlayingContent(
        tracks = if (isFiltering) matches else queue,
        trackCount = totalItems,
        isConnected = false,
        playingTrackIndex = if (isFiltering) null else playingIndex,
        isFiltering = isFiltering,
        lazyListState = if (isFiltering) searchState else queueState
      )
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      queueState.firstVisibleItemIndex == playingIndex - 2
    }
    composeTestRule.runOnIdle { queueState.requestScrollToItem(0) }
    composeTestRule.waitForIdle()
    assertThat(queueState.firstVisibleItemIndex).isEqualTo(0)

    composeTestRule.runOnIdle { isFiltering = true }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { isFiltering = false }
    composeTestRule.waitForIdle()

    assertThat(queueState.firstVisibleItemIndex).isEqualTo(0)
  }

  @Test
  fun `the queue stays at the top when the playing track is not in it`() {
    val totalItems = 350
    val listState = LazyListState()
    val pager = pagerOf(totalItems)

    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = pager.flow.collectAsLazyPagingItems(),
        trackCount = totalItems,
        isConnected = false,
        playingTrackIndex = null,
        lazyListState = listState
      )
    }
    composeTestRule.waitForIdle()

    assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
  }

  @Test
  fun `the auto-scroll leaves a list the user already scrolled alone`() {
    // The index is resolved by a database lookup, so it can arrive after the user has started
    // scrolling. Jumping the list out from under them at that point is worse than not scrolling.
    val totalItems = 350
    val listState = LazyListState()
    var playingIndex by mutableStateOf<Int?>(null)
    // Hoisted: writing playingIndex recomposes this lambda, and building the Pager inside it would
    // hand collectAsLazyPagingItems a new flow, restarting paging and collapsing itemCount to 0.
    val pager = pagerOf(totalItems)

    composeTestRule.setContent {
      TestNowPlayingContent(
        tracks = pager.flow.collectAsLazyPagingItems(),
        trackCount = totalItems,
        isConnected = false,
        playingTrackIndex = playingIndex,
        lazyListState = listState
      )
    }

    composeTestRule.onRoot().performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    val scrolledTo = listState.firstVisibleItemIndex
    assertThat(scrolledTo).isGreaterThan(0)

    playingIndex = 200
    composeTestRule.waitForIdle()

    assertThat(listState.firstVisibleItemIndex).isEqualTo(scrolledTo)
  }
}

private fun pagerOf(totalItems: Int): Pager<Int, NowPlaying> = Pager(
  config = PagingConfig(
    pageSize = 50,
    initialLoadSize = 100,
    prefetchDistance = 25,
    enablePlaceholders = true
  ),
  pagingSourceFactory = { CountingPagingSource(totalItems = totalItems) }
)

private class CountingPagingSource(private val totalItems: Int) : PagingSource<Int, NowPlaying>() {
  @Volatile var loadCount: Int = 0
    private set

  override fun getRefreshKey(state: PagingState<Int, NowPlaying>): Int? = null

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, NowPlaying> {
    loadCount += 1
    val key = params.key ?: 0
    val end = (key + params.loadSize).coerceAtMost(totalItems)
    val data = (key until end).map { i ->
      NowPlaying(
        id = i.toLong() + 1,
        title = "Track ${i + 1}",
        artist = "Artist ${i + 1}",
        path = "/path/$i",
        position = i + 1
      )
    }
    val nextKey = if (end >= totalItems) null else end
    val prevKey = if (key == 0) null else (key - params.loadSize).coerceAtLeast(0)
    return LoadResult.Page(
      data = data,
      prevKey = prevKey,
      nextKey = nextKey,
      itemsBefore = key,
      itemsAfter = totalItems - end
    )
  }
}

private fun ComposeContentTestRule.setEmptyContent() {
  setContent {
    val tracks = flowOf(
      PagingData.empty<NowPlaying>(
        sourceLoadStates = LoadStates(
          refresh = LoadState.NotLoading(endOfPaginationReached = true),
          prepend = LoadState.NotLoading(endOfPaginationReached = true),
          append = LoadState.NotLoading(endOfPaginationReached = true)
        )
      )
    ).collectAsLazyPagingItems()
    TestNowPlayingContent(tracks = tracks, trackCount = 0, isConnected = true)
  }
}

@Composable
private fun TestNowPlayingContent(
  tracks: LazyPagingItems<NowPlaying>,
  trackCount: Int,
  isConnected: Boolean,
  playingTrackIndex: Int? = null,
  isFiltering: Boolean = false,
  onTrackClick: (Int) -> Unit = {},
  onTrackRemove: (Int) -> Unit = {},
  onTrackMove: (Int, Int) -> Unit = { _, _ -> },
  lazyListState: LazyListState = rememberLazyListState()
) {
  NowPlayingContent(
    tracks = tracks,
    playingTrackPath = "",
    trackCount = trackCount,
    isRefreshing = false,
    isConnected = isConnected,
    onRefresh = {},
    onTrackClick = onTrackClick,
    onTrackRemove = onTrackRemove,
    onTrackMove = onTrackMove,
    onDragEnd = {},
    onGoToAlbum = null,
    onGoToArtist = {},
    playingTrackIndex = playingTrackIndex,
    isFiltering = isFiltering,
    lazyListState = lazyListState
  )
}

private val filteredQueue = flowOf(
  PagingData.from(
    listOf(
      NowPlaying("First match", "Artist", "/first.mp3", position = 3, id = 3),
      NowPlaying("Second match", "Artist", "/second.mp3", position = 8, id = 8)
    )
  )
)
