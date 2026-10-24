package com.kelsos.mbrc.feature.content.playlists

import android.content.Context
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.networking.ClientIdProvider
import com.kelsos.mbrc.core.networking.DefaultConnectionProvider
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Read-only playlist entries. Opening a playlist MUST NOT mutate MusicBee's queue. */
data class PlaylistSong(val title: String, val path: String)

class PlaylistBrowseUnsupportedException : IOException(
  "The MusicBee server does not support read-only playlist browsing (protocol v6 required)."
)

fun interface PlaylistTrackReader {
  suspend fun read(url: String): List<PlaylistSong>
}

/**
 * V6 is a distinct wire protocol, on the same TCP port as the existing V4 client.
 * A short-lived auxiliary socket leaves all existing playback, sync and V4 events
 * untouched. The handshake token is retained for future requests to that server.
 */
class V6PlaylistTrackReader(
  private val connectionProvider: DefaultConnectionProvider,
  private val idProvider: ClientIdProvider,
  private val context: Context,
  private val dispatchers: AppCoroutineDispatchers
) : PlaylistTrackReader {
  override suspend fun read(url: String): List<PlaylistSong> = withContext(dispatchers.network) {
    val connection = connectionProvider.getDefault()
      ?: throw IOException("No MusicBee server connection configured")
    val preferences = context.getSharedPreferences("mbrc_playlist_v6", Context.MODE_PRIVATE)
    val serverKey = "${connection.address}:${connection.port}"
    val clientId = (idProvider.getClientId() + "-playlist-browser").take(128)
    val data = JSONObject()
      .put("protocol_version", 6)
      .put("client_type", "android")
      .put("client_id", clientId)
      .put("no_broadcast", true)
    preferences.getString("token:$serverKey", null)?.let { data.put("client_token", it) }

    Socket().use { socket ->
      socket.soTimeout = 8000
      socket.connect(InetSocketAddress(connection.address, connection.port), 5000)
      val output = socket.getOutputStream()
      val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
      fun send(id: Int, op: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
          .put("id", id)
          .put("kind", "request")
          .put("op", op)
          .put("data", payload)
        output.write((request.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
        output.flush()
        // Auxiliary sockets opt out of broadcasts. Match correlation id anyway.
        val line = input.readLine() ?: throw IOException("MusicBee server closed the connection")
        val response = JSONObject(line)
        if (response.optInt("id", -1) != id || response.optString("kind") != "response") {
          throw IOException("Unexpected MusicBee response")
        }
        response.optJSONObject("error")?.let { error ->
          val code = error.optString("code")
          if (code == "unsupported_version" || code == "unknown_op") {
            throw PlaylistBrowseUnsupportedException()
          }
          throw IOException("MusicBee: " + error.optString("message", code))
        }
        return response.optJSONObject("data")
          ?: throw IOException("Missing response data from MusicBee")
      }

      val hello = try {
        send(0, "handshake", data)
      } catch (e: IOException) {
        if (e is PlaylistBrowseUnsupportedException) throw e
        // The old V4 server does not understand the V6 handshake.
        throw PlaylistBrowseUnsupportedException()
      }
      hello.optString("client_token").takeIf { it.isNotBlank() }?.let { token ->
        preferences.edit().putString("token:$serverKey", token).commit()
      }
      val ops = hello.optJSONObject("capabilities")?.optJSONArray("ops")
        ?: throw PlaylistBrowseUnsupportedException()
      if ((0 until ops.length()).none { ops.optString(it) == "playlist_tracks" }) {
        throw PlaylistBrowseUnsupportedException()
      }

      val songs = mutableListOf<PlaylistSong>()
      var offset = 0
      var requestId = 1
      do {
        val page = send(
          requestId++,
          "playlist_tracks",
          JSONObject().put("url", url).put("offset", offset).put("limit", 200)
        )
        val total = page.optInt("total", 0)
        val items = page.optJSONArray("items") ?: JSONArray()
        for (index in 0 until items.length()) {
          val entry = items.getJSONObject(index)
          val path = entry.optString("src")
          if (path.isNotBlank()) {
            songs.add(PlaylistSong(entry.optString("title").ifBlank { path.substringAfterLast('\\') }, path))
          }
        }
        // Preserve paging even when some tracks have missing paths.
        if (items.length() == 0) break
        offset += items.length()
        if (offset >= total) break
        if (offset > 50_000) throw IOException("Playlist is too large to browse safely")
      } while (true)
      songs
    }
  }
}
