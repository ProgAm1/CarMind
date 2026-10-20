# Sprint 2 — Bluetooth readings and local history

Version 0.2 release candidate, 20 October 2026. Based on repository commit `83b5954`, retaining native Android Java/XML and Firebase login. The course brief requires at least two Sprint 2 stories, an improved main screen, develop/test/release evidence, screenshots, test cases, and actual sprint meetings. The two-week sprint dates and at least two meeting records must be completed by the team using real events.

## Develop

| Story | User outcome | Acceptance criteria |
| --- | --- | --- |
| 003 — Bluetooth OBD reader connection | A signed-in user chooses a reader already paired with their Android phone. | Handle missing Bluetooth, Bluetooth off, permission denied and no paired devices; display the chosen reader; allow one request at a time; permit cancellation; close the socket on completion or leaving the screen; bound the request to 40 seconds. |
| 004 — Vehicle readings and local history | A signed-in user reviews supported vehicle values and previous snapshots. | Request supported RPM, speed and coolant temperature; display units, acquisition time and reader name; retain valid zero values; show missing values as unavailable; save the latest 20 snapshots per Firebase UID on the phone. |

The main screen provides reader selection, Bluetooth settings, a reading action, progress/cancel controls, three value cards and history. Native vertical scrolling accommodates small screens and large text.

### Implementation map

| File | Responsibility |
| --- | --- |
| `HomeActivity.java` | Existing authentication guard, account ownership, permission flow, paired-device picker and request lifecycle. |
| `BluetoothObdClient.java` | Background Bluetooth Classic RFCOMM SPP socket with a 40-second deadline and cleanup. |
| `ObdProtocol.java` | Reader setup, supported-PID query and strict response parsing. |
| `VehicleReading.java` | Validated timestamped snapshot with nullable values. |
| `ReadingHistory.java` | Bounded local JSON history, separated by Firebase UID. |
| `DashboardRenderer.java`, `activity_home.xml`, styles/colors/strings | Dashboard and history presentation. |
| `src/debug/.../DashboardPreviewActivity.java` | Non-exported, debug-only component test host with a fixed simulated-data banner; no release activity or production auth bypass. |
| `ObdProtocolTest`, `VehicleReadingTest`, `Sprint2DeviceTest` | Protocol, model, persistence, dashboard and auth-guard verification. |

Reader setup sends `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`, then `0100` to ask what is supported. Only supported `010C` (RPM), `010D` (speed) and `0105` (coolant) are requested. The parser accepts the first valid headerless response for each request; it does not combine multiple ECUs. These are reading requests, not commands to clear faults or control the vehicle.

### Hardware explanation

The team uses a ready-made OBD reader connected to the vehicle's diagnostic port. Bluetooth carries requests and responses between the reader and the phone. The app requests supported values, converts responses into numbers with units, and keeps a dated local history. Circuit design and firmware development are not part of this course increment.

The user confirmed Bluetooth and supplied the three hardware photos. The photos do not establish the model or whether the device supports Classic SPP versus BLE. This implementation targets Classic SPP readers accepting ELM-compatible serial commands; BLE and Wi-Fi are not implemented. Confirm the actual reader and vehicle before claiming a working live connection.

## Test

See [SPRINT2_TEST_RESULTS.txt](SPRINT2_TEST_RESULTS.txt). The delivered suite has 28 unit tests (including six existing login-validation cases) and six Android instrumentation tests. All passed. UI rendering was also checked at 320 dp width with font scale 2.0 and in landscape.

The screenshots use a debug-only component host with explicit sample readings. They exercise the same dashboard layout and renderer used by authenticated Home. They are evidence of implemented UI rendering, not evidence of physical Bluetooth communication or successful cloud authentication.

### Field acceptance — not executed here

Use a physical Android phone, the actual reader and a compatible stationary vehicle.

| ID | Action | Expected |
| --- | --- | --- |
| P01 | Deny Nearby devices permission, then grant it. | Clear permission message; allow selection after grant. |
| P02 | Disable Bluetooth or leave the reader unpaired. | Explain the required setup without starting a read. |
| P03 | Pair the reader in Android settings, then select it in CarMind. | Reader name shown; reading action enabled. |
| P04 | Request a snapshot from the test vehicle. | Supported values and timestamp shown; one record saved. |
| P05 | Test a vehicle that lacks one of the target readings. | That value remains unavailable; valid values remain visible. |
| P06 | Disconnect the reader during a request. | Error within the deadline, controls recover, retry allowed. |
| P07 | Cancel, background or rotate during the request. | Socket closes; stale UI callbacks are ignored; no stuck form. |
| P08 | Sign out, sign in with another account, then return. | Histories remain account-scoped; signed-out Home redirects. |

Record phone/Android version, reader model and Bluetooth name, vehicle make/model/year, supported values, test date and tester. Mark each case only after executing it.

## Release and demo

1. Add the team's `app/google-services.json` for `com.carmind.app`, enable Firebase Email/Password sign-in, and use an authorized existing account.
2. Rebuild with `./gradlew assembleDebug`. The supplied APK was built without the team's Firebase configuration: login is unavailable until configuration and rebuild.
3. Pair the reader in Android Bluetooth settings. Sign in, choose the paired reader and grant Nearby devices permission on Android 12+.
4. With the vehicle stationary, request a reading, explain the units and timestamp, then show history and sign out.
5. Complete the physical acceptance cases, team names, actual sprint dates and two meeting records in the Word report.
6. Integrate the teammate's registration implementation before presenting the combined Sprint 1 + 2 increment as complete. Registration is absent from this checkout; the existing integration hook in the login screen is preserved.

`assembleRelease` succeeds and produces an **unsigned** release APK. It needs the team's signing configuration for distribution. The debug APK is a development build. No application release has been published.

The repository includes the [revised 14-slide presentation](CarMind_Sprint2_Deck.pptx), [Word report](CarMind_Sprint2_Report.docx) and [Arabic speaker notes](SPRINT2_PRESENTER_NOTES_AR.txt). The presentation uses isolated hardware images and editable generic power/data diagrams; it does not identify the exact internal components of the photographed reader.

The local submission folder `output/sprint2/` contains the PowerPoint, Word progress/test report, images ZIP, source ZIP, APKs and an Arabic start guide. Build products and submission bundles are ignored by Git. Open the source ZIP in Android Studio; install the required SDK from README and add local Firebase configuration.

The course meeting forms are intentionally unfilled. The team must provide factual attendance, dates, roles, completed work, next work, blockers and findings. No meeting or physical test is claimed to have occurred.

## References

- User-supplied `Final_Project_CCSW431 2.pdf`, Sprint 2 instructions and stand-up/test templates.
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android Bluetooth connections](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)
- [ELM Electronics serial command reference](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf) — implementation reference, not identification of the photographed hardware.
- [Image provenance](sprint2-assets/README.txt).
