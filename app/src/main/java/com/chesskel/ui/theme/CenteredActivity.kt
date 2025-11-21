package com.chesskel.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.chesskel.R

open class CenteredActivity : AppCompatActivity() {

    /**
     * Use this instead of setContentView(...) in Activities that should be centered with padding.
     */
    protected fun setCenteredContentView(@LayoutRes layoutResID: Int) {
        val container = LayoutInflater.from(this).inflate(R.layout.centered_container, null) as FrameLayout
        val centeredContent = container.findViewById<FrameLayout>(R.id.centered_content)
        val contentView = LayoutInflater.from(this).inflate(layoutResID, centeredContent, false)
        centeredContent.addView(contentView)
        super.setContentView(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

