# Rootdir maintenance for Android 17 / SM8250

This tree targets Kona and keeps the Qualcomm 4.19 vendor interfaces required
by the supported devices. A new Android release alone does not justify changing
scheduler, CPU, ZRAM capacity, watermark, or power values.

## Ownership

- The platform `system/core/libprocessgroup/profiles/cgroups.json` mounts
  controllers during early-init. Do not mount `/dev/blkio` again in vendor init.
  The optional `ro.vendor.iocgrp.config=1` action retains its subgroup and weights.
- Use task profiles for service placement. The audio override already uses
  `ProcessCapacityHigh HighPerformance`; this rootdir has no `writepid` options.
- Preserve the existing schedtune and CFQ integration on kernels that provide it.
  Do not force a cgroup-v2-only or GKI layout onto this legacy kernel.
- Post-boot memory setup validates MemTotal by key rather than column width.
  It never reformats an already initialized ZRAM device. Devfreq loops skip
  unmatched directories, and NPU loops require the power control to be writable.
- Android 17 mmd is an alternative ZRAM owner, not an additional tuning script.
  It is not enabled on the audited device. Before enabling it, migrate ZRAM
  configuration and remove competing legacy setup; validate kernel support for
  the selected maintenance features. No mmd/writeback migration is made here.

## Validation and boundaries

The September 23 audit used the connected alioth user build with SELinux
Enforcing, the checked-out platform source, and the references below. PSI,
ZRAM, schedtune, CFQ and NPU devfreq nodes were observed on the installed image.
Shell scripts passed host bash and device `/vendor/bin/sh -n` parsing; changed
init files passed the existing host_init_verifier. Temporary host fixtures
checked RAM parsing and read-ahead behavior, including absent MMC paths.
No ROM was rebuilt and the changed boot sequence has not run on-device.
No measured performance improvement is claimed.

Keep `TARGET_BUILD_VARIANT=user`. Validate the next user build's boot logs,
service states, swap, memory pressure and suspend behavior before further tuning.
Keep SELinux enforcing; diagnose consumers and labels instead of adding blanket
allows or treating every debug-access denial as a functional defect.

## Primary references

- [AOSP cgroups and task profiles](https://source.android.com/docs/core/perf/cgroups)
- [Android 17 init reference](https://android.googlesource.com/platform/system/core/+/android17-release/init/README.md)
- [AOSP memory management daemon](https://source.android.com/docs/core/perf/mmd)
- [AOSP LMKD](https://source.android.com/docs/core/perf/lmkd)
