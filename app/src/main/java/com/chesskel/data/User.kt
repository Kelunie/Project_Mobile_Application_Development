package com.chesskel.data

data class User(
    val id: Long,
    val name: String,
    val email: String
)

fun UserEntity.toUser(): User = User(id = id, name = nombre, email = email)
