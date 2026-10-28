package com.kelsos.mbrc.core.networking.protocol.payloads

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NowPlayingRemoveRequest(
  @Json(name = "orders")
  val orders: List<Int>
)
