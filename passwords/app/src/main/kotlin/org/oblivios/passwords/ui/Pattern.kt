// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The fallback logo of an entry: a symmetric 5 by 5 grid and a hue, both made from the name, in a tonal pair per theme
 * (spec 4.2). [rows] has five strings of `.` and `#`, each a mirror image.
 *
 * The cells follow the reference page (docs/design/password-manager-2026-10-20.html): the 32-bit FNV-1a hash of the
 * lower-cased name picks one of seven hues by its remainder, and the cell `(y, x)` is filled by the hash of the name with
 * `y` and `x` appended, mirrored left to right. One change: the page took bit 3 of that hash directly, which depends only
 * on the low bits of the input and put 888 of 1000 domains on a few patterns; here the hash goes through an avalanche
 * step (the finaliser of MurmurHash3) first, which leaves one collision in 1000.
 */
data class Pattern(val hueIndex: Int, val rows: List<String>)

private fun fnv1a(s: String): Int {
    var x = -2128831035 // 2166136261
    for (c in s) {
        x = x xor c.code
        x *= 16777619
    }
    return x
}

private fun avalanche(h: Int): Int {
    var x = h
    x = x xor (x ushr 16)
    x *= -2048144789 // 0x85ebca6b
    x = x xor (x ushr 13)
    x *= -1028477387 // 0xc2b2ae35
    return x xor (x ushr 16)
}

fun patternFor(name: String): Pattern {
    val n = name.lowercase(Locale.ROOT)
    val hue = (fnv1a(n).toUInt() % PatternColors.HUES.size.toUInt()).toInt()
    val rows = (0 until 5).map { y ->
        val cells = CharArray(5) { '.' }
        for (x in 0 until 3) {
            if (avalanche(fnv1a("$n$y$x")) and 1 == 1) {
                cells[x] = '#'
                if (x < 2) cells[4 - x] = '#'
            }
        }
        String(cells)
    }
    return Pattern(hue, rows)
}

/** The tonal pairs of the reference page, as ARGB: `hsl(h, 28%, 26%)` on `hsl(h, 70%, 86%)` in Dark, `hsl(h, 65%, 90%)` and `hsl(h, 40%, 28%)` in Light. */
object PatternColors {
    val HUES = intArrayOf(215, 165, 35, 330, 265, 95, 10)

    /** (tile, cells) in Dark. */
    fun dark(hueIndex: Int): Pair<Int, Int> = hsl(HUES[hueIndex], 28, 26) to hsl(HUES[hueIndex], 70, 86)

    /** (tile, cells) in Light. */
    fun light(hueIndex: Int): Pair<Int, Int> = hsl(HUES[hueIndex], 65, 90) to hsl(HUES[hueIndex], 40, 28)

    private fun hsl(h: Int, sPercent: Int, lPercent: Int): Int {
        val s = sPercent / 100.0
        val l = lPercent / 100.0
        val c = (1 - abs(2 * l - 1)) * s
        val hp = h / 60.0
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0.0)
            hp < 2 -> Triple(x, c, 0.0)
            hp < 3 -> Triple(0.0, c, x)
            hp < 4 -> Triple(0.0, x, c)
            hp < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2
        fun ch(v: Double) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}

private fun luminance(argb: Int): Double {
    fun lin(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin((argb shr 16) and 0xff) + 0.7152 * lin((argb shr 8) and 0xff) + 0.0722 * lin(argb and 0xff)
}

/** WCAG contrast ratio of two colours (1 to 21). */
fun contrastRatio(a: Int, b: Int): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
