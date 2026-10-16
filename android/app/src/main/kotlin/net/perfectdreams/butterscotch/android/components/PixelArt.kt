package net.perfectdreams.butterscotch.android.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.perfectdreams.butterscotch.android.settings.GamepadStyle
import net.perfectdreams.butterscotch.android.theme.ButterscotchPrimary
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

// Pixel art look for the on-screen gamepad (GamepadStyle.PIXEL_ART). Everything is drawn on a coarse
// cell grid scaled up to the element's size, with hard cell edges, so it matches the chunky look of
// the games themselves. Elements with a custom sprite keep their sprite in either style.

/** Which look the gamepad elements below use, provided by GameControls from the app settings. */
val LocalGamepadStyle = compositionLocalOf { GamepadStyle.NORMAL }

internal object PixelColors {
    val fill = Color.White.copy(alpha = 0.20f)
    val outline = Color.White.copy(alpha = 0.55f)
    val glyph = Color.White.copy(alpha = 0.95f)
    val pressed = ButterscotchPrimary.copy(alpha = 0.45f)
    val thumb = Color.White.copy(alpha = 0.6f)
}

// ===[ Grid drawing ]===

/**
 * Lays a [cols]x[rows] cell grid centered in the draw area (square cells) and lets [block] paint cells.
 * Cell edges are snapped to whole pixels, so neighbouring translucent cells never overlap or leave seams.
 */
internal class CellPainter(private val scope: DrawScope, val cols: Int, val rows: Int) {
    val cellSize: Float = min(scope.size.width / cols, scope.size.height / rows)
    private val originX = (scope.size.width - cellSize * cols) / 2f
    private val originY = (scope.size.height - cellSize * rows) / 2f

    fun cell(x: Int, y: Int, color: Color) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) return
        val left = floor(originX + x * cellSize)
        val top = floor(originY + y * cellSize)
        val right = floor(originX + (x + 1) * cellSize)
        val bottom = floor(originY + (y + 1) * cellSize)
        scope.drawRect(color, Offset(left, top), Size(right - left, bottom - top))
    }
}

internal inline fun DrawScope.paintCells(cols: Int, rows: Int, block: CellPainter.() -> Unit) {
    CellPainter(this, cols, rows).block()
}

/** Cells of a filled circle of [diameter] cells, as a row-major boolean mask. */
internal fun circleMask(diameter: Int): BooleanArray {
    val mask = BooleanArray(diameter * diameter)
    val c = (diameter - 1) / 2f
    val r = diameter / 2f
    for (y in 0 until diameter) for (x in 0 until diameter) {
        val dx = x - c
        val dy = y - c
        mask[y * diameter + x] = dx * dx + dy * dy <= r * r - r * 0.35f
    }
    return mask
}

/** True for mask cells that touch the outside (4-neighbourhood), i.e. the 1 cell border of the shape. */
internal fun isEdge(mask: BooleanArray, cols: Int, rows: Int, x: Int, y: Int): Boolean {
    fun inside(ix: Int, iy: Int) = ix in 0 until cols && iy in 0 until rows && mask[iy * cols + ix]
    return inside(x, y) && (!inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1))
}

// ===[ Glyphs ]===

/** A 1-bit pixel picture, rows of '#' (set) and '.' (empty). */
class PixelGlyph(val rows: List<String>) {
    val width = rows.maxOf { it.length }
    val height = rows.size
    fun isSet(x: Int, y: Int) = rows[y].getOrNull(x) == '#'
}

object PixelIcons {
    val MENU = PixelGlyph(listOf(
        "#######",
        ".......",
        "#######",
        ".......",
        "#######",
    ))
    val FAST_FORWARD = PixelGlyph(listOf(
        "#...#...",
        "##..##..",
        "###.###.",
        "##..##..",
        "#...#...",
    ))
    val MOUSE = PixelGlyph(listOf(
        ".###.",
        "#.#.#",
        "#.#.#",
        "#####",
        "#...#",
        "#...#",
        ".###.",
    ))
    val DPAD = PixelGlyph(listOf(
        "..###..",
        "..###..",
        "#######",
        "#######",
        "#######",
        "..###..",
        "..###..",
    ))
    val JOYSTICK = PixelGlyph(listOf(
        "..###..",
        ".#...#.",
        "#..#..#",
        "#.###.#",
        "#..#..#",
        ".#...#.",
        "..###..",
    ))
}

