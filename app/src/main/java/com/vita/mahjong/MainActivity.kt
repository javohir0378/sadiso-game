package com.vita.mahjong

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), MahjongBoardView.Listener {

    private lateinit var board: MahjongBoardView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        statusText = findViewById(R.id.statusText)
        board.listener = this

        findViewById<Button>(R.id.newGameButton).setOnClickListener { board.newGame() }
        findViewById<Button>(R.id.shuffleButton).setOnClickListener { board.shuffleRemaining() }

        onMovesChanged(0)
    }

    override fun onMovesChanged(moves: Int) {
        statusText.text = "Vita Mahjong — urinishlar: $moves"
    }

    override fun onWin(moves: Int) {
        AlertDialog.Builder(this)
            .setTitle("G'alaba!")
            .setMessage("Barcha juftliklarni $moves ta urinishda topdingiz.")
            .setPositiveButton("Yangi o'yin") { _, _ -> board.newGame() }
            .setCancelable(false)
            .show()
    }

    override fun onNoMovesLeft() {
        Toast.makeText(this, "Yurish qolmadi — 'Aralashtirish' tugmasini bosing", Toast.LENGTH_LONG).show()
    }
}
