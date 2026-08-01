package com.sadiso.game

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_main_menu)

        val menuRoot = findViewById<View>(R.id.menuRoot)
        ViewCompat.setOnApplyWindowInsetsListener(menuRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft + bars.left, v.paddingTop, v.paddingRight + bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.mahjongCard).setOnClickListener {
            startActivity(Intent(this, MahjongActivity::class.java))
        }
    }
}
