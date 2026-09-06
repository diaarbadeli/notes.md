package com.diaar.notes

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

enum class ToolbarButton(val iconRes: Int, val draggable: Boolean = true, val swipeUp: Boolean = false) {
    UNDO(R.drawable.ic_undo),
    NEW(R.drawable.ic_new, draggable = false, swipeUp = true),
    LOAD(R.drawable.ic_send, swipeUp = true),
    CHECKBOX(R.drawable.ic_checkbox),
    DATETIME(R.drawable.ic_datetime, swipeUp = true),
    ITALIC(R.drawable.ic_italic),
    BOLD(R.drawable.ic_bold),
    CODEBLOCK(R.drawable.ic_codeblock)
}

class ToolbarAdapter(
    initialOrder: List<ToolbarButton>,
    private val onClick: (ToolbarButton) -> Unit,
    private val onLongPressNew: () -> Unit,
    private val onSwipeUp: (ToolbarButton, android.view.View) -> Unit,
    private val onOrderChanged: (List<ToolbarButton>) -> Unit
) : RecyclerView.Adapter<ToolbarAdapter.ViewHolder>() {

    val items: MutableList<ToolbarButton> = initialOrder.toMutableList()

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
    }

    val touchHelper: ItemTouchHelper by lazy { ItemTouchHelper(DragCallback()) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.toolbar_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val button = items[position]
        holder.icon.setImageResource(button.iconRes)

        val gestureDetector = GestureDetector(holder.itemView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (button.swipeUp && e1 != null) {
                    val dy = e2.y - e1.y
                    if (dy < -40 && abs(velocityY) > abs(velocityX)) {
                        onSwipeUp(button, holder.itemView)
                        return true
                    }
                }
                return false
            }
        })

        holder.itemView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // never consume: let click / long-click keep working normally
        }

        holder.itemView.setOnClickListener { onClick(button) }

        if (!button.draggable) {
            holder.itemView.setOnLongClickListener { onLongPressNew(); true }
        } else {
            holder.itemView.setOnLongClickListener(null) // ItemTouchHelper drives long-press-to-drag
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
            if (!items[to].draggable) return false
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            persistOrder()
        }
    }
}
