# CarMind classroom demonstration

The classroom walkthrough runs **after real Firebase email/password sign-in** in the same Android app. It does not replace authentication, create a guest session, or connect to Bluetooth. Only the vehicle, connection steps, and readings are simulated. The persistent banner identifies simulated data.

## Present in class

1. Sign in to your existing CarMind Firebase account. Internet is required for a new cloud login.
2. Tap **Classroom demo · simulated vehicle** on the vehicle dashboard.
3. Tap **Simulate plugging in the reader**. Explain that the vehicle supplies power through the OBD-II port; Bluetooth does not power the adapter.
4. Choose the **Classroom OBD** simulated reader. The walkthrough shows Bluetooth pairing, then requesting supported readings, then the connected state.
5. Watch speed, engine RPM and coolant temperature update once per second. Scroll to choose **Idle**, **City** or **Highway**. This is a teaching model, not a validated vehicle physics model.
6. Use **Pause readings** while explaining. **Simulate connection loss** freezes the values and their timestamp; reconnect explicitly to resume.
7. Show **Demo reading history**. A simulated snapshot is saved every five seconds while running, or manually. The latest 20 are stored on this phone, separately for each Firebase UID and separately from real vehicle history.
8. **Restart the connection walkthrough** returns to step 1 without deleting saved history. **Back to real vehicle dashboard** restores the ordinary paired-reader flow. **Sign out** ends the real Firebase session.

Leaving the screen or rotating it pauses connected readings. Tap Resume after returning. Incomplete connections are cancelled and can be started again. No background telemetry collection takes place.

## كلام مختصر للشرح

«ندخل بحساب Firebase الحقيقي. عشان السيارة ما تدخل القاعة، هذا وضع محاكاة واضح في التطبيق. في السيارة الحقيقية نركب القطعة في منفذ OBD؛ تأخذ الكهرباء من السيارة. الجوال يتصل بالقطعة بالبلوتوث، وتطبيقنا يطلب القراءات التي تدعمها السيارة. القطعة تنقل الطلب والرد، والتطبيق يعرض الأرقام ويحفظ وقت القراءة. هنا نولّد البيانات لنشرح الفكرة، وما نرسل أوامر لسيارة فعلية.»

«السرعة وRPM يتغيران مع سيناريو القيادة، والحرارة ترتفع تدريجيًا. إذا انقطع الاتصال تتوقف الأرقام ويبقى وقت آخر قراءة ظاهرًا. السجل التجريبي منفصل عن بيانات السيارة الحقيقية.»

## Account-specific numbered trips

Each Firebase UID has separate local demo history and its own paused trip. The first visit adds four generated past journeys. Those examples are saved once; they vary by UID. Trip 1, Trip 2, Trip 3 and subsequent numbers remain stable. Locations, neighborhood selection and destinations have been removed. Older demo records keep their dates and metrics and receive saved numbers on upgrade.

After connecting, tap **Start simulated trip**. The teaching clock advances 30 simulated seconds per real second. The screen shows start date/time, current simulated time, duration, distance and average/max speed. All trip timestamps use **Asia/Riyadh (UTC+3)**. Tap **End trip and save** to finish. There is no destination or automatic arrival. The latest 20 demo trips are retained; numbering continues beyond retained history. Pause, connection loss and backgrounding freeze the teaching clock. An in-progress draft can be resumed after returning/reconnecting.

للعرض: «كل حساب له رحلاته الخاصة: رحلة ١، رحلة ٢ وهكذا. نشوف التاريخ ووقت البداية والنهاية والمدة والسرعات. هنا في القاعة القراءات محاكاة والزمن مسرّع. لما نجرّبه بالسيارة نستخدم زر بدء رحلة في الشاشة الرئيسية، وتجي البيانات من القطعة فعلًا، والوقت هناك طبيعي.»

## Real vehicle recording

The real dashboard has a separate **Start trip** flow. It continuously polls supported speed/RPM/coolant on the selected Bluetooth Classic SPP ELM-compatible adapter, with a foreground service for background/screen-lock recording. This does not establish physical compatibility with the photographed reader. See [installation and vehicle steps](REAL_TRIPS_AR.md). Real trips have no generated examples and are stored separately from demo history. Neither flow collects location or sends vehicle-control commands.

## Firebase and credentials

Restore the team's `app/google-services.json` for `carmind-senior` / `com.carmind.app` and enable Email/Password in Firebase Authentication. This local configuration stays ignored by Git. No account password is stored in source or documentation. Existing Firebase initialization, login, validation, session handling and sign-out remain in place.

The checkout still does not contain the teammate's RegistrationActivity. This change preserves the existing optional registration integration; it does not introduce a local registration substitute.

Executed results: [CLASSROOM_TEST_RESULTS.txt](CLASSROOM_TEST_RESULTS.txt).

## Verification

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug lintRelease
./gradlew connectedDebugAndroidTest
```

Use an isolated signed-out Android emulator. `CloudLoginDeviceTest` is skipped by default. To explicitly exercise the configured real Firebase provider:

```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.cloudAuthTest=true
```

The cloud test creates one uniquely named disposable `@example.com` identity, exercises the actual login screen, checks the authenticated simulation entry and sign-out, and removes only its own identity in cleanup. It refuses to replace an existing signed-in session. Do not run against an unattended personal-device session.

`SimulationPreviewActivity` exists only in debug builds as a non-exported instrumentation test host. It has no launcher entry and is absent from the release APK. The ordinary SimulationActivity checks Firebase on entry and when authentication changes.
