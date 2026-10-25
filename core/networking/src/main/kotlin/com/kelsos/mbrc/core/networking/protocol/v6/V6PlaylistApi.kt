package com.kelsos.mbrc.core.networking.protocol.v6

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** Typed convenience API for V6 playlist operations over HTTP-RPC. */
class V6PlaylistApi(private val client: V6HttpRpcClient, moshi: Moshi = Moshi.Builder().build()) {
  private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
  private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

  /** Fetch one page of playlists. The server defaults the paging fields when omitted. */
  suspend fun list(offset: Int = 0, limit: Int = 100): V6PlaylistPage {
    require(offset >= 0) { "offset must not be negative" }
    require(limit in 1..1000) { "limit must be between 1 and 1000" }
    val raw = client.request("playlist_list", mapOf("offset" to offset, "limit" to limit))
    val json = mapAdapter.fromJson(raw) ?: error("Empty playlist_list response")
    val items = (json["items"] as? List<*>)?.mapNotNull { row ->
      val item = row as? Map<*, *> ?: return@mapNotNull null
      val url = item["url"] as? String ?: return@mapNotNull null
      val name = item["name"] as? String ?: url
      V6Playlist(url = url, name = name, editable = item["editable"] as? Boolean ?: false)
    }.orEmpty()
    return V6PlaylistPage(
      total = (json["total"] as? Number)?.toInt() ?: items.size,
      offset = (json["offset"] as? Number)?.toInt() ?: offset,
      items = items
    )
  }

  /** Read a page of tracks and the opaque version token used to guard playlist edits. */
  suspend fun tracks(
    url: String,
    offset: Int = 0,
    limit: Int = 100,
    query: String? = null
  ): V6PlaylistTracksPage {
    require(url.isNotBlank()) { "playlist url must not be blank" }
    require(offset >= 0) { "offset must not be negative" }
    require(limit in 1..1000) { "limit must be between 1 and 1000" }
    val payload = buildMap<String, Any> {
      put("url", url)
      put("offset", offset)
      put("limit", limit)
      query?.takeIf { it.isNotBlank() }?.let { put("query", it) }
    }
    val raw = client.request("playlist_tracks", payload)
    val json = mapAdapter.fromJson(raw) ?: error("Empty playlist_tracks response")
    val items = (json["items"] as? List<*>)?.mapNotNull { row ->
      val item = row as? Map<*, *> ?: return@mapNotNull null
      val src = item["src"] as? String ?: return@mapNotNull null
      V6PlaylistTrack(
        src = src,
        title = item["title"] as? String ?: "",
        artist = item["artist"]?.toString() ?: "",
        album = item["album"] as? String ?: "",
        order = (item["order"] as? Number)?.toInt() ?: -1,
        position = (item["position"] as? Number)?.toInt() ?: -1
      )
    }.orEmpty()
    return V6PlaylistTracksPage(
      name = json["name"] as? String ?: url,
      version = json["version"] as? String,
      editable = json["editable"] as? Boolean ?: false,
      total = (json["total"] as? Number)?.toInt() ?: items.size,
      offset = (json["offset"] as? Number)?.toInt() ?: offset,
      items = items
    )
  }

  /** Append file paths to the end of a playlist without replacing its current contents. */
  suspend fun addTracks(url: String, paths: List<String>, version: String? = null): V6PlaylistMutation {
    require(url.isNotBlank()) { "playlist url must not be blank" }
    require(paths.isNotEmpty()) { "at least one track path is required" }
    require(paths.all { it.isNotBlank() }) { "track paths must not be blank" }
    val payload = buildMap<String, Any> {
      put("url", url)
      put("paths", paths)
      version?.let { put("version", it) }
    }
    val raw = client.request("playlist_add_tracks", payload)
    val json = mapAdapter.fromJson(raw) ?: error("Empty playlist_add_tracks response")
    return V6PlaylistMutation(
      version = json["version"] as? String,
      affected = (json["added"] as? Number)?.toInt() ?: paths.size
    )
  }
}

data class V6Playlist(val url: String, val name: String, val editable: Boolean)
data class V6PlaylistPage(val total: Int, val offset: Int, val items: List<V6Playlist>)
data class V6PlaylistMutation(val version: String?, val affected: Int)
data class V6PlaylistTrack(
  val src: String,
  val title: String,
  val artist: String,
  val album: String,
  /** Stable playlist index used by mutation operations. */
  val order: Int,
  /** Position within the current returned page/search results. */
  val position: Int
)
data class V6PlaylistTracksPage(
  val name: String,
  val version: String?,
  val editable: Boolean,
  val total: Int,
  val offset: Int,
  val items: List<V6PlaylistTrack>
)
