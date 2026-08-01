package com.sadiso.game

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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

class MahjongFragment : BaseFragment(), MahjongBoardView.Listener {

    private lateinit var ctx: Context
    private lateinit var board: MahjongBoardView
    private lateinit var statusText: TextView

    override fun createView(context: Context): View {
        ctx = context

        val root = FrameLayout(ctx)

        val contentRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(contentRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val topBar = RelativeLayout(ctx).apply {
            setPadding(ctx.dp(18), ctx.dp(14), ctx.dp(18), ctx.dp(6))
        }
        contentRoot.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val newGameButton = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_new_game)
            contentDescription = ctx.getString(R.string.new_game)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(13), ctx.dp(13), ctx.dp(13), ctx.dp(13))
        }
        topBar.addView(
            newGameButton,
            RelativeLayout.LayoutParams(ctx.dp(52), ctx.dp(52)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        )

        statusText = TextView(ctx).apply {
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

        val menuButton = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_home)
            contentDescription = ctx.getString(R.string.main_menu)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(13), ctx.dp(13), ctx.dp(13), ctx.dp(13))
        }
        topBar.addView(
            menuButton,
            RelativeLayout.LayoutParams(ctx.dp(52), ctx.dp(52)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        )

        board = MahjongBoardView(ctx)
        contentRoot.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(10), 0, ctx.dp(22))
        }
        contentRoot.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val shuffleButton = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_shuffle)
            contentDescription = ctx.getString(R.string.shuffle)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(15), ctx.dp(15), ctx.dp(15), ctx.dp(15))
        }
        bottomBar.addView(shuffleButton, LinearLayout.LayoutParams(ctx.dp(56), ctx.dp(56)))

        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(1)))

        val undoButton = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_undo)
            contentDescription = ctx.getString(R.string.undo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(15), ctx.dp(15), ctx.dp(15), ctx.dp(15))
        }
        bottomBar.addView(undoButton, LinearLayout.LayoutParams(ctx.dp(56), ctx.dp(56)))

        board.listener = this
        newGameButton.setOnClickListener { board.newGame() }
        shuffleButton.setOnClickListener { board.shuffleRemaining() }
        undoButton.setOnClickListener { board.undo() }
        menuButton.setOnClickListener { finishFragment() }

        onMovesChanged(0)
        return root
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
        AlertDialog.Builder(ctx)
            .setTitle("G'alaba!")
            .setMessage("Barcha juftliklarni $moves ta urinishda topdingiz.")
            .setPositiveButton("Yangi o'yin") { _, _ -> board.newGame() }
            .setCancelable(false)
            .show()
    }

    override fun onLose() {
        AlertDialog.Builder(ctx)
            .setTitle("O'yin tugadi")
            .setMessage("Yuqoridagi joylar to'lib qoldi. Qayta urinib ko'ring!")
            .setPositiveButton("Qayta boshlash") { _, _ -> board.newGame() }
            .setCancelable(false)
            .show()
    }
}
