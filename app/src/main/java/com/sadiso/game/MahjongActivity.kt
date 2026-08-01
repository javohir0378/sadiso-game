package com.sadiso.game

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MahjongActivity : AppCompatActivity(), MahjongBoardView.Listener {

    private lateinit var board: MahjongBoardView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_gradient)
        }

        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(contentRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val topBar = RelativeLayout(this).apply {
            setPadding(dp(18), dp(14), dp(18), dp(6))
        }
        contentRoot.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val newGameButton = ImageButton(this).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_new_game)
            contentDescription = getString(R.string.new_game)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13), dp(13), dp(13), dp(13))
        }
        topBar.addView(
            newGameButton,
            RelativeLayout.LayoutParams(dp(52), dp(52)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        )

        statusText = TextView(this).apply {
            text = "0"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }
        topBar.addView(
            statusText,
            RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        )

        val menuButton = ImageButton(this).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_home)
            contentDescription = getString(R.string.main_menu)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13), dp(13), dp(13), dp(13))
        }
        topBar.addView(
            menuButton,
            RelativeLayout.LayoutParams(dp(52), dp(52)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        )

        board = MahjongBoardView(this)
        contentRoot.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(22))
        }
        contentRoot.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val shuffleButton = ImageButton(this).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_shuffle)
            contentDescription = getString(R.string.shuffle)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        bottomBar.addView(shuffleButton, LinearLayout.LayoutParams(dp(56), dp(56)))

        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(44), dp(1)))

        val undoButton = ImageButton(this).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_undo)
            contentDescription = getString(R.string.undo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        bottomBar.addView(undoButton, LinearLayout.LayoutParams(dp(56), dp(56)))

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        board.listener = this
        newGameButton.setOnClickListener { board.newGame() }
        shuffleButton.setOnClickListener { board.shuffleRemaining() }
        undoButton.setOnClickListener { board.undo() }
        menuButton.setOnClickListener {
            startActivity(Intent(this, MainMenuActivity::class.java))
            finish()
        }

        onMovesChanged(0)
    }

    override fun onMovesChanged(moves: Int) {
        val label = "Urinish: "
        val text = "$label$moves"
        val spannable = SpannableString(text)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#FFC107")),
            0, label.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#F5EAD3")),
            label.length, text.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        statusText.text = spannable
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
