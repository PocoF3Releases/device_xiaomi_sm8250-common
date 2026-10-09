/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.thermal;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SliderPreference;

import org.lineageos.settings.R;
import org.lineageos.settings.touchsampling.TouchSamplingSettingsActivity;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;

import java.util.Arrays;

import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

/** Package-specific touch tuning; presets and manual controls never stack. */
public class TouchSettingsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "TouchSettings";
    private SharedPreferences mPrefs;
    private String mPackageName;
    private MainSwitchPreference mGameMode;
    private SliderPreference mResponse, mSensitivity, mResistance;
    private SliderPreference mAim, mStability;
    private ListPreference mExpert;
    private Preference mStatus, mModeGroup, mManualGroup, mAdvancedGroup, mEdgeGroup, mReset;
    private boolean mHalAvailable = true;
    private int mPresetMax = 3;
    private int mDefaultEdge;
    private final int[] mValues = new int[4];

    @Override
    public void onCreatePreferences(Bundle state, String rootKey) {
        addPreferencesFromResource(R.xml.touch_settings);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPackageName = requireArguments().getString("packageName", "");
        mGameMode = findPreference(Constants.PREF_TOUCH_GAME_MODE);
        mResponse = findPreference(Constants.PREF_TOUCH_RESPONSE);
        mSensitivity = findPreference(Constants.PREF_TOUCH_SENSITIVITY);
        mResistance = findPreference(Constants.PREF_TOUCH_RESISTANT);
        mAim = findPreference("touch_aim");
        mStability = findPreference("touch_stability");
        mExpert = findPreference("touch_expert");
        mStatus = findPreference("touch_status");
        mModeGroup = findPreference("touch_mode_group");
        mManualGroup = findPreference("touch_manual_group");
        mAdvancedGroup = findPreference("touch_advanced_group");
        mEdgeGroup = findPreference("touch_edge_group");
        mReset = findPreference("touch_reset");
        mExpert.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        for (Preference pref : new Preference[]{mGameMode, mResponse, mSensitivity,
                mResistance, mAim, mStability, mExpert}) {
            pref.setPersistent(false);
            pref.setOnPreferenceChangeListener(this);
        }
        for (SliderPreference pref : new SliderPreference[]{mResponse, mSensitivity,
                mResistance, mAim, mStability}) {
            pref.setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        }
        mGameMode.setSummary(requireArguments().getString("appName", mPackageName));
        mReset.setOnPreferenceClickListener(unused -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.touch_controls_reset_title)
                    .setMessage(R.string.touch_controls_reset_summary)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> resetTuning())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    private void configureAliothRanges() {
        mHalAvailable = true;
        if (!AliothTouchProfile.isSupported()) return;
        try {
            ITouchFeature hal = ITouchFeature.getService();
            if (hal == null) throw new IllegalStateException("Touch HAL is unavailable");
            configureSlider(hal, mResponse, Constants.MODE_TOUCH_UP_THRESHOLD, 1, 5);
            configureSlider(hal, mSensitivity, Constants.MODE_TOUCH_TOLERANCE, 1, 5);
            configureSlider(hal, mAim, 4, 1, 5);
            configureSlider(hal, mStability, 5, 1, 5);
            configureSlider(hal, mResistance, Constants.MODE_TOUCH_EDGE_FILTER, 0, 3);
            mPresetMax = hal.getTouchModeMaxValue(6);
            if (hal.getTouchModeMinValue(6) != 1 || mPresetMax < 1 || mPresetMax > 3) {
                throw new IllegalStateException("Invalid touch preset range");
            }
            mDefaultEdge = hal.getTouchModeDefValue(Constants.MODE_TOUCH_EDGE_FILTER);
            if (mDefaultEdge < 0 || mDefaultEdge > mResistance.getMax()) {
                throw new IllegalStateException("Invalid edge default");
            }
            mExpert.setEntries(Arrays.copyOf(
                    getResources().getTextArray(R.array.touch_controls_mode_entries), mPresetMax + 1));
            mExpert.setEntryValues(Arrays.copyOf(
                    getResources().getStringArray(R.array.touch_controls_mode_values), mPresetMax + 1));
        } catch (RemoteException | RuntimeException e) {
            mHalAvailable = false;
            Log.w(TAG, "Cannot query touch control ranges", e);
        }
    }

    private static void configureSlider(ITouchFeature hal, SliderPreference pref,
            int mode, int expectedMin, int limit) throws RemoteException {
        int max = hal.getTouchModeMaxValue(mode);
        if (hal.getTouchModeMinValue(mode) != expectedMin || max < expectedMin || max > limit) {
            throw new IllegalStateException("Invalid range for touch mode " + mode);
        }
        pref.setMax(max);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.touch_controls_screen_title);
        configureAliothRanges();
        loadTuning();
        updateEnabled();
    }

    private void loadTuning() {
        String value = null;
        try {
            value = mPrefs.getString(mPackageName, null);
        } catch (ClassCastException ignored) { }
        String[] saved = value == null ? new String[0] : value.split(",", -1);
        int[] limits = {1, mResponse.getMax(), mSensitivity.getMax(), mResistance.getMax()};
        for (int i = 0; i < mValues.length; i++) {
            int parsed = i == Constants.TOUCH_RESISTANT ? mDefaultEdge : 0;
            try {
                if (saved.length == mValues.length) parsed = Integer.parseInt(saved[i]);
            } catch (NumberFormatException ignored) { }
            mValues[i] = Math.max(0, Math.min(limits[i], parsed));
        }
        mAim.setValue(AliothTouchProfile.read(mPrefs, mPackageName, "touch_aim", mAim.getMax()));
        mStability.setValue(AliothTouchProfile.read(mPrefs, mPackageName,
                "touch_stability", mStability.getMax()));
        mExpert.setValue(Integer.toString(AliothTouchProfile.read(mPrefs,
                mPackageName, "touch_expert", mPresetMax)));
        mGameMode.setChecked(mValues[Constants.TOUCH_GAME_MODE] == 1);
        mResponse.setValue(mValues[Constants.TOUCH_RESPONSE]);
        mSensitivity.setValue(mValues[Constants.TOUCH_SENSITIVITY]);
        mResistance.setValue(mValues[Constants.TOUCH_RESISTANT]);
    }

    private void updateEnabled() {
        boolean alioth = AliothTouchProfile.isSupported();
        boolean globalOverride = alioth && TouchSamplingUtils.isEnabled(requireContext());
        boolean enabled = !mPackageName.isEmpty() && mHalAvailable
                && mValues[Constants.TOUCH_GAME_MODE] == 1 && !globalOverride;
        boolean manual = !alioth || AliothTouchProfile.read(mPrefs,
                mPackageName, "touch_expert", mPresetMax) == 0;
        mGameMode.setEnabled(!mPackageName.isEmpty() && mHalAvailable);
        mModeGroup.setVisible(enabled && alioth);
        mManualGroup.setVisible(enabled && manual);
        mAdvancedGroup.setVisible(enabled && manual && alioth);
        mEdgeGroup.setVisible(enabled);
        mReset.setVisible(enabled);
        mStatus.setSelectable(globalOverride);
        mStatus.setIntent(globalOverride
                ? new Intent(requireContext(), TouchSamplingSettingsActivity.class) : null);
        mStatus.setTitle(!mHalAvailable ? R.string.touch_controls_unavailable_title
                : globalOverride ? R.string.touch_controls_global_title
                : enabled ? R.string.touch_controls_active_title : R.string.touch_controls_start_title);
        mStatus.setSummary(!mHalAvailable ? R.string.touch_controls_unavailable_summary
                : globalOverride ? R.string.touch_controls_global_summary
                : enabled ? R.string.touch_controls_active_summary : R.string.touch_controls_start_summary);
    }

    private void persistTuning() {
        mPrefs.edit().putString(mPackageName, mValues[0] + "," + mValues[1] + ","
                + mValues[2] + "," + mValues[3]).apply();
        ThermalUtils.startService(requireContext());
    }

    private void resetTuning() {
        mValues[Constants.TOUCH_RESPONSE] = 0;
        mValues[Constants.TOUCH_SENSITIVITY] = 0;
        mValues[Constants.TOUCH_RESISTANT] = mDefaultEdge;
        mPrefs.edit().remove(AliothTouchProfile.key(mPackageName, "touch_aim"))
                .remove(AliothTouchProfile.key(mPackageName, "touch_stability"))
                .remove(AliothTouchProfile.key(mPackageName, "touch_expert")).apply();
        persistTuning();
        loadTuning();
        updateEnabled();
    }

    public void showHelp() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.touch_controls_help_title)
                .setMessage(R.string.touch_controls_help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object value) {
        if (mPackageName.isEmpty() || !mHalAvailable) return false;
        if (pref == mExpert) {
            int preset;
            try {
                preset = Integer.parseInt((String) value);
            } catch (NumberFormatException | ClassCastException e) {
                return false;
            }
            if (!AliothTouchProfile.isSupported() || preset < 0 || preset > mPresetMax) return false;
            mPrefs.edit().putInt(AliothTouchProfile.key(mPackageName, "touch_expert"), preset).apply();
            ThermalUtils.startService(requireContext());
            updateEnabled();
            return true;
        }
        if (pref == mAim || pref == mStability) {
            if (!AliothTouchProfile.isSupported()) return false;
            int level = (Integer) value;
            if (level < 0 || level > ((SliderPreference) pref).getMax()) return false;
            mPrefs.edit().putInt(AliothTouchProfile.key(mPackageName, pref.getKey()), level).apply();
            ThermalUtils.startService(requireContext());
            return true;
        }
        if (pref == mGameMode) mValues[Constants.TOUCH_GAME_MODE] = (Boolean) value ? 1 : 0;
        else {
            int index;
            SliderPreference slider;
            if (pref == mResponse) { index = Constants.TOUCH_RESPONSE; slider = mResponse; }
            else if (pref == mSensitivity) { index = Constants.TOUCH_SENSITIVITY; slider = mSensitivity; }
            else if (pref == mResistance) { index = Constants.TOUCH_RESISTANT; slider = mResistance; }
            else return false;
            int level = (Integer) value;
            if (level < 0 || level > slider.getMax()) return false;
            mValues[index] = level;
        }
        persistTuning();
        updateEnabled();
        return true;
    }
}
