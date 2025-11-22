package com.chesskel.ui.learn

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.chesskel.R
import com.chesskel.ui.theme.CenteredActivity
import com.chesskel.ui.theme.ThemeUtils

// Identificadores de cada lección disponible en la sección "Aprender a jugar"
enum class LessonId {
    PAWN,
    BISHOP,
    KNIGHT,
    ROOK,
    QUEEN,
    KING,
    MOVES,
    RULES
}

class LearningActivity : CenteredActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar el tema gótico/rock guardado y configurar el layout centrado
        ThemeUtils.applySavedTheme(this)
        setCenteredContentView(R.layout.activity_learning)

        // Título principal de la sección
        val titleView = findViewById<TextView>(R.id.tvLearningTitle)
        titleView.text = getString(R.string.learn_to_play)

        // Asociar cada tarjeta con su lección correspondiente
        bindLessonCard(R.id.option_lesson_pawn, LessonId.PAWN)
        bindLessonCard(R.id.option_lesson_bishop, LessonId.BISHOP)
        bindLessonCard(R.id.option_lesson_knight, LessonId.KNIGHT)
        bindLessonCard(R.id.option_lesson_rook, LessonId.ROOK)
        bindLessonCard(R.id.option_lesson_queen, LessonId.QUEEN)
        bindLessonCard(R.id.option_lesson_king, LessonId.KING)
        bindLessonCard(R.id.option_lesson_moves, LessonId.MOVES)
        bindLessonCard(R.id.option_lesson_rules, LessonId.RULES)
    }

    private fun bindLessonCard(viewId: Int, lessonId: LessonId) {
        val card = findViewById<LinearLayout>(viewId)
        card?.setOnClickListener {
            val intent = Intent(this, LessonDetailActivity::class.java).apply {
                putExtra(LessonDetailActivity.EXTRA_LESSON_ID, lessonId.name)
            }
            startActivity(intent)
        }
    }
}
