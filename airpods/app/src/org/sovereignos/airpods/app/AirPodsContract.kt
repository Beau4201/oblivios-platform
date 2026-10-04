package org.sovereignos.airpods.app

/** Names shared between the services, the icon provider and the metadata writer. */
internal object AirPodsContract {
    const val ACTION_DEVICE_SETTINGS = "org.sovereignos.airpods.action.DEVICE_SETTINGS"
    const val SETTINGS_PACKAGE = "com.android.settings"

    // Our own page (Plan 7B); the link row only exists when the layout enables ALL_SETTINGS_PAGE.
    const val ALL_SETTINGS_ACTIVITY = "org.sovereignos.airpods.app.AllSettingsActivity"

    const val ICON_LEFT = "content://org.sovereignos.airpods.icons/v1/left"
    const val ICON_RIGHT = "content://org.sovereignos.airpods.icons/v1/right"
    const val ICON_CASE = "content://org.sovereignos.airpods.icons/v1/case"

    // BluetoothDevice.METADATA_* values (packages/modules/Bluetooth/framework/java/android/
    // bluetooth/BluetoothDevice.java:715-846). Literals because METADATA_FAST_PAIR_CUSTOMIZED_
    // FIELDS is @Hide and not in the system API stubs.
    const val KEY_IS_UNTETHERED_HEADSET = 6
    const val KEY_LEFT_ICON = 7
    const val KEY_RIGHT_ICON = 8
    const val KEY_CASE_ICON = 9
    const val KEY_LEFT_BATTERY = 10
    const val KEY_RIGHT_BATTERY = 11
    const val KEY_CASE_BATTERY = 12
    const val KEY_LEFT_CHARGING = 13
    const val KEY_RIGHT_CHARGING = 14
    const val KEY_CASE_CHARGING = 15
    const val KEY_FAST_PAIR_CUSTOMIZED_FIELDS = 25
}
