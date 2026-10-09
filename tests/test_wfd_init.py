#!/usr/bin/env python3
"""Regression fixtures for the packaged 64-bit WFD init service."""
import hashlib
import unittest
from vendor_init_fixtures import REPO, fixup

PATH = "system_ext/etc/init/wfdservice.rc"
STOCK = """service wfdservice /system_ext/bin/wfdservice
    class main
    user system
    disabled
    oneshot

service wfdservice64 /system_ext/bin/wfdservice64
    class main
    user system
    disabled
    oneshot

on property:vendor.wfdservice=enable
    start wfdservice

on property:vendor.wfdservice64=enable
    start wfdservice64

on property:vendor.wfdservice=disable
    stop wfdservice

on property:vendor.wfdservice64=disable
    stop wfdservice64
"""

class WfdInitTest(unittest.TestCase):
    def test_missing_32_bit_service_and_triggers_removed(self):
        result = fixup(PATH, STOCK)
        self.assertNotRegex(result, r"(?m)^service wfdservice ")
        self.assertNotIn("vendor.wfdservice=", result)
        self.assertNotRegex(result, r"(?m)^    (start|stop) wfdservice$")

    def test_64_bit_service_and_triggers_preserved(self):
        result = fixup(PATH, STOCK)
        kept = STOCK[STOCK.index("service wfdservice64"):STOCK.index("on property:vendor.wfdservice=enable")]
        self.assertIn(kept, result)
        for action, verb in [("enable", "start"), ("disable", "stop")]:
            self.assertIn(f"on property:vendor.wfdservice64={action}\n    {verb} wfdservice64", result)

    def test_fixup_is_idempotent(self):
        result = fixup(PATH, STOCK)
        self.assertEqual(result, fixup(PATH, result))

    def test_packaged_file_and_pin(self):
        vendor = REPO.parents[2] / "vendor/xiaomi/sm8250-common/proprietary" / PATH
        packaged = vendor.read_text()
        self.assertEqual(packaged, fixup(PATH, packaged))
        prefix = packaged[:packaged.index("service ")]
        self.assertEqual(packaged, prefix + fixup(PATH, STOCK))
        pin = next(line for line in (REPO / "proprietary-files.txt").read_text().splitlines()
                   if line.startswith(PATH + "|"))
        self.assertEqual(pin.split("|")[1], "907def8565d8f91f531ca7dfad880af05d540b0c")
        self.assertEqual(pin.split("|")[2], hashlib.sha1(vendor.read_bytes()).hexdigest())

if __name__ == "__main__":
    unittest.main()
