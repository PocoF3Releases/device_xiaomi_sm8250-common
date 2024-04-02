/*
 * Copyright (C) 2022 The CipherOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

public class DisplayNodes {

    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/msm_fb_ea_enable";
    private static final String HBM_ENABLE_KEY = "hbm_mode";
    private static final String HBM_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";

    public static final String getDcDimmingEnableKey() {
        return DC_DIMMING_ENABLE_KEY;
    }

    public static final String getDcDimmingNode() {
        return DC_DIMMING_NODE;
    }

    public static final String getHbmEnableKey() {
        return HBM_ENABLE_KEY;
    }

    public static final String getHbmNode() {
        return HBM_NODE;
    }
}