// 3x5 font (arrows and the home glyph are 5 wide). Labels are upper-cased first; Turkish letters fall
// back to their base letter, anything else unknown is drawn as '?'
private val FONT: Map<Char, PixelGlyph> = mapOf(
    'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"),
    'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
    'C' to listOf(".##", "#..", "#..", "#..", ".##"),
    'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
    'E' to listOf("###", "#..", "##.", "#..", "###"),
    'F' to listOf("###", "#..", "##.", "#..", "#.."),
    'G' to listOf(".##", "#..", "#.#", "#.#", ".##"),
    'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
    'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
    'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
    'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"),
    'L' to listOf("#..", "#..", "#..", "#..", "###"),
    'M' to listOf("#.#", "###", "###", "#.#", "#.#"),
    'N' to listOf("##.", "#.#", "#.#", "#.#", "#.#"),
    'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."),
    'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
    'Q' to listOf(".#.", "#.#", "#.#", "##.", ".##"),
    'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
    'S' to listOf(".##", "#..", ".#.", "..#", "##."),
    'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
    'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
    'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
    'W' to listOf("#.#", "#.#", "###", "###", "#.#"),
    'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
    'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."),
    'Z' to listOf("###", "..#", ".#.", "#..", "###"),
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
    '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
    '2' to listOf("##.", "..#", ".#.", "#..", "###"),
    '3' to listOf("##.", "..#", ".#.", "..#", "##."),
    '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
    '5' to listOf("###", "#..", "##.", "..#", "##."),
    '6' to listOf(".##", "#..", "###", "#.#", "###"),
    '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
    '8' to listOf("###", "#.#", "###", "#.#", "###"),
    '9' to listOf("###", "#.#", "###", "..#", "##."),
    '-' to listOf("...", "...", "###", "...", "..."),
    '+' to listOf("...", ".#.", "###", ".#.", "..."),
    '.' to listOf(".", ".", ".", ".", "#"),
    '/' to listOf("..#", "..#", ".#.", "#..", "#.."),
    '?' to listOf("##.", "..#", ".#.", "...", ".#."),
    ' ' to listOf("..", "..", "..", "..", ".."),
    '←' to listOf("..#..", ".#...", "#####", ".#...", "..#.."),
    '→' to listOf("..#..", "...#.", "#####", "...#.", "..#.."),
    '↑' to listOf("..#..", ".###.", "#.#.#", "..#..", "..#.."),
    '↓' to listOf("..#..", "..#..", "#.#.#", ".###.", "..#.."),
    '⌂' to listOf("..#..", ".#.#.", "#...#", "#...#", "#####"),
).mapValues { PixelGlyph(it.value) }

private val TURKISH_FALLBACK = mapOf('Ç' to 'C', 'Ş' to 'S', 'Ğ' to 'G', 'İ' to 'I', 'Ö' to 'O', 'Ü' to 'U')

/** Renders [text] with the 3x5 pixel font into one glyph (1 cell between letters). */
fun pixelText(text: String): PixelGlyph {
    val glyphs = text.uppercase(Locale.ROOT).map { ch -> FONT[TURKISH_FALLBACK[ch] ?: ch] ?: FONT.getValue('?') }
    if (glyphs.isEmpty()) return PixelGlyph(listOf(""))
    val rows = (0 until 5).map { y -> glyphs.joinToString(".") { g -> g.rows[y].padEnd(g.width, '.') } }
    return PixelGlyph(rows)
}

// ===[ Elements ]===

/**
 * Round pixel button face with [glyph] centered on it. The grid grows with the glyph, so long labels
 * like "SHIFT" still fit with a margin.
 */
