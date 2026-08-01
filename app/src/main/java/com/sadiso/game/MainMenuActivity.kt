package com.sadiso.game

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_gradient)
        }
        root.addView(LoginBackdropView(this), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val menuRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), 0)
        }
        root.addView(menuRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        menuRoot.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })

        menuRoot.addView(
            TextView(this).apply {
                text = getString(R.string.menu_subtitle)
                setTextColor(ContextCompat.getColor(context, R.color.label_muted))
                textSize = 13f
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(28)
            }
        )

        val mahjongCard = gameCard(R.drawable.ic_new_game, getString(R.string.menu_mahjong_title), getString(R.string.menu_mahjong_subtitle))
        menuRoot.addView(mahjongCard, cardParams())

        val comingSoonCard = gameCard(R.drawable.ic_shuffle, getString(R.string.menu_coming_soon_title), getString(R.string.menu_coming_soon_subtitle)).apply {
            alpha = 0.45f
        }
        menuRoot.addView(comingSoonCard, cardParams())

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(menuRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft + bars.left, v.paddingTop, v.paddingRight + bars.right, bars.bottom)
            insets
        }

        mahjongCard.setOnClickListener {
            startActivity(Intent(this, MahjongActivity::class.java))
        }
    }

    private fun cardParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(14)
    }

    private fun gameCard(iconRes: Int, title: String, subtitle: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }

        val icon = ImageView(this).apply {
            setBackgroundResource(R.drawable.circle_button_bg)
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(
            textColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) }
        )

        textColumn.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, R.color.text_title))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        })

        textColumn.addView(TextView(this).apply {
            text = subtitle
            setTextColor(ContextCompat.getColor(context, R.color.label_muted))
            textSize = 12f
        })

        return card
    }
}
