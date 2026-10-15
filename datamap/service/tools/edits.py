# SPDX-License-Identifier: Apache-2.0
# Exact edits to existing files, as (repo, path, old, new). OLD must occur exactly once (apply-edits.py).
FB = "frameworks/base"
EDITS = [
    # ---- the fourth slot for the event callbacks ----
    (FB, "core/java/android/net/INetdEventCallback.aidl",
     "    const int CALLBACK_CALLER_NETWORK_WATCHLIST = 2;\n",
     "    const int CALLBACK_CALLER_NETWORK_WATCHLIST = 2;\n"
     "    // The Data Map service (FreeholdOS). It has a slot of its own, so that it never shares one with\n"
     "    // another consumer of the same events.\n"
     "    const int CALLBACK_CALLER_DATA_MAP = 3;\n"),
    (FB, "services/core/java/com/android/server/connectivity/NetdEventListenerService.java",
     "     * There are only 3 possible callbacks.\n",
     "     * There are only 4 possible callbacks.\n"),
    (FB, "services/core/java/com/android/server/connectivity/NetdEventListenerService.java",
     "     * mNetdEventCallbackList[CALLBACK_CALLER_NETWORK_WATCHLIST]\n"
     "     * Callback registered/unregistered by NetworkWatchlistService.\n"
     "     */\n",
     "     * mNetdEventCallbackList[CALLBACK_CALLER_NETWORK_WATCHLIST]\n"
     "     * Callback registered/unregistered by NetworkWatchlistService.\n"
     "     *\n"
     "     * mNetdEventCallbackList[CALLBACK_CALLER_DATA_MAP]\n"
     "     * Callback registered/unregistered by DataMapService (FreeholdOS).\n"
     "     */\n"),
    (FB, "services/core/java/com/android/server/connectivity/NetdEventListenerService.java",
     "        INetdEventCallback.CALLBACK_CALLER_NETWORK_WATCHLIST\n    };\n",
     "        INetdEventCallback.CALLBACK_CALLER_NETWORK_WATCHLIST,\n"
     "        INetdEventCallback.CALLBACK_CALLER_DATA_MAP\n    };\n"),
    # ---- the permission ----
    (FB, "core/res/AndroidManifest.xml",
     '        android:name="android.permission.PREFER_FULLSCREEN_IN_NEW_TASK"\n'
     '        android:protectionLevel="signature|privileged"\n'
     '        android:featureFlag="com.android.window.flags.enable_fullscreen_in_new_task_preference" />\n',
     '        android:name="android.permission.PREFER_FULLSCREEN_IN_NEW_TASK"\n'
     '        android:protectionLevel="signature|privileged"\n'
     '        android:featureFlag="com.android.window.flags.enable_fullscreen_in_new_task_preference" />\n'
     '\n'
     '    <!-- Allows the FreeholdOS Data Map app to read which services each app contacts, for its own\n'
     '         user. Only that app holds it (ADR-157). -->\n'
     '    <permission android:name="org.freeholdos.permission.READ_DATA_MAP"\n'
     '        android:label="@string/permlab_freeholdosReadDataMap"\n'
     '        android:description="@string/permdesc_freeholdosReadDataMap"\n'
     '        android:protectionLevel="signature|privileged" />\n'),
    (FB, "core/res/res/values/strings.xml",
     '    <string name="permdesc_manageProfileAndDeviceOwners">Allows apps to set the profile owners and the device owner.</string>\n',
     '    <string name="permdesc_manageProfileAndDeviceOwners">Allows apps to set the profile owners and the device owner.</string>\n'
     '\n'
     '    <!-- Title of an application permission, listed so the user can choose whether they want to allow the application to do this. -->\n'
     '    <string name="permlab_freeholdosReadDataMap">read which services apps contact</string>\n'
     '    <!-- Description of an application permission. Only the FreeholdOS Data Map app holds it.\n'
     '     [CHAR LIMIT=NONE] -->\n'
     '    <string name="permdesc_freeholdosReadDataMap">Allows the Data Map app to read the names and counts of the services that apps of the same user contact.</string>\n'),
    # ---- audit events: what the user changed, never what was collected ----
    (FB, "services/core/java/com/android/server/EventLogTags.logtags",
     "51631 timezone_nothing_complete (token|3)\n",
     "51631 timezone_nothing_complete (token|3)\n"
     "\n"
     "# FreeholdOS Data Map (ADR-157): what the user changed, never what was collected\n"
     "51700 datamap_collecting (user|1), (enabled|1)\n"
     "51701 datamap_history (user|1), (enabled|1), (days|1)\n"
     "51702 datamap_cleared (user|1)\n"),
    # ---- start the service ----
    (FB, "services/java/com/android/server/SystemServer.java",
     "import com.android.server.devicepolicy.DevicePolicyManagerService;\n",
     "import com.android.server.datamap.DataMapService;\n"
     "import com.android.server.devicepolicy.DevicePolicyManagerService;\n"),
    (FB, "services/java/com/android/server/SystemServer.java",
     '            t.traceBegin("NetworkWatchlistService");\n'
     '            mSystemServiceManager.startService(NetworkWatchlistService.Lifecycle.class);\n'
     '            t.traceEnd();\n',
     '            t.traceBegin("NetworkWatchlistService");\n'
     '            mSystemServiceManager.startService(NetworkWatchlistService.Lifecycle.class);\n'
     '            t.traceEnd();\n'
     '\n'
     '            t.traceBegin("DataMapService");\n'
     '            mSystemServiceManager.startService(DataMapService.Lifecycle.class);\n'
     '            t.traceEnd();\n'),
    # ---- SELinux: a service type, its name, and the app domain ----
    ("system/sepolicy", "private/service.te",
     "type dynamic_system_service,         system_api_service, system_server_service, service_manager_type;\n",
     "type dynamic_system_service,         system_api_service, system_server_service, service_manager_type;\n"
     "type datamap_service,                system_server_service, service_manager_type;\n"),
    ("system/sepolicy", "private/service_contexts",
     "dataloader_manager                        u:object_r:dataloader_manager_service:s0\n",
     "dataloader_manager                        u:object_r:dataloader_manager_service:s0\n"
     "datamap                                   u:object_r:datamap_service:s0\n"),
    ("system/sepolicy", "private/seapp_contexts",
     "user=_app isPrivApp=true name=app.grapheneos.logviewer domain=logviewer_app type=app_data_file levelFrom=all\n",
     "user=_app isPrivApp=true name=app.grapheneos.logviewer domain=logviewer_app type=app_data_file levelFrom=all\n"
     "user=_app isPrivApp=true name=org.freeholdos.datamap domain=datamap_app type=app_data_file levelFrom=all\n"),
    # ---- the product ships the classifier file ----
    ("vendor/adevtool", "config/mk/google_devices/common/device-common.mk",
     "PRODUCT_PRODUCT_PROPERTIES += mmd.zram.size=100%\n",
     "PRODUCT_PRODUCT_PROPERTIES += mmd.zram.size=100%\n"
     "\n"
     "# FreeholdOS Data Map (ADR-157): the read-only classifier file that the Data Map service reads from\n"
     "# /system/etc/datamap. The module is defined in vendor/freeholdos/datamap. Kept in the common file like\n"
     "# Guardian and AirPods, so every Pixel target carries it.\n"
     "PRODUCT_PACKAGES += \\\n"
     "    datamap-classifier.tsv\n"),
]