@Composable
fun PixelButtonFace(glyph: PixelGlyph, pressed: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    // Odd grids keep 1 cell wide strokes centered; pick the parity that centers the glyph horizontally
    val size = remember(glyph) {
        var s = maxOf(15, glyph.width + 6)
        if ((s - glyph.width) % 2 != 0) s++
        s
    }
    val mask = remember(size) { circleMask(size) }
    Canvas(modifier) {
        paintCells(size, size) {
            for (y in 0 until size) for (x in 0 until size) {
                if (!mask[y * size + x]) continue
                cell(x, y, if (isEdge(mask, size, size, x, y)) PixelColors.outline else if (pressed) PixelColors.pressed else PixelColors.fill)
            }
            val gx = (size - glyph.width) / 2
            val gy = (size - glyph.height) / 2
            for (y in 0 until glyph.height) for (x in 0 until glyph.width) {
                if (glyph.isSet(x, y)) cell(gx + x, gy + y, PixelColors.glyph)
            }
        }
    }
}

private const val JOYSTICK_CELLS = 25
private const val JOYSTICK_THUMB_CELLS = 10

/**
 * Pixel joystick: ring + translucent base, and a round thumb that moves in whole cell steps.
 * [thumbOffset] is the thumb displacement in pixels (as the normal joystick tracks it).
 */
fun DrawScope.drawPixelJoystick(thumbOffset: Offset, drawBase: Boolean, drawThumb: Boolean, holding: Boolean) {
    val baseMask = circleMask(JOYSTICK_CELLS)
    val thumbMask = circleMask(JOYSTICK_THUMB_CELLS)
    paintCells(JOYSTICK_CELLS, JOYSTICK_CELLS) {
        if (drawBase) {
            for (y in 0 until JOYSTICK_CELLS) for (x in 0 until JOYSTICK_CELLS) {
                if (!baseMask[y * JOYSTICK_CELLS + x]) continue
                cell(x, y, if (isEdge(baseMask, JOYSTICK_CELLS, JOYSTICK_CELLS, x, y)) PixelColors.outline else PixelColors.fill)
            }
        }
        if (drawThumb) {
            val dx = (thumbOffset.x / cellSize).roundToInt()
            val dy = (thumbOffset.y / cellSize).roundToInt()
            val start = (JOYSTICK_CELLS - JOYSTICK_THUMB_CELLS) / 2
            for (y in 0 until JOYSTICK_THUMB_CELLS) for (x in 0 until JOYSTICK_THUMB_CELLS) {
                if (!thumbMask[y * JOYSTICK_THUMB_CELLS + x]) continue
                val edge = isEdge(thumbMask, JOYSTICK_THUMB_CELLS, JOYSTICK_THUMB_CELLS, x, y)
                cell(start + x + dx, start + y + dy, if (edge) PixelColors.outline else if (holding) PixelColors.pressed else PixelColors.thumb)
            }
        }
    }
}

private const val DPAD_CELLS = 21
private const val DPAD_ARM = 7

/** Pixel d-pad: outlined cross, pressed arms highlighted, an arrow near the end of each arm. */
fun DrawScope.drawPixelDPad(upHeld: Boolean, downHeld: Boolean, leftHeld: Boolean, rightHeld: Boolean) {
    val lo = (DPAD_CELLS - DPAD_ARM) / 2
    val hi = lo + DPAD_ARM - 1
    val mask = BooleanArray(DPAD_CELLS * DPAD_CELLS) { i -> val x = i % DPAD_CELLS; val y = i / DPAD_CELLS; x in lo..hi || y in lo..hi }
    val mid = DPAD_CELLS / 2
    paintCells(DPAD_CELLS, DPAD_CELLS) {
        for (y in 0 until DPAD_CELLS) for (x in 0 until DPAD_CELLS) {
            if (!mask[y * DPAD_CELLS + x]) continue
            val pressed = (upHeld && y < lo) || (downHeld && y > hi) || (leftHeld && x < lo) || (rightHeld && x > hi)
            cell(x, y, if (isEdge(mask, DPAD_CELLS, DPAD_CELLS, x, y)) PixelColors.outline else if (pressed) PixelColors.pressed else PixelColors.fill)
        }
        // 5 wide, 3 deep arrows pointing outwards
        for (row in 0 until 3) for (i in -row..row) {
            cell(mid + i, 2 + row, PixelColors.glyph)
            cell(mid + i, DPAD_CELLS - 3 - row, PixelColors.glyph)
            cell(2 + row, mid + i, PixelColors.glyph)
            cell(DPAD_CELLS - 3 - row, mid + i, PixelColors.glyph)
        }
    }
}
