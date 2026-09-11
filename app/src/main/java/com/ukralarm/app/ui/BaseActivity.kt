package com.ukralarm.app.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ukralarm.app.util.LocaleHelper
import com.ukralarm.app.util.ThemeHelper

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        com.ukralarm.app.util.ThemeTransitionHelper.applyEnterTransition(this)
    }
}
