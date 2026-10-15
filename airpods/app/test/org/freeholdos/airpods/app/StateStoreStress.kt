// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.app

import org.freeholdos.airpods.ui.AirPodsUiState

/**
 * Host check (no Android classes): eight threads each apply the same read-modify-write 20000 times
 * through AirPodsStateStore.transform. If a change were lost, the final value would differ from the
 * count of changes. Run with tools/run-store-stress.sh; the check counts only if removing the lock
 * makes it fail (the script's second run does that).
 */
object StateStoreStress {
    @JvmStatic
    fun main(args: Array<String>) {
        val threads = 8
        val perThread = 20000
        AirPodsStateStore.update(AirPodsUiState.Builder().address("AA:BB:CC:DD:EE:FF").percents(0, -1, -1).build())
        var notified = java.util.concurrent.atomic.AtomicInteger()
        AirPodsStateStore.addListener { _, _ -> notified.incrementAndGet() }
        val workers = (1..threads).map {
            Thread {
                repeat(perThread) {
                    AirPodsStateStore.transform { s ->
                        s.toBuilder().percents((s.leftPercent + 1) % 101, s.rightPercent, s.casePercent).build()
                    }
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        val expected = (threads * perThread) % 101
        val actual = AirPodsStateStore.state.leftPercent
        check(actual == expected) { "lost updates: expected $expected, got $actual" }
        check(notified.get() == threads * perThread) { "listeners saw ${notified.get()} changes, expected ${threads * perThread}" }
        println("StateStoreStress: ok ($expected)")
    }
}
