package com.sportage.obd.ui.boards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Tracks a long-press drag inside one board page.
 *     * Item bounds are captured from layout coordinates in the grid's coordinate space.
     * [targetIndex] is the index whose cell currently sits under the dragged card's centre,
     * so dropping a card over another card reorders to that position, including spanning
     * widgets such as the map. Dropping in the gutters leaves the order unchanged.
 */
internal class WidgetDragState {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var targetIndex by mutableStateOf<Int?>(null)
        private set

    private val bounds = mutableStateMapOf<Int, Rect>()
    private var itemCount = 0

    val isDragging: Boolean get() = draggedIndex != null

    /** Records where an item was laid out; ignored mid-drag so offsets stay stable. */
    fun updateBounds(index: Int, positionInParent: Offset, size: Size) {
        if (isDragging) return
        bounds[index] = Rect(positionInParent, size)
    }

    fun start(index: Int, count: Int) {
        itemCount = count
        bounds.keys.filter { it >= count }.forEach(bounds::remove)
        draggedIndex = index
        targetIndex = index
        dragOffset = Offset.Zero
    }

    fun drag(delta: Offset) {
        if (!isDragging) return
        dragOffset += delta
        targetIndex = hitTest() ?: targetIndex
    }

    /** Ends the drag and returns the (from, to) indices when the card moved. */
    fun finish(): Pair<Int, Int>? {
        val from = draggedIndex ?: return null
        val to = targetIndex
        reset()
        return if (to == null || to == from || to !in 0 until itemCount) null else from to to
    }

    fun reset() {
        draggedIndex = null
        targetIndex = null
        dragOffset = Offset.Zero
    }

    private fun hitTest(): Int? {
        val from = draggedIndex ?: return null
        val start = bounds[from] ?: return null
        val center = start.center + dragOffset
        // Reorder only when the card is actually dropped over another card; a small
        // movement or a drop in the gutters keeps the current position.
        return bounds.entries
            .firstOrNull { (index, rect) -> index != from && index < itemCount && rect.contains(center) }
            ?.key
    }
}
