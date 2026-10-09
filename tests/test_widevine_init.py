#!/usr/bin/env python3
"""Preserve Widevine service setup while stripping an unavailable MIUI migrator."""
import unittest
from vendor_init_fixtures import REPO, fixup

PATH = "vendor/etc/init/android.hardware.drm@1.3-service.widevine.rc"
STOCK = """on property:init.svc.mediadrm=running
    mkdir /data/vendor/mediadrm 0770 media mediadrm
    start vendor.move_data_sh

service vendor.move_data_sh /system/bin/move_widevine_data.sh
    class late_start
    user media
    group media mediadrm system
    disabled
    oneshot

service vendor.drm-widevine-hal-1-3 /vendor/bin/hw/android.hardware.drm@1.3-service.widevine
    interface android.hardware.drm@1.0::ICryptoFactory widevine
    interface android.hardware.drm@1.0::IDrmFactory widevine
    interface android.hardware.drm@1.1::ICryptoFactory widevine
    interface android.hardware.drm@1.1::IDrmFactory widevine
    interface android.hardware.drm@1.2::ICryptoFactory widevine
    interface android.hardware.drm@1.2::IDrmFactory widevine
    interface android.hardware.drm@1.3::ICryptoFactory widevine
    interface android.hardware.drm@1.3::IDrmFactory widevine
    interface android.hidl.base@1.0::IBase widevine
    class hal
    user media
    group media mediadrm drmrpc system
    ioprio rt 4
    task_profiles ProcessCapacityHigh
    capabilities SYS_NICE
"""

class WidevineInitTest(unittest.TestCase):
    def test_missing_migration_service_and_start_removed(self):
        result = fixup(PATH, STOCK)
        self.assertNotIn("vendor.move_data_sh", result)
        self.assertNotIn("move_widevine_data.sh", result)

    def test_drm_service_preserved_byte_for_byte(self):
        service = STOCK[STOCK.index("service vendor.drm-widevine-hal-1-3"):]
        self.assertTrue(fixup(PATH, STOCK).endswith(service))

    def test_data_directory_setup_preserved(self):
        self.assertIn("on property:init.svc.mediadrm=running\n"
                      "    mkdir /data/vendor/mediadrm 0770 media mediadrm\n", fixup(PATH, STOCK))

    def test_stock_writepid_migration_retained(self):
        old = STOCK.replace("task_profiles ProcessCapacityHigh", "writepid /dev/cpuset/foreground/tasks")
        self.assertEqual(fixup(PATH, old), fixup(PATH, STOCK))

    def test_idempotent(self):
        result = fixup(PATH, STOCK)
        self.assertEqual(result, fixup(PATH, result))

    def test_packaged_file_matches_extraction(self):
        vendor = REPO.parents[2] / "vendor/xiaomi/sm8250-common/proprietary" / PATH
        self.assertEqual(vendor.read_text(), fixup(PATH, STOCK))

if __name__ == "__main__":
    unittest.main()
