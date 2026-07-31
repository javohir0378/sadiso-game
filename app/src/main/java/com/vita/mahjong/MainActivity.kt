package com.vita.mahjong

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), MahjongBoardView.Listener {

    private lateinit var board: MahjongBoardView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_main)

        val contentRoot = findViewById<View>(R.id.contentRoot)
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        board = findViewById(R.id.board)
        statusText = findViewById(R.id.statusText)
        board.listener = this

        findViewById<ImageButton>(R.id.newGameButton).setOnClickListener { board.newGame() }
        findViewById<ImageButton>(R.id.shuffleButton).setOnClickListener { board.shuffleRemaining() }

        onMovesChanged(0)
    }

    override fun onMovesChanged(moves: Int) {
        statusText.text = "Urinishlar: $moves"
    }

    override fun onWin(moves: Int) {
        AlertDialog.Builder(this)
            .setTitle("G'alaba!")
            .setMessage("Barcha juftliklarni $moves ta urinishda topdingiz.")
            .setPositiveButton("Yangi o'yin") { _, _ -> board.newGame() }
            .setCancelable(false)
            .show()
    }

    override fun onLose() {
        AlertDialog.Builder(this)
            .setTitle("O'yin tugadi")
            .setMessage("Yuqoridagi joylar to'lib qoldi. Qayta urinib ko'ring!")
            .setPositiveButton("Qayta boshlash") { _, _ -> board.newGame() }
            .setCancelable(false)
            .show()
    }
}
