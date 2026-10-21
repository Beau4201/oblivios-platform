// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.core.Checkup
import org.freeholdos.passwords.core.Entry
import org.freeholdos.passwords.core.HistoryItem
import org.freeholdos.passwords.core.Summary
import org.freeholdos.passwords.core.Wire
import org.freeholdos.passwords.core.WeakReason

// The same strings as the Rust test `the_wire_format_is_pinned` (vault-core/tests/jni_handles.rs).
private const val GOLDEN_ENTRY =
    "0102030405060708090a0b0c0d0e0f10070000004865747a6e6572010000000b0000006865747a6e65722e636f6d010000000e00000064652e6865747a6e65722e61707004000000616e6e610500000070772dc3bc020000006e0a0100000000000000020000000000000001030000000000000001030000006f6c640400000000000000"
private const val GOLDEN_LIST =
    "020000000102030405060708090a0b0c0d0e0f10070000004865747a6e657204000000616e6e61010000000b0000006865747a6e65722e636f6d010000000e00000064652e6865747a6e65722e617070020000000000000001030000000000000009090909090909090909090909090909040000005765616b04000000616e6e610000000000000000020000000000000000"
private const val GOLDEN_CHECKUP = "00000000020000000102030405060708090a0b0c0d0e0f10000909090909090909090909090909090900"

private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

private val id1 = ByteArray(16) { (it + 1).toByte() }
private val id9 = ByteArray(16) { 9 }

private fun golden() = Entry(
    id = id1, title = "Hetzner", domains = listOf("hetzner.com"), packages = listOf("de.hetzner.app"), username = "anna",
    password = "pw-ü".toCharArray(), notes = "n\n", createdMs = 1, editedMs = 2, lastFilledMs = 3L, history = listOf(HistoryItem("old".toCharArray(), 4L)),
)

fun main() {
    encodingMatchesTheRustBytes()
    decodingTheRustBytes()
    summaries()
    checkup()
    hostileInput()
    Check.done("WireTest")
}

private fun encodingMatchesTheRustBytes() {
    Check.equal("a full entry encodes to the pinned bytes", GOLDEN_ENTRY, hex(Wire.encode(golden())))
    val plain = golden().copy(lastFilledMs = null, history = emptyList(), domains = emptyList(), packages = emptyList(), username = "", notes = "")
    val back = Wire.decode(Wire.encode(plain))
    Check.equal("an entry with nothing optional roundtrips", plain.title, back!!.title)
    Check.that("including the absent last fill", back.lastFilledMs == null && back.history.isEmpty())
    val emoji = golden().copy(title = "🔐 مرحبا", password = "päss🔑".toCharArray())
    Check.equal("emoji and right-to-left text roundtrip", emoji, Wire.decode(Wire.encode(emoji)))
}

private fun decodingTheRustBytes() {
    val e = Wire.decode(unhex(GOLDEN_ENTRY))
    Check.equal("the pinned bytes decode to the entry", golden(), e)
    Check.that("the id keeps its bytes", e!!.id.contentEquals(id1))
}

private fun summaries() {
    val list = Wire.decodeSummaries(unhex(GOLDEN_LIST))!!
    Check.equal("two summaries", 2, list.size)
    val a = list[0]
    Check.equal("title", "Hetzner", a.title)
    Check.equal("username", "anna", a.username)
    Check.equal("domains", listOf("hetzner.com"), a.domains)
    Check.equal("packages", listOf("de.hetzner.app"), a.packages)
    Check.equal("edited", 2L, a.editedMs)
    Check.equal("last filled", 3L, a.lastFilledMs)
    Check.that("id", a.id.contentEquals(id1))
    val b: Summary = list[1]
    Check.that("the second has no last fill and no lists", b.lastFilledMs == null && b.domains.isEmpty() && b.packages.isEmpty())
    Check.that("and the id 9", b.id.contentEquals(id9))
}

private fun checkup() {
    val c: Checkup = Wire.decodeCheckup(unhex(GOLDEN_CHECKUP))!!
    Check.that("no reuse", c.reused.isEmpty())
    Check.equal("two weak", 2, c.weak.size)
    Check.that("first id and reason", c.weak[0].first.contentEquals(id1) && c.weak[0].second == WeakReason.TOO_SHORT)
    Check.that("second id and reason", c.weak[1].first.contentEquals(id9) && c.weak[1].second == WeakReason.TOO_SHORT)
    val groups = Wire.decodeCheckup(unhex("01000000" + "02000000" + hex(id1) + hex(id9) + "01000000" + hex(id1) + "02"))!!
    Check.equal("one reuse group of two", 1, groups.reused.size)
    Check.equal("with both ids", 2, groups.reused[0].size)
    Check.equal("the reason numbers", WeakReason.PATTERN, groups.weak[0].second)
    Check.equal("1 is common", WeakReason.COMMON, Wire.decodeCheckup(unhex("00000000" + "01000000" + hex(id1) + "01"))!!.weak[0].second)
}

private fun hostileInput() {
    val entry = unhex(GOLDEN_ENTRY)
    for (len in entry.indices) Check.that("an entry cut to $len bytes is refused", Wire.decode(entry.copyOf(len)) == null)
    Check.that("trailing bytes are refused", Wire.decode(entry + byteArrayOf(0)) == null)
    val list = unhex(GOLDEN_LIST)
    for (len in list.indices) Check.that("a list cut to $len bytes is refused", Wire.decodeSummaries(list.copyOf(len)) == null)
    Check.that("a list that claims four billion summaries is refused", Wire.decodeSummaries(unhex("ffffffff")) == null)
    val checkup = unhex(GOLDEN_CHECKUP)
    for (len in checkup.indices) Check.that("a checkup cut to $len bytes is refused", Wire.decodeCheckup(checkup.copyOf(len)) == null)
    Check.that("a checkup with an unknown reason is refused", Wire.decodeCheckup(unhex("00000000" + "01000000" + hex(id1) + "07")) == null)
    val badUtf8 = entry.copyOf().also { it[16 + 4] = 0xff.toByte() }
    Check.that("invalid UTF-8 in a field is refused", Wire.decode(badUtf8) == null)
    Check.that("a field length beyond the limit is refused", Wire.decode(entry.copyOf().also { it[16 + 3] = 0x7f }) == null)
}
