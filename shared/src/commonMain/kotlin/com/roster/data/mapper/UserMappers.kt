package com.roster.data.mapper

import com.roster.data.local.UserEntity
import com.roster.data.remote.dto.CreateUserRequest
import com.roster.data.remote.dto.UserDto
import com.roster.domain.model.Gender
import com.roster.domain.model.NewUser
import com.roster.domain.model.User
import com.roster.domain.model.UserStatus
import kotlinx.datetime.Instant

fun UserEntity.toDomain(): User =
    User(
        id = id,
        name = name,
        email = email,
        gender = Gender.fromWire(gender),
        status = UserStatus.fromWire(status),
        firstSeenAt = Instant.fromEpochMilliseconds(firstSeenAtEpochMillis),
    )

/**
 * [firstSeenAt] is supplied by the caller rather than read from the payload: GoRest has no
 * creation timestamp, so "age" means "first observed locally at".
 */
fun UserDto.toDomain(firstSeenAt: Instant): User =
    User(
        id = id,
        name = name,
        email = email,
        gender = Gender.fromWire(gender),
        status = UserStatus.fromWire(status),
        firstSeenAt = firstSeenAt,
    )

fun NewUser.toRequest(): CreateUserRequest =
    CreateUserRequest(
        name = name,
        email = email,
        gender = gender.wireValue,
        status = status.wireValue,
    )
