package com.united.digitaldispatch.Login

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.united.digitaldispatch.R

class SplashScreen : AppCompatActivity() {

    private lateinit var imgLogo: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash_screen)

        imgLogo = findViewById(R.id.imgLogo)

        startLogoAnimation()

        Handler(Looper.getMainLooper()).postDelayed({

            val intent = Intent(
                this@SplashScreen,
                LoginActivity::class.java
            )

            startActivity(intent)
            finish()

        }, 3000)
    }

    private fun startLogoAnimation() {

        // Gives perspective for 3D turning effect
        imgLogo.cameraDistance =
            12000 * resources.displayMetrics.density

        val animator = ObjectAnimator.ofFloat(
            imgLogo,
            "rotationY",

            0f, 8f, 0f, -8f, 0f   // Back to front
        )

        animator.duration = 2500

        animator.repeatCount = ObjectAnimator.INFINITE

        animator.repeatMode = ObjectAnimator.RESTART

        animator.interpolator =
            AccelerateDecelerateInterpolator()

        animator.start()
    }
}