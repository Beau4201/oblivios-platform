// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.util.concurrent.atomic.AtomicInteger;

public final class PendingLimitCheck {
    public static void main(String[] args) throws Exception {
        PendingLimit p = new PendingLimit(3);
        Check.isTrue(p.tryAcquire() && p.tryAcquire() && p.tryAcquire(), "three fit");
        Check.isTrue(!p.tryAcquire(), "the fourth is refused");
        Check.eq(3, p.held(), "a refusal holds nothing");
        p.release();
        Check.isTrue(p.tryAcquire(), "a released place is free again");
        Check.isTrue(!p.tryAcquire(), "and the limit holds again");
        Check.throwsA(IllegalArgumentException.class, () -> new PendingLimit(0), "a limit of 0");

        // Many threads at once: the places in use never exceed the limit, and all are returned.
        PendingLimit q = new PendingLimit(8);
        AtomicInteger inUse = new AtomicInteger();
        AtomicInteger worst = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        Thread[] ts = new Thread[16];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                for (int n = 0; n < 50_000; n++) {
                    if (q.tryAcquire()) {
                        worst.accumulateAndGet(inUse.incrementAndGet(), Math::max);
                        inUse.decrementAndGet();
                        q.release();
                    } else {
                        refused.incrementAndGet();
                    }
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        Check.isTrue(worst.get() <= 8, "never more than the limit in use at once: " + worst.get());
        Check.eq(0, q.held(), "every place is returned");
        Check.isTrue(refused.get() > 0, "the stress did reach the limit");
        Check.passed("PendingLimitCheck");
    }
}
