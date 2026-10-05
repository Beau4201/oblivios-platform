package org.sovereignos.airpods.ui;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.sovereignos.airpods.ui.AirPodsUiState.Feature;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/** Host-runnable check: javac + java -ea with airpods-core.jar on the class path. */
public final class UiLogicCheck {
    private static final String ADDR = "AA:BB:CC:DD:EE:FF";

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void rejects(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("accepted: " + what);
    }

    private static String s(byte[] b) {
        return new String(b, StandardCharsets.US_ASCII);
    }

    private static AirPodsUiState.Builder full() {
        return new AirPodsUiState.Builder()
                .address(ADDR)
                .percents(80, 82, 100)
                .charging(false, false, true)
                .listeningMode(ListeningMode.ANC)
                .earDetection(true)
                .conversationAwareness(false)
                .features(Feature.LISTENING_MODE, Feature.EAR_DETECTION, Feature.CONVERSATION_AWARENESS);
    }

    public static void main(String[] args) {
        // ---- AirPodsUiState
        AirPodsUiState st = full().build();
        check(st.isConnected() && st.leftPercent == 80 && st.casePercent == 100, "state fields");
        check(!AirPodsUiState.DISCONNECTED.isConnected(), "disconnected default");
        check(st.equals(full().build()) && st.hashCode() == full().build().hashCode(), "equality");
        check(!st.equals(full().percents(81, 82, 100).build()), "inequality");
        rejects(() -> full().address("aa:bb:cc:dd:ee:ff").build(), "lower-case address");
        rejects(() -> full().address("AA:BB:CC:DD:EE").build(), "short address");
        rejects(() -> full().percents(101, 0, 0).build(), "percent 101");
        rejects(() -> full().percents(0, -2, 0).build(), "percent -2");
        check(st.duckPercent == DuckLevels.DEFAULT && DuckLevels.DEFAULT == 50, "default lowered volume");
        check(full().duckPercent(20).build().duckPercent == 20 && !st.equals(full().duckPercent(20).build()), "lowered volume is part of the state");
        rejects(() -> full().duckPercent(0).build(), "lowered volume 0");
        check(full().duckPercent(55).build().duckPercent == 55 && full().duckPercent(10).build().duckPercent == 10
                && full().duckPercent(90).build().duckPercent == 90, "lowered volume is any value from 10 to 90");
        rejects(() -> full().duckPercent(9).build(), "lowered volume 9");
        rejects(() -> full().duckPercent(91).build(), "lowered volume 91");
        check(full().duckPercent(70).build().toBuilder().build().duckPercent == 70, "toBuilder keeps the lowered volume");
        rejects(() -> new SessionSeed(true, 91, -1, false), "seed with a bad lowered volume");
        rejects(() -> new SessionSeed(true, 50, 101, false), "seed with a bad case percent");
        rejects(() -> new SessionSeed(true, 50, -2, false), "seed with case percent -2");
        rejects(() -> full().listeningMode(ListeningMode.ADAPTIVE).build(), "adaptive without feature");
        check(full().feature(Feature.ADAPTIVE_MODE).listeningMode(ListeningMode.ADAPTIVE).build()
                .listeningMode == ListeningMode.ADAPTIVE, "adaptive with feature");
        check(full().percents(-1, -1, -1).build().leftPercent == -1, "unknown percent allowed");

        // ---- MetadataEncoder
        check(s(MetadataEncoder.percent(0)).equals("0") && s(MetadataEncoder.percent(100)).equals("100"), "percent");
        rejects(() -> MetadataEncoder.percent(-1), "percent -1");
        rejects(() -> MetadataEncoder.percent(101), "percent 101 encoder");
        check(MetadataEncoder.percentOrClear(-1).length == 0, "unknown clears");
        check(s(MetadataEncoder.percentOrClear(85)).equals("85"), "known percent");
        check(s(MetadataEncoder.bool(true)).equals("true") && s(MetadataEncoder.bool(false)).equals("false"), "bool");
        check(s(MetadataEncoder.uri("content://org.sovereignos.airpods.icons/v1/left"))
                .equals("content://org.sovereignos.airpods.icons/v1/left"), "uri");
        rejects(() -> MetadataEncoder.uri("file:///data/x"), "non-content uri");
        rejects(() -> MetadataEncoder.uri("content://a/b c"), "uri with space");
        rejects(() -> MetadataEncoder.uri("content://a/b\n"), "uri with newline");
        String tags = s(MetadataEncoder.configTags("org.sovereignos.airpods",
                "org.sovereignos.airpods.app.AirPodsConfigService", "org.sovereignos.airpods.action.DEVICE_SETTINGS"));
        check(tags.equals("<DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>org.sovereignos.airpods</DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>"
                + "<DEVICE_SETTINGS_CONFIG_CLASS>org.sovereignos.airpods.app.AirPodsConfigService</DEVICE_SETTINGS_CONFIG_CLASS>"
                + "<DEVICE_SETTINGS_CONFIG_ACTION>org.sovereignos.airpods.action.DEVICE_SETTINGS</DEVICE_SETTINGS_CONFIG_ACTION>"), "tags exact");
        rejects(() -> MetadataEncoder.configTags("a<b", "c", "d"), "tag injection");
        rejects(() -> MetadataEncoder.configTags("a", "", "d"), "empty tag value");
        rejects(() -> MetadataEncoder.configTags("a", "c\nd", "e"), "newline in tag value");
        rejects(() -> MetadataEncoder.configTags("x".repeat(2100), "c", "d"), "over 2048 bytes");

        // ---- ListeningModeToggles
        List<ListeningMode> three = ListeningModeToggles.modes(st);
        check(three.equals(List.of(ListeningMode.OFF, ListeningMode.TRANSPARENCY, ListeningMode.ANC)), "three modes in design order");
        AirPodsUiState ad = full().feature(Feature.ADAPTIVE_MODE).build();
        check(ListeningModeToggles.modes(ad).equals(List.of(ListeningMode.OFF, ListeningMode.TRANSPARENCY, ListeningMode.ADAPTIVE, ListeningMode.ANC)), "adaptive sits before noise cancel");
        check(ListeningModeToggles.indexOf(ListeningModeToggles.modes(ad), ListeningMode.ANC) == 3, "noise cancel is last with adaptive");
        check(ListeningModeToggles.indexOf(three, ListeningMode.ANC) == 2, "index of ANC");
        check(ListeningModeToggles.indexOf(three, ListeningMode.ADAPTIVE) == -1, "adaptive absent");
        check(ListeningModeToggles.indexOf(three, null) == -1, "null mode");
        check(ListeningModeToggles.modeAt(three, 1) == ListeningMode.TRANSPARENCY, "modeAt");
        check(ListeningModeToggles.modeAt(three, 3) == null && ListeningModeToggles.modeAt(three, -1) == null, "modeAt bounds");

        // ---- NativePageLayout
        List<NativePageLayout.Item> items = NativePageLayout.items(st);
        int[] ids = items.stream().mapToInt(i -> i.settingId).toArray();
        check(java.util.Arrays.equals(ids, new int[] {2, 6, 1001, 50001, 50002, 14, 19}), "order without link: " + java.util.Arrays.toString(ids));
        check(items.get(0).kind == NativePageLayout.Kind.BUILTIN && "advanced_bluetooth_device_header".equals(items.get(0).builtinKey), "header key");
        check("action_buttons".equals(items.get(1).builtinKey), "action buttons kept");
        check(items.get(2).kind == NativePageLayout.Kind.LISTENING_TOGGLE && items.get(2).groupIndex == NativePageLayout.GROUP_LISTENING, "toggle group 0");
        check(items.get(3).groupIndex == NativePageLayout.GROUP_COMFORT && items.get(4).groupIndex == NativePageLayout.GROUP_COMFORT,
                "the two switches in group 1");
        check(items.get(5).groupIndex == NativePageLayout.GROUP_AUDIO && items.get(6).groupIndex == null,
                "the profiles sit in the Audio group, the footer has no group");
        check(NativePageLayout.GROUP_AUDIO == 2, "three groups: listening, comfort, audio");
        List<NativePageLayout.Item> withLink = NativePageLayout.items(full().feature(Feature.ALL_SETTINGS_PAGE).build());
        check(withLink.size() == 8 && withLink.get(5).settingId == NativePageLayout.ID_ALL_SETTINGS
                && withLink.get(5).kind == NativePageLayout.Kind.ALL_SETTINGS_LINK && withLink.get(5).groupIndex == null, "flat link after the switches");
        // The page of a recognised pair that has no session still lists every row (AIR-194): Settings reads the list once.
        AirPodsUiState layout = AirPodsUiState.layoutOnly();
        check(!layout.isConnected() && layout.address == null, "the layout state is not connected and has no address");
        check(java.util.Arrays.equals(NativePageLayout.items(layout).stream().mapToInt(i -> i.settingId).toArray(),
                withLink.stream().mapToInt(i -> i.settingId).toArray()),
                "the layout state lists the same rows as a connected pair with the link");
        AirPodsUiState bare = full().features().build();
        check(NativePageLayout.items(bare).size() == 4, "no features: only builtins");
        AirPodsUiState onlyEar = full().features(Feature.EAR_DETECTION).build();
        check(NativePageLayout.items(onlyEar).stream().noneMatch(i -> i.settingId == NativePageLayout.ID_CONVERSATION), "unsupported switch hidden, not disabled");

        // ---- DebugStateParser
        AirPodsUiState p = DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;l=80;r=82;c=100;lc=0;rc=0;cc=1;mode=ANC;ear=1;conv=0;feat=LISTENING_MODE,EAR_DETECTION,CONVERSATION_AWARENESS");
        check(p.equals(st), "parser round trip");
        check(!DebugStateParser.parse(null).isConnected() && !DebugStateParser.parse("").isConnected() && !DebugStateParser.parse("off").isConnected(), "empty and off");
        check(DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;mode=-").listeningMode == null, "mode dash is unknown");
        rejects(() -> DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;l=abc"), "non-numeric percent");
        rejects(() -> DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;l=101"), "percent out of range via parser");
        rejects(() -> DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;bogus=1"), "unknown key");
        rejects(() -> DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;mode=LOUD"), "unknown mode");
        rejects(() -> DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;feat=FLY"), "unknown feature");
        rejects(() -> DebugStateParser.parse("addr=AA:BB:CC:DD:EE:FF;l"), "missing value");
        rejects(() -> DebugStateParser.parse("x".repeat(600)), "over-long input");

        System.out.println("UiLogicCheck OK");
    }
}
