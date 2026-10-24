# Track search

## Search entry points

Track searches run against the local Room cache, not a MusicBee server-side search
API. A two-character term has no special minimum-length restriction.

| Entry point | Data and matching | Updates |
| --- | --- | --- |
| Library → Tracks | `TrackDao.search` and all eight sorted `searchBy…` queries match an order-independent match for every non-empty, space-separated title fragment using a recursive SQLite token query. Artist, album, album artist, path, and genre do not affect membership. The selected sort field only affects ordering. | `LibraryScreen.onQueryChange` → `LibraryViewModel.search` → `LibrarySearchModel.term` → `BrowseTrackViewModel.tracks`. Input is debounced for 300 ms; submission is not required. Clearing restores the sorted full library. |
| Now Playing / Queue | `NowPlayingDao.search`, `simpleSearch`, `findPositionByQuery`, and `searchTrack` match each title fragment independently (regardless of word order) and order results by queue position. Single-result lookups return the first match in queue order. Only cached queue entries are searched. | Typing filters the list after 300 ms without sending playback commands. Enter retains the existing play-first-match action. Clearing or closing returns to the full queue and its scroll state. |

Filtered queue rows retain their original queue positions for playback, removal,
and movement. List offsets within the search results are not queue positions.

## Multi-word search

Spaces separate independent title fragments: `SIMPLY BEST`, `BEST SIMPLY`,
`sim be`, and `be sim` all find `SIMPLY THE BEST`. The fragments can appear
in any order and other words may occur between them. Cyrillic queries such as
`ПРИЕ БУКУ` find `БУКУРАЦИ-ВЭ ПРИЕТЕНЬ`. A single
fragment (for example `be`) still matches anywhere in a title. Filtering
continues to use the title only; matching artists or albums never adds tracks.

## Why the original behavior could look different

Before this change, the Tracks tab already supported two letters anywhere in a
title: all nine track DAO queries used `LIKE '%term%'`, and
`LibrarySearchModel` already emitted after a 300 ms debounce without a length
check. However, those queries also matched the artist. All four queue search
queries likewise matched title **or artist**. The queue screen only executed a
search on submission, then played the first match instead of filtering while
editing. Changing only `LibrarySearchModel` would not fix the queue screen.

The library search bar is shared by four tabs, but each tab searches its own
entity. The library initially opens on Genres. Genres searches genre names;
Artists searches artist names (respecting the album-artists-only preference);
Albums searches album and artist names. These are not song-title searches and
keep their existing behavior. Switching to Tracks searches song titles using
the same entered term.

Album-track detail screens browse a selected album (or an artist's non-album
tracks) and have no search field. Playlist and radio repository/DAO `search`
methods match playlist/station names, but their current browser screens do not
expose a search field. License search and connection discovery are unrelated to
track search. There is no additional track-search entry point in the media
session or network protocol.

Both track-search scopes depend on cached data. A new or renamed library track
will not appear until library sync updates the cache; a track outside the current
queue will not appear in a queue search. SQLite's existing `LIKE` semantics are
preserved, including ASCII case-insensitivity, case-sensitive non-ASCII letters,
and `%`/`_` pattern characters.

## Regression coverage and manual checks

Room tests cover two-letter matches at the start, middle, and end of titles,\norder-independent partial-word matching across intervening words,
metadata-only false positives, all track sort fields/directions and the legacy
track query, and all four queue DAO queries with deterministic queue ordering.
Flow/ViewModel tests cover typing, replacing, clearing, and debouncing without
submission. Queue UI tests cover actions on filtered rows using original queue
positions and preserve the explicit search-and-play action.

For a device check, sync a library containing `ab opening`, `Middle ab Song`,
`Ending ab`, and a track whose artist alone contains `ab`. Search `ab` on Tracks
and in a queue containing those entries: only the first three should appear
while typing. Replace the term, clear it, and close search; confirm the full list
returns. In the filtered queue, play, remove, and drag a match that was not near
the start of the original queue, and confirm the intended queue entries change.
