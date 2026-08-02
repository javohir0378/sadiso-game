package com.sadiso.game

data class OnetTile(val row: Int, val col: Int, val symbol: String)

const val ONET_ROWS = 9
const val ONET_COLS = 8

private val ONET_SYMBOLS = listOf(
    "🀇", "🀈", "🀉", "🀊", "🀋", "🀌",
    "🀐", "🀑", "🀒", "🀓", "🀔",
    "🀙", "🀚", "🀛",
    "🀄", "🀅"
)

/**
 * Random pair placement, re-rolled until the starting board actually has
 * at least one connectable pair - otherwise the game could open in an
 * already-stuck state.
 */
fun generateOnetBoard(): MutableList<OnetTile> {
    val total = ONET_ROWS * ONET_COLS
    val pairsTotal = total / 2
    val basePairs = pairsTotal / ONET_SYMBOLS.size
    val extraPairs = pairsTotal % ONET_SYMBOLS.size
    val symbols = mutableListOf<String>()
    ONET_SYMBOLS.forEachIndexed { i, symbol ->
        val pairs = basePairs + if (i < extraPairs) 1 else 0
        repeat(pairs * 2) { symbols.add(symbol) }
    }

    var attempt = 0
    while (true) {
        symbols.shuffle()
        val tiles = mutableListOf<OnetTile>()
        var idx = 0
        for (r in 0 until ONET_ROWS) {
            for (c in 0 until ONET_COLS) {
                tiles.add(OnetTile(r, c, symbols[idx]))
                idx++
            }
        }
        attempt++
        if (attempt >= 30 || hasValidOnetMove(tiles)) return tiles
    }
}

/**
 * Classic "at most 2 turns" connect-the-dots check. The board is treated
 * as padded with one extra empty row/column of border on every side (the
 * path is allowed to route around the outside of the grid, not just
 * through it), so callers pass real (row, col) tile coordinates and this
 * shifts internally.
 */
fun findOnetPath(tiles: List<OnetTile>, a: OnetTile, b: OnetTile): List<Pair<Int, Int>>? {
    val pr = ONET_ROWS + 2
    val pc = ONET_COLS + 2
    val grid = Array(pr) { BooleanArray(pc) }
    for (t in tiles) {
        if ((t.row == a.row && t.col == a.col) || (t.row == b.row && t.col == b.col)) continue
        grid[t.row + 1][t.col + 1] = true
    }
    val ar = a.row + 1
    val ac = a.col + 1
    val br = b.row + 1
    val bc = b.col + 1

    fun occ(r: Int, c: Int): Boolean {
        if (r !in 0 until pr || c !in 0 until pc) return true
        return grid[r][c]
    }
    fun hClear(r: Int, c1: Int, c2: Int): Boolean {
        val lo = minOf(c1, c2)
        val hi = maxOf(c1, c2)
        for (c in lo..hi) if (occ(r, c)) return false
        return true
    }
    fun vClear(c: Int, r1: Int, r2: Int): Boolean {
        val lo = minOf(r1, r2)
        val hi = maxOf(r1, r2)
        for (r in lo..hi) if (occ(r, c)) return false
        return true
    }

    // 0 turns
    if (ar == br && hClear(ar, ac, bc)) return listOf(ar to ac, br to bc)
    if (ac == bc && vClear(ac, ar, br)) return listOf(ar to ac, br to bc)

    // 1 turn - corner at (ar,bc) or (br,ac)
    if (!occ(ar, bc) && hClear(ar, ac, bc) && vClear(bc, ar, br)) return listOf(ar to ac, ar to bc, br to bc)
    if (!occ(br, ac) && vClear(ac, ar, br) && hClear(br, ac, bc)) return listOf(ar to ac, br to ac, br to bc)

    // 2 turns - horizontal bridge row R: A -> (R,ac) -> (R,bc) -> B
    for (r in 0 until pr) {
        if (occ(r, ac) || occ(r, bc)) continue
        if (vClear(ac, ar, r) && hClear(r, ac, bc) && vClear(bc, r, br)) {
            return listOf(ar to ac, r to ac, r to bc, br to bc)
        }
    }
    // 2 turns - vertical bridge column C: A -> (ar,C) -> (br,C) -> B
    for (c in 0 until pc) {
        if (occ(ar, c) || occ(br, c)) continue
        if (hClear(ar, ac, c) && vClear(c, ar, br) && hClear(br, c, bc)) {
            return listOf(ar to ac, ar to c, br to c, br to bc)
        }
    }
    return null
}

fun hasValidOnetMove(tiles: List<OnetTile>): Boolean {
    val bySymbol = tiles.groupBy { it.symbol }
    for (group in bySymbol.values) {
        if (group.size < 2) continue
        for (i in group.indices) {
            for (j in i + 1 until group.size) {
                if (findOnetPath(tiles, group[i], group[j]) != null) return true
            }
        }
    }
    return false
}

fun findAnyOnetHint(tiles: List<OnetTile>): Pair<OnetTile, OnetTile>? {
    val bySymbol = tiles.groupBy { it.symbol }
    for (group in bySymbol.values) {
        if (group.size < 2) continue
        for (i in group.indices) {
            for (j in i + 1 until group.size) {
                if (findOnetPath(tiles, group[i], group[j]) != null) return group[i] to group[j]
            }
        }
    }
    return null
}
