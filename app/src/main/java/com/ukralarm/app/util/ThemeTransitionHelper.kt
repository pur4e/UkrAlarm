package com.ukralarm.app.util

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView

object ThemeTransitionHelper {
    var snapshot: Bitmap? = null

    fun switchThemeWithTransition(
        activity: Activity,
        targetActivityClass: Class<*>,
        scrollY: Int = 0,
        applyChanges: () -> Unit
    ) {
        try {
            val decorView = activity.window.decorView as? ViewGroup
            val width = decorView?.width ?: 0
            val height = decorView?.height ?: 0
            if (width > 0 && height > 0 && decorView != null) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                decorView.draw(canvas)
                snapshot = bitmap
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        applyChanges()

        val intent = Intent(activity, targetActivityClass).apply {
            putExtra("EXTRA_SAVED_SCROLL_Y", scrollY)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        activity.finish()
        activity.overridePendingTransition(0, 0)
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
    }

    fun applyEnterTransition(activity: Activity) {
        val bmp = snapshot ?: return
        snapshot = null

        activity.window.decorView.post {
            try {
                val decorView = activity.window.decorView as? ViewGroup ?: return@post
                val overlay = ImageView(activity).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_XY
                    isClickable = true
                    isFocusable = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                decorView.addView(overlay)
                overlay.animate()
                    .alpha(0f)
                    .setDuration(350L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        try {
                            decorView.removeView(overlay)
                            if (!bmp.isRecycled) {
                                bmp.recycle()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .start()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}
