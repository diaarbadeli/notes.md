package com.diaar.notes

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow

/** Shows [contentView] as a borderless popup anchored just above [anchor], centered on it. */
object AnchoredPopup {

    fun showAbove(context: Context, anchor: View, contentView: View): PopupWindow {
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

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        return popup
    }
}
