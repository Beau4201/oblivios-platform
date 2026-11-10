// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.hosttest

import org.oblivios.passwords.core.Code
import org.oblivios.passwords.core.CreatedVault
import org.oblivios.passwords.core.Reply
import org.oblivios.passwords.core.handleOrCode

fun main() {
    replyStatusAndPayload()
    anEmptyOrMissingReplyIsAnInternalError()
    createdVaultParsing()
    wipeOverwritesTheAnswerAndKeepsTheStatus()
    handlesAndCodes()
    theCodesAreTheWireContract()
    Check.done("ReplyTest")
}

private fun replyStatusAndPayload() {
    val ok = Reply.of(byteArrayOf(0, 1, 2, 3))
    Check.that("status 0 is ok", ok.ok && ok.status == 0)
    Check.bytes("the payload follows the status byte", byteArrayOf(1, 2, 3), ok.payload())
    val bare = Reply.of(byteArrayOf(0))
    Check.that("ok with an empty payload", bare.ok && bare.payload().isEmpty())
    val bad = Reply.of(byteArrayOf(Code.BAD_CREDENTIALS.toByte()))
    Check.that("a non-zero status is an error", !bad.ok && bad.status == Code.BAD_CREDENTIALS)
    Check.that("an error reply has no payload", bad.payload().isEmpty())
    Check.that("an error reply with trailing bytes still reports no payload", !Reply.of(byteArrayOf(2, 9, 9)).ok && Reply.of(byteArrayOf(2, 9, 9)).payload().isEmpty())
}

private fun anEmptyOrMissingReplyIsAnInternalError() {
    Check.equal("null: the JVM could not allocate the answer", Code.INTERNAL, Reply.of(null).status)
    Check.equal("empty", Code.INTERNAL, Reply.of(ByteArray(0)).status)
    Check.that("neither is ok", !Reply.of(null).ok && !Reply.of(ByteArray(0)).ok)
}

private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

private fun createdVaultParsing() {
    val file = ByteArray(10) { (it + 1).toByte() }
    val key = ByteArray(32) { (100 + it).toByte() }
    val good = CreatedVault.parse(le(10) + file + key)
    Check.that("a well-formed payload parses", good != null)
    Check.bytes("the file", file, good!!.file)
    Check.bytes("the key", key, good.vaultKey)
    Check.that("a payload one byte short is refused", CreatedVault.parse((le(10) + file + key).copyOf(10 + 4 + 31)) == null)
    Check.that("a payload one byte long is refused", CreatedVault.parse(le(10) + file + key + byteArrayOf(0)) == null)
    Check.that("a length larger than the data is refused", CreatedVault.parse(le(1_000_000) + file + key) == null)
    Check.that("a negative length is refused", CreatedVault.parse(le(-1) + file + key) == null)
    Check.that("less than a header is refused", CreatedVault.parse(ByteArray(3)) == null)
    Check.that("an empty file is refused", CreatedVault.parse(le(0) + key) == null)
}

private fun wipeOverwritesTheAnswerAndKeepsTheStatus() {
    // P-08: the answer of the core can hold a vault key or a password; payload() is a copy, so the answer needs its own wipe.
    val raw = byteArrayOf(0, 7, 8, 9)
    val r = Reply.of(raw)
    val copy = r.payload()
    r.wipe()
    Check.that("the answer array is zero", raw.all { it == 0.toByte() })
    Check.bytes("a copy taken before is not touched", byteArrayOf(7, 8, 9), copy)
    Check.that("the status stays", r.ok && r.status == 0)
    Check.that("a payload after the wipe is zeros", r.payload().all { it == 0.toByte() })
    val err = byteArrayOf(Code.BAD_CREDENTIALS.toByte(), 5)
    Reply.of(err).wipe()
    Check.that("an error answer is wiped too", err.all { it == 0.toByte() })
}

private fun handlesAndCodes() {
    Check.equal("a positive handle is a handle", 42L, handleOrCode(42L).handle)
    Check.that("and not an error", handleOrCode(42L).code == Code.OK)
    Check.equal("a negative number is the code", Code.BAD_CREDENTIALS, handleOrCode(-1L).code)
    Check.equal("with no handle", 0L, handleOrCode(-1L).handle)
    Check.equal("code 6", Code.DEAD_HANDLE, handleOrCode(-6L).code)
    Check.equal("zero never is a handle", Code.INTERNAL, handleOrCode(0L).code)
}

private fun theCodesAreTheWireContract() {
    Check.equal(
        "the numbers of vault-core/src/api.rs, never changed",
        listOf(0, 1, 2, 3, 4, 5, 6, 7, 255),
        listOf(Code.OK, Code.BAD_CREDENTIALS, Code.CORRUPT, Code.UNSUPPORTED_VERSION, Code.TOO_LARGE, Code.IO, Code.DEAD_HANDLE, Code.BAD_ARGUMENT, Code.INTERNAL),
    )
    Check.equal("a status byte above 127 is read as unsigned", 128, Reply.of(byteArrayOf(0x80.toByte())).status)
    Check.equal("0xff", 255, Reply.of(byteArrayOf(-1)).status)
}
