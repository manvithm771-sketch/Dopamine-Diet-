package com.reeltracker.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

/**
 * The floating counter: a brain that rots as today's reel count climbs.
 *
 *  - idle      the brain breathes
 *  - +1 reel   it squishes and sheds a drop (sweat, then slime once it turns green)
 *  - new stage lightning, a shockwave ring, a shake, and the brain swaps to the next sprite
 *  - stages 5+ smoke curls off the top, continuously
 *
 * Plain Views, not Compose: it lives in an accessibility overlay window. Every child is drawn
 * inside this view's bounds, because an overlay window clips anything that spills outside it.
 */
@SuppressLint("ViewConstructor")
internal class BrainCounterView(context: Context) : FrameLayout(context) {

    private val brain = ImageView(context)
    private val badge = TextView(context)
    private val fx = FrameLayout(context) // one-shot effects are added and removed here
    private var count: Int? = null
    private var stage = 0
    private val breathing: AnimatorSet

    init {
        clipChildren = false
        layoutParams = LayoutParams(dp(W), dp(H))

        brain.setImageResource(BrainRot.sprite(0))
        brain.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(brain, LayoutParams(dp(BRAIN_W), dp(BRAIN_H)).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(BRAIN_TOP)
        })
        // Squish from the bottom, like it is sitting on something.
        brain.post { brain.pivotX = brain.width / 2f; brain.pivotY = brain.height * 0.85f }

        addView(fx, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        badge.apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            minWidth = dp(30)
            setPadding(dp(8), dp(1), dp(8), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xE6141416.toInt())
                setStroke(dp(1), ACCENT)
            }
            text = "–"
        }
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(4)
        })

        breathing = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(brain, View.SCALE_X, 1f, 1.035f).breathe(),
                ObjectAnimator.ofFloat(brain, View.SCALE_Y, 1f, 1.05f).breathe(),
            )
        }

        contentDescription = context.getString(R.string.overlay_description, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        breathing.start()
    }

    override fun onDetachedFromWindow() {
        breathing.cancel()
        fx.removeAllViews()
        super.onDetachedFromWindow()
    }

    /**
     * Shows [newCount]. The first value after attaching is shown without fuss; after that an
     * increase plays the per-reel animation, and crossing a threshold plays the stage-up one.
     */
    fun setCount(newCount: Int) {
        val previous = count
        count = newCount
        badge.text = newCount.toString()
        contentDescription = context.getString(R.string.overlay_description, newCount)

        val newStage = BrainRot.stage(newCount)
        if (previous == null || newCount < previous) {
            // First value, or a new day: no celebration, just the right state.
            applyStage(newStage)
            return
        }
        if (newCount == previous) return

        if (newStage > stage) {
            stageUp(newStage)
        } else {
            reelCounted()
        }
    }

    /** A tap with no drag: a quick wobble, so the widget feels alive under the finger. */
    fun poke() {
        ObjectAnimator.ofFloat(brain, View.ROTATION, 0f, -10f, 8f, -5f, 3f, 0f).apply {
            duration = 420
            start()
        }
    }

    // ------------------------------------------------------------------ animations

    private fun reelCounted() {
        squish()
        popBadge()
        dropFrom(R.drawable.fx_drop_blue)
    }

    private fun stageUp(newStage: Int) {
        popBadge()
        shake()
        // Swap the sprite at the peak of the flash.
        brain.animate().alpha(0.35f).setDuration(120).withEndAction {
            applyStage(newStage)
            brain.animate().alpha(1f).setDuration(220).start()
        }.start()
        dropFrom(if (BrainRot.isSlimy(newStage)) R.drawable.fx_drip_green else R.drawable.fx_drip_blue, big = true)
    }

    private fun applyStage(newStage: Int) {
        stage = newStage
        brain.setImageResource(BrainRot.sprite(newStage))
        (badge.background as GradientDrawable).setStroke(dp(1), if (BrainRot.isSlimy(newStage)) SLIME else ACCENT)
    }

    private fun squish() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(brain, View.SCALE_Y, 1f, 0.84f, 1.08f, 1f),
                ObjectAnimator.ofFloat(brain, View.SCALE_X, 1f, 1.12f, 0.96f, 1f),
            )
            duration = 360
            start()
        }
    }

    private fun shake() {
        ObjectAnimator.ofFloat(brain, View.TRANSLATION_X, 0f, -dp(5f), dp(5f), -dp(4f), dp(3f), 0f).apply {
            duration = 380
            start()
        }
    }

    private fun popBadge() {
        badge.animate().cancel()
        badge.scaleX = 1.3f
        badge.scaleY = 1.3f
        badge.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
    }

    /** A drop falls from under the brain and fades; slime falls slower and stretches. */
    private fun dropFrom(sprite: Int, big: Boolean = false) {
        val w = dp(if (big) 30 else 10)
        val h = dp(if (big) 28 else 16)
        val drop = ImageView(context).apply { setImageResource(sprite) }
        val left = dp(W / 2) - w / 2 + if (big) 0 else Random.nextInt(-dp(16), dp(16))
        fx.addView(drop, LayoutParams(w, h).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = left
            topMargin = dp(BRAIN_TOP + BRAIN_H - 16)
        })
        drop.alpha = 0f
        drop.pivotY = 0f
        drop.animate()
            .alpha(1f)
            .translationY(dp(if (big) 8f else 18f))
            .scaleY(if (BrainRot.isSlimy(stage)) 1.35f else 1f)
            .setDuration(if (big) 700 else 520)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                drop.animate().alpha(0f).setDuration(220).withEndAction { fx.removeView(drop) }.start()
            }
            .start()
    }

    // ------------------------------------------------------------------ helpers

    private fun ObjectAnimator.breathe() = apply {
        duration = 1700
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * resources.displayMetrics.density

    companion object {
        /** Window size in dp: room above the brain for smoke and bolts, below for drops and the badge. */
        const val W = 92
        const val H = 104
        private const val BRAIN_W = 62
        private const val BRAIN_H = 52
        private const val BRAIN_TOP = 18

        private const val ACCENT = 0xFFFF5A4E.toInt()
        private const val SLIME = 0xFF8BC34A.toInt()
    }
}
