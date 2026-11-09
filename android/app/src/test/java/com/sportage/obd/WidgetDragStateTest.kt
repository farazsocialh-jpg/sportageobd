package com.sportage.obd

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sportage.obd.ui.boards.WidgetDragState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDragStateTest {

    private fun stateWithThreeCards(): WidgetDragState = WidgetDragState().apply {
        updateBounds(0, Offset.Zero, Size(100f, 100f))
        updateBounds(1, Offset(110f, 0f), Size(100f, 100f))
        updateBounds(2, Offset(220f, 0f), Size(100f, 100f))
    }

    @Test
    fun draggingRightTargetsTheNeighbour() {
        val state = stateWithThreeCards()
        state.start(0, 3)
        state.drag(Offset(120f, 0f))

        assertEquals(1, state.targetIndex)
        assertEquals(0 to 1, state.finish())
        assertFalse(state.isDragging)
    }

    @Test
    fun draggingAcrossTwoCardsTargetsTheSecond() {
        val state = stateWithThreeCards()
        state.start(0, 3)
        state.drag(Offset(230f, 0f))

        assertEquals(2, state.targetIndex)
        assertEquals(0 to 2, state.finish())
    }

    @Test
    fun draggingLeftMovesTowardsTheStart() {
        val state = stateWithThreeCards()
        state.start(2, 3)
        state.drag(Offset(-120f, 0f))

        assertEquals(1, state.targetIndex)
        assertEquals(2 to 1, state.finish())
    }

    @Test
    fun droppingWithoutMovingDoesNotReorder() {
        val state = stateWithThreeCards()
        state.start(1, 3)
        state.drag(Offset(4f, 3f))

        assertEquals(1, state.targetIndex)
        assertNull(state.finish())
    }

    @Test
    fun droppingInAGapDoesNotReorder() {
        val state = WidgetDragState().apply {
            updateBounds(0, Offset.Zero, Size(100f, 100f))
            updateBounds(1, Offset(0f, 200f), Size(100f, 100f))
        }
        state.start(0, 2)
        state.drag(Offset(0f, 110f))

        assertEquals(0, state.targetIndex)
        assertNull(state.finish())
    }

    @Test
    fun staleBoundsArePrunedWhenTheBoardShrinks() {
        val state = stateWithThreeCards()
        state.start(0, 2)
        state.drag(Offset(230f, 0f))

        // Index 2 was pruned with the board, so the old card-2 area is not a drop target.
        assertEquals(0, state.targetIndex)
        assertNull(state.finish())
    }
}
