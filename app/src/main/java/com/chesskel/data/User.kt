package com.chesskel.data

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val profileImageUri: String?,
    val location: String?
)

fun UserEntity.toUser(): User = User(
    id = id,
    name = nombre,
    email = email,
    profileImageUri = profileImageUri,
    location = location
)
