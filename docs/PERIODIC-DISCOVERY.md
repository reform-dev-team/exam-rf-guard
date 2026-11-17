# Periodic discovery

Bluetooth discovery supplements the continuous BLE advertisement and Wi-Fi AP scanners. It starts immediately, then every 120 seconds while the app is in the foreground. Each attempt is bounded to 30 seconds. Discovery may affect existing Bluetooth connections; discovery capability does not guarantee reception.

`BLUETOOTH_DISCOVERY` describes the API path, not a proven transport: Android can report BLE devices during general discovery. The app never pairs, connects, or queries paired-device history. Names and advertised capabilities are self-reported. Counts describe source-specific groups, not unique physical devices.

Completed rounds replace the previous list, including a successful empty list. Failed, interrupted, timed-out, and busy rounds do not mean zero devices. Partial or previous results remain visible with their report time and are excluded from current candidate counts. Returning to the app starts a new round; cancelled rounds cannot publish later.

During empty-room measurement, each successful source records the maximum count per normalized name group across completed rounds. Unnamed peers share one source-specific group. Addresses are deduplicated within a round, never accumulated across rounds. Equal-name devices can substitute for each other without detection. The baseline freezes when saved; exam observations never teach it new exclusions.

Snapshot version 3 stores these baseline counts separately from packet observations. A source mapped to an empty object means a successful empty baseline; an absent source means unmeasured. Versions 1 and 2 remain readable, with no baseline for the new source. Users must measure an empty room again to enable its comparison. Baseline names stay in the same private, non-backed-up local file.

Periodic results have no continuous ranging or proximity vibration. RSSI, when provided, is labelled as strength during that discovery. Existing BLE/AP tracking and location finding retain their existing behavior.

## Validation

Run `./gradlew testDebugUnitTest assembleDebug lintDebug` with JDK 17+ and Android SDK 35. Scanner tests cover completion, duplicates, absent fields, cancellation, timeout, and existing discovery ownership. Core tests cover baseline maxima, missing versus empty coverage, stale results, and sequential scheduling. Permission tests exercise API 29, 31, 33, and 35.

Physical checks must separately record the Android model/version, app build, known peer state, discovery outcome, and lifecycle behavior. Automated or simulated tests do not establish RF reception rates or classroom accuracy. Do not commit real device names, addresses, or logs.
