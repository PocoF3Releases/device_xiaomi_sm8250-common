# RRO overlays naming conventions

## AOSP vs AOSP with Lineage additions vs Lineage-only

We add `.lineage` and increase priority only when we overlay LineageOS additions to AOSP components

## Name

`[Lineage]<component name>Overlay(Common|Device)`

- `android`: `FrameworkRes`
- `com.android.carrierconfig`: `CarrierConfig`
- `com.android.dialer`: `Dialer`
- `com.android.phone`: `Telephony`
- `com.android.providers.settings`: `SettingsProvider`
- `com.android.settings`: `Settings`
- `com.android.systemui`: `SystemUI`
- `com.android.wifi.resources:WifiCustomization`: `WifiResources`
- `lineageos.platform`: `LineageSDK`
- `org.lineageos.aperture`: `Aperture`

## Package name

`<target package>.overlay[.lineage].(common|device)`

## Priority

- AOSP common overlay: 100
- Lineage common overlay: 150
- AOSP device overlay: 200
- Lineage device overlay: 250

## Framework defaults

Do not override SQLite journal or synchronization modes for device performance
tuning. The Android 17 framework in this tree defaults to `TRUNCATE`, `FULL`
(non-WAL), and `NORMAL` (WAL). Inherit these resources so future framework
changes apply automatically. `MEMORY` journaling and `OFF` synchronization
weaken crash/power-loss recovery; apps may still choose their own database policy.

When updating an overlay, verify the installed value with
`adb shell cmd overlay lookup --verbose <target-package> <package>:<type>/<name>`.
An enabled overlay alone does not prove it wins resource resolution.
