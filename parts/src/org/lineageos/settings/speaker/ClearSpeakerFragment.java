/*
 * Copyright (C) 2023 Paranoid Android
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

package org.lineageos.settings.speaker;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.io.IOException;

public class ClearSpeakerFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "ClearSpeakerFragment";
    private static final String PREF_CLEAR_SPEAKER = "clear_speaker_pref";
    private static final int PLAY_DURATION_MS = 30000;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;
    private AudioFocusRequest mFocusRequest;
    private SwitchPreferenceCompat mClearSpeakerPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.clear_speaker_settings);

        mClearSpeakerPref = findPreference(PREF_CLEAR_SPEAKER);
        mClearSpeakerPref.setPersistent(false);
        mClearSpeakerPref.setChecked(false);
        mClearSpeakerPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mClearSpeakerPref) {
            boolean value = (Boolean) newValue;
            if (!value) {
                stopPlaying();
                return true;
            }
            if (startPlaying()) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(this::stopPlaying, PLAY_DURATION_MS);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onStop() {
        super.onStop();
        stopPlaying();
    }

    public boolean startPlaying() {
        stopPlaying();
        mAudioManager = requireContext().getSystemService(AudioManager.class);
        if (mAudioManager == null || mAudioManager.getMode() != AudioManager.MODE_NORMAL) {
            return false;
        }
        AudioDeviceInfo speaker = null;
        for (AudioDeviceInfo device : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                speaker = device;
                break;
            }
        }
        if (speaker == null) return false;
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        try (AssetFileDescriptor afd = getResources().openRawResourceFd(
                R.raw.clear_speaker_sound)) {
            mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change != AudioManager.AUDIOFOCUS_GAIN) stopPlaying();
                    }, mHandler)
                    .build();
            if (mAudioManager.requestAudioFocus(mFocusRequest)
                    != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                stopPlaying();
                return false;
            }
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioAttributes(attributes);
            if (!mMediaPlayer.setPreferredDevice(speaker)) {
                stopPlaying();
                return false;
            }
            mMediaPlayer.addOnRoutingChangedListener(router -> {
                AudioDeviceInfo routed = router.getRoutedDevice();
                if (routed != null && routed.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    stopPlaying();
                }
            }, mHandler);
            mMediaPlayer.setOnErrorListener((player, what, extra) -> {
                stopPlaying();
                return true;
            });
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setDataSource(afd);
            mMediaPlayer.setVolume(1.0f, 1.0f);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to play speaker clean sound!", e);
            stopPlaying();
            return false;
        }
        return true;
    }

    public void stopPlaying() {
        mHandler.removeCallbacksAndMessages(null);
        if (mMediaPlayer != null) {
            // release() also handles failed prepare/start and already stopped players.
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
            mFocusRequest = null;
        }
        if (mClearSpeakerPref != null) mClearSpeakerPref.setChecked(false);
    }
}
