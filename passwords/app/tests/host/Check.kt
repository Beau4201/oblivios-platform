// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.hosttest

/**
 * The whole test framework of the host checks: plain assertions in a `main`. A failed check throws, so `java` exits
 * non-zero, and `run-host-checks.sh` additionally requires the line `host checks passed: N` of every test class.
 */
object Check {
    var count = 0
        private set

    fun that(name: String, condition: Boolean) {
        count++
        if (!condition) throw AssertionError("FAILED: $name")
    }

    fun <T> equal(name: String, expected: T, actual: T) {
        count++
        if (expected != actual) throw AssertionError("FAILED: $name\n  expected: $expected\n  actual:   $actual")
    }

    fun bytes(name: String, expected: ByteArray, actual: ByteArray) {
        count++
        if (!expected.contentEquals(actual)) throw AssertionError("FAILED: $name (byte arrays differ)")
    }

    inline fun <reified E : Throwable> throws(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) {
                that(name, true)
                return
            }
            throw AssertionError("FAILED: $name: expected ${E::class.java.simpleName}, got $e")
        }
        throw AssertionError("FAILED: $name: nothing was thrown")
    }

    fun done(suite: String) = println("host checks passed: $suite $count")
}
