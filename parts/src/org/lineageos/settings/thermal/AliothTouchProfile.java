// SPDX-License-Identifier: Apache-2.0
package org.lineageos.settings.thermal;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.RemoteException;
import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

/** Additional modes verified against Alioth FocalTech firmware commands. */
final class AliothTouchProfile {
    private AliothTouchProfile() {}

    static boolean isSupported() {
        return "alioth".equals(Build.DEVICE) || "aliothin".equals(Build.DEVICE);
    }

    static String key(String pkg, String setting) {
        return "alioth_touch:" + pkg + ":" + setting;
    }

    static int read(SharedPreferences prefs, String pkg, String setting, int max) {
        try {
            return Math.max(0, Math.min(max, prefs.getInt(key(pkg, setting), 0)));
        } catch (ClassCastException e) {
            return 0;
        }
    }

    static void apply(ITouchFeature hal, SharedPreferences prefs, String pkg)
            throws RemoteException {
        if (!isSupported()) return;
        applyMode(hal, 4, read(prefs, pkg, "touch_aim", 5));
        applyMode(hal, 5, read(prefs, pkg, "touch_stability", 5));
        int expert = read(prefs, pkg, "touch_expert", 3);
        // Individual mode writes disable expert mode: apply the preset last.
        if (expert > 0) applyMode(hal, 6, expert);
    }

    private static void applyMode(ITouchFeature hal, int mode, int value)
            throws RemoteException {
        // Zero means firmware default, never a raw out-of-range mode value.
        if (value == 0) {
            hal.resetTouchMode(mode);
        } else if (value >= hal.getTouchModeMinValue(mode)
                && value <= hal.getTouchModeMaxValue(mode)) {
            hal.setTouchMode(mode, value);
        }
    }
}
