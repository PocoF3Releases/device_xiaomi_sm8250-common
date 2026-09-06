/*
 * Copyright (C) 2018,2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.dirac;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/** Process-wide owner of the session-0 MiSound effect and its last successful settings. */
public class DiracUtils {
    private static final String TAG = "DiracUtils";
    static final String PREF_ENABLE = "dirac_enable";
    static final String PREF_HEADSET = "dirac_headset_pref";
    static final String PREF_PRESET = "dirac_preset_pref";
    static final String PREF_SCENE = "scenario_selection";
    static final String PREF_HIFI = "dirac_hifi_pref";

    private static DiracUtils sInstance;
    private final SharedPreferences mPreferences;
    private final AudioManager mAudioManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Set<Runnable> mListeners = new HashSet<>();
    private boolean mServerDown;
    private DiracSound mSound;
    private int mRestoreAttempts;
    private final Runnable mRestore = this::restore;

    private DiracUtils(Context context) {
        Context appContext = context.getApplicationContext().createDeviceProtectedStorageContext();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        mAudioManager = appContext.getSystemService(AudioManager.class);
        mAudioManager.setAudioServerStateCallback(appContext.getMainExecutor(),
                new AudioManager.AudioServerStateCallback() {
                    @Override
                    public void onAudioServerDown() {
                        synchronized (DiracUtils.this) {
                            mServerDown = true;
                            mHandler.removeCallbacks(mRestore);
                            releaseEffect();
                            notifyListeners();
                        }
                    }

                    @Override
                    public void onAudioServerUp() {
                        synchronized (DiracUtils.this) {
                            mServerDown = false;
                            mRestoreAttempts = 0;
                            restore();
                        }
                    }
                });
        restore();
    }

    public static synchronized DiracUtils getInstance(Context context) {
        if (sInstance == null) sInstance = new DiracUtils(context);
        return sInstance;
    }

    private synchronized void restore() {
        try {
            requireEffect();
            mRestoreAttempts = 0;
            mHandler.removeCallbacks(mRestore);
            notifyListeners();
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot restore MiSound", e);
            // The effects service may become ready after AudioService's up notification.
            if (++mRestoreAttempts < 5) {
                mHandler.removeCallbacks(mRestore);
                mHandler.postDelayed(mRestore, 1000);
            }
        }
    }

    public synchronized void addListener(Runnable listener) {
        mListeners.add(listener);
    }

    public synchronized void removeListener(Runnable listener) {
        mListeners.remove(listener);
        mHandler.removeCallbacks(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : mListeners) {
            mHandler.removeCallbacks(listener);
            mHandler.post(listener);
        }
    }

    private void releaseEffect() {
        if (mSound != null) {
            mSound.release();
            mSound = null;
        }
    }

    private DiracSound requireEffect() {
        if (mServerDown) throw new IllegalStateException("Audio server is down");
        try {
            if (mSound != null && mSound.hasControl()) return mSound;
            releaseEffect();
            mSound = new DiracSound(0, 0);
            if (!mSound.hasControl()) throw new IllegalStateException("MiSound control unavailable");
            mSound.setHeadsetType(Integer.parseInt(mPreferences.getString(PREF_HEADSET, "0")));
            applyLevel(mPreferences.getString(PREF_PRESET, "0,0,0,0,0,0,0"));
            mSound.setScenario(Integer.parseInt(mPreferences.getString(PREF_SCENE, "4")));
            applyHifi(mPreferences.getBoolean(PREF_HIFI, false));
            applyEnabled(mPreferences.getBoolean(PREF_ENABLE, false));
            return mSound;
        } catch (RuntimeException e) {
            releaseEffect();
            throw e;
        }
    }

    private void applyEnabled(boolean enable) {
        // Both the proprietary music parameter and AudioEffect state must agree.
        mSound.setMusic(enable ? 1 : 0);
        int status = mSound.setEnabled(enable);
        if (status != AudioEffect.SUCCESS) {
            throw new IllegalStateException("MiSound setEnabled failed: " + status);
        }
        if (mSound.getEnabled() != enable || (mSound.getMusic() == 1) != enable) {
            throw new IllegalStateException("MiSound enable state did not apply");
        }
    }

    public synchronized void restoreAfterFailure() {
        releaseEffect();
        mRestoreAttempts = 0;
        restore();
    }

    public synchronized boolean isAvailable() {
        try {
            requireEffect();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "MiSound is unavailable", e);
            return false;
        }
    }

    public synchronized boolean isDiracEnabled() {
        try {
            DiracSound sound = requireEffect();
            return sound.getEnabled() && sound.getMusic() == 1;
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot read MiSound state", e);
            releaseEffect();
            return false;
        }
    }

    public synchronized boolean setEnabled(boolean enable) {
        try {
            requireEffect();
            applyEnabled(enable);
            mPreferences.edit().putBoolean(PREF_ENABLE, enable).apply();
            notifyListeners();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot change MiSound state", e);
            // Do not save a failed toggle. Restore the last successful state, including
            // rolling back a music-parameter write if setEnabled failed afterwards.
            releaseEffect();
            mRestoreAttempts = 0;
            restore();
            return false;
        }
    }

    public synchronized void setLevel(String preset) {
        requireEffect();
        applyLevel(preset);
        mPreferences.edit().putString(PREF_PRESET, preset).apply();
    }

    private void applyLevel(String preset) {
        String[] values = preset.split(",", -1);
        if (values.length != 7) throw new IllegalArgumentException("Expected seven MiSound bands");
        float[] levels = new float[values.length];
        for (int band = 0; band < values.length; band++) {
            levels[band] = Float.parseFloat(values[band].trim());
            if (!Float.isFinite(levels[band])) {
                throw new IllegalArgumentException("Invalid MiSound band gain");
            }
        }
        for (int band = 0; band < levels.length; band++) mSound.setLevel(band, levels[band]);
    }

    public synchronized void setHeadsetType(int value) {
        requireEffect().setHeadsetType(value);
        mPreferences.edit().putString(PREF_HEADSET, Integer.toString(value)).apply();
    }

    public synchronized boolean getHifiMode() {
        return mPreferences.getBoolean(PREF_HIFI, false);
    }

    public synchronized void setHifiMode(int value) {
        requireEffect();
        applyHifi(value != 0);
        mPreferences.edit().putBoolean(PREF_HIFI, value != 0).apply();
    }

    private void applyHifi(boolean enabled) {
        mSound.setHifiMode(enabled ? 1 : 0);
        mAudioManager.setParameters("hifi_mode=" + enabled);
    }

    public synchronized void setScenario(int value) {
        requireEffect().setScenario(value);
        mPreferences.edit().putString(PREF_SCENE, Integer.toString(value)).apply();
    }
}
