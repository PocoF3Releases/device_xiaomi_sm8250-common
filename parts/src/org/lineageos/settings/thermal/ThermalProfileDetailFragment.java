/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.thermal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.lineageos.settings.R;

import java.util.Locale;

public class ThermalProfileDetailFragment extends Fragment {
    private static final String ARG_STATE = "storage_state";

    static ThermalProfileDetailFragment newInstance(int state) {
        ThermalProfileDetailFragment fragment = new ThermalProfileDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_STATE, state);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.thermal_profile_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int state = getArguments() == null ? -1 : getArguments().getInt(ARG_STATE, -1);
        int region = ThermalUtils.getSelectedRegion();
        ThermalProfiles.Profile profile = ThermalProfiles.findByStorageState(region, state);
        if (profile == null) return;

        requireActivity().setTitle(profile.titleRes);
        ((TextView) view.findViewById(R.id.thermal_detail_summary)).setText(profile.summaryRes);
        ((TextView) view.findViewById(R.id.thermal_detail_meta)).setText(
                (region == ThermalProfiles.REGION_INDIA ? "India" : "Global")
                        + " • sconfig " + profile.sconfig + " • " + profile.policy.configName);
        ((TextView) view.findViewById(R.id.thermal_detail_cpu)).setText(formatCpu(profile.policy));
        ((TextView) view.findViewById(R.id.thermal_detail_gpu)).setText(formatGpu(profile.policy));
        ((TextView) view.findViewById(R.id.thermal_detail_controls))
                .setText(formatControls(profile.policy));
        ((TextView) view.findViewById(R.id.thermal_detail_sensor))
                .setText(formatSensor(profile.policy));
    }

    private static String formatCpu(ThermalProfiles.Policy p) {
        StringBuilder out = new StringBuilder();
        appendCpuCurve(out, "CPU4 / performance cluster", p.cpu4Trigger, p.cpu4Clear, p.cpu4Khz);
        out.append("\n\n");
        appendCpuCurve(out, "CPU7 / prime core", p.cpu7Trigger, p.cpu7Clear, p.cpu7Khz);
        return out.toString();
    }

    private static void appendCpuCurve(StringBuilder out, String label,
            int[] trigger, int[] clear, int[] khz) {
        out.append(label).append('\n');
        if (trigger.length == 0 || khz.length == 0) {
            out.append("No explicit profile ceiling");
            return;
        }
        int min = khz[0], max = khz[0];
        for (int value : khz) { min = Math.min(min, value); max = Math.max(max, value); }
        out.append("Below ").append(trigger[0]).append("°C: no explicit profile cap\n");
        if (min == max) {
            out.append("Configured thermal ceiling: ").append(formatMhz(max)).append('\n');
        } else {
            out.append("Configured ceiling range: ")
                    .append(formatMhz(min)).append(" – ").append(formatMhz(max)).append('\n');
        }
        for (int i = 0; i < trigger.length && i < khz.length; i++) {
            out.append(trigger[i]).append("°C");
            if (i < clear.length) out.append(" (clear ").append(clear[i]).append("°C)");
            out.append(" → ").append(formatMhz(khz[i]));
            if (i + 1 < trigger.length && i + 1 < khz.length) out.append('\n');
        }
    }

    private static String formatGpu(ThermalProfiles.Policy p) {
        if (p.gpuTrigger.length == 0 || p.gpuState.length == 0) {
            return "No explicit GPU thermal ceiling";
        }
        double min = gpuMhzValue(p.gpuState[0]), max = min;
        for (int state : p.gpuState) {
            double mhz = gpuMhzValue(state);
            min = Math.min(min, mhz);
            max = Math.max(max, mhz);
        }
        StringBuilder out = new StringBuilder();
        out.append("Below ").append(p.gpuTrigger[0]).append("°C: no explicit profile cap\n");
        if (Double.compare(min, max) == 0) {
            out.append("Configured thermal ceiling: ").append(formatGpuMhzValue(max)).append('\n');
        } else {
            out.append("Configured ceiling range: ").append(formatGpuMhzValue(min))
                    .append(" – ").append(formatGpuMhzValue(max)).append('\n');
        }
        for (int i = 0; i < p.gpuTrigger.length && i < p.gpuState.length; i++) {
            out.append(p.gpuTrigger[i]).append("°C");
            if (i < p.gpuClear.length) out.append(" (clear ").append(p.gpuClear[i]).append("°C)");
            out.append(" → state ").append(p.gpuState[i])
                    .append(" ≈ ").append(formatGpuMhz(p.gpuState[i]));
            if (i + 1 < p.gpuTrigger.length && i + 1 < p.gpuState.length) out.append('\n');
        }
        out.append("\n\nAlioth kona-v2 GPU states used here: "
                + "0≈670 MHz, 1≈587 MHz, 2≈525 MHz.");
        return out.toString();
    }

    private static String formatControls(ThermalProfiles.Policy p) {
        StringBuilder out = new StringBuilder();
        if (p.hasControl(ThermalProfiles.CONTROL_BATTERY)) appendRange(out, "Battery cooling", p.batteryTrigger);
        if (p.hasControl(ThermalProfiles.CONTROL_TEMP_STATE)) appendRange(out, "Thermal state", p.tempStateTrigger);
        if (p.hasControl(ThermalProfiles.CONTROL_HOTPLUG)) appendLine(out, "Big-core hotplug", "50°C");
        if (p.hasControl(ThermalProfiles.CONTROL_BOOST_LIMIT)) appendLine(out, "Boost limit", "55°C");
        if (p.hasControl(ThermalProfiles.CONTROL_LOW_BATTERY)) appendLine(out, "Low-battery CPU/core protection", "5% SOC");
        if (p.hasControl(ThermalProfiles.CONTROL_HBM)) appendLine(out, "HBM disable", "40°C");
        if (p.hasControl(ThermalProfiles.CONTROL_BACKLIGHT)) appendLine(out, "Backlight cooling", "37–49°C");
        if (p.hasControl(ThermalProfiles.CONTROL_MODEM)) appendLine(out, "Modem UL/DL cooling", "48°C");
        if (p.hasControl(ThermalProfiles.CONTROL_WIRELESS)) appendLine(out, "Wireless charging cooling", "20°C and 60°C");
        return out.length() == 0 ? "No additional controls in this config." : out.toString();
    }

    private static void appendRange(StringBuilder out, String label, int[] values) {
        if (values.length == 0) return;
        String value = values.length == 1 ? values[0] + "°C"
                : values[0] + "–" + values[values.length - 1] + "°C";
        appendLine(out, label, value);
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        if (out.length() > 0) out.append('\n');
        out.append(label).append(": ").append(value);
    }

    private static String formatSensor(ThermalProfiles.Policy p) {
        String base = "CPU/GPU triggers reference VIRTUAL-SENSOR0. "
                + "The Normal-family configs define it from quiet_therm, cpu_therm, battery, "
                + "wifi_therm, pa_therm0, pa_therm1 and charger_therm0 with weights "
                + "362, 229, 108, -111, -224, 734 and -159 "
                + "(sum 1000, compensation 2169).";
        return base + (p.definesVirtualSensor
                ? "\n\nThis config contains the VIRTUAL-SENSOR0 definition."
                : "\n\nThis config references the active VIRTUAL-SENSOR0 definition.");
    }

    private static String formatMhz(int khz) {
        return String.format(Locale.US, "%.1f MHz", khz / 1000f);
    }

    private static double gpuMhzValue(int state) {
        switch (state) {
            case 0: return 670.0;
            case 1: return 587.0;
            case 2: return 525.0;
            case 3: return 490.0;
            case 4: return 441.6;
            case 5: return 400.0;
            case 6: return 305.0;
            default: return 0.0;
        }
    }

    private static String formatGpuMhz(int state) { return formatGpuMhzValue(gpuMhzValue(state)); }
    private static String formatGpuMhzValue(double mhz) {
        return Math.rint(mhz) == mhz
                ? String.format(Locale.US, "%.0f MHz", mhz)
                : String.format(Locale.US, "%.1f MHz", mhz);
    }
}
