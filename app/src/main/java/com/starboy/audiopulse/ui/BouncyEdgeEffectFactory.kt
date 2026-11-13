package com.starboy.audiopulse.ui

import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

class BouncyEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        return object : EdgeEffect(view.context) {

            // Holds active spring animation
            private var translationAnim: SpringAnimation? = null

            override fun onPull(deltaDistance: Float, displacement: Float) {
                super.onPull(deltaDistance, displacement)
                handlePull(deltaDistance)
            }

            override fun onPull(deltaDistance: Float) {
                super.onPull(deltaDistance)
                handlePull(deltaDistance)
            }

            private fun handlePull(deltaDistance: Float) {
                // Determine direction: pull down moves view down (+), pull up moves view up (-)
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                // Resistance factor (0.35f gives that weighted rubber band feeling)
                val translationYDelta = sign * view.height * deltaDistance * 0.35f

                view.translationY += translationYDelta
                translationAnim?.cancel()
            }

            override fun onRelease() {
                super.onRelease()
                // If the view was dragged away, spring it back to 0
                if (view.translationY != 0f) {
                    createSpring().start()
                }
            }

            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity)
                // Fling momentum creates the blank pure black gap
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                val translationVelocity = sign * velocity * 0.5f

                createSpring()
                    .setStartVelocity(translationVelocity)
                    .start()
            }

            override fun draw(canvas: Canvas?): Boolean {
                // Suppress Android's default stretch/glow rendering
                return false
            }

            private fun createSpring(): SpringAnimation {
                return SpringAnimation(view, SpringAnimation.TRANSLATION_Y, 0f).apply {
                    spring = SpringForce(0f).apply {
                        // Bouncy and responsive
                        stiffness = SpringForce.STIFFNESS_LOW
                        dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    }
                }.also { translationAnim = it }
            }
        }
    }
}