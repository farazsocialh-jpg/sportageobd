package com.sportage.obd.ui.boards

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-widget configuration for every board, aligned by index with [BoardLayout].
 *
 * Persisted as versioned JSON under its own key, so the legacy token-string layout stays
 * readable and migrates cleanly: layouts saved before this feature simply get default
 * widget configs, and their widget order is preserved.
 */
data class BoardWidgetConfigs(val boards: List<List<WidgetConfig>>) {

    fun board(boardIndex: Int): List<WidgetConfig> = boards.getOrNull(boardIndex) ?: emptyList()

    fun configAt(boardIndex: Int, widgetIndex: Int): WidgetConfig =
        board(boardIndex).getOrNull(widgetIndex)?.normalized() ?: WidgetConfig()

    fun withAdded(boardIndex: Int, config: WidgetConfig): BoardWidgetConfigs {
        val next = normalisedBoards().toMutableList()
        if (boardIndex !in next.indices) return this
        val board = next[boardIndex].toMutableList()
        if (board.size >= BoardLimits.MAX_WIDGETS_PER_BOARD) return this
        board += config.normalized()
        next[boardIndex] = board
        return BoardWidgetConfigs(next)
    }

    fun withInserted(boardIndex: Int, widgetIndex: Int, config: WidgetConfig): BoardWidgetConfigs {
        val next = normalisedBoards().toMutableList()
        if (boardIndex !in next.indices) return this
        val board = next[boardIndex].toMutableList()
        if (board.size >= BoardLimits.MAX_WIDGETS_PER_BOARD) return this
        board.add(widgetIndex.coerceIn(0, board.size), config.normalized())
        next[boardIndex] = board
        return BoardWidgetConfigs(next)
    }

    fun withUpdated(boardIndex: Int, widgetIndex: Int, config: WidgetConfig): BoardWidgetConfigs {
        val next = normalisedBoards().toMutableList()
        if (boardIndex !in next.indices) return this
        val board = next[boardIndex].toMutableList()
        if (widgetIndex !in board.indices) return this
        board[widgetIndex] = config.normalized()
        next[boardIndex] = board
        return BoardWidgetConfigs(next)
    }

    fun withRemoved(boardIndex: Int, widgetIndex: Int): BoardWidgetConfigs {
        val next = normalisedBoards().toMutableList()
        if (boardIndex !in next.indices) return this
        val board = next[boardIndex].toMutableList()
        if (widgetIndex !in board.indices) return this
        board.removeAt(widgetIndex)
        next[boardIndex] = board
        return BoardWidgetConfigs(next)
    }

    fun withMoved(boardIndex: Int, from: Int, to: Int): BoardWidgetConfigs {
        val next = normalisedBoards().toMutableList()
        if (boardIndex !in next.indices) return this
        val board = next[boardIndex].toMutableList()
        if (from !in board.indices || to !in board.indices || from == to) return this
        val item = board.removeAt(from)
        board.add(to, item)
        next[boardIndex] = board
        return BoardWidgetConfigs(next)
    }

    /** Grows/shrinks config boards so each one matches its widget count. */
    fun alignedTo(layout: BoardLayout): BoardWidgetConfigs {
        val next = layout.boards.mapIndexed { index, widgets ->
            val existing = board(index)
            if (existing.size == widgets.size) {
                existing.map { it.normalized() }
            } else {
                List(widgets.size) { i ->
                    existing.getOrNull(i)?.normalized() ?: widgets[i].defaultWidgetConfig()
                }
            }
        }
        return BoardWidgetConfigs(next)
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val array = JSONArray()
        normalisedBoards().forEach { board ->
            val boardJson = JSONArray()
            board.forEach { config -> boardJson.put(config.normalized().toJson()) }
            array.put(boardJson)
        }
        root.put("boards", array)
        return root.toString()
    }

    private fun normalisedBoards(): List<List<WidgetConfig>> =
        boards.take(BoardLimits.MAX_BOARDS).map { board ->
            board.take(BoardLimits.MAX_WIDGETS_PER_BOARD).map { it.normalized() }
        }

    companion object {
        const val VERSION = 2
        const val KEY = "board_widget_configs_v2"
        val EMPTY = BoardWidgetConfigs(emptyList())

        /**
         * Strict decoder. Returns null for malformed input so callers can log and fall back
         * to defaults instead of rendering a half-broken board.
         */
        fun decode(raw: String?): BoardWidgetConfigs? {
            if (raw.isNullOrBlank()) return null
            return try {
                val root = JSONObject(raw)
                val version = root.optInt("version", VERSION)
                if (version !in 1..VERSION) return null
                val array = root.optJSONArray("boards") ?: return null
                val boards = mutableListOf<List<WidgetConfig>>()
                for (i in 0 until minOf(array.length(), BoardLimits.MAX_BOARDS)) {
                    val board = array.optJSONArray(i) ?: continue
                    val configs = mutableListOf<WidgetConfig>()
                    for (j in 0 until minOf(board.length(), BoardLimits.MAX_WIDGETS_PER_BOARD)) {
                        val config = WidgetConfig.fromJson(board.optJSONObject(j)) ?: WidgetConfig()
                        configs += config
                    }
                    boards += configs
                }
                BoardWidgetConfigs(boards)
            } catch (e: Exception) {
                Log.w("BoardConfigStore", "Ignoring malformed widget configs", e)
                null
            }
        }

        /** Validates an imported layout document; null means rejected. */
        fun import(raw: String?): BoardWidgetConfigs? = decode(raw)
    }
}

/**
 * Portable board document (layout + widget configs) used by export/import. Unknown widget
 * ids are dropped, limits are enforced and malformed documents are rejected wholesale.
 */
object BoardDocument {
    const val VERSION = 2

    data class Decoded(val layout: BoardLayout, val configs: BoardWidgetConfigs)

    fun export(layout: BoardLayout, configs: BoardWidgetConfigs): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val arrays = JSONArray()
        layout.boards.forEachIndexed { boardIndex, widgets ->
            val boardJson = JSONObject()
            val widgetArray = JSONArray()
            widgets.forEachIndexed { widgetIndex, type ->
                widgetArray.put(JSONObject().apply {
                    put("type", type.id)
                    put("config", configs.configAt(boardIndex, widgetIndex).toJson())
                })
            }
            boardJson.put("widgets", widgetArray)
            arrays.put(boardJson)
        }
        root.put("boards", arrays)
        return root.toString(2)
    }

    fun import(raw: String?): Decoded? {
        if (raw.isNullOrBlank()) return null
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", VERSION) !in 1..VERSION) return null
            val arrays = root.optJSONArray("boards") ?: return null
            val boards = mutableListOf<List<BoardWidgetType>>()
            val configs = mutableListOf<List<WidgetConfig>>()
            for (i in 0 until minOf(arrays.length(), BoardLimits.MAX_BOARDS)) {
                val boardJson = arrays.optJSONObject(i) ?: continue
                val widgets = boardJson.optJSONArray("widgets") ?: continue
                val types = mutableListOf<BoardWidgetType>()
                val boardConfigs = mutableListOf<WidgetConfig>()
                for (j in 0 until minOf(widgets.length(), BoardLimits.MAX_WIDGETS_PER_BOARD)) {
                    val entry = widgets.optJSONObject(j) ?: continue
                    val type = BoardWidgetType.byId(entry.optString("type", "")) ?: continue
                    types += type
                    boardConfigs += (WidgetConfig.fromJson(entry.optJSONObject("config"))
                        ?: type.defaultWidgetConfig()).normalized()
                }
                if (types.isNotEmpty()) {
                    boards += types
                    configs += boardConfigs
                }
            }
            if (boards.isEmpty()) return null
            return Decoded(BoardLayout(boards), BoardWidgetConfigs(configs))
        } catch (e: Exception) {
            Log.w("BoardDocument", "Ignoring malformed board document", e)
            null
        }
    }
}

/** Persists per-widget configuration. Kept behind an interface so tests need no Android. */
interface BoardConfigStore {
    fun load(): BoardWidgetConfigs
    fun save(configs: BoardWidgetConfigs)
}

class PrefsBoardConfigStore(context: Context) : BoardConfigStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sportage_obd_boards", Context.MODE_PRIVATE)

    override fun load(): BoardWidgetConfigs {
        val raw = prefs.getString(BoardWidgetConfigs.KEY, null)
        if (raw == null) {
            // No v2 document yet: the first save migrates this board seamlessly.
            return BoardWidgetConfigs.EMPTY
        }
        val decoded = BoardWidgetConfigs.decode(raw)
        if (decoded == null) {
            Log.w("BoardConfigStore", "Saved widget configs are invalid; using defaults")
            return BoardWidgetConfigs.EMPTY
        }
        return decoded
    }

    override fun save(configs: BoardWidgetConfigs) {
        prefs.edit().putString(BoardWidgetConfigs.KEY, configs.toJson()).apply()
    }
}
