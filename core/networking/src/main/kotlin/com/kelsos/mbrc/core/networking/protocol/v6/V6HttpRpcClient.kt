package com.kelsos.mbrc.core.networking.protocol.v6

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the MusicBee Remote V6 HTTP-RPC transport.
 *
 * serverUrl must be the server origin, for example "http://192.168.1.20:3000".
 * The optional pairing token is sent as a Bearer token and is never added to a URL.
 * This client does not silently fall back to V4/V5.
 */
class V6HttpRpcClient(
  serverUrl: String,
  private val pairingToken: String? = null,
  private val httpClient: OkHttpClient = OkHttpClient(),
  moshi: Moshi = Moshi.Builder().build()
) {
  private val origin: HttpUrl = serverUrl.toV6HttpUrl()
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
  private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
  private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

  /** Executes one V6 operation and returns its raw JSON data object. */
  suspend fun request(op: String, data: Map<String, Any?> = emptyMap()): String =
    withContext(Dispatchers.IO) {
      require(op.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid V6 operation name: $op" }
      val body = mapAdapter.toJson(data).toRequestBody(jsonMediaType)
      val url = origin.newBuilder().addPathSegments("api/v6/$op").build()
      execute(buildRequest(url).post(body).build())
    }

  /** Returns V6 capabilities and this caller permissions as raw JSON. */
  suspend fun capabilities(): String = withContext(Dispatchers.IO) {
    val url = origin.newBuilder().addPathSegments("api/v6/capabilities").build()
    execute(buildRequest(url).get().build())
  }

  private fun buildRequest(url: HttpUrl): Request.Builder {
    val builder = Request.Builder().url(url).header("Accept", "application/json")
    pairingToken?.takeIf { it.isNotBlank() }?.let {
      builder.header("Authorization", "Bearer $it")
    }
    return builder
  }

  private fun execute(request: Request): String {
    httpClient.newCall(request).execute().use { response ->
      val responseText = response.body?.string().orEmpty()
      if (response.isSuccessful) return responseText
      val error = runCatching { mapAdapter.fromJson(responseText) }.getOrNull()
        ?.get("error") as? Map<*, *>
      val code = error?.get("code")?.toString() ?: "http_${response.code}"
      val message = error?.get("message")?.toString()
        ?: response.message.ifBlank { "V6 request failed with HTTP ${response.code}" }
      throw V6ProtocolException(response.code, code, message)
    }
  }
}

class V6ProtocolException(
  val httpStatus: Int,
  val code: String,
  override val message: String
) : IOException("MusicBee Remote V6 error $code (HTTP $httpStatus): $message")

private fun String.toV6HttpUrl(): HttpUrl {
  val url = toHttpUrlOrNull()
    ?: throw IllegalArgumentException("Invalid MusicBee Remote server URL")
  require(url.scheme == "http" || url.scheme == "https") { "V6 HTTP-RPC requires an http(s) URL" }
  require(url.encodedPath == "/" || url.encodedPath.isEmpty()) {
    "Pass the server origin only, without an API path"
  }
  return url
}
