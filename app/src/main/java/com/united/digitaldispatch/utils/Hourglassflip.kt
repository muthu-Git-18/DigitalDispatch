package com.united.digitaldispatch.utils

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View

/**
 * Flips a View 180° on a loop: holds flat, snaps through the flip, holds
 * flat again, then flips back -- same hold/flip/hold/flip-back pacing on
 * every screen that shows a "working" state (splash screen sync, the
 * dashboard's floating sync button, and anywhere else this gets reused).
 *
 * Pulled out as a shared helper instead of copy-pasted per screen so the
 * timing only needs to be tuned in one place.
 */
object HourglassFlip {

    fun start(target: View, cycleMillis: Long = 2000): ObjectAnimator {
        val rotation = PropertyValuesHolder.ofKeyframe(
            View.ROTATION,
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.42f, 0f),
            Keyframe.ofFloat(0.98f, 180f),
            Keyframe.ofFloat(1f, 180f)
        )

        return ObjectAnimator.ofPropertyValuesHolder(target, rotation).apply {
            duration = cycleMillis
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    /** Stops the flip and resets rotation, so the icon isn't left mid-flip when hidden/swapped. */
    fun stop(animator: ObjectAnimator?, target: View) {
        animator?.cancel()
        target.rotation = 0f
    }
}