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

package org.lineageos.settings.misound

import android.content.Context
import android.media.*
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemProperties
import android.util.Log
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lineageos.settings.utils.PartsPreferences

data class MiSoundState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val equalizer: Boolean = true,
    val pauseDuringCalls: Boolean = true,
    val paused: Boolean = false,
    val headset: Int = 0,
    val preset: String = "0,0,0,0,0,0,0",
    val scenario: Int = 4,
    val hifi: Boolean = false,
    val supportedHeadsets: List<Int> = emptyList(),
    val customLevels: List<Float> = List(MiSoundUtils.BAND_COUNT) { 0f },
    val surroundSupported: Boolean = false,
    val surroundAvailable: Boolean = false,
    val surroundRequested: Boolean = false,
    val earCompensationSupported: Boolean = false,
    val earCompensationAvailable: Boolean = false,
    val earCompensationRequested: Boolean = false,
    val earCompensationPreset: Int = 1,
)

/** Persistent process-wide owner: activities never own or release the DSP effect. */
class MiSoundUtils private constructor(context: Context) {
    companion object {
        const val PREF_ENABLE = "misound_enable"
        const val PREF_HEADSET = "misound_headset_pref"
        const val PREF_PRESET = "misound_preset_pref"
        const val PREF_SCENE = "scenario_selection"
        const val PREF_HIFI = "misound_hifi_pref"
        const val PREF_EQ = "misound_eq_enabled"
        const val PREF_PAUSE = "misound_pause_during_calls"
        const val PREF_CUSTOM = "misound_custom_levels"
        const val PREF_SURROUND = "misound_surround_enabled"
        const val PREF_EAR_COMPENSATION = "misound_ear_compensation_enabled"
        const val PREF_EAR_PRESET = "misound_ear_compensation_preset"
        /** Stored in PREF_PRESET while the user curve in PREF_CUSTOM is selected. */
        const val CUSTOM = "custom"
        const val BAND_COUNT = 7
        private const val LEGACY_CLASSICAL = "0,0,0,0,3,6,6"
        private const val STOCK_CLASSICAL = "-3,-3,-3,-3,0,3,3"
        /** Stock DiracEqualizer: rank -10..10 dB with a level factor of two (0.5 dB steps). */
        val LEVEL_RANGE = -10f..10f
        private const val FLAT = "0,0,0,0,0,0,0"
        private var instance: MiSoundUtils? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): MiSoundUtils =
            instance ?: MiSoundUtils(context).also { instance = it }

        private fun parseLevels(preset: String): FloatArray {
            val values = preset.split(',')
            require(values.size == BAND_COUNT) { "Expected seven MiSound bands" }
            return values
                .map {
                    it.trim().toFloat().also { level ->
                        require(level.isFinite() && level in LEVEL_RANGE) { "Level out of range" }
                    }
                }
                .toFloatArray()
        }

