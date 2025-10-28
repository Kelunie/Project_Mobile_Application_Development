// kotlin
package com.chesskel.game

enum class AiMode(val label: String, val minElo: Int, val maxElo: Int) {
    EASY("Easy", 0, 450),
    NORMAL("Normal", 550, 800),
    HARD("Hard", 950, 1350),
    PRO("Pro", 1500, 2000)
}
