package com.kelsos.mbrc.core.data.nowplaying

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.data.Database
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingSearchTest {
  private lateinit var database: Database
  private lateinit var dao: NowPlayingDao

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      Database::class.java
    ).allowMainThreadQueries().build()
    dao = database.nowPlayingDao()

    // Insert out of queue order so the first match cannot depend on insertion order.
    dao.insertAll(
      listOf(
        NowPlayingEntity(title = "Onward", path = "/music/first.mp3", position = 30),
        NowPlayingEntity(title = "Moonlight", path = "/music/middle.mp3", position = 10),
        NowPlayingEntity(title = "Horizon", path = "/music/last.mp3", position = 50),
        NowPlayingEntity(
          title = "Echo",
          artist = "On Ensemble",
          path = "/music/artist.mp3",
          position = 20
        ),
        NowPlayingEntity(title = "Dawn", path = "/folder/on.mp3", position = 40)
      )
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun pagedSearchMatchesTwoLettersAnywhereInTitleInQueueOrder() = runTest {
    val results = loadSearch("oN")

    assertThat(results.map { it.title }).containsExactly("Moonlight", "Onward", "Horizon").inOrder()
    assertThat(results.map { it.position }).containsExactly(10, 30, 50).inOrder()
  }

  @Test
  fun simpleSearchMatchesTwoLettersAnywhereInTitleInQueueOrder() {
    val results = dao.simpleSearch("oN")

    assertThat(results.map { it.title }).containsExactly("Moonlight", "Onward", "Horizon").inOrder()
    assertThat(results.map { it.position }).containsExactly(10, 30, 50).inOrder()
  }

  @Test
  fun firstMatchSearchesUseQueueOrderAndReturnTheOriginalPosition() {
    assertThat(dao.findPositionByQuery("oN")).isEqualTo(10)
    assertThat(dao.searchTrack("oN")).isEqualTo(SearchResult(10, "Moonlight"))

    assertThat(dao.findPositionByQuery("wa")).isEqualTo(30)
    assertThat(dao.findPositionByQuery("ht")).isEqualTo(10)
    assertThat(dao.searchTrack("Ho")).isEqualTo(SearchResult(50, "Horizon"))
    assertThat(dao.searchTrack("zo")).isEqualTo(SearchResult(50, "Horizon"))
  }

  @Test
  fun allSearchesExcludeArtistAndPathOnlyMatchesAndHandleNoMatch() = runTest {
    for (query in listOf("Ensemble", "folder", "missing")) {
      assertThat(loadSearch(query)).isEmpty()
      assertThat(dao.simpleSearch(query)).isEmpty()
      assertThat(dao.findPositionByQuery(query)).isNull()
      assertThat(dao.searchTrack(query)).isNull()
    }
  }

  @Test
  fun clearingTheQueryRestoresTheFullQueueInPositionOrder() = runTest {
    assertThat(loadSearch("on")).hasSize(3)

    val expectedPositions = listOf(10, 20, 30, 40, 50)
    assertThat(loadSearch("").map { it.position }).containsExactlyElementsIn(expectedPositions).inOrder()
    assertThat(dao.simpleSearch("").map { it.position })
      .containsExactlyElementsIn(expectedPositions).inOrder()
  }

  private suspend fun loadSearch(query: String): List<NowPlayingEntity> {
    val result = dao.search(query).load(
      PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false)
    )
    assertThat(result).isInstanceOf(PagingSource.LoadResult.Page::class.java)
    return (result as PagingSource.LoadResult.Page<Int, NowPlayingEntity>).data
  }
}