        /** A connection notification alone does not establish the media route. */
        internal fun isHeadphoneRouteEligible(
            types: List<Int>,
            usb: Boolean,
            bluetooth: Boolean,
        ): Boolean = types.size == 1 && types.all {
            when (it) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> true
                AudioDeviceInfo.TYPE_USB_HEADSET -> usb
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> bluetooth
                else -> false
            }
        }

        private fun cleanup(failure: RuntimeException, action: () -> Unit) {
            try {
                action()
            } catch (error: RuntimeException) {
                if (failure !== error) failure.addSuppressed(error)
            }
        }
    }

    private val app = context.applicationContext.createDeviceProtectedStorageContext()
    private val preferences = PartsPreferences.getDefaultSharedPreferences(app)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val worker = HandlerThread("MiSound").apply { start() }
    private val handler = Handler(worker.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executor { handler.post(it) }
    private val mutableState = MutableStateFlow(MiSoundState())
    val state = mutableState.asStateFlow()
    private var supportedHeadsets = emptyList<Int>()
    private var callbacksRegistered = false
    private var callbackAttempts = 0
    private val callbackTask = Runnable { registerCallbacks() }
    private var serverDown = false
    private var communicationActive = false
    private var appliedEnabled: Boolean? = null
    private var appliedSurround: Boolean? = null
    private var appliedEarCompensation: Boolean? = null
    private var appliedEarPreset: Int? = null
    private var routeCallbacksRegistered = false
    private var routeCallbackAttempts = 0
    private var routeKnown = false
    private var eligibleHeadphones = false
    private var platformSpatializerSupported = true
    private val mediaAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    private val routeListener = AudioManager.OnDevicesForAttributesChangedListener { _, _ ->
        scheduleAudioUpdate()
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = scheduleAudioUpdate()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = scheduleAudioUpdate()
    }
    private val routeCallbackTask = Runnable {
        registerRouteCallbacks()
        if (callbacksRegistered && !serverDown && sound?.hasControl() == true) updateAudioState()
        else publishState()
    }
    private var sound: MiSoundEffect? = null
    private var attempts = 0
    private val restoreTask = Runnable { restore() }
    private val audioTask = Runnable { updateAudioState() }
    private val modeListener = AudioManager.OnModeChangedListener { scheduleAudioUpdate() }
    private val playback =
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                scheduleAudioUpdate()
            }
        }
    private val recording =
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(
                configs: MutableList<AudioRecordingConfiguration>
            ) {
                scheduleAudioUpdate()
            }
        }

    /** Move legacy audio keys without overwriting values already saved by this version. */
    private fun migratePreferences() {
        val saved = preferences.all
        val editor = preferences.edit()
        var changed = false
        for (key in listOf(PREF_ENABLE, PREF_HEADSET, PREF_PRESET, PREF_HIFI, PREF_EQ, PREF_PAUSE)) {
            val oldKey = key.replaceFirst("misound_", "dirac_")
            if (!saved.containsKey(oldKey)) continue
            if (!saved.containsKey(key)) {
                when (val value = saved[oldKey]) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.remove(oldKey)
            changed = true
        }
        if (changed) editor.apply()
    }

    init {
        handler.post(callbackTask)
    }

    private fun registerCallbacks() {
        migratePreferences()
        try {
            audio.setAudioServerStateCallback(
                executor,
                object : AudioManager.AudioServerStateCallback() {
                    override fun onAudioServerDown() =
                        synchronized(this@MiSoundUtils) {
                            serverDown = true
                            handler.removeCallbacks(restoreTask)
                            handler.removeCallbacks(audioTask)
                            handler.removeCallbacks(routeCallbackTask)
                            routeKnown = false
                            eligibleHeadphones = false
                            releaseEffect()
                            publishState()
                        }

                    override fun onAudioServerUp() =
                        synchronized(this@MiSoundUtils) {
                            serverDown = false
                            attempts = 0
                            registerRouteCallbacks()
                            restore()
                        }
                },
            )
            audio.addOnModeChangedListener(executor, modeListener)
            audio.registerAudioPlaybackCallback(playback, handler)
            audio.registerAudioRecordingCallback(recording, handler)
            callbacksRegistered = true
        } catch (failure: RuntimeException) {
            cleanup(failure) { audio.removeOnModeChangedListener(modeListener) }
            cleanup(failure) { audio.unregisterAudioPlaybackCallback(playback) }
            cleanup(failure) { audio.unregisterAudioRecordingCallback(recording) }
            cleanup(failure) { audio.clearAudioServerStateCallback() }
            handler.removeCallbacks(audioTask)
            Log.w("MiSoundUtils", "Cannot register audio callbacks", failure)
            if (++callbackAttempts < 5) handler.postDelayed(callbackTask, 1000)
            releaseEffect()
            publishState()
            return
        }
        registerRouteCallbacks()
        restore()
    }

    /** Optional route observation must not take down the existing effect owner. */
    private fun registerRouteCallbacks() {
        if (routeCallbacksRegistered || serverDown) return
        try {
            audio.registerAudioDeviceCallback(devices, handler)
            audio.addOnDevicesForAttributesChangedListener(mediaAttributes, executor, routeListener)
            routeCallbacksRegistered = true
            routeCallbackAttempts = 0
            handler.removeCallbacks(routeCallbackTask)
        } catch (failure: RuntimeException) {
            cleanup(failure) { audio.unregisterAudioDeviceCallback(devices) }
            cleanup(failure) { audio.removeOnDevicesForAttributesChangedListener(routeListener) }
            routeKnown = false
            eligibleHeadphones = false
            platformSpatializerSupported = true
            Log.w("MiSoundUtils", "Cannot observe headphone route", failure)
            if (++routeCallbackAttempts < 5) handler.postDelayed(routeCallbackTask, 1000)
        }
    }

    private fun refreshRouteState() {
        routeKnown = false
        eligibleHeadphones = false
        if (!routeCallbacksRegistered || serverDown) return
        try {
            // Stock excludes legacy 3D whenever the platform supports spatialization,
            // including when that spatializer is currently disabled or unavailable.
            platformSpatializerSupported =
                audio.spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
            val types = audio.getDevicesForAttributes(mediaAttributes).map { it.type }
            eligibleHeadphones = isHeadphoneRouteEligible(
                types,
                SystemProperties.getBoolean("ro.vendor.audio.soundfx.usb", false),
                SystemProperties.getBoolean("ro.vendor.audio.misound.bluetooth.enable", false),
            )
            routeKnown = true
        } catch (error: RuntimeException) {
            platformSpatializerSupported = true
            Log.w("MiSoundUtils", "Cannot query headphone route", error)
        }
    }

    @Synchronized
    private fun scheduleAudioUpdate() {
        handler.removeCallbacks(audioTask)
        handler.post(audioTask)
    }

    private fun effectiveEnabled(requested: Boolean, pause: Boolean): Boolean {
        val mode = audio.mode
        communicationActive =
            mode != AudioManager.MODE_NORMAL && mode != AudioManager.MODE_RINGTONE
        if (!communicationActive)
            communicationActive =
                audio.activePlaybackConfigurations.any {
                    it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED &&
                        it.audioAttributes.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
                }
        if (!communicationActive)
            communicationActive =
                audio.activeRecordingConfigurations.any {
                    !it.isClientSilenced &&
                        it.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
                }
        return requested && !(pause && communicationActive)
    }

    @Synchronized
    private fun updateAudioState() {
        if (serverDown || (sound == null && attempts >= 5)) return
        try {
            reconcileEnabledState()
            publishState()
        } catch (error: RuntimeException) {
            Log.w("MiSoundUtils", "Cannot update communication bypass", error)
            restoreAfterFailure()
        }
    }

    private fun reconcileEnabledState() {
        val effect = requireEffect()
        val enabled = effectiveEnabled(isEnabledRequested(), isPauseDuringCallsEnabled())
        // Control can return after another client changed private native state.
        // A valid handle and our last write do not prove the current state.
        if (appliedEnabled != enabled || !effect.hasExpectedEnabledState(enabled) ||
            effect.getMusic() != (if (enabled) 1 else 0)) {
            applyEnabled(enabled)
        }
        refreshRouteState()
        applySurround(effect, enabled)
        applyEarCompensation(effect, enabled)
    }

    @Synchronized
    private fun restore() {
        try {
            reconcileEnabledState()
            attempts = 0
            handler.removeCallbacks(restoreTask)
            publishState()
        } catch (error: RuntimeException) {
            Log.w("MiSoundUtils", "Cannot restore MiSound", error)
            if (++attempts < 5 && !serverDown) {
                handler.removeCallbacks(restoreTask)
                handler.postDelayed(restoreTask, 1000)
            }
            publishState()
        }
    }

    fun requestRefresh() {
        handler.post {
            attempts = 0
            if (callbacksRegistered) {
                routeCallbackAttempts = 0
                registerRouteCallbacks()
                restore()
            } else {
                callbackAttempts = 0
                handler.removeCallbacks(callbackTask)
                registerCallbacks()
            }
        }
    }

    /** Queue native operations in user order; neither UI nor tile threads wait for the DSP. */
    fun submit(action: (MiSoundUtils) -> Unit, onFailure: () -> Unit = {}) {
        handler.post {
            try {
                action(this)
            } catch (error: RuntimeException) {
                Log.w("MiSoundUtils", "Cannot apply MiSound", error)
                restoreAfterFailure()
                mainHandler.post(onFailure)
            }
            publishState()
        }
    }

    private fun publishState() {
        mutableState.value = MiSoundState(
            available = !serverDown && sound?.hasControl() == true,
            enabled = isEnabledRequested(),
            equalizer = isEqualizerEnabled(),
            pauseDuringCalls = isPauseDuringCallsEnabled(),
            paused = isPausedForCommunication(),
            headset = getHeadsetType(),
            preset = getSavedPreset(),
            scenario = getScenario(),
            hifi = getHifiMode(),
            supportedHeadsets = supportedHeadsets,
            customLevels = parseLevels(getCustomLevels()).toList(),
            surroundSupported = isSurroundConfigured() && !platformSpatializerSupported,
            surroundAvailable = isSurroundAvailable(),
            surroundRequested = isSurroundRequested(),
            earCompensationSupported = isEarCompensationConfigured(),
            earCompensationAvailable = isEarCompensationAvailable(),
            earCompensationRequested = isEarCompensationRequested(),
            earCompensationPreset = getEarCompensationPreset(),
        )
    }

    private fun releaseEffect() {
        val previous = sound
        sound = null
        supportedHeadsets = emptyList()
        appliedEnabled = null
        if (previous == null) return
        try {
            if (!serverDown && previous.hasControl()) bypassOwnedEffect(previous)
        } catch (error: RuntimeException) {
            Log.w("MiSoundUtils", "Cannot bypass MiSound before release", error)
        } finally {
            appliedSurround = null
            appliedEarCompensation = null
            appliedEarPreset = null
            try {
                previous.release()
            } catch (error: RuntimeException) {
                Log.w("MiSoundUtils", "Cannot release MiSound", error)
            }
        }
    }

    private fun requireEffect(): MiSoundEffect {
        check(callbacksRegistered) { "Audio callbacks unavailable" }
        check(!serverDown) { "Audio server is down" }
        sound?.let {
            check(it.hasControl()) { "MiSound control unavailable" }
            return it
        }
        try {
            releaseEffect()
            val effect = MiSoundEffect(0, 0)
            sound = effect
            effect.setControlStatusListener { _, granted ->
                handler.post {
                    synchronized(this@MiSoundUtils) {
                        if (sound !== effect || serverDown) return@synchronized
                        appliedEnabled = null
                        appliedSurround = null
                        appliedEarCompensation = null
                        appliedEarPreset = null
                        if (granted) {
                            attempts = 0
                            // Another owner may have changed tuning as well as enable state.
                            releaseEffect()
                            restore()
                        } else {
                            publishState()
                        }
                    }
                }
            }
            check(effect.hasControl()) { "MiSound control unavailable" }
            applyEnabled(false)
            if (isEnabledRequested()) {
                applySettings()
                applyEnabled(effectiveEnabled(true, isPauseDuringCallsEnabled()))
            }
            supportedHeadsets = try {
                effect.getHeadsetList().toList()
            } catch (error: RuntimeException) {
                Log.w("MiSoundUtils", "Headset capabilities unavailable", error)
                emptyList()
            }
            return effect
        } catch (error: RuntimeException) {
            if (sound?.hasControl() != false) releaseEffect()
            throw error
        }
    }

    private fun safeString(key: String, fallback: String): String =
        try {
            preferences.getString(key, fallback) ?: fallback
        } catch (_: ClassCastException) {
            preferences.edit().remove(key).apply()
            fallback
        }

    private fun safeBoolean(key: String, fallback: Boolean): Boolean =
        try {
            preferences.getBoolean(key, fallback)
        } catch (_: ClassCastException) {
            preferences.edit().remove(key).apply()
            fallback
        }

    private fun safeInt(key: String, fallback: Int, range: IntRange): Int {
        val parsed = safeString(key, fallback.toString()).toIntOrNull()
        if (parsed != null && parsed in range) return parsed
        preferences.edit().remove(key).apply()
        return fallback
    }

    @Synchronized fun getHeadsetType() = safeInt(PREF_HEADSET, 0, 0..255)

    @Synchronized fun getScenario() = safeInt(PREF_SCENE, 4, 0..4)

    @Synchronized
    fun getSavedPreset(): String {
        val preset = safeString(PREF_PRESET, FLAT)
        if (preset == CUSTOM) return preset
        // Earlier builds shipped Classical shifted 3 dB above the stock curve.
        if (preset == LEGACY_CLASSICAL) {
            preferences.edit().putString(PREF_PRESET, STOCK_CLASSICAL).apply()
            return STOCK_CLASSICAL
        }
        return try {
            parseLevels(preset)
            preset
        } catch (error: IllegalArgumentException) {
            Log.w("MiSoundUtils", "Discarding invalid saved preset", error)
            preferences.edit().remove(PREF_PRESET).apply()
            FLAT
        }
    }

    @Synchronized
    fun getCustomLevels(): String {
        val levels = safeString(PREF_CUSTOM, FLAT)
        return try {
            parseLevels(levels)
            levels
        } catch (error: IllegalArgumentException) {
            Log.w("MiSoundUtils", "Discarding invalid custom curve", error)
            preferences.edit().remove(PREF_CUSTOM).apply()
            FLAT
        }
    }

    private fun levelsFor(preset: String) =
        parseLevels(if (preset == CUSTOM) getCustomLevels() else preset)

    private fun applySettings() {
        sound!!.setHeadsetType(getHeadsetType())
        applyLevel(getSavedPreset(), isEqualizerEnabled())
        sound!!.setScenario(getScenario())
        applyHifi(getHifiMode())
    }

    private fun isSurroundConfigured() =
        (SystemProperties.getInt("ro.vendor.audio.feature.spatial", 0) and 2) != 0

    private fun isSurroundAvailable() =
        isSurroundConfigured() && routeKnown && eligibleHeadphones && !platformSpatializerSupported

    @Synchronized
    fun isSurroundRequested() = safeBoolean(PREF_SURROUND, false)

    private fun applySurround(
        effect: MiSoundEffect,
        enable: Boolean,
        requested: Boolean = isSurroundRequested(),
        force: Boolean = false,
    ) {
        // Never probe optional ABI support from an ACK: unknown IDs can return success.
        if (!isSurroundConfigured() && appliedSurround == null) return
        val active = enable && requested && isSurroundAvailable()
        if (force || appliedSurround != active) {
            effect.setSurroundEnabled(active)
            appliedSurround = active
        }
    }

    @Synchronized
    fun setSurroundEnabled(enabled: Boolean) {
        refreshRouteState()
        check(isSurroundConfigured()) { "Headphone 3D is not configured" }
        check(!enabled || (isEnabledRequested() && isSurroundAvailable())) {
            "Headphone 3D route unavailable"
        }
        val effect = requireEffect()
        applySurround(
            effect,
            effectiveEnabled(isEnabledRequested(), isPauseDuringCallsEnabled()),
            requested = enabled,
            force = true,
        )
        preferences.edit().putBoolean(PREF_SURROUND, enabled).apply()
    }

    private fun isEarCompensationConfigured() =
        SystemProperties.getBoolean("ro.vendor.audio.sfx.earadj", false)

    private fun isEarCompensationAvailable() =
        isEarCompensationConfigured() && routeKnown && eligibleHeadphones

    @Synchronized
    fun isEarCompensationRequested() = safeBoolean(PREF_EAR_COMPENSATION, false)

    @Synchronized
    fun getEarCompensationPreset() = safeInt(PREF_EAR_PRESET, 1, 1..3)

    private fun applyEarCompensation(
        effect: MiSoundEffect,
        enable: Boolean,
        requested: Boolean = isEarCompensationRequested(),
        preset: Int = getEarCompensationPreset(),
        forceEnabled: Boolean = false,
        forcePreset: Boolean = false,
    ) {
        if (!isEarCompensationConfigured() && appliedEarCompensation == null) return
        val active = enable && requested && isEarCompensationAvailable()
        if ((active || forcePreset) && (forcePreset || appliedEarPreset != preset)) {
            effect.setEarCompensationPreset(preset)
            appliedEarPreset = preset
        }
        if (forceEnabled || appliedEarCompensation != active) {
            effect.setEarCompensationEnabled(active)
            appliedEarCompensation = active
        }
    }

    @Synchronized
    fun setEarCompensationEnabled(enabled: Boolean) {
        refreshRouteState()
        check(isEarCompensationConfigured()) { "Headphone volume adjustment is not configured" }
        check(!enabled || (isEnabledRequested() && isEarCompensationAvailable())) {
            "Headphone volume adjustment route unavailable"
        }
        val effect = requireEffect()
        applyEarCompensation(
            effect,
            effectiveEnabled(isEnabledRequested(), isPauseDuringCallsEnabled()),
            requested = enabled,
            forceEnabled = true,
        )
        preferences.edit().putBoolean(PREF_EAR_COMPENSATION, enabled).apply()
    }

    @Synchronized
    fun setEarCompensationPreset(preset: Int) {
        require(preset in 1..3)
        refreshRouteState()
        check(isEnabledRequested() && isEarCompensationRequested() && isEarCompensationAvailable()) {
            "Headphone volume adjustment unavailable"
        }
        val effect = requireEffect()
        applyEarCompensation(
            effect,
            effectiveEnabled(isEnabledRequested(), isPauseDuringCallsEnabled()),
            preset = preset,
            forceEnabled = true,
            forcePreset = true,
        )
        preferences.edit().putString(PREF_EAR_PRESET, preset.toString()).apply()
    }

    /** Clear independent headphone DSP gates even if a later core bypass write fails. */
    private fun bypassOwnedEffect(effect: MiSoundEffect) {
        var failure: RuntimeException? = null
        try {
            applySurround(effect, false)
        } catch (error: RuntimeException) {
            failure = error
        }
        try {
            applyEarCompensation(effect, false)
        } catch (error: RuntimeException) {
            if (failure == null) failure = error
            else if (failure !== error) failure.addSuppressed(error)
        }
        try {
            check(effect.setEnabled(false) == AudioEffect.SUCCESS)
        } catch (error: RuntimeException) {
            if (failure == null) failure = error
            else if (failure !== error) failure.addSuppressed(error)
        }
        try {
            effect.setMusic(0)
        } catch (error: RuntimeException) {
            if (failure == null) failure = error
            else if (failure !== error) failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    private fun applyEnabled(enable: Boolean) {
        val effect = checkNotNull(sound)
        if (enable) {
            refreshRouteState()
            applySurround(effect, true)
            applyEarCompensation(effect, true)
            effect.setMusic(1)
            effect.setScenario(getScenario())
            check(effect.setEnabled(true) == AudioEffect.SUCCESS)
        } else {
            bypassOwnedEffect(effect)
        }
        check(effect.hasExpectedEnabledState(enable) && effect.getMusic() == if (enable) 1 else 0) {
            "MiSound enable state did not apply"
        }
        appliedEnabled = enable
    }

    @Synchronized
    fun restoreAfterFailure() {
        // Keep the listener attached while another client owns the effect.
        // Its control-regain callback resumes recovery even after bounded retries.
        if (sound?.hasControl() != false) releaseEffect()
        attempts = 0
        restore()
    }

    @Synchronized fun isEnabledRequested() = safeBoolean(PREF_ENABLE, false)

    @Synchronized fun isEqualizerEnabled() = safeBoolean(PREF_EQ, true)

    @Synchronized fun isPauseDuringCallsEnabled() = safeBoolean(PREF_PAUSE, true)

    @Synchronized
    fun isPausedForCommunication() =
        isEnabledRequested() && isPauseDuringCallsEnabled() && communicationActive

    @Synchronized
    fun setEnabled(enable: Boolean): Boolean {
        if (!enable) preferences.edit().putBoolean(PREF_ENABLE, false).apply()
        return try {
            requireEffect()
            applyEnabled(false)
            if (enable) {
                applySettings()
                applyEnabled(effectiveEnabled(true, isPauseDuringCallsEnabled()))
            }
            preferences.edit().putBoolean(PREF_ENABLE, enable).apply()
            true
        } catch (error: RuntimeException) {
            Log.w("MiSoundUtils", "Cannot change MiSound", error)
            false
        }
    }

    @Synchronized
    fun setEqualizerEnabled(enabled: Boolean) {
        requireEffect()
        applyLevel(getSavedPreset(), enabled)
        preferences.edit().putBoolean(PREF_EQ, enabled).apply()
    }

    @Synchronized
    fun setPauseDuringCallsEnabled(enabled: Boolean) {
        requireEffect()
        applyEnabled(effectiveEnabled(isEnabledRequested(), enabled))
        preferences.edit().putBoolean(PREF_PAUSE, enabled).apply()
    }

    @Synchronized
    fun setLevel(preset: String) {
        levelsFor(preset)
        requireEffect()
        applyLevel(preset, isEqualizerEnabled())
        preferences.edit().putString(PREF_PRESET, preset).apply()
    }

    private fun applyLevel(preset: String, enabled: Boolean) {
        val levels = if (enabled) levelsFor(preset) else FloatArray(0)
        repeat(MiSoundEffect.EQ_BAND_COUNT) { sound!!.setLevel(it, levels.getOrElse(it) { 0f }) }
    }

    /** Edits the user curve; the DSP only changes while that curve is selected. */
    @Synchronized
    fun setCustomLevel(band: Int, level: Float) {
        require(band in 0 until BAND_COUNT && level in LEVEL_RANGE)
        val levels = parseLevels(getCustomLevels())
        levels[band] = Math.round(level * 2f) / 2f
        val curve = levels.joinToString(",")
        if (getSavedPreset() == CUSTOM) {
            requireEffect()
            if (isEqualizerEnabled()) {
                repeat(MiSoundEffect.EQ_BAND_COUNT) { sound!!.setLevel(it, levels.getOrElse(it) { 0f }) }
            }
        }
        preferences.edit().putString(PREF_CUSTOM, curve).apply()
    }

    @Synchronized
    fun setHeadsetType(value: Int) {
        requireEffect().setHeadsetType(value)
        preferences.edit().putString(PREF_HEADSET, value.toString()).apply()
    }

    @Synchronized fun getHifiMode() = safeBoolean(PREF_HIFI, false)

    fun isHifiSupported() =
        SystemProperties.getBoolean("vendor.audio.feature.hifi_audio.enable", false)

    private fun applyHifi(enabled: Boolean) {
        if (isHifiSupported()) {
            audio.setParameters("hifi_mode=$enabled")
            sound!!.setHifiMode(if (enabled) 1 else 0)
        }
    }

    @Synchronized
    fun setHifiMode(value: Int) {
        check(isHifiSupported()) { "HAL Hi-Fi feature is disabled" }
        require(value in 0..1)
        requireEffect()
        applyHifi(value != 0)
        preferences.edit().putBoolean(PREF_HIFI, value != 0).apply()
    }

    @Synchronized
    fun setScenario(value: Int) {
        requireEffect().setScenario(value)
        preferences.edit().putString(PREF_SCENE, value.toString()).apply()
    }
}
