// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.os.Bundle;
import android.view.View;

import com.android.settingslib.preference.PreferenceFragment;

/**
 * A page that builds its rows again from what the service reports. A new row is a new item to the list, whose default animator
 * fades every old row out and then every new row in, so each change made the page flash for half a second (CMP-046, owner,
 * 2026-11-03). Stock Settings turns the animator off for a list it rebuilds (NetworkProviderSettings); so do these pages.
 */
public abstract class CalmPreferenceFragment extends PreferenceFragment {
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setItemAnimator(null);
    }
}
