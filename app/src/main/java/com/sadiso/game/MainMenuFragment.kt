package com.sadiso.game

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainMenuFragment : BaseFragment() {

    private lateinit var ctx: Context

    override fun createView(context: Context): View {
        ctx = context

        val root = FrameLayout(ctx)
        root.addView(LoginBackdropView(ctx), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val menuRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ctx.dp(24), ctx.dp(48), ctx.dp(24), 0)
        }
        root.addView(menuRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        menuRoot.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.app_name)
            setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })

        menuRoot.addView(
            TextView(ctx).apply {
                text = ctx.getString(R.string.menu_subtitle)
                setTextColor(ContextCompat.getColor(context, R.color.label_muted))
                textSize = 13f
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ctx.dp(4)
                bottomMargin = ctx.dp(28)
            }
        )

        val mahjongCard = gameCard(R.drawable.ic_new_game, ctx.getString(R.string.menu_mahjong_title), ctx.getString(R.string.menu_mahjong_subtitle))
        menuRoot.addView(mahjongCard, cardParams())

        val onetCard = gameCard(R.drawable.ic_onet, ctx.getString(R.string.menu_onet_title), ctx.getString(R.string.menu_onet_subtitle))
        menuRoot.addView(onetCard, cardParams())

        val comingSoonCard = gameCard(R.drawable.ic_shuffle, ctx.getString(R.string.menu_coming_soon_title), ctx.getString(R.string.menu_coming_soon_subtitle)).apply {
            alpha = 0.45f
        }
        menuRoot.addView(comingSoonCard, cardParams())

        mahjongCard.setOnClickListener {
            presentFragment(MahjongFragment())
        }
        onetCard.setOnClickListener {
            presentFragment(OnetFragment())
        }

        return root
    }

    private fun cardParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = ctx.dp(14)
    }

    private fun gameCard(iconRes: Int, title: String, subtitle: String): LinearLayout {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            val pad = ctx.dp(20)
            setPadding(pad, pad, pad, pad)
        }

        val icon = ImageView(ctx).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = ctx.dp(12)
            setPadding(pad, pad, pad, pad)
        }
        card.addView(icon, LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48)))

        val textColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(
            textColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(16) }
        )

        textColumn.addView(TextView(ctx).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, R.color.text_title))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        })

        textColumn.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(ContextCompat.getColor(context, R.color.label_muted))
            textSize = 12f
        })

        return card
    }
}
