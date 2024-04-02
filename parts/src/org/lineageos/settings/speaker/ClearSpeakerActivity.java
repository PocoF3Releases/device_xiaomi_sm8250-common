/*
 * Copyright (C) 2020 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.speaker;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.widget.R;

public class ClearSpeakerActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG_CLEARSPEAKER = "clearspeaker";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(R.id.content_frame,
                new ClearSpeakerFragment(), TAG_CLEARSPEAKER).commit();
    }
}
