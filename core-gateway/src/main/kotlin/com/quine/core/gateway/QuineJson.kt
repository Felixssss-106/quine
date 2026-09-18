package com.quine.core.gateway

import kotlinx.serialization.json.Json

/** 全项目统一的 JSON 配置。 */
val QuineJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}
