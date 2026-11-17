# Periodic discovery

Bluetooth discovery and Wi-Fi Direct peer discovery supplement the continuous BLE advertisement and Wi-Fi AP scanners. A cycle starts immediately, then every 120 seconds while the app is in the foreground. Bluetooth runs first, followed by Wi-Fi Direct, each bounded to 30 seconds. Discovery may affect existing wireless activity; discovery capability does not guarantee reception.

`BLUETOOTH_DISCOVERY` describes the API path, not a proven transport: Android can report BLE devices during general discovery. The app never pairs, connects, or queries paired-device history. Names and advertised capabilities are self-reported. Counts describe source-specific groups, not unique physical devices.

`WIFI_P2P` represents an available peer list reported by Android after discovery starts. It cannot enumerate all infrastructure Wi-Fi clients. Final lists replace earlier lists rather than accumulating devices that disappeared. Entries explicitly marked unavailable or failed are omitted. The report time is when the app received the list, not when a radio packet arrived; the platform can retain cached peer information. No packet timestamp, RSSI, or physical distance is invented.

Wi-Fi Direct discovery is limited to discovery/list APIs and uses the existing Wi-Fi state, nearby-device, and precise-location permissions. It does not add internet permission, create a group, start a socket, publish a service, or connect to peers. Already-running discovery is reported as busy. Cancellation removes listeners, stops the app's own request, and closes its P2P channel. The final list request starts 25 seconds after discovery is accepted, leaving time inside the 30-second bound for a callback.

Completed rounds replace the previous list, including a successful empty list. Failed, interrupted, timed-out, and busy rounds do not mean zero devices. Partial or previous results remain visible with their report time and are excluded from current candidate counts. Returning to the app starts a new round; cancelled rounds cannot publish later.

During empty-room measurement, each successful source records the maximum count per normalized name group across completed rounds. Unnamed peers share one source-specific group. Addresses are deduplicated within a round, never accumulated across rounds. Equal-name devices can substitute for each other without detection. The baseline freezes when saved; exam observations never teach it new exclusions.

Snapshot version 3 stores these baseline counts separately from packet observations. A source mapped to an empty object means a successful empty baseline; an absent source means unmeasured. Versions 1 and 2 remain readable, with no baseline for the new source. Users must measure an empty room again to enable its comparison. Baseline names stay in the same private, non-backed-up local file.

Periodic results have no continuous ranging or proximity vibration. RSSI, when provided, is labelled as strength during that discovery. Existing BLE/AP tracking and location finding retain their existing behavior.

## Validation

Run `./gradlew testDebugUnitTest assembleDebug lintDebug` with JDK 17+ and Android SDK 35. Scanner tests cover completion, duplicates, absent fields, cancellation, timeout, busy state, asynchronous permission failures, channel loss, and existing discovery ownership. P2P scanner callback tests run on API 29 and 35. Core tests cover baseline maxima, missing versus empty coverage, stale results, and sequential scheduling. Permission tests exercise API 29, 31, 33, and 35.

Physical checks must separately record the Android model/version, app build, known peer state, discovery outcome, and lifecycle behavior. Automated or simulated tests do not establish RF reception rates or classroom accuracy. Do not commit real device names, addresses, or logs.
