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
    private SwitchPreferenceCompat mEqualizer;
    private SwitchPreferenceCompat mPauseDuringCalls;
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
        mEqualizer = findPreference(DiracUtils.PREF_EQ);
        mPauseDuringCalls = findPreference(DiracUtils.PREF_PAUSE);

        // The owner persists applied tuning and explicit Off recovery requests.
        for (Preference preference : new Preference[] {
                mSwitchBar, mHeadsetType, mPreset, mScenes, mHifi,
                mEqualizer, mPauseDuringCalls}) {
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
        boolean requested = mDiracUtils != null && mDiracUtils.isEnabledRequested();
        boolean enabled = available && requested;
        // Keep Off reachable after a failed restore; the unavailable summary remains.
        mSwitchBar.setEnabled(available || requested);
        mSwitchBar.setChecked(requested);
        mSwitchBar.setSummary(!available ? getString(R.string.dirac_unavailable)
                : mDiracUtils.isPausedForCommunication()
                        ? getString(R.string.dirac_paused_for_calls) : null);
        mHifi.setVisible(mDiracUtils != null && mDiracUtils.isHifiSupported());
        mPauseDuringCalls.setEnabled(available);
        mEqualizer.setChecked(mDiracUtils != null && mDiracUtils.isEqualizerEnabled());
        mPauseDuringCalls.setChecked(mDiracUtils != null && mDiracUtils.isPauseDuringCallsEnabled());
        setControlsEnabled(enabled);
        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(
                        requireContext().createDeviceProtectedStorageContext());
        // Restore the complete catalog if a later query is unavailable or malformed.
        mHeadsetType.setEntries(R.array.dirac_headset_pref_entries);
        mHeadsetType.setEntryValues(R.array.dirac_headset_pref_values);
        if (available) {
            try {
                int[] supported = mDiracUtils.getSupportedHeadsets();
                CharSequence[] names = getResources().getTextArray(R.array.dirac_headset_pref_entries);
                String[] values = getResources().getStringArray(R.array.dirac_headset_pref_values);
                java.util.ArrayList<CharSequence> entries = new java.util.ArrayList<>();
                java.util.ArrayList<CharSequence> ids = new java.util.ArrayList<>();
                for (int index = 0; index < values.length; index++) {
                    int id = Integer.parseInt(values[index]);
                    for (int supportedId : supported) {
                        if (id == supportedId) {
                            entries.add(names[index]);
                            ids.add(values[index]);
                            break;
                        }
                    }
                }
                if (!ids.isEmpty()) {
                    mHeadsetType.setEntries(entries.toArray(new CharSequence[0]));
                    mHeadsetType.setEntryValues(ids.toArray(new CharSequence[0]));
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Cannot query supported MiSound headsets", error);
            }
        }
        mHeadsetType.setValue(prefs.getString(DiracUtils.PREF_HEADSET, "0"));
        mPreset.setValue(prefs.getString(DiracUtils.PREF_PRESET, "0,0,0,0,0,0,0"));
        mScenes.setValue(prefs.getString(DiracUtils.PREF_SCENE, "4"));
        mHifi.setChecked(prefs.getBoolean(DiracUtils.PREF_HIFI, false));
    }

    private void setControlsEnabled(boolean enabled) {
        mHeadsetType.setEnabled(enabled);
        mEqualizer.setEnabled(enabled);
        mPreset.setEnabled(enabled && mDiracUtils != null && mDiracUtils.isEqualizerEnabled());
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
                case DiracUtils.PREF_EQ:
                    mDiracUtils.setEqualizerEnabled((Boolean) value);
                    setControlsEnabled(mDiracUtils.isEnabledRequested());
                    return true;
                case DiracUtils.PREF_PAUSE:
                    mDiracUtils.setPauseDuringCallsEnabled((Boolean) value);
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
