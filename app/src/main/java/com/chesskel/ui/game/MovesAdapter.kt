package com.chesskel.ui.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chesskel.R

class MovesAdapter(private val moves: List<String>) : RecyclerView.Adapter<MovesAdapter.MoveViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoveViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_move, parent, false)
        return MoveViewHolder(view)
    }

    override fun onBindViewHolder(holder: MoveViewHolder, position: Int) {
        val moveNumber = position + 1
        val whiteMoveIndex = position * 2
        val blackMoveIndex = whiteMoveIndex + 1

        val whiteMove = moves.getOrNull(whiteMoveIndex) ?: ""
        val blackMove = moves.getOrNull(blackMoveIndex) ?: ""

        holder.bind(moveNumber, whiteMove, blackMove)
    }

    override fun getItemCount(): Int = (moves.size + 1) / 2

    class MoveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val moveNumberTextView: TextView = itemView.findViewById(R.id.tvMoveNumber)
        private val whiteMoveTextView: TextView = itemView.findViewById(R.id.tvWhiteMove)
        private val blackMoveTextView: TextView = itemView.findViewById(R.id.tvBlackMove)

        fun bind(moveNumber: Int, whiteMove: String, blackMove: String) {
            moveNumberTextView.text = "$moveNumber."
            whiteMoveTextView.text = whiteMove
            blackMoveTextView.text = blackMove
        }
    }
}

