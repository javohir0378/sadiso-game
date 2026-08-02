package com.sadiso.game

import android.graphics.Color
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.sadiso.game.tdlib.TdlibController

class LaunchActivity : AppCompatActivity(), FragmentHost {

    private lateinit var container: FrameLayout
    private val stack = mutableListOf<BaseFragment>()

    lateinit var tdlib: TdlibController
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_gradient)
        }
        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(container)

        tdlib = TdlibController(applicationContext)

        presentFragment(LoginFragment())
    }

    override fun presentFragment(fragment: BaseFragment) {
        fragment.attach(this, this)
        fragment.onFragmentCreate()
        val view = fragment.getOrCreateFragmentView(this)

        val old = stack.lastOrNull()
        stack.add(fragment)

        view.alpha = 0f
        view.translationX = dp(60).toFloat()
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(OvershootInterpolator(0.8f)).start()

        if (old != null) {
            old.onPause()
            val oldView = old.fragmentView
            oldView?.animate()?.alpha(0f)?.translationX(-dp(60).toFloat())?.setDuration(260)?.withEndAction {
                container.removeView(oldView)
            }?.start()
        }
        fragment.onResume()
    }

    override fun presentAsRoot(fragment: BaseFragment) {
        val old = ArrayList(stack)
        stack.clear()

        fragment.attach(this, this)
        fragment.onFragmentCreate()
        val view = fragment.getOrCreateFragmentView(this)
        stack.add(fragment)

        view.alpha = 0f
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.animate().alpha(1f).setDuration(320).start()

        old.forEach { f ->
            val v = f.fragmentView
            f.onPause()
            f.onFragmentDestroy()
            f.detach()
            if (v != null && v.parent === container) container.removeView(v)
        }
        fragment.onResume()
    }

    override fun popFragment() {
        if (stack.size <= 1) {
            finish()
            return
        }
        val removed = stack.removeAt(stack.lastIndex)
        removed.onPause()
        val removedView = removed.fragmentView

        val current = stack.last()
        val currentView = current.getOrCreateFragmentView(this)
        if (currentView.parent == null) {
            currentView.alpha = 0f
            currentView.translationX = -dp(60).toFloat()
            container.addView(currentView, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        currentView.animate().alpha(1f).translationX(0f).setDuration(260).start()

        removedView?.animate()?.alpha(0f)?.translationX(dp(60).toFloat())?.setDuration(220)?.withEndAction {
            container.removeView(removedView)
        }?.start()
        removed.onFragmentDestroy()
        removed.detach()

        current.onResume()
    }

    override fun onBackPressed() {
        val top = stack.lastOrNull()
        if (top?.onBackPressed() == true) return
        popFragment()
    }
}
