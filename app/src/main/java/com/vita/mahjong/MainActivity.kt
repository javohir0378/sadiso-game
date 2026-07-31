package com.vita.mahjong

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Unicode Mahjong Tiles block (U+1F004, U+1F007..U+1F00D)
    private val tileSymbols = listOf(
        "🀄", "🀇", "🀈", "🀉",
        "🀊", "🀋", "🀌", "🀍"
    )

    private lateinit var grid: GridLayout
    private lateinit var statusText: TextView

    private val buttons = mutableListOf<Button>()
    private var values = mutableListOf<String>()
    private var revealed = mutableListOf<Boolean>()
    private var matched = mutableListOf<Boolean>()
    private var firstIndex: Int? = null
    private var moves = 0
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.newGameButton).setOnClickListener { startNewGame() }

        startNewGame()
    }

    private fun startNewGame() {
        grid.removeAllViews()
        buttons.clear()

        values = (tileSymbols + tileSymbols).shuffled().toMutableList()
        revealed = MutableList(values.size) { false }
        matched = MutableList(values.size) { false }
        firstIndex = null
        moves = 0
        busy = false
        updateStatus()

        for (i in values.indices) {
            val button = Button(this).apply {
                text = ""
                textSize = 22f
                isAllCaps = false
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(i % 4, 1f)
                    rowSpec = GridLayout.spec(i / 4, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener { onTileClicked(i) }
            }
            buttons.add(button)
            grid.addView(button)
        }
    }

    private fun onTileClicked(index: Int) {
        if (busy || revealed[index] || matched[index]) return

        revealed[index] = true
        buttons[index].text = values[index]

        val first = firstIndex
        if (first == null) {
            firstIndex = index
            return
        }

        moves++
        updateStatus()

        if (values[first] == values[index]) {
            matched[first] = true
            matched[index] = true
            buttons[first].isEnabled = false
            buttons[index].isEnabled = false
            firstIndex = null
            checkWin()
        } else {
            busy = true
            firstIndex = null
            Handler(Looper.getMainLooper()).postDelayed({
                revealed[first] = false
                revealed[index] = false
                buttons[first].text = ""
                buttons[index].text = ""
                busy = false
            }, 700)
        }
    }

    private fun checkWin() {
        if (matched.all { it }) {
            Toast.makeText(this, "G'alaba! $moves ta urinishda yutdingiz.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStatus() {
        statusText.text = "Vita Mahjong — urinishlar: $moves"
    }
}
