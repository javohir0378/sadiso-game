package com.sadiso.game

import android.app.AlertDialog
import android.content.Context
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

class OnetFragment : BaseFragment(), OnetBoardView.Listener {

    private lateinit var ctx: Context
    private lateinit var board: OnetBoardView
    private lateinit var avatarView: ImageView
    private lateinit var nameText: TextView

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
            setPadding(ctx.dp(14), ctx.dp(16), ctx.dp(14), ctx.dp(6))
        }
        contentRoot.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val backBtn = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_back)
            contentDescription = ctx.getString(R.string.main_menu)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(11), ctx.dp(11), ctx.dp(11), ctx.dp(11))
        }
        topBar.addView(backBtn, LinearLayout.LayoutParams(ctx.dp(46), ctx.dp(46)))

        val namePill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.name_pill_bg)
            setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(18), ctx.dp(6))
        }
        topBar.addView(
            namePill,
            LinearLayout.LayoutParams(0, ctx.dp(56), 1f).apply {
                marginStart = ctx.dp(10)
                marginEnd = ctx.dp(10)
            }
        )

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
        avatarFrame.addView(avatarView, FrameLayout.LayoutParams(ctx.dp(42), ctx.dp(42)))
        namePill.addView(avatarFrame, LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42)))

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
            text = ctx.getString(R.string.menu_onet_title)
            setTextColor(ContextCompat.getColor(context, R.color.label_muted))
            textSize = 12f
        })
        namePill.addView(
            nameColumn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = ctx.dp(10)
            }
        )

        val settingsBtn = ImageButton(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(R.drawable.ic_settings)
            contentDescription = ctx.getString(R.string.action_settings)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ctx.dp(11), ctx.dp(11), ctx.dp(11), ctx.dp(11))
        }
        topBar.addView(settingsBtn, LinearLayout.LayoutParams(ctx.dp(46), ctx.dp(46)))

        board = OnetBoardView(ctx)
        contentRoot.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(12), 0, ctx.dp(24))
        }
        contentRoot.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val (shuffleCol, shuffleBtn) = iconColumn(R.drawable.ic_shuffle, ctx.getString(R.string.shuffle), R.drawable.circle_button_purple, 58)
        val (hintCol, hintBtn) = iconColumn(R.drawable.ic_hint, ctx.getString(R.string.action_hint), R.drawable.circle_button_gold, 58)
        bottomBar.addView(shuffleCol)
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(ctx.dp(32), 1))
        bottomBar.addView(hintCol)

        board.listener = this
        shuffleBtn.setOnClickListener { board.shuffleRemaining() }
        hintBtn.setOnClickListener {
            if (!board.hint()) {
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.hint_none), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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
            val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return@fetchMe
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
        // Not shown in the toolbar, matching the Mahjong screen's style.
    }

    override fun onWin(moves: Int) {
        AlertDialog.Builder(ctx)
            .setTitle("G'alaba!")
            .setMessage("Barcha juftliklarni $moves ta urinishda topdingiz.")
            .setPositiveButton("Yangi o'yin") { _, _ -> board.newGame() }
            .setCancelable(false)
            .show()
    }
}
