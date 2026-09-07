package com.diaar.notes

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow

/** Thin wrapper so callers can keep calling `.dismiss()` while getting a fade+scale-out for free. */
class AnchoredPopupHandle(private val popup: PopupWindow, private val contentView: View) {
    fun dismiss() {
        contentView.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(100)
            .withEndAction { popup.dismiss() }.start()
    }
}

/** Shows [contentView] as a borderless popup anchored just above [anchor], with a soft fade+scale-in. */
object AnchoredPopup {

    fun showAbove(context: Context, anchor: View, contentView: View): AnchoredPopupHandle {
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popup = PopupWindow(
            contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.elevation = 12f

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val screenWidth = context.resources.displayMetrics.widthPixels

        val popupWidth = contentView.measuredWidth
        val popupHeight = contentView.measuredHeight

        var x = anchorLoc[0] + anchor.width / 2 - popupWidth / 2
        x = x.coerceIn(16, (screenWidth - popupWidth - 16).coerceAtLeast(16))
        val y = anchorLoc[1] - popupHeight - (8 * context.resources.displayMetrics.density).toInt()

        contentView.pivotX = popupWidth / 2f
        contentView.pivotY = popupHeight.toFloat()
        contentView.alpha = 0f
        contentView.scaleX = 0.9f
        contentView.scaleY = 0.9f

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        contentView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()

        return AnchoredPopupHandle(popup, contentView)
    }
}
