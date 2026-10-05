package org.sovereignos.airpods.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sovereignos.airpods.ui.AirPodsUiState.Feature;

/**
 * Items of Settings' own device page, in order. Built-in items name a Settings preference key
 * (reorder only); app items are served by our provider. Items without a group stay flat; items
 * with the same consecutive group index form one titled category (Settings reads the titles from
 * the config's group list, so GROUP_LISTENING, GROUP_COMFORT and GROUP_AUDIO are indexes into that list).
 */
public final class NativePageLayout {
    public enum Kind {
        BUILTIN,
        LISTENING_TOGGLE,
        EAR_DETECTION_SWITCH,
        CONVERSATION_SWITCH,
        ALL_SETTINGS_LINK
    }

    // DeviceSettingId values of Settings (SettingsLib devicesettings/DeviceSettingId.java).
    public static final int ID_ADVANCED_HEADER = 2;
    public static final int ID_ACTION_BUTTONS = 6;
    public static final int ID_PROFILES = 14;
    public static final int ID_FOOTER = 19;
    public static final int ID_LISTENING = 1001; // DEVICE_SETTING_ID_ANC
    // App-chosen ids, outside every id Settings defines or expands (3001 and 3100 expand to 15 ids).
    public static final int ID_EAR = 50001;
    public static final int ID_CONVERSATION = 50002;
    public static final int ID_ALL_SETTINGS = 50003;

    public static final int GROUP_LISTENING = 0;
    public static final int GROUP_COMFORT = 1;
    public static final int GROUP_AUDIO = 2;

    public static final class Item {
        public final int settingId;
        public final Kind kind;
        public final String builtinKey;
        public final Integer groupIndex;

        Item(int settingId, Kind kind, String builtinKey, Integer groupIndex) {
            this.settingId = settingId;
            this.kind = kind;
            this.builtinKey = builtinKey;
            this.groupIndex = groupIndex;
        }
    }

    private NativePageLayout() {}

    public static List<Item> items(AirPodsUiState s) {
        List<Item> out = new ArrayList<>();
        out.add(builtin(ID_ADVANCED_HEADER, "advanced_bluetooth_device_header"));
        // Forget and Disconnect live here; hiding them would remove the only unpair path.
        out.add(builtin(ID_ACTION_BUTTONS, "action_buttons"));
        if (s.supported.contains(Feature.LISTENING_MODE)) {
            out.add(new Item(ID_LISTENING, Kind.LISTENING_TOGGLE, null, GROUP_LISTENING));
        }
        if (s.supported.contains(Feature.EAR_DETECTION)) {
            out.add(new Item(ID_EAR, Kind.EAR_DETECTION_SWITCH, null, GROUP_COMFORT));
        }
        if (s.supported.contains(Feature.CONVERSATION_AWARENESS)) {
            out.add(new Item(ID_CONVERSATION, Kind.CONVERSATION_SWITCH, null, GROUP_COMFORT));
        }
        if (s.supported.contains(Feature.ALL_SETTINGS_PAGE)) {
            // Flat: a link after the Comfort group would repeat its title.
            out.add(new Item(ID_ALL_SETTINGS, Kind.ALL_SETTINGS_LINK, null, null));
        }
        // A titled group of its own: Settings draws the title and the spacing above the profile switches.
        out.add(new Item(ID_PROFILES, Kind.BUILTIN, "bluetooth_profiles", GROUP_AUDIO));
        out.add(builtin(ID_FOOTER, "device_details_footer"));
        return Collections.unmodifiableList(out);
    }

    private static Item builtin(int id, String key) {
        return new Item(id, Kind.BUILTIN, key, null);
    }
}
