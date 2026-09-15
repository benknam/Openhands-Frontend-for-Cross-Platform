package com.openhands.remote.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BackendProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val note: String = "",
    val authMode: AuthMode = AuthMode.SESSION_API_KEY,
    val enabled: Boolean = true,
)

@Serializable
data class BackendConnection(
    val profile: BackendProfile,
    val apiKey: String? = null,
)

@Serializable
enum class AuthMode {
    SESSION_API_KEY,
    BEARER,
    COOKIE,
}
