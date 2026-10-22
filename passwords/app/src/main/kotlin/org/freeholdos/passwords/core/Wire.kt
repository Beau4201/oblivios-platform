// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** An earlier password of an entry and when it was replaced. */
class HistoryItem(val password: CharArray, val replacedMs: Long) {
    override fun equals(other: Any?) = other is HistoryItem && password.contentEquals(other.password) && replacedMs == other.replacedMs
    override fun hashCode() = 31 * password.contentHashCode() + replacedMs.hashCode()
    fun wipe() = password.fill('\u0000')
}

/**
 * One password entry as the core stores it. Passwords are character arrays so that they can be wiped; [wipe] does it.
 * Equality is by value, ids and passwords included.
 */
class Entry(
    val id: ByteArray,
    val title: String,
    val domains: List<String>,
    val packages: List<String>,
    val username: String,
    val password: CharArray,
    val notes: String,
    val createdMs: Long,
    val editedMs: Long,
    val lastFilledMs: Long?,
    val history: List<HistoryItem>,
) {
    fun copy(
        id: ByteArray = this.id, title: String = this.title, domains: List<String> = this.domains,
        packages: List<String> = this.packages, username: String = this.username, password: CharArray = this.password,
        notes: String = this.notes, createdMs: Long = this.createdMs, editedMs: Long = this.editedMs,
        lastFilledMs: Long? = this.lastFilledMs, history: List<HistoryItem> = this.history,
    ) = Entry(id, title, domains, packages, username, password, notes, createdMs, editedMs, lastFilledMs, history)

    fun wipe() {
        password.fill('\u0000')
        history.forEach(HistoryItem::wipe)
    }

    override fun equals(other: Any?) = other is Entry && id.contentEquals(other.id) && title == other.title &&
        domains == other.domains && packages == other.packages && username == other.username &&
        password.contentEquals(other.password) && notes == other.notes && createdMs == other.createdMs &&
        editedMs == other.editedMs && lastFilledMs == other.lastFilledMs && history == other.history

    override fun hashCode() = id.contentHashCode()

    /** Never prints the password. */
    override fun toString() = "Entry($title, ${username.length} chars of username, password hidden)"
}

/** A row of the list: no password, no notes. */
class Summary(
    val id: ByteArray,
    val title: String,
    val username: String,
    val domains: List<String>,
    val packages: List<String>,
    val editedMs: Long,
    val lastFilledMs: Long?,
)

enum class WeakReason(val code: Int) {
    TOO_SHORT(0), COMMON(1), PATTERN(2);

    companion object {
        fun of(code: Int): WeakReason? = values().firstOrNull { it.code == code }
    }
}

class Checkup(val reused: List<List<ByteArray>>, val weak: List<Pair<ByteArray, WeakReason>>)

/**
 * The binary forms the core speaks (vault-core/src/vault.rs `encode`, api.rs `summary` and `checkup`), all little
 * endian, strings as `u32 length | UTF-8`. Every decoder checks every length against the bytes that remain and returns
 * `null` for anything malformed, trailing bytes included. The Rust test `the_wire_format_is_pinned` and WireTest hold
 * the same bytes.
 */
object Wire {
    const val MAX_FIELD = 64 * 1024
    private const val MAX_LIST = 256
    private const val MAX_HISTORY = 10
    private const val MAX_SUMMARIES = 100_000

    private class Out {
        val bytes = ByteArrayOutputStream()
        fun u8(v: Int) = bytes.write(v)
        fun u32(v: Int) = repeat(4) { bytes.write((v ushr (8 * it)) and 0xff) }
        fun i64(v: Long) = repeat(8) { bytes.write(((v ushr (8 * it)) and 0xff).toInt()) }
        fun raw(b: ByteArray) = bytes.write(b)
        fun str(s: String) = utf8(StandardCharsets.UTF_8.encode(s))
        fun secret(c: CharArray) {
            val buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(c))
            utf8(buf)
        }

