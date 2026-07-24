package com.plutoforce.tapcopy

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast

/** AI Tools screen — visual shell; features are marked coming soon (Pro). */
class AiToolsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_tools)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        val soon = View.OnClickListener {
            Toast.makeText(this, "Coming soon in Pro", Toast.LENGTH_SHORT).show()
        }
        intArrayOf(
            R.id.toolRewrite, R.id.toolShorten, R.id.toolTranslate,
            R.id.toolHook, R.id.toolTone, R.id.toolPlagiarism, R.id.upgradeButton
        ).forEach { findViewById<View>(it).setOnClickListener(soon) }
    }
}
