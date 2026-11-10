// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.hosttest

import org.oblivios.passwords.ui.PatternColors
import org.oblivios.passwords.ui.contrastRatio
import org.oblivios.passwords.ui.patternFor

// Pinned against a port of the algorithm in JavaScript (node), not against the reference page: the page's own hash puts
// every name on one of a few patterns (888 of 1000 domains collided), so the app adds an avalanche step; see Pattern.kt.
fun main() {
    matchesThePinnedPatterns()
    isDeterministicAndIgnoresCase()
    isMirrored()
    contrastIsAtLeastThreeToOne()
    differentNamesRarelyCollide()
    Check.done("PatternTest")
}

private fun matchesThePinnedPatterns() {
    val golden = mapOf(
        "hetzner" to (2 to listOf("..#..", ".#.#.", ".#.#.", "#####", "#.#.#")),
        "github" to (0 to listOf("..#..", ".#.#.", ".#.#.", "#...#", "##.##")),
        "codeberg" to (6 to listOf("#...#", "#...#", ".#.#.", ".....", ".....")),
        "a" to (5 to listOf("#...#", ".###.", "#####", "..#..", "#...#")),
        "" to (2 to listOf(".....", ".#.#.", ".###.", "#...#", "..#..")),
        "Ünïcode 🔐" to (2 to listOf(".....", "#####", "#.#.#", ".....", "#.#.#")),
    )
    for ((name, want) in golden) {
        val p = patternFor(name)
        Check.equal("hue index of '$name'", want.first, p.hueIndex)
        Check.equal("rows of '$name'", want.second, p.rows)
    }
}

private fun isDeterministicAndIgnoresCase() {
    Check.equal("the same name twice", patternFor("Hetzner"), patternFor("Hetzner"))
    Check.equal("case does not matter", patternFor("HETZNER"), patternFor("hetzner"))
    Check.that("another name is another pattern", patternFor("github") != patternFor("hetzner"))
}

private fun isMirrored() {
    for (i in 0 until 200) {
        val p = patternFor("name$i")
        Check.equal("five rows", 5, p.rows.size)
        Check.that("each row is five cells and a mirror image ($i)", p.rows.all { it.length == 5 && it == it.reversed() && it.all { c -> c == '.' || c == '#' } })
    }
}

private fun contrastIsAtLeastThreeToOne() {
    for (h in PatternColors.HUES.indices) {
        val (darkBg, darkFg) = PatternColors.dark(h)
        val (lightBg, lightFg) = PatternColors.light(h)
        Check.that("dark hue $h: ${contrastRatio(darkBg, darkFg)}", contrastRatio(darkBg, darkFg) >= 3.0)
        Check.that("light hue $h: ${contrastRatio(lightBg, lightFg)}", contrastRatio(lightBg, lightFg) >= 3.0)
    }
    Check.that("the ratio of black on white is 21", Math.abs(contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) - 21.0) < 1e-9)
    Check.that("the ratio of a colour with itself is 1", Math.abs(contrastRatio(0xFF336699.toInt(), 0xFF336699.toInt()) - 1.0) < 1e-9)
}

private fun differentNamesRarelyCollide() {
    val seen = HashSet<Pair<Int, List<String>>>()
    var collisions = 0
    for (i in 0 until 1000) {
        val p = patternFor("site$i.example")
        if (!seen.add(p.hueIndex to p.rows)) collisions++
    }
    Check.that("fewer than 5 of 1000 domains share a pattern (found $collisions)", collisions < 5)
}
