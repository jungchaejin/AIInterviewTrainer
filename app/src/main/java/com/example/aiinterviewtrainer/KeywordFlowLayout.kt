package com.example.aiinterviewtrainer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

class KeywordFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private val horizontalSpacing = dp(8)
    private val verticalSpacing = dp(8)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = (
            MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            ).coerceAtLeast(0)
        var lineWidth = 0
        var lineHeight = 0
        var contentHeight = 0
        var widestLine = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue

            child.measure(
                MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            val requiredWidth = if (lineWidth == 0) {
                child.measuredWidth
            } else {
                lineWidth + horizontalSpacing + child.measuredWidth
            }

            if (lineWidth > 0 && requiredWidth > availableWidth) {
                widestLine = max(widestLine, lineWidth)
                contentHeight += lineHeight + verticalSpacing
                lineWidth = child.measuredWidth
                lineHeight = child.measuredHeight
            } else {
                lineWidth = requiredWidth
                lineHeight = max(lineHeight, child.measuredHeight)
            }
        }

        widestLine = max(widestLine, lineWidth)
        if (lineWidth > 0) contentHeight += lineHeight

        setMeasuredDimension(
            resolveSize(widestLine + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val availableWidth = right - left - paddingLeft - paddingRight
        var currentLeft = paddingLeft
        var currentTop = paddingTop
        var lineHeight = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue

            val spacing = if (currentLeft == paddingLeft) 0 else horizontalSpacing
            if (currentLeft + spacing + child.measuredWidth > paddingLeft + availableWidth &&
                currentLeft > paddingLeft
            ) {
                currentLeft = paddingLeft
                currentTop += lineHeight + verticalSpacing
                lineHeight = 0
            } else {
                currentLeft += spacing
            }

            child.layout(
                currentLeft,
                currentTop,
                currentLeft + child.measuredWidth,
                currentTop + child.measuredHeight
            )
            currentLeft += child.measuredWidth
            lineHeight = max(lineHeight, child.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
