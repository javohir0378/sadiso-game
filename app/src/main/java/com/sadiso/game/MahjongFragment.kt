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
import com.sadiso.game.tdlib.TdlibController

class MahjongFragment : BaseFragment(), MahjongBoardView.Listener {

    private lateinit var ctx: Context
    private lateinit var board: MahjongBoardView
    private lateinit var avatarView: ImageView
    private lateinit var nameText: TextView

    override fun createView(context: Context): View {
        ctx = context

        val root = FrameLayout(ctx)

        val contentRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(contentRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Telegram-style toolbar: back button, avatar + name, settings icon.
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.toolbar_bg))
            setPadding(ctx.dp(4), ctx.dp(16), ctx.dp(14), ctx.dp(10))
        }
        contentRoot.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val backBtn = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.borderless_icon_bg)
            setImageResource(R.drawable.ic_back)
            contentDescription = ctx.getString(R.string.main_menu)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
        }
        topBar.addView(backBtn, LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)))

        val avatarFrame = FrameLayout(ctx)
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
        avatarFrame.addView(avatarView, FrameLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))
        topBar.addView(
            avatarFrame,
            LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)).apply { marginStart = ctx.dp(4) }
        )

        val nameColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        nameText = TextView(ctx).apply {
            text = ""
            setTextColor(ContextCompat.getColor(context, R.color.text_title))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        nameColumn.addView(nameText)
        nameColumn.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.menu_mahjong_title)
            setTextColor(ContextCompat.getColor(context, R.color.label_muted))
            textSize = 12f
        })
        topBar.addView(
            nameColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dp(12)
            }
        )

        val settingsBtn = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.borderless_icon_bg)
            setImageResource(R.drawable.ic_settings)
            contentDescription = ctx.getString(R.string.action_settings)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
        }
        topBar.addView(settingsBtn, LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)))

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
        shuffleBtn.setOnClickListener { board.shuffleRemaining() }
        hintBtn.setOnClickListener {
            if (!board.hint()) {
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.hint_none), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        undoBtn.setOnClickListener { board.undo() }
        backBtn.setOnClickListener { finishFragment() }
        settingsBtn.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.settings_soon_title))
                .setMessage(ctx.getString(R.string.settings_soon_message))
                .setPositiveButton("OK", null)
                .show()
        }

        loadMe()
        return root
    }

    private fun loadMe() {
        val activity = parentActivity as? LaunchActivity ?: return
        activity.tdlib.fetchMe { me: TdlibController.Me ->
            nameText.text = me.name
            val path = me.photoPath ?: return@fetchMe
            val bitmap = BitmapFactory.decodeFile(path) ?: return@fetchMe
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

    override fun onMovesChanged(moves: Int) {
        // Moves count is no longer shown in the toolbar, but still tracked
        // internally by the board for the win-dialog message below.
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
