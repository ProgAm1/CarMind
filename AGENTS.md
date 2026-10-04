# CarMind — Codex instructions

Read [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) before planning or changing project behavior. It records the agreed baseline; inspect actual code, tests, schema, and local instructions before implementation. Distinguish implemented facts from proposals and unknowns.

- CarMind is a Connected Car / IoT / Automotive Software graduation project. Core scope: **Monitoring + Diagnostics + History**.
- Target flow: Vehicle → OBD-II/CAN → Custom IoT Device → 4G → Backend → Database → Flutter App.
- Vehicle access is **read-only**: standard OBD-II diagnostic requests and responses are allowed for reading supported data. Never add vehicle control, ECU programming, remote start, door/window actuation, or fault-code clearing.
- Prototype baseline: ESP32-class MCU, LTE/4G with a physical Nano-SIM, local Bluetooth, and local buffering during network outages. Existing Bluetooth OBD hardware may support early tests.
- Proposed software: NestJS backend, PostgreSQL database, Flutter for iOS/Android. These are planned choices, not evidence of existing implementation.
- OEM-specific vehicle profiles are future work. Do not assume every vehicle supports every reading, or expand scope without an explicit user request.
- Prototype hardware budget target is approximately **350 SAR**, subject to verified component compatibility and pricing.
- Prefer the smallest correct change, preserve unrelated work, run proportionate verification, and report only checks actually run. Keep this file concise and maintain detailed context in the linked document.
- Do not commit, push, merge, or mutate external systems unless the user explicitly authorizes it. Never store secrets or add AI attribution.
