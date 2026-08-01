package com.plutoforce.tapcopy

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View

/**
 * Base for every TapCopy screen: applies the dark/light choice, the app text
 * size and the accent colour.
 *
 * A screen built before the user changed one of those would otherwise sit there
 * in the old look until it was reopened, so each one checks on the way back and
 * rebuilds itself if the settings moved underneath it.
 */
open class ThemedActivity : Activity() {

    private var stamp: String = ""

    /** Screens drawn over other apps stay dark whatever the setting says. */
    protected open val forceDark: Boolean get() = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase, forceDark))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stamp = AppTheme.stamp(this)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        AppTheme.applyAccent(window.decorView, this)
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        AppTheme.applyAccent(window.decorView, this)
    }

    override fun onResume() {
        super.onResume()
        if (stamp.isNotEmpty() && stamp != AppTheme.stamp(this)) recreate()
    }
}
