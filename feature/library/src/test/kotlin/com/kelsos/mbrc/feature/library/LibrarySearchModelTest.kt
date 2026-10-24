package com.kelsos.mbrc.feature.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibrarySearchModelTest {

  @Test
  fun `two letter input replacement and clearing should emit without submitting`() {
    runTest {
      val searchModel = LibrarySearchModel()

      searchModel.term.test {
        advanceTimeBy(350)
        assertThat(awaitItem()).isEmpty()

        searchModel.setTerm("x")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEqualTo("x")

        searchModel.setTerm("xy")
        advanceTimeBy(200)
        expectNoEvents()
        advanceTimeBy(150)
        assertThat(awaitItem()).isEqualTo("xy")

        searchModel.setTerm("но")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEqualTo("но")

        searchModel.setTerm("")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEmpty()
      }
    }
  }

  @Test
  fun `term should debounce rapid emissions`() {
    runTest {
      val searchModel = LibrarySearchModel()

      searchModel.term.test {
        searchModel.setTerm("a")
        searchModel.setTerm("ab")
        searchModel.setTerm("abc")

        // Advance past debounce delay
        advanceTimeBy(350)

        // Should only receive the last value after debounce
        assertThat(awaitItem()).isEqualTo("abc")

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  @Test
  fun `term should emit distinct values only`() {
    runTest {
      val searchModel = LibrarySearchModel()

      searchModel.term.test {
        searchModel.setTerm("test")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEqualTo("test")

        // Emit same value again
        searchModel.setTerm("test")
        advanceTimeBy(350)

        // Should not receive duplicate
        expectNoEvents()

        // Different value should be emitted
        searchModel.setTerm("different")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEqualTo("different")

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  @Test
  fun `term should emit after debounce delay`() {
    runTest {
      val searchModel = LibrarySearchModel()

      searchModel.term.test {
        searchModel.setTerm("query")

        // Before debounce completes - should not emit yet
        advanceTimeBy(200)
        expectNoEvents()

        // After debounce completes
        advanceTimeBy(150)
        assertThat(awaitItem()).isEqualTo("query")

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  @Test
  fun `clearing search should emit empty string after debounce`() {
    runTest {
      val searchModel = LibrarySearchModel()

      searchModel.term.test {
        // Set initial term
        searchModel.setTerm("search")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEqualTo("search")

        // Clear search
        searchModel.setTerm("")
        advanceTimeBy(350)
        assertThat(awaitItem()).isEqualTo("")

        cancelAndIgnoreRemainingEvents()
      }
    }
  }
}
