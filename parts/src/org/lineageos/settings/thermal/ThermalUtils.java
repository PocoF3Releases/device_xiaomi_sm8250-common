/*
 * Copyright (C) 2020 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.thermal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.util.NoSuchElementException;

import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

public final class ThermalUtils {
    private static final String TAG = "ThermalUtils";

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_BENCHMARK = 1;
    protected static final int STATE_BROWSER = 2;
    protected static final int STATE_CAMERA = 3;
    protected static final int STATE_DIALER = 4;
    protected static final int STATE_GAMING = 5;
    protected static final int STATE_STREAMING = 6;

    private static final String THERMAL_CONTROL = "thermal_control";
    private static final String THERMAL_STATE_DEFAULT = "0";
    private static final String THERMAL_STATE_BENCHMARK = "10";
    private static final String THERMAL_STATE_BROWSER = "11";
    private static final String THERMAL_STATE_CAMERA = "12";
    private static final String THERMAL_STATE_DIALER = "8";
    private static final String THERMAL_STATE_GAMING = "9";
    private static final String THERMAL_STATE_STREAMING = "14";

    private static final String THERMAL_BENCHMARK = "thermal.benchmark=";
    private static final String THERMAL_BROWSER = "thermal.browser=";
    private static final String THERMAL_CAMERA = "thermal.camera=";
    private static final String THERMAL_DIALER = "thermal.dialer=";
    private static final String THERMAL_GAMING = "thermal.gaming=";
    private static final String THERMAL_STREAMING = "thermal.streaming=";
    private static final String[] PROFILE_PREFIXES = {
            THERMAL_BENCHMARK, THERMAL_BROWSER, THERMAL_CAMERA,
            THERMAL_DIALER, THERMAL_GAMING, THERMAL_STREAMING
    };

    private static final String THERMAL_SCONFIG =
            "/sys/class/thermal/thermal_message/sconfig";

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private final Display mDisplay;
    private ITouchFeature mTouchFeature;
    private boolean mTouchModeChanged;

    protected ThermalUtils(Context context) {
        mContext = context.getApplicationContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        mDisplay = displayManager == null ? null
                : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        mTouchFeature = getTouchFeature();
    }

    public static void startService(Context context) {
        if (FileUtils.isFileWritable(THERMAL_SCONFIG)) {
            context.startService(new Intent(context, ThermalService.class));
        }
    }

    private ITouchFeature getTouchFeature() {
        try {
            return ITouchFeature.getService();
        } catch (RemoteException | NoSuchElementException e) {
            Log.d(TAG, "Touch feature HAL is unavailable");
            return null;
        }
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(THERMAL_CONTROL, profiles).apply();
    }

    private static boolean isValidProfiles(String[] modes, int expectedCount) {
        if (modes.length != expectedCount) return false;
        for (int i = 0; i < expectedCount; i++) {
            if (!modes[i].startsWith(PROFILE_PREFIXES[i])) return false;
        }
        return true;
    }

    private String getValue() {
        String value = mSharedPrefs.getString(THERMAL_CONTROL, null);
        if (value != null && !value.isEmpty()) {
            String[] modes = value.split(":", -1);
            if (isValidProfiles(modes, 5)) {
                value += ":" + THERMAL_STREAMING;
                writeValue(value);
            } else if (!isValidProfiles(modes, PROFILE_PREFIXES.length)) {
                value = null;
            }
        }

        if (value == null || value.isEmpty()) {
            value = String.join(":", PROFILE_PREFIXES);
            writeValue(value);
        }
        return value;
    }

    private static boolean hasPackage(String profile, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        int separator = profile.indexOf('=');
        if (separator < 0) return false;
        for (String entry : profile.substring(separator + 1).split(",")) {
            if (packageName.equals(entry)) return true;
        }
        return false;
    }

    private static String removePackage(String profile, String packageName) {
        if (packageName == null || packageName.isEmpty()) return profile;
        int separator = profile.indexOf('=');
        if (separator < 0) return profile;
        StringBuilder result = new StringBuilder(profile.substring(0, separator + 1));
        for (String entry : profile.substring(separator + 1).split(",")) {
            if (!entry.isEmpty() && !packageName.equals(entry)) {
                result.append(entry).append(',');
            }
        }
        return result.toString();
    }

    protected void writePackage(String packageName, int mode) {
        if (packageName == null || packageName.isEmpty()) return;
        String[] modes = getValue().split(":", -1);
        for (int i = 0; i < modes.length; i++) {
            modes[i] = removePackage(modes[i], packageName);
        }

        if (mode >= STATE_BENCHMARK && mode <= STATE_STREAMING) {
            modes[mode - 1] += packageName + ",";
        }
        writeValue(String.join(":", modes));
        startService(mContext);
    }

    protected int getStateForPackage(String packageName) {
        String[] modes = getValue().split(":", -1);
        for (int i = 0; i < modes.length; i++) {
            if (hasPackage(modes[i], packageName)) return i + 1;
        }
        return STATE_DEFAULT;
    }

    private static String stateForProfile(int profile) {
        switch (profile) {
            case STATE_BENCHMARK: return THERMAL_STATE_BENCHMARK;
            case STATE_BROWSER: return THERMAL_STATE_BROWSER;
            case STATE_CAMERA: return THERMAL_STATE_CAMERA;
            case STATE_DIALER: return THERMAL_STATE_DIALER;
            case STATE_GAMING: return THERMAL_STATE_GAMING;
            case STATE_STREAMING: return THERMAL_STATE_STREAMING;
            default: return THERMAL_STATE_DEFAULT;
        }
    }

    private void writeThermalState(String state) {
        if (!FileUtils.writeLine(THERMAL_SCONFIG, state)) {
            Log.w(TAG, "Cannot write thermal state " + state);
        }
    }

    protected void setDefaultThermalProfile() {
        writeThermalState(THERMAL_STATE_DEFAULT);
    }

    protected void setThermalProfile(String packageName) {
        int profile = getStateForPackage(packageName);
        String state = stateForProfile(profile);
        writeThermalState(state);

        if (profile == STATE_BENCHMARK || profile == STATE_GAMING) {
            updateTouchModes(packageName);
        } else {
            resetTouchModes();
        }
    }

    private void updateTouchModes(String packageName) {
        String values = mSharedPrefs.getString(packageName, null);
        resetTouchModes();

        if (values == null || values.isEmpty()) return;
        if (mTouchFeature == null) mTouchFeature = getTouchFeature();
        if (mTouchFeature == null) return;

        String[] value = values.split(",", -1);
        if (value.length != 4) return;

        final int gameMode;
        final int touchResponse;
        final int touchSensitivity;
        final int touchResistant;
        try {
            gameMode = Integer.parseInt(value[Constants.TOUCH_GAME_MODE]);
            touchResponse = Integer.parseInt(value[Constants.TOUCH_RESPONSE]);
            touchSensitivity = Integer.parseInt(value[Constants.TOUCH_SENSITIVITY]);
            touchResistant = Integer.parseInt(value[Constants.TOUCH_RESISTANT]);
        } catch (NumberFormatException e) {
            return;
        }

        int touchActiveMode =
                (touchResponse != 0 && touchSensitivity != 0 && touchResistant != 0) ? 1 : 0;
        try {
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_TOLERANCE, touchSensitivity);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_UP_THRESHOLD, touchResponse);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_EDGE_FILTER, touchResistant);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_GAME_MODE, gameMode);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_ACTIVE_MODE, touchActiveMode);
            mTouchModeChanged = true;
            updateTouchRotation();
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot apply touch profile", e);
            resetTouchModesBestEffort();
            mTouchFeature = null;
            mTouchModeChanged = false;
        }
    }

    private void resetTouchModeBestEffort(int mode) {
        if (mTouchFeature == null) return;
        try {
            mTouchFeature.resetTouchMode(mode);
        } catch (RemoteException e) {
            Log.d(TAG, "Cannot reset touch mode " + mode, e);
        }
    }

    private void resetTouchModesBestEffort() {
        resetTouchModeBestEffort(Constants.MODE_TOUCH_GAME_MODE);
        resetTouchModeBestEffort(Constants.MODE_TOUCH_ACTIVE_MODE);
        resetTouchModeBestEffort(Constants.MODE_TOUCH_UP_THRESHOLD);
        resetTouchModeBestEffort(Constants.MODE_TOUCH_TOLERANCE);
        resetTouchModeBestEffort(Constants.MODE_TOUCH_EDGE_FILTER);
        resetTouchModeBestEffort(Constants.MODE_TOUCH_ROTATION);
    }

    protected void resetTouchModes() {
        if (!mTouchModeChanged) return;
        resetTouchModesBestEffort();
        mTouchModeChanged = false;
    }

    protected void updateTouchRotation() {
        if (!mTouchModeChanged || mDisplay == null || mTouchFeature == null) return;

        final int touchRotation;
        switch (mDisplay.getRotation()) {
            case Surface.ROTATION_90:
                touchRotation = 1;
                break;
            case Surface.ROTATION_180:
                touchRotation = 2;
                break;
            case Surface.ROTATION_270:
                touchRotation = 3;
                break;
            case Surface.ROTATION_0:
            default:
                touchRotation = 0;
                break;
        }

        try {
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_ROTATION, touchRotation);
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot update touch rotation", e);
            mTouchFeature = null;
            mTouchModeChanged = false;
        }
    }
}
