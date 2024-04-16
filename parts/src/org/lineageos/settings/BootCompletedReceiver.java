/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2020 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.display.ColorService;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.refreshrate.RefreshUtils;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;
import org.lineageos.settings.utils.FileUtils;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "XiaomiParts";
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/msm_fb_ea_enable";
    private static final String HBM_ENABLE_KEY = "hbm_mode";
    private static final String HBM_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (DEBUG)
            Log.d(TAG, "Received boot completed intent");
        try {
            DiracUtils.getInstance(context);
        } catch (Exception e) {
            Log.d(TAG, "Dirac is not present in system");
        }
        ThermalUtils.startService(context);
        RefreshUtils.startService(context);
        TouchSamplingUtils.restoreSamplingValue(context);

        // DisplayFeature
        ColorService.startService(context);

        // DC Dimming
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean dcDimmingEnabled = sharedPrefs.getBoolean(DC_DIMMING_ENABLE_KEY, false);
        FileUtils.writeLine(DC_DIMMING_NODE, dcDimmingEnabled ? "1" : "0");
        boolean hbmEnabled = sharedPrefs.getBoolean(HBM_ENABLE_KEY, false);
        FileUtils.writeLine(HBM_NODE, hbmEnabled ? "1" : "0");
    }
}
