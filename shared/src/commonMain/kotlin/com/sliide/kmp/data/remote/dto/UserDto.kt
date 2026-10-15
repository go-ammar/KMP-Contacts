package com.sliide.kmp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val gender: String? = null,
    val status: String? = null,
)

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
)

/** GoRest returns 422 as a list of `{field, message}` pairs rather than a problem document. */
@Serializable
data class FieldErrorDto(
    @SerialName("field") val field: String? = null,
    @SerialName("message") val message: String? = null,
)
