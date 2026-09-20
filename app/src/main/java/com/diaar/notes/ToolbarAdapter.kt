package com.diaar.notes

import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sqrt

enum class ToolbarButton(val iconRes: Int, val draggable: Boolean = true) {
    UNDO(R.drawable.ic_undo, draggable = false),
    NEW(R.drawable.ic_new, draggable = false),
    LOAD(R.drawable.ic_send, draggable = false),
    CHECKBOX(R.drawable.ic_checkbox),
    DATETIME(R.drawable.ic_datetime, draggable = false),
    ITALIC(R.drawable.ic_italic),
    BOLD(R.drawable.ic_bold),
    CODEBLOCK(R.drawable.ic_codeblock)
}

private const val HOLD_MS = 300L
private const val TAP_VIBE_MS = 1L
private const val HOLD_VIBE_MS = 20L
private const val MOVE_SLOP = 20f
private const val SWIPE_UP_THRESHOLD = 40f

class ToolbarAdapter(
    initialOrder: List<ToolbarButton>,
    private val onClick: (ToolbarButton, View) -> Unit,
    private val onLongPress: (ToolbarButton, View) -> Unit,
    private val onSwipeUp: (ToolbarButton, View) -> Unit,
    private val onOrderChanged: (List<ToolbarButton>) -> Unit
) : RecyclerView.Adapter<ToolbarAdapter.ViewHolder>() {

    val items: MutableList<ToolbarButton> = initialOrder.toMutableList()

    /** Icon tint — vector drawables are compiled with a fixed color, so runtime palette
     * changes (the color-directive feature) re-tint them here instead. Set + call
     * notifyDataSetChanged() to apply. */
    var iconColor: Int? = null

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
    }

    val touchHelper: ItemTouchHelper by lazy { ItemTouchHelper(DragCallback()) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.toolbar_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount() = items.size

    private fun vibrate(view: View, ms: Long) {
        try {
            val vibrator = view.context.getSystemService(Vibrator::class.java) ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* haptics are a nicety, never worth crashing over */ }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val button = items[position]
        holder.icon.setImageResource(button.iconRes)
        iconColor?.let { holder.icon.setColorFilter(it) } ?: holder.icon.clearColorFilter()

        if (button.draggable) {
            holder.itemView.setOnTouchListener(null)
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.setOnClickListener {
                vibrate(holder.itemView, TAP_VIBE_MS)
                onClick(button, holder.itemView)
            }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)

            var downX = 0f
            var downY = 0f
            var holdFired = false
            var actionConsumed = false
            val holdRunnable = Runnable {
                holdFired = true
                actionConsumed = true
                vibrate(holder.itemView, HOLD_VIBE_MS)
                onLongPress(button, holder.itemView)
            }

            holder.itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x; downY = event.y
                        holdFired = false; actionConsumed = false
                        v.postDelayed(holdRunnable, HOLD_MS)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!actionConsumed && !holdFired) {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (dy < -SWIPE_UP_THRESHOLD && abs(dy) > abs(dx)) {
                                v.removeCallbacks(holdRunnable)
                                actionConsumed = true
                                onSwipeUp(button, v)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.removeCallbacks(holdRunnable)
                        if (!actionConsumed && !holdFired) {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (sqrt(dx * dx + dy * dy) < MOVE_SLOP) {
                                vibrate(v, TAP_VIBE_MS)
                                onClick(button, v)
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> v.removeCallbacks(holdRunnable)
                }
                true
            }
        }
    }

    private fun persistOrder() = onOrderChanged(items.toList())

    private inner class DragCallback : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled() = true
        override fun isItemViewSwipeEnabled() = false

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return 0
            val button = items[position]
            if (!button.draggable) return makeMovementFlags(0, 0)
            val dragFlags = ItemTouchHelper.START or ItemTouchHelper.END
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                vibrate(viewHolder.itemView, HOLD_VIBE_MS)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            persistOrder()
        }
    }
}
