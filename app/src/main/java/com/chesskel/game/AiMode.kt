package com.chesskel.game

// enumc class for AI difficulty levels
enum class AiMode(val label: String, val minElo: Int, val maxElo: Int){
    EASY("Easy", 800, 1200),
    NORMAL("Medium", 1201, 1600),
    HARD("Hard", 1601, 2000),
    PRO("Expert", 2001, 2400)
}