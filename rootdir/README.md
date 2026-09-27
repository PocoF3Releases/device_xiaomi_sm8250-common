# Rootdir maintenance for Android 16 / SM8250

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
- mmd is an alternative ZRAM owner, not an additional tuning script.
  It is not enabled on the audited device. Before enabling it, migrate ZRAM
  configuration and remove competing legacy setup; validate kernel support for
  the selected maintenance features. No mmd/writeback migration is made here.

## Android 16 source compatibility

The `aosp-16` branch uses the Evolution X Android 16 platform. Its
`ProcessCapacityHigh` and `HighPerformance` task profiles exist, as do the
fs_mgr wrapped-key and filesystem-checkpoint paths. The four imported init
files have install rules. All 27 service executable paths declared in rootdir
have corresponding generated install rules; this is packaging evidence, not
proof that each service runs successfully.

Remove controls for services that are not defined in the product. The stale
`wcnss-service`, `leds-sh`, `bridgemgrd` and `chre` controls were removed. This
does not remove Wi-Fi firmware setup, the sensors HAL or an active context-hub
service. Keep logical AVB first-stage mounts and the current ZRAM owner.

All six init scripts pass the available host init verifier and all seven shell
scripts pass `bash -n`. These checks do not verify runtime sysfs paths or SELinux
access. No Android 16 boot or full policy/build validation is claimed here.

## Historical validation and boundaries

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
- [Android 16 init reference](https://android.googlesource.com/platform/system/core/+/android16-release/init/README.md)
- [AOSP memory management daemon](https://source.android.com/docs/core/perf/mmd)
- [AOSP LMKD](https://source.android.com/docs/core/perf/lmkd)
