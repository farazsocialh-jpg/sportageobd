package com.sportage.obd.ui.boards

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.LocalCockpitTokens
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import kotlinx.coroutines.launch

/** A board+configs snapshot used by undo and reset. */
private data class BoardSnapshot(val layout: BoardLayout, val configs: BoardWidgetConfigs)

/**
 * The customizable boards screen: horizontally swipeable pages that the driver
 * fills with widgets of their choice. Layouts and per-widget configuration persist
 * across restarts. Edit mode exposes configure, duplicate, reorder and remove
 * controls plus layout export/import, undo and reset.
 */
@Composable
fun BoardScreen(
    data: BoardData,
    store: BoardStore,
    modifier: Modifier = Modifier,
    configStore: BoardConfigStore? = null
) {
    val themePresets = LocalCockpitTokens.current.spec.layout.widgetPresets
    var layout by remember {
        mutableStateOf(
            if (!store.hasSaved()) BoardLayout.fromPreset(themePresets) ?: store.load() else store.load()
        )
    }
    var configs by remember {
        mutableStateOf(
            (configStore?.load() ?: BoardWidgetConfigs.EMPTY).alignedTo(layout)
        )
    }
    var editing by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var settingsTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<BoardSnapshot>>(emptyList()) }
    var dragActive by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { layout.boards.size })
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun snapshot() {
        history = (history + BoardSnapshot(layout, configs)).takeLast(20)
    }

    fun persist(next: BoardLayout, nextConfigs: BoardWidgetConfigs = configs) {
        val aligned = nextConfigs.alignedTo(next)
        layout = next
        configs = aligned
        store.save(next)
        configStore?.save(aligned)
        if (pagerState.currentPage >= next.boards.size) {
            scope.launch { pagerState.scrollToPage((next.boards.size - 1).coerceAtLeast(0)) }
        }
    }

    fun addBoard() {
        if (layout.boards.size >= BoardLimits.MAX_BOARDS) return
        snapshot()
        val next = BoardLayout(layout.boards.toMutableList().apply { add(emptyList()) })
        persist(next, BoardWidgetConfigs(configs.boards.toMutableList().apply { add(emptyList()) }))
        scope.launch { pagerState.animateScrollToPage(next.boards.size - 1) }
    }

    fun removeBoard(index: Int) {
        if (layout.boards.size <= 1) return
        snapshot()
        val next = BoardLayout(layout.boards.filterIndexed { i, _ -> i != index })
        persist(next, BoardWidgetConfigs(configs.boards.filterIndexed { i, _ -> i != index }))
    }

    fun addWidget(type: BoardWidgetType) {
        val index = pagerState.currentPage
        val board = layout.boards.getOrNull(index) ?: return
        if (type in board || board.size >= BoardLimits.MAX_WIDGETS_PER_BOARD) return
        snapshot()
        val next = BoardLayout(layout.boards.mapIndexed { i, b -> if (i == index) b + type else b })
        persist(next, configs.withAdded(index, type.defaultWidgetConfig()))
    }

    fun duplicateWidget(boardIndex: Int, widgetIndex: Int) {
        val board = layout.boards.getOrNull(boardIndex) ?: return
        if (board.size >= BoardLimits.MAX_WIDGETS_PER_BOARD) return
        val type = board.getOrNull(widgetIndex) ?: return
        snapshot()
        val nextBoard = board.toMutableList().apply { add(widgetIndex + 1, type) }
        val next = BoardLayout(layout.boards.mapIndexed { i, b -> if (i == boardIndex) nextBoard else b })
        persist(next, configs.withInserted(boardIndex, widgetIndex + 1, configs.configAt(boardIndex, widgetIndex)))
    }

    fun removeWidget(boardIndex: Int, widgetIndex: Int) {
        snapshot()
        val next = BoardLayout(layout.boards.mapIndexed { i, b ->
            if (i == boardIndex) b.filterIndexed { j, _ -> j != widgetIndex } else b
        })
        persist(next, configs.withRemoved(boardIndex, widgetIndex))
    }

    /** Moves a widget to an absolute index, used by drag-and-drop and the arrow buttons. */
    fun moveWidgetTo(boardIndex: Int, widgetIndex: Int, target: Int) {
        val board = layout.boards.getOrNull(boardIndex) ?: return
        if (widgetIndex !in board.indices || target !in board.indices || target == widgetIndex) return
        snapshot()
        val reordered = board.toMutableList().apply {
            val w = removeAt(widgetIndex); add(target, w)
        }
        val next = BoardLayout(layout.boards.mapIndexed { i, b -> if (i == boardIndex) reordered else b })
        persist(next, configs.withMoved(boardIndex, widgetIndex, target))
    }

    fun moveWidget(boardIndex: Int, widgetIndex: Int, delta: Int) {
        moveWidgetTo(boardIndex, widgetIndex, widgetIndex + delta)
    }

    fun updateConfig(boardIndex: Int, widgetIndex: Int, config: WidgetConfig) {
        snapshot()
        configs = configs.withUpdated(boardIndex, widgetIndex, config)
        configStore?.save(configs)
    }

    fun undo() {
        val previous = history.lastOrNull() ?: return
        history = history.dropLast(1)
        layout = previous.layout
        configs = previous.configs
        store.save(previous.layout)
        configStore?.save(previous.configs)
        if (pagerState.currentPage >= previous.layout.boards.size) {
            scope.launch { pagerState.scrollToPage((previous.layout.boards.size - 1).coerceAtLeast(0)) }
        }
    }

    fun resetLayout() {
        snapshot()
        val preset = BoardLayout.fromPreset(themePresets) ?: BoardLayout.DEFAULT
        persist(preset, BoardWidgetConfigs.EMPTY)
        statusMessage = "Board reset to this theme's defaults"
    }

    fun exportLayout() {
        clipboard.setText(AnnotatedString(BoardDocument.export(layout, configs)))
        statusMessage = "Board layout copied — paste it anywhere to keep a backup"
    }

    fun importLayout() {
        val decoded = BoardDocument.import(clipboard.getText()?.text)
        if (decoded == null) {
            statusMessage = "Clipboard does not contain a valid board layout"
            return
        }
        snapshot()
        persist(decoded.layout, decoded.configs)
        statusMessage = "Board layout imported"
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header: title, board dots, edit toggle and add-widget action.
        Row(Modifier.testTag("boards_header").fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Boards", color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (editing) "Edit mode · drag to reorder, tap icons to configure, duplicate or add" else "Swipe left and right between your boards",
                    color = SecondaryText(), style = MaterialTheme.typography.labelMedium
                )
            }
            if (layout.boards.size > 1) {
                Row(
                    Modifier.testTag("board_dots").padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    layout.boards.indices.forEach { i ->
                        Box(
                            Modifier.size(if (pagerState.currentPage == i) 9.dp else 6.dp)
                                .background(
                                    if (pagerState.currentPage == i) ElectricCyan() else SecondaryText().copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            if (editing) {
                FilledIconButton(
                    onClick = ::undo,
                    enabled = history.isNotEmpty(),
                    modifier = Modifier.testTag("board_undo").size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = ElectricCyan()
                    )
                ) { Icon(Icons.Default.Undo, "Undo last board change", Modifier.size(18.dp)) }
                Spacer(Modifier.width(4.dp))
                FilledIconButton(
                    onClick = ::exportLayout,
                    modifier = Modifier.testTag("board_export").size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = ElectricCyan()
                    )
                ) { Icon(Icons.Default.ContentCopy, "Copy board layout", Modifier.size(18.dp)) }
                Spacer(Modifier.width(4.dp))
                FilledIconButton(
                    onClick = ::importLayout,
                    modifier = Modifier.testTag("board_import").size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = ElectricCyan()
                    )
                ) { Icon(Icons.Default.ContentPaste, "Paste board layout", Modifier.size(18.dp)) }
            }
            if (editing && layout.boards.size > 1) {
                Spacer(Modifier.width(4.dp))
                FilledIconButton(
                    onClick = { removeBoard(pagerState.currentPage) },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = CriticalRed()
                    )
                ) { Icon(Icons.Default.Delete, "Remove this board", Modifier.size(18.dp)) }
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { if (editing) addBoard() else pickerOpen = true },
                modifier = Modifier.testTag("board_add_button").size(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = ElectricCyan()
                )
            ) { Icon(Icons.Default.Add, if (editing) "Add board" else "Add widgets", Modifier.size(20.dp)) }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { editing = !editing },
                modifier = Modifier.testTag("board_edit_toggle").size(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (editing) ElectricCyan() else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (editing) MaterialTheme.colorScheme.onPrimary else ElectricCyan()
                )
            ) { Icon(if (editing) Icons.Default.Check else Icons.Default.Edit, if (editing) "Finish editing" else "Edit boards", Modifier.size(20.dp)) }
        }

        if (editing) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hold a widget and drag to reorder; arrows do the same with TalkBack",
                    color = SecondaryText(), fontSize = 11.sp, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = ::resetLayout, modifier = Modifier.testTag("board_reset")) {
                    Text("Reset board", color = SecondaryText(), fontSize = 11.sp)
                }
            }
        }

        // The swipeable boards themselves.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.testTag("boards_pager").weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0,
            // A horizontal drag on a widget must reorder it, not swipe to the next board.
            userScrollEnabled = !dragActive
        ) { pageIndex ->
            val widgets = layout.boards[pageIndex]
            val dragState = remember(pageIndex) { WidgetDragState() }
            val haptic = LocalHapticFeedback.current
            LaunchedEffect(editing, pageIndex) {
                if (!editing) {
                    dragState.reset()
                    dragActive = false
                }
            }
            if (widgets.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize().testTag("board_empty_${pageIndex}"),
                    color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Board ${pageIndex + 1} is empty", color = PrimaryText(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap the pencil icon to edit, then + to add widgets", color = SecondaryText(), fontSize = 13.sp)
                    }
                }
            } else {
                val visibleIndices = widgets.indices.filter { index ->
                    editing || configs.configAt(pageIndex, index).visible
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val widgetSizePref = remember {
                    runCatching {
                        context.getSharedPreferences("sportage_obd_ui", android.content.Context.MODE_PRIVATE)
                            .getString("widgetSize", "Standard (3x2 Grid)")
                    }.getOrNull() ?: "Standard (3x2 Grid)"
                }
                val gridCells = when (widgetSizePref) {
                    "Expanded (Single Column)" -> GridCells.Fixed(1)
                    "Compact (2x2 Grid)" -> GridCells.Fixed(2)
                    else -> GridCells.Adaptive(minSize = 150.dp)
                }
                LazyVerticalGrid(
                    columns = gridCells,
                    modifier = Modifier.fillMaxSize().testTag("board_grid_$pageIndex"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = visibleIndices,
                        span = { widgetIndex ->
                            val type = widgets[widgetIndex]
                            val requested = if (type == BoardWidgetType.MAP) 2
                            else configs.configAt(pageIndex, widgetIndex).span
                            GridItemSpan(requested.coerceAtMost(maxLineSpan))
                        }
                    ) { widgetIndex ->
                        val type = widgets[widgetIndex]
                        val dragged = dragState.draggedIndex == widgetIndex
                        val dropTarget = dragState.isDragging && !dragged &&
                            dragState.targetIndex == widgetIndex
                        val itemModifier = if (editing) {
                            Modifier
                                .onGloballyPositioned { coordinates ->
                                    dragState.updateBounds(
                                        widgetIndex,
                                        coordinates.positionInParent(),
                                        Size(
                                            coordinates.size.width.toFloat(),
                                            coordinates.size.height.toFloat()
                                        )
                                    )
                                }
                                .pointerInput(pageIndex, widgetIndex) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragState.start(widgetIndex, widgets.size)
                                            dragActive = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragState.drag(amount)
                                        },
                                        onDragEnd = {
                                            dragActive = false
                                            dragState.finish()?.let { (from, to) ->
                                                moveWidgetTo(pageIndex, from, to)
                                            }
                                        },
                                        onDragCancel = {
                                            dragActive = false
                                            dragState.reset()
                                        }
                                    )
                                }
                                .zIndex(if (dragged) 1f else 0f)
                                .graphicsLayer {
                                    if (dragged) {
                                        translationX = dragState.dragOffset.x
                                        translationY = dragState.dragOffset.y
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                        shadowElevation = 16.dp.toPx()
                                    }
                                }
                        } else Modifier
                        BoardWidget(
                            type = type,
                            data = data,
                            editing = editing,
                            canMoveLeft = editing && widgetIndex > 0,
                            canMoveRight = editing && widgetIndex < widgets.size - 1,
                            config = configs.configAt(pageIndex, widgetIndex),
                            highlighted = dropTarget,
                            onRemove = { removeWidget(pageIndex, widgetIndex) },
                            onMoveLeft = { moveWidget(pageIndex, widgetIndex, -1) },
                            onMoveRight = { moveWidget(pageIndex, widgetIndex, 1) },
                            onConfigure = { settingsTarget = pageIndex to widgetIndex },
                            onDuplicate = { duplicateWidget(pageIndex, widgetIndex) },
                            modifier = itemModifier
                        )
                    }
                }
            }
        }
    }

    WidgetPicker(
        isOpen = pickerOpen,
        boardIndex = pagerState.currentPage,
        layout = layout,
        onAdd = { addWidget(it) },
        onDismiss = { pickerOpen = false }
    )

    settingsTarget?.let { (boardIndex, widgetIndex) ->
        val type = layout.boards.getOrNull(boardIndex)?.getOrNull(widgetIndex) ?: return@let
        WidgetSettingsDialog(
            type = type,
            initial = configs.configAt(boardIndex, widgetIndex),
            onDismiss = { settingsTarget = null },
            onSave = { config ->
                updateConfig(boardIndex, widgetIndex, config)
                settingsTarget = null
            }
        )
    }

    statusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { statusMessage = null },
            confirmButton = { TextButton(onClick = { statusMessage = null }) { Text("OK") } },
            title = { Text("Boards") },
            text = { Text(message) }
        )
    }
}
