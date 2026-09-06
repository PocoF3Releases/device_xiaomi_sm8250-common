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

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class DiracSettingsFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DiracSettingsFragment";
    private MainSwitchPreference mSwitchBar;
    private ListPreference mHeadsetType;
    private ListPreference mPreset;
    private ListPreference mScenes;
    private SwitchPreferenceCompat mHifi;
    private DiracUtils mDiracUtils;
    private final Runnable mStateListener = this::updateState;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.dirac_settings);
        mSwitchBar = findPreference(DiracUtils.PREF_ENABLE);
        mHeadsetType = findPreference(DiracUtils.PREF_HEADSET);
        mPreset = findPreference(DiracUtils.PREF_PRESET);
        mScenes = findPreference(DiracUtils.PREF_SCENE);
        mHifi = findPreference(DiracUtils.PREF_HIFI);

        // DiracUtils persists only successfully applied values, including tile changes.
        for (Preference preference : new Preference[] {
                mSwitchBar, mHeadsetType, mPreset, mScenes, mHifi}) {
            preference.setPersistent(false);
            preference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mDiracUtils = DiracUtils.getInstance(requireContext());
            mDiracUtils.addListener(mStateListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot initialize MiSound", e);
        }
        updateState();
    }

    @Override
    public void onPause() {
        if (mDiracUtils != null) mDiracUtils.removeListener(mStateListener);
        super.onPause();
    }

    private void updateState() {
        boolean available = mDiracUtils != null && mDiracUtils.isAvailable();
        boolean enabled = available && mDiracUtils.isDiracEnabled();
        mSwitchBar.setEnabled(available);
        mSwitchBar.setChecked(enabled);
        mSwitchBar.setSummary(available ? null : getString(R.string.dirac_unavailable));
        setControlsEnabled(enabled);
        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(
                        requireContext().createDeviceProtectedStorageContext());
        mHeadsetType.setValue(prefs.getString(DiracUtils.PREF_HEADSET, "0"));
        mPreset.setValue(prefs.getString(DiracUtils.PREF_PRESET, "0,0,0,0,0,0,0"));
        mScenes.setValue(prefs.getString(DiracUtils.PREF_SCENE, "4"));
        mHifi.setChecked(prefs.getBoolean(DiracUtils.PREF_HIFI, false));
    }

    private void setControlsEnabled(boolean enabled) {
        mHeadsetType.setEnabled(enabled);
        mPreset.setEnabled(enabled);
        mScenes.setEnabled(enabled);
        mHifi.setEnabled(enabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (mDiracUtils == null) return false;
        try {
            switch (preference.getKey()) {
                case DiracUtils.PREF_ENABLE:
                    if (!mDiracUtils.setEnabled((Boolean) value)) {
                        throw new IllegalStateException("MiSound toggle failed");
                    }
                    setControlsEnabled((Boolean) value);
                    return true;
                case DiracUtils.PREF_HEADSET:
                    mDiracUtils.setHeadsetType(Integer.parseInt((String) value));
                    return true;
                case DiracUtils.PREF_PRESET:
                    mDiracUtils.setLevel((String) value);
                    return true;
                case DiracUtils.PREF_SCENE:
                    mDiracUtils.setScenario(Integer.parseInt((String) value));
                    return true;
                case DiracUtils.PREF_HIFI:
                    mDiracUtils.setHifiMode((Boolean) value ? 1 : 0);
                    return true;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot apply MiSound preference " + preference.getKey(), e);
            Toast.makeText(requireContext(), R.string.dirac_apply_failed, Toast.LENGTH_SHORT).show();
            mDiracUtils.restoreAfterFailure();
            updateState();
            return false;
        }
    }
}
