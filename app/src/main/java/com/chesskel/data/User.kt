package com.chesskel.data

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val profileImagePath: String? = null,
    val location: String? = null,
    var profileImageUrl: String? = null
)

fun UserEntity.toUser(): User = User(
    id = id,
    name = nombre,
    email = email,
    profileImagePath = profileImagePath,
    location = location,
    profileImageUrl = profileImageUrl
)
