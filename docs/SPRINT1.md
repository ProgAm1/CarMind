# CarMind Sprint 1 login handoff

Abdullah Misar owns the Login and Authentication story. The course's Sprint 1 increment must also include Registration, which the teammate supplies. This branch provides the login implementation and its integration contract. It does not claim registration is complete.

## Story 002

**Component name:** Login and Authentication

**Story name:** Login and Authentication

**Story sequence number:** 002

**Short description:** A registered CarMind user signs in with an email address and password to access the authenticated home screen.

**Long description:** The app validates required fields and the email format, sends credentials to Firebase Authentication, and opens Home only when authentication succeeds. It masks the password, prevents repeated submissions while a request runs, displays understandable failures, and supports session restoration and sign-out. Firebase manages the account and session. The app does not persist the password.

## Acceptance criteria

- A blank or malformed email displays a field error without sending a login request.
- An empty password displays a field error. Login does not impose a new registration password policy.
- Password input starts masked and preserves all characters, including surrounding spaces.
- A submitted request disables the form and shows a loading state.
- Incorrect credentials leave the user on Login with a generic email/password error.
- Valid credentials open Home. Back does not reopen the completed login flow.
- An existing Firebase session opens Home when the app starts again.
- Sign-out clears the session and navigation history. A signed-out user cannot reach Home through Back.
- Network, disabled-account, throttling and other failures have readable messages without raw exception details.
- Registration uses the same Firebase environment and shared auth instance as login.

## Test cases

Run the app against the team's configured Firebase project using authorized accounts. The previous local Auth emulator mode and its account-seeding script have been removed. Automated unit checks validate inputs only. Device checks verify the installed APK and SDK integration.

| ID | Test | Expected result | Evidence |
| --- | --- | --- | --- |
| LOGIN-01 | Submit blank email and password | Email field requests an email | Device check and unit check |
| LOGIN-02 | Submit an invalid email | Email field requests a valid email | Device check and unit check |
| LOGIN-03 | Submit a valid email and blank password | Password field requests a password | Device check and unit check |
| LOGIN-04 | Type a password with Show password off | Characters remain masked | Device check |
| LOGIN-05 | Submit the wrong password | Login stays open and shows a generic credential error | Device check |
| LOGIN-06 | Submit valid team Firebase credentials | Home opens and shows the account email | Device check |
| LOGIN-07 | Stop and restart the app after successful login | Firebase restores the session and Home opens | Device check |
| LOGIN-08 | Sign out, then press Back and restart | Login opens and Home stays inaccessible | Device check |
| LOGIN-09 | Login with a password containing leading/trailing spaces | Exact password succeeds without trimming | Device check |
| LOGIN-10 | Submit repeatedly while signing in | One request runs and the form stays disabled | Manual check |
| LOGIN-11 | Disconnect the network during a cloud login | Connection error appears and the form re-enables | Manual check on cloud project |
| LOGIN-12 | Sign in with a disabled account | Disabled-account message appears | Manual check on cloud project |
| LOGIN-13 | Trigger provider throttling | Retry-later message appears | Manual check with controlled provider setup |
| LOGIN-14 | Rotate or background the app during a request | Pending request completes without leaking a password or leaving the form stuck | Manual lifecycle check |
| LOGIN-15 | Register through the teammate's completed screen, then log in | Shared Firebase account opens Home | Pending teammate integration |

Record actual outcomes in `docs/TEST_RESULTS.txt`. Do not replace a pending outcome with Pass before running its case. Local emulator evidence is not cloud deployment evidence.

## Class demo sequence

1. Introduce Story 002 and explain that a registered user needs an authenticated entry point.
2. Submit the blank form, then a malformed email.
3. Show masked password input and a wrong-password failure.
4. Submit a valid team Firebase account and show Home.
5. Restart the app to demonstrate session restoration.
6. Sign out, press Back, and show that Home is inaccessible.
7. Let the teammate present Registration and the shared-account integration test when ready.

Historical local Auth emulator results describe earlier testing, not current cloud verification. Do not present mock vehicle measurements or old senior-project screenshots as proof of this sprint's working features.

## Meeting records

The course appendix requires at least two stand-up meetings per sprint. Fill the following with actual meetings. No meetings have been invented in this handoff.

### Meeting 1

Date and time: [record actual date and time]

Sprint duration: [record the team's two-week sprint dates]

Scrum master: [team decision]

Client: [team decision]

Pair programmers: [actual participants]

Stories: 001 Registration, 002 Login and Authentication

- Completed since last meeting: [actual work]
- Work planned next: [actual plan]
- Issues or impediments: [actual issues]
- Scrum master's comments: [actual comments]

### Meeting 2

Date and time: [record actual date and time]

Sprint duration: [same sprint dates]

Scrum master: [team decision]

Client: [team decision]

Pair programmers: [actual participants]

Stories: 001 Registration, 002 Login and Authentication

- Completed since last meeting: [actual work]
- Work planned next: [actual plan]
- Issues or impediments: [actual issues]
- Scrum master's comments: [actual comments]

## Blackboard submission checklist

The project brief requires a progress document with app screenshots, the stand-up meeting cheat sheet, test cases for each story, and a ZIP of the code after each milestone. Add the teammate's registration evidence and both real meeting records before calling the full Sprint 1 submission complete.

Source: CCSW 431 Final Project, PDF pages 3-4, 7 and 10-11, available from the [course site](https://ccsw431.malahmadi.sa/). The story and criteria above adapt those requirements to CarMind.
