package com.sliide.kmp.domain.model

import kotlinx.datetime.Instant

/**
 * A user in the directory.
 *
 * [firstSeenAt] is local metadata, not part of the GoRest payload: the API exposes no
 * creation timestamp, so the app records when each user first entered the local cache and
 * derives the relative timestamp from that. Users created in-app get their real creation time.
 */
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val firstSeenAt: Instant,
) {
    /** Up to two upper-case initials, for the avatar placeholder. */
    val initials: String
        get() =
            name
                .split(' ')
                .filter { it.isNotBlank() }
                .take(2)
                .map { it.first().uppercaseChar() }
                .joinToString(separator = "")
                .ifEmpty { "?" }
}

enum class Gender(
    val wireValue: String,
) {
    Male("male"),
    Female("female"),
    ;

    companion object {
        fun fromWire(value: String?): Gender =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: Male
    }
}

enum class UserStatus(
    val wireValue: String,
) {
    Active("active"),
    Inactive("inactive"),
    ;

    companion object {
        fun fromWire(value: String?): UserStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: Active
    }
}

/** The fields the caller supplies when creating a user; the API assigns the id. */
data class NewUser(
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)
