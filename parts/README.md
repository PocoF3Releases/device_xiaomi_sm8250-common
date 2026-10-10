# XiaomiParts

Device settings use the checkout's Kotlin and Jetpack Compose/Material 3 modules. Soong supplies the matching compiler; use platform dependencies without a separate Gradle build.

## Feature owners

| Folder | Responsibility |
| --- | --- |
| compose | Theme, accessible controls, haptics and searchable app lists |
| display | HBM/DC state, brightness recovery and tiles |
| touchsampling | Global touch polling and successful-write persistence |
| speaker | Timed cleaning tone, routing, focus and lifecycle cleanup |
| dirac | MiSound ownership, presets, calls/routes and recovery |
| refreshrate | Per-app display modes and user-baseline restoration |
| thermal | Regional policy, HAL-aware touch tuning and profile reference |
| utils | File operations and device-protected preferences |

Retain manifest entry points, permissions, translated resources, preference keys and hardware policy contracts. Compose owns layout and insets. Artwork provenance and licenses are in `licenses/`.

## Regression checks

Host fixtures compile the actual Kotlin owners against simulated Android/HAL boundaries in disposable temporary directories. From this repository, use the checkout JDK, for example:

```sh
python3 -B tests/test_hbm_restore.py --jdk ../../../prebuilts/jdk/jdk21/linux-x86
```

Related checks cover Alioth touch defaults, touch controller state/reset, thermal detail formatting and haptic settings/capability fallback. These do not establish rendered UI, production SELinux access or peripheral behavior. Device acceptance records are maintained in the Agents.md workhub.

## Build and device verification

Preserve the existing ROM configuration and incremental output. For app-only verification, reuse existing dependencies with temporary compiler/package outputs; do not clean output or invoke an unconstrained ROM build graph.

A staged system-app update in `/data/app` overrides the ROM base APK. Inspect `pm path org.lineageos.settings` before ROM-base validation and remove the update through the supported package-manager flow if needed. Restore user settings after device checks and keep raw screenshots and preference backups private.