        private fun utf8(buf: ByteBuffer) {
            val b = ByteArray(buf.remaining())
            buf.get(b)
            u32(b.size)
            raw(b)
            b.fill(0)
            if (buf.hasArray()) buf.array().fill(0)
        }
    }

    private class In(private val b: ByteArray) {
        private var at = 0
        val remaining get() = b.size - at
        fun take(n: Int): ByteArray? {
            if (n < 0 || n > remaining) return null
            return b.copyOfRange(at, at + n).also { at += n }
        }
        fun u8() = take(1)?.get(0)?.toInt()?.and(0xff)
        fun u32(): Long? = take(4)?.let { (it[0].toLong() and 0xff) or ((it[1].toLong() and 0xff) shl 8) or ((it[2].toLong() and 0xff) shl 16) or ((it[3].toLong() and 0xff) shl 24) }
        fun i64(): Long? = take(8)?.let { a -> var v = 0L; for (i in 7 downTo 0) v = (v shl 8) or (a[i].toLong() and 0xff); v }
        fun id() = take(16)
        fun count(max: Int, minEach: Int): Int? {
            val n = u32() ?: return null
            if (n > max || n * minEach > remaining) return null
            return n.toInt()
        }
        fun str(): String? {
            val raw = field() ?: return null
            return decode(raw)?.let { String(it) }.also { raw.fill(0) }
        }
        fun secret(): CharArray? {
            val raw = field() ?: return null
            val chars = decode(raw)
            raw.fill(0)
            return chars?.let { it.copyOf().also { _ -> it.fill('\u0000') } }
        }
        fun finished() = remaining == 0

        private fun field(): ByteArray? {
            val len = u32() ?: return null
            if (len > MAX_FIELD) return null
            return take(len.toInt())
        }

        private fun decode(raw: ByteArray): CharArray? = try {
            val d = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val cb = d.decode(ByteBuffer.wrap(raw))
            CharArray(cb.remaining()).also { cb.get(it); if (cb.hasArray()) cb.array().fill('\u0000') }
        } catch (e: CharacterCodingException) {
            null
        }
    }

    fun encode(e: Entry): ByteArray {
        val o = Out()
        o.raw(e.id)
        o.str(e.title)
        o.u32(e.domains.size); e.domains.forEach(o::str)
        o.u32(e.packages.size); e.packages.forEach(o::str)
        o.str(e.username)
        o.secret(e.password)
        o.str(e.notes)
        o.i64(e.createdMs)
        o.i64(e.editedMs)
        if (e.lastFilledMs != null) { o.u8(1); o.i64(e.lastFilledMs) } else o.u8(0)
        o.u8(e.history.size)
        e.history.forEach { o.secret(it.password); o.i64(it.replacedMs) }
        return o.bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Entry? {
        val r = In(bytes)
        val id = r.id() ?: return null
        val title = r.str() ?: return null
        val domains = strings(r) ?: return null
        val packages = strings(r) ?: return null
        val username = r.str() ?: return null
        val password = r.secret() ?: return null
        val notes = r.str() ?: return null
        val created = r.i64() ?: return null
        val edited = r.i64() ?: return null
        val filled = when (r.u8()) { 0 -> null; 1 -> r.i64() ?: return null; else -> return null }
        val n = r.u8() ?: return null
        if (n > MAX_HISTORY) return null
        val history = ArrayList<HistoryItem>(n)
        repeat(n) { history.add(HistoryItem(r.secret() ?: return null, r.i64() ?: return null)) }
        if (!r.finished()) return null
        return Entry(id, title, domains, packages, username, password, notes, created, edited, filled, history)
    }

    private fun strings(r: In): List<String>? {
        val n = r.count(MAX_LIST, 4) ?: return null
        val out = ArrayList<String>(n)
        repeat(n) { out.add(r.str() ?: return null) }
        return out
    }

    fun decodeSummaries(bytes: ByteArray): List<Summary>? {
        val r = In(bytes)
        val n = r.count(MAX_SUMMARIES, 16 + 4 + 4 + 4 + 4 + 8 + 1) ?: return null
        val out = ArrayList<Summary>(n)
        repeat(n) {
            val id = r.id() ?: return null
            val title = r.str() ?: return null
            val username = r.str() ?: return null
            val domains = strings(r) ?: return null
            val packages = strings(r) ?: return null
            val edited = r.i64() ?: return null
            val filled = when (r.u8()) { 0 -> null; 1 -> r.i64() ?: return null; else -> return null }
            out.add(Summary(id, title, username, domains, packages, edited, filled))
        }
        return if (r.finished()) out else null
    }

    fun decodeCheckup(bytes: ByteArray): Checkup? {
        val r = In(bytes)
        val groups = r.count(MAX_SUMMARIES, 4) ?: return null
        val reused = ArrayList<List<ByteArray>>(groups)
        repeat(groups) {
            val m = r.count(MAX_SUMMARIES, 16) ?: return null
            val ids = ArrayList<ByteArray>(m)
            repeat(m) { ids.add(r.id() ?: return null) }
            reused.add(ids)
        }
        val k = r.count(MAX_SUMMARIES, 17) ?: return null
        val weak = ArrayList<Pair<ByteArray, WeakReason>>(k)
        repeat(k) {
            val id = r.id() ?: return null
            val reason = WeakReason.of(r.u8() ?: return null) ?: return null
            weak.add(id to reason)
        }
        return if (r.finished()) Checkup(reused, weak) else null
    }

    /**
     * What a call that merges entries answers: [words] counts of `u32`, then the new vault file (never empty). `null` for a
     * count that does not fit an Int, a missing file or too few bytes.
     */
    fun decodeMergeReply(bytes: ByteArray, words: Int): MergeReply? {
        val r = In(bytes)
        val counts = List(words) { val n = r.u32() ?: return null; if (n > Int.MAX_VALUE) return null; n.toInt() }
        val file = r.take(r.remaining) ?: return null
        return if (file.isEmpty()) null else MergeReply(counts, file)
    }
}

/** The counts of a merge and the new vault file that came with them. */
class MergeReply(val counts: List<Int>, val file: ByteArray)
