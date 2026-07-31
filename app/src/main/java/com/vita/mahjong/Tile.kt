package com.vita.mahjong

data class Tile(
    val x: Int,
    val y: Int,
    val z: Int,
    val symbol: String
)

/**
 * Layered step-pyramid: each layer is inset by one full tile width on every
 * side, so a layer never fully covers the ring of tiles around the edge of
 * the layer below it (that ring must stay clickable from the start, or the
 * board is unsolvable from move one).
 */
fun generateBoard(): MutableList<Tile> {
    val layerSizes = listOf(
        6 to 6,
        4 to 4,
        2 to 2
    )

    data class Pos(val x: Int, val y: Int, val z: Int)
    val positions = mutableListOf<Pos>()
    for ((z, size) in layerSizes.withIndex()) {
        val (cols, rows) = size
        val offset = z * 2
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                positions.add(Pos(offset + col * 2, offset + row * 2, z))
            }
        }
    }

    val symbolSet = listOf(
        "🀇", "🀈", "🀉", "🀊", "🀋", "🀌",
        "🀐", "🀑", "🀒", "🀓", "🀔",
        "🀙", "🀚", "🀛",
        "🀄", "🀅"
    )

    // Distribute tiles evenly across symbols, always in even counts, so the
    // total exactly matches positions.size regardless of layerSizes above.
    val pairsTotal = positions.size / 2
    val basePairs = pairsTotal / symbolSet.size
    val extraPairs = pairsTotal % symbolSet.size
    val symbols = mutableListOf<String>()
    symbolSet.forEachIndexed { i, symbol ->
        val pairs = basePairs + if (i < extraPairs) 1 else 0
        repeat(pairs * 2) { symbols.add(symbol) }
    }
    symbols.shuffle()

    return positions.mapIndexed { i, p -> Tile(p.x, p.y, p.z, symbols[i]) }.toMutableList()
}

fun boardBoundsFine(tiles: List<Tile>): Pair<Int, Int> {
    val maxX = tiles.maxOf { it.x } + 2
    val maxY = tiles.maxOf { it.y } + 2
    return maxX to maxY
}

private fun rectsOverlap(a: Tile, b: Tile): Boolean =
    a.x < b.x + 2 && a.x + 2 > b.x && a.y < b.y + 2 && a.y + 2 > b.y

fun isCovered(t: Tile, all: List<Tile>): Boolean =
    all.any { o -> o.z > t.z && rectsOverlap(t, o) }

fun isOpenLeft(t: Tile, all: List<Tile>): Boolean =
    all.none { o -> o.z == t.z && o.x == t.x - 2 && o.y == t.y }

fun isOpenRight(t: Tile, all: List<Tile>): Boolean =
    all.none { o -> o.z == t.z && o.x == t.x + 2 && o.y == t.y }

fun isSelectable(t: Tile, all: List<Tile>): Boolean =
    !isCovered(t, all) && (isOpenLeft(t, all) || isOpenRight(t, all))
