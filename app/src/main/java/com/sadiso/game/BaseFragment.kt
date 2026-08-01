package com.sadiso.game

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * A lightweight screen unit hosted inside a single Activity - deliberately
 * NOT an androidx/platform Fragment (no FragmentManager, no separate
 * lifecycle machinery). The host Activity owns a simple back-stack of
 * these and swaps their views in and out directly.
 */
abstract class BaseFragment {

    var parentActivity: AppCompatActivity? = null
        private set

    var fragmentView: View? = null
        private set

    private var host: FragmentHost? = null

    abstract fun createView(context: Context): View

    open fun onFragmentCreate() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onFragmentDestroy() {
        fragmentView = null
    }

    /** Return true to consume the back press yourself instead of popping. */
    open fun onBackPressed(): Boolean = false

    fun getOrCreateFragmentView(context: Context): View {
        var view = fragmentView
        if (view == null) {
            view = createView(context)
            fragmentView = view
        }
        return view
    }

    fun attach(activity: AppCompatActivity, host: FragmentHost) {
        parentActivity = activity
        this.host = host
    }

    fun detach() {
        parentActivity = null
        host = null
    }

    fun presentFragment(fragment: BaseFragment) {
        host?.presentFragment(fragment)
    }

    fun presentAsRoot(fragment: BaseFragment) {
        host?.presentAsRoot(fragment)
    }

    fun finishFragment() {
        host?.popFragment()
    }
}

interface FragmentHost {
    fun presentFragment(fragment: BaseFragment)
    fun presentAsRoot(fragment: BaseFragment)
    fun popFragment()
}
