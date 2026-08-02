package com.sadiso.game

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MahjongFragment : BaseFragment(), MahjongBoardView.Listener {

    private lateinit var ctx: Context
    private lateinit var board: MahjongBoardView
    private lateinit var statusNumberText: TextView
    private lateinit var avatarView: ImageView

    override fun createView(context: Context): View {
        ctx = context

        val root = FrameLayout(ctx)

        val contentRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(contentRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(12), ctx.dp(18), ctx.dp(12), ctx.dp(8))
        }
        contentRoot.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // left: refresh
        val leftGroup = FrameLayout(ctx)
        topBar.addView(leftGroup, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val (refreshCol, refreshBtn) = iconColumn(R.drawable.ic_new_game, ctx.getString(R.string.action_refresh), R.drawable.circle_button_bg, 46)
        leftGroup.addView(refreshCol, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))

        // center: moves plaque
        val plaque = buildPlaque()
        topBar.addView(plaque, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // right: home + settings
        val rightGroup = FrameLayout(ctx)
        topBar.addView(rightGroup, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val rightRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val (homeCol, homeBtn) = iconColumn(R.drawable.ic_home, ctx.getString(R.string.action_home), R.drawable.circle_button_bg, 46)
        val (settingsCol, settingsBtn) = iconColumn(R.drawable.ic_settings, ctx.getString(R.string.action_settings), R.drawable.circle_button_bg, 46)
        rightRow.addView(homeCol)
        rightRow.addView(View(ctx), LinearLayout.LayoutParams(ctx.dp(10), 1))
        rightRow.addView(settingsCol)
        rightGroup.addView(rightRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))

        board = MahjongBoardView(ctx)
        contentRoot.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(12), 0, ctx.dp(24))
        }
        contentRoot.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val (shuffleCol, shuffleBtn) = iconColumn(R.drawable.ic_shuffle, ctx.getString(R.string.shuffle), R.drawable.circle_button_purple, 58)
        val (hintCol, hintBtn) = iconColumn(R.drawable.ic_hint, ctx.getString(R.string.action_hint), R.drawable.circle_button_gold, 58)
        val (undoCol, undoBtn) = iconColumn(R.drawable.ic_undo, ctx.getString(R.string.undo), R.drawable.circle_button_blue, 58)
        bottomBar.addView(shuffleCol)
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(ctx.dp(28), 1))
        bottomBar.addView(hintCol)
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(ctx.dp(28), 1))
        bottomBar.addView(undoCol)

        board.listener = this
        refreshBtn.setOnClickListener { board.newGame() }
        shuffleBtn.setOnClickListener { board.shuffleRemaining() }
        hintBtn.setOnClickListener {
            if (!board.hint()) {
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.hint_none), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        undoBtn.setOnClickListener { board.undo() }
        homeBtn.setOnClickListener { finishFragment() }
        settingsBtn.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.settings_soon_title))
                .setMessage(ctx.getString(R.string.settings_soon_message))
                .setPositiveButton("OK", null)
                .show()
        }

        onMovesChanged(0)
        loadAvatar()
        return root
    }

    private fun loadAvatar() {
        val activity = parentActivity as? LaunchActivity ?: return
        activity.tdlib.fetchProfilePhoto { path ->
            if (path == null) return@fetchProfilePhoto
            val bitmap = BitmapFactory.decodeFile(path) ?: return@fetchProfilePhoto
            avatarView.setImageBitmap(bitmap)
        }
    }

    private fun iconColumn(iconRes: Int, label: String, bgRes: Int, sizeDp: Int): Pair<LinearLayout, ImageButton> {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val btn = ImageButton(ctx).apply {
            setBackgroundResource(bgRes)
            setImageResource(iconRes)
            contentDescription = label
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = ctx.dp((sizeDp * 0.28f).toInt())
            setPadding(pad, pad, pad, pad)
        }
        col.addView(btn, LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp)))
        col.addView(
            TextView(ctx).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.label_muted))
                textSize = 10f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ctx.dp(4)
            }
        )
        return col to btn
    }

    private fun buildPlaque(): LinearLayout {
        val plaque = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.plaque_bg)
            setPadding(ctx.dp(18), ctx.dp(10), ctx.dp(18), ctx.dp(10))
        }

        val avatarFrame = FrameLayout(ctx).apply {
            setBackgroundResource(R.drawable.avatar_glow)
        }
        avatarView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_person)
            setBackgroundColor(ContextCompat.getColor(context, R.color.circle_button_fill_dark))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        avatarFrame.addView(avatarView, FrameLayout.LayoutParams(ctx.dp(48), ctx.dp(48), Gravity.CENTER))
        plaque.addView(avatarFrame, LinearLayout.LayoutParams(ctx.dp(58), ctx.dp(58)))

        plaque.addView(
            TextView(ctx).apply {
                text = ctx.getString(R.string.moves_label)
                setTextColor(ContextCompat.getColor(context, R.color.label_muted))
                textSize = 10f
                letterSpacing = 0.15f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ctx.dp(6)
            }
        )
        statusNumberText = TextView(ctx).apply {
            text = "0"
            setTextColor(ContextCompat.getColor(context, R.color.text_title))
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        plaque.addView(
            statusNumberText,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ctx.dp(2)
            }
        )
        return plaque
    }

    override fun onMovesChanged(moves: Int) {
        statusNumberText.text = moves.toString()
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
