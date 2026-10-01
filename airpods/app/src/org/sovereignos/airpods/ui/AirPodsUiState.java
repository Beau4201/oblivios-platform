package org.sovereignos.airpods.ui;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/**
 * Immutable, validated snapshot of what the AirPods UI shows. Percent -1 means unknown. Anything
 * the device or the debug input reports is checked here once, before it reaches the Settings page
 * or the Bluetooth metadata.
 */
public final class AirPodsUiState {
    /** What the exact model and firmware supports (design 5.1: unsupported controls are hidden). */
    public enum Feature {
        LISTENING_MODE,
        ADAPTIVE_MODE,
        EAR_DETECTION,
        CONVERSATION_AWARENESS,
        ALL_SETTINGS_PAGE
    }

    private static final Pattern ADDRESS = Pattern.compile("([0-9A-F]{2}:){5}[0-9A-F]{2}");

    /** Upper-case address of the recognised, connected AirPods, or null. */
    public final String address;
    public final int leftPercent;
    public final int rightPercent;
    public final int casePercent;
    public final boolean leftCharging;
    public final boolean rightCharging;
    public final boolean caseCharging;
    /** Null when unknown. */
    public final ListeningMode listeningMode;
    public final boolean earDetection;
    public final boolean conversationAwareness;
    public final Set<Feature> supported;

    public static final AirPodsUiState DISCONNECTED = new Builder().build();

    private AirPodsUiState(Builder b) {
        if (b.address != null && !ADDRESS.matcher(b.address).matches()) {
            throw new IllegalArgumentException("bad address");
        }
        for (int p : new int[] {b.left, b.right, b.caze}) {
            if (p < -1 || p > 100) throw new IllegalArgumentException("bad percent");
        }
        if (b.mode == ListeningMode.ADAPTIVE && !b.features.contains(Feature.ADAPTIVE_MODE)) {
            throw new IllegalArgumentException("adaptive mode not supported");
        }
        address = b.address;
        leftPercent = b.left;
        rightPercent = b.right;
        casePercent = b.caze;
        leftCharging = b.leftCharging;
        rightCharging = b.rightCharging;
        caseCharging = b.caseCharging;
        listeningMode = b.mode;
        earDetection = b.ear;
        conversationAwareness = b.conversation;
        Set<Feature> copy = EnumSet.noneOf(Feature.class);
        copy.addAll(b.features);
        supported = Collections.unmodifiableSet(copy);
    }

    public boolean isConnected() {
        return address != null;
    }

    public Builder toBuilder() {
        return new Builder()
                .address(address)
                .percents(leftPercent, rightPercent, casePercent)
                .charging(leftCharging, rightCharging, caseCharging)
                .listeningMode(listeningMode)
                .earDetection(earDetection)
                .conversationAwareness(conversationAwareness)
                .features(supported.toArray(new Feature[0]));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AirPodsUiState)) return false;
        AirPodsUiState s = (AirPodsUiState) o;
        return Objects.equals(address, s.address)
                && leftPercent == s.leftPercent
                && rightPercent == s.rightPercent
                && casePercent == s.casePercent
                && leftCharging == s.leftCharging
                && rightCharging == s.rightCharging
                && caseCharging == s.caseCharging
                && listeningMode == s.listeningMode
                && earDetection == s.earDetection
                && conversationAwareness == s.conversationAwareness
                && supported.equals(s.supported);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, leftPercent, rightPercent, casePercent, leftCharging,
                rightCharging, caseCharging, listeningMode, earDetection, conversationAwareness,
                supported);
    }

    public static final class Builder {
        private String address;
        private int left = -1;
        private int right = -1;
        private int caze = -1;
        private boolean leftCharging;
        private boolean rightCharging;
        private boolean caseCharging;
        private ListeningMode mode;
        private boolean ear;
        private boolean conversation;
        private final Set<Feature> features = EnumSet.noneOf(Feature.class);

        public Builder address(String a) {
            address = a;
            return this;
        }

        public Builder percents(int l, int r, int c) {
            left = l;
            right = r;
            caze = c;
            return this;
        }

        public Builder charging(boolean l, boolean r, boolean c) {
            leftCharging = l;
            rightCharging = r;
            caseCharging = c;
            return this;
        }

        public Builder listeningMode(ListeningMode m) {
            mode = m;
            return this;
        }

        public Builder earDetection(boolean v) {
            ear = v;
            return this;
        }

        public Builder conversationAwareness(boolean v) {
            conversation = v;
            return this;
        }

        public Builder feature(Feature f) {
            features.add(f);
            return this;
        }

        /** Replaces the whole set. */
        public Builder features(Feature... fs) {
            features.clear();
            Collections.addAll(features, fs);
            return this;
        }

        public AirPodsUiState build() {
            return new AirPodsUiState(this);
        }
    }
}
