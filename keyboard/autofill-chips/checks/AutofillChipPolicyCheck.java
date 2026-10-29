package com.android.inputmethod.latin.suggestions;

/** Host checks of AutofillChipPolicy: plain assertions in a main; prints "host checks passed: name N" last. */
public final class AutofillChipPolicyCheck {
    private static int sCount;

    private static void that(final String name, final boolean ok) {
        sCount++;
        if (!ok) throw new AssertionError("FAILED: " + name);
    }

    private static void equal(final String name, final int expected, final int actual) {
        sCount++;
        if (expected != actual) throw new AssertionError("FAILED: " + name + ": expected " + expected + ", got " + actual);
    }

    public static void main(final String[] args) {
        visibleCountIsCapped();
        aloneIsOnlyForOneChip();
        aResponseIsCurrentUntilSomethingReplacesIt();
        typingEndsTheOfferUntilTheNextRequest();
        aNewRequestDropsWhatWasInFlight();
        clearingDropsWhatIsInFlight();
        anEmptyResponseShowsNothingAndDropsWhatWasInFlight();
        System.out.println("host checks passed: AutofillChipPolicy " + sCount);
    }

    private static void visibleCountIsCapped() {
        equal("none stays none", 0, AutofillChipPolicy.visibleCount(0));
        equal("a negative count is none", 0, AutofillChipPolicy.visibleCount(-3));
        equal("one", 1, AutofillChipPolicy.visibleCount(1));
        equal("the cap itself", AutofillChipPolicy.MAX_CHIPS, AutofillChipPolicy.visibleCount(AutofillChipPolicy.MAX_CHIPS));
        equal("one over the cap", AutofillChipPolicy.MAX_CHIPS, AutofillChipPolicy.visibleCount(AutofillChipPolicy.MAX_CHIPS + 1));
        equal("the cap is five", 5, AutofillChipPolicy.MAX_CHIPS);
    }

    private static void aloneIsOnlyForOneChip() {
        that("zero is not alone", !AutofillChipPolicy.alone(0));
        that("one is alone", AutofillChipPolicy.alone(1));
        that("two share", !AutofillChipPolicy.alone(2));
    }

    private static void aResponseIsCurrentUntilSomethingReplacesIt() {
        final AutofillChipPolicy p = new AutofillChipPolicy();
        p.onNewRequest();
        final int first = p.begin(2);
        that("the first response has a token", first != AutofillChipPolicy.NONE);
        that("and is current", p.isCurrent(first));
        final int second = p.begin(1);
        that("a newer response replaces it", !p.isCurrent(first));
        that("and is itself current", p.isCurrent(second));
        that("NONE is never current", !p.isCurrent(AutofillChipPolicy.NONE));
        that("also on a policy that was never used", !new AutofillChipPolicy().isCurrent(AutofillChipPolicy.NONE));
    }

    private static void typingEndsTheOfferUntilTheNextRequest() {
        final AutofillChipPolicy p = new AutofillChipPolicy();
        p.onNewRequest();
        final int token = p.begin(2);
        p.onTyped();
        that("typing is remembered", p.typed());
        that("typing drops what was in flight", !p.isCurrent(token));
        equal("a response after typing shows nothing", AutofillChipPolicy.NONE, p.begin(2));
        equal("also after the field was emptied again", AutofillChipPolicy.NONE, p.begin(1));
        p.onNewRequest();
        that("a new request forgets the typing", !p.typed());
        that("and offers again", p.begin(1) != AutofillChipPolicy.NONE);
    }

    private static void aNewRequestDropsWhatWasInFlight() {
        final AutofillChipPolicy p = new AutofillChipPolicy();
        p.onNewRequest();
        final int token = p.begin(2);
        p.onNewRequest();
        that("a response of the old field is not shown", !p.isCurrent(token));
    }

    private static void clearingDropsWhatIsInFlight() {
        final AutofillChipPolicy p = new AutofillChipPolicy();
        p.onNewRequest();
        final int token = p.begin(3);
        p.onCleared();
        that("a cleared strip drops the response", !p.isCurrent(token));
        that("clearing is not typing", !p.typed());
        that("and the next response is shown", p.begin(1) != AutofillChipPolicy.NONE);
    }

    private static void anEmptyResponseShowsNothingAndDropsWhatWasInFlight() {
        final AutofillChipPolicy p = new AutofillChipPolicy();
        p.onNewRequest();
        final int token = p.begin(2);
        equal("an empty response has no token", AutofillChipPolicy.NONE, p.begin(0));
        equal("a negative count has no token", AutofillChipPolicy.NONE, p.begin(-1));
        that("and drops the one in flight", !p.isCurrent(token));
    }
}
