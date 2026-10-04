# CarMind — Project Context

Baseline date: 2026-10-04. This document preserves the user-provided project baseline for future Codex work. It describes intended scope and proposed architecture, not completed functionality.

## Current repository state

At baseline intake, the repository contained only a root `README.md` naming CarMind. No application, firmware, backend, database schema, tests, or build configuration existed. Reinspect the repository as implementation progresses; actual code, tests, and schema are authoritative for implemented behavior.

## Purpose and problem

CarMind is a graduation project in **Connected Car / IoT / Automotive Software**. It aims to make supported vehicle telemetry, diagnostic information, and historical records available through a mobile app.

A basic Bluetooth OBD adapter relies on a nearby connected phone to relay data. The target custom device has its own cellular connection, allowing it to upload available readings when powered and connected even if the phone is away or the app is closed. This does not imply continuous readings while the vehicle is off; vehicle availability and device power behavior still need validation.

## Target system flow

**Vehicle → OBD-II/CAN → Custom IoT Device → 4G → Backend → Database → Flutter App**

The device reads supported vehicle data, buffers it locally when the network is unavailable, and uploads it to the backend when connectivity returns. The backend stores data in the database and supplies it to the mobile app. Bluetooth provides a local connection path; its exact role and protocol remain to be designed.

## Core scope

| Area | Intended behavior |
| --- | --- |
| Monitoring | Display supported readings such as engine RPM, vehicle speed, and coolant temperature. Other readings, including voltage, require confirmation for the selected vehicle and hardware. |
| Diagnostics | Read available standard diagnostic trouble codes (DTCs) and display diagnostic information. Do not clear codes or alter vehicle state. |
| History | Store and retrieve telemetry and diagnostic history. Trip summaries may be defined within this scope; their detection rules and fields are not yet specified. |

Remote monitoring depends on available vehicle data, device power, and network connectivity. Buffered or historical readings must not be presented as live data. Future implementation should preserve acquisition timestamps and make freshness understandable to the user.

## Prototype hardware baseline

- Custom device connected through the vehicle's OBD-II port, reading standard OBD-II data over CAN where supported by the selected vehicle.
- **ESP32-class microcontroller** for device logic.
- Compatible CAN interface/transceiver and OBD-II connector/cable; exact parts are undecided.
- **LTE/4G modem with a physical Nano-SIM** for the prototype. A SIM alone does not provide connectivity; a compatible modem and service are required. eSIM is not a prototype requirement.
- **Bluetooth** for local communication.
- **Local buffering** during network outages, followed by upload after connectivity returns. Storage medium, capacity, retry rules, ordering, and duplicate handling remain open design decisions.
- Suitable vehicle power conversion and protection. Power draw, sleep behavior, battery impact, and hardware compatibility must be validated before deployment in a vehicle.

The **prototype hardware budget target is approximately 350 SAR**. This is a planning target, not a confirmed bill of materials or price quotation. Exact components, availability, taxes, shipping, and recurring cellular/backend costs have not been established.

The existing **Bluetooth OBD adapter** may be used for initial vehicle compatibility checks, reading experiments, and early app/backend tests. It does not by itself demonstrate the final device's independent cellular upload capability.

## Proposed software stack

| Layer | Baseline proposal | Status |
| --- | --- | --- |
| Device firmware | ESP32-class MCU firmware; framework and language to be selected | Planned |
| Backend | NestJS | Proposed |
| Database | PostgreSQL | Proposed |
| Mobile app | Flutter for iOS and Android | Proposed |

Device-to-backend transport, authentication, API contracts, telemetry schema, hosting, retention, and Bluetooth integration are not yet selected. Do not treat these as settled requirements or invent an existing implementation.

## Vehicle compatibility and read-only boundary

Start with **standard OBD-II diagnostics over CAN** on a verified compatible test vehicle. Availability depends on the vehicle, model year, ECUs, and supported PIDs/services. The adapter alone does not determine which readings exist.

Read-only means requests may be transmitted to obtain supported readings and diagnostic responses. It does not require passive CAN listening only. It excludes commands that modify vehicle state, clear diagnostic codes, reprogram modules, or actuate components.

Do not promise access to odometer, TPMS, ABS, transmission, body modules, hybrid systems, or other manufacturer-specific data without vehicle-specific evidence. Generic support for all vehicles and all OBD protocols is not established.

## Explicitly excluded scope

- Vehicle control or actuation.
- ECU programming, flashing, or coding.
- Remote engine start.
- Door lock/unlock or window control.
- Diagnostic trouble-code clearing or other state-changing diagnostic operations.

## Future work

**OEM-specific vehicle profiles** may later map supported readings and protocols for particular manufacturers, models, and years. They are outside the current core scope and should be introduced only after explicit scope approval and compatibility validation. Future profiles must preserve the read-only boundary.

## Decisions still needed

- Test vehicle and verified standard readings/DTC support.
- Compatible device components and a priced bill of materials against the budget target.
- Firmware framework, power behavior, and buffering strategy.
- Cellular service and backend communication protocol.
- Bluetooth responsibilities and mobile connection workflow.
- Backend/API design, device identity, user access, database schema, and retention.
- Mobile screens, history/trip requirements, and prototype acceptance criteria.

Resolve these during the relevant implementation task. Do not expand the baseline merely to fill an undecided detail.

## Context provenance and maintenance

This baseline was supplied by the user while continuing the ChatGPT conversation **“قراءة شات تحويل السيارات”** (conversation ID `6ac1f7cf-6148-83eb-b380-766f7478945b`) into the Codex project **CarMind**. Earlier references to a tentative name or specific component candidates do not override this baseline.

Keep operational Codex instructions in root `AGENTS.md` and detailed agreed context here. Update this document when decisions are actually made, identifying proposals versus verified implementation. Never add credentials, SIM secrets, or private vehicle/user data to these files.
