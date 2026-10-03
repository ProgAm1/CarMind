"""Exercise the installed emulator-mode APK against the local Firebase Auth emulator.

Uses only Python's standard library and adb. Never run against a physical device.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--device", default="emulator-5554")
parser.add_argument("--screenshots", type=Path, default=Path("docs/screenshots"))
args = parser.parse_args()
package = "com.carmind.app"
results = []


def adb(*command, binary=False):
    value = subprocess.run([args.adb, "-s", args.device, *command],
                           check=True, capture_output=True, timeout=30).stdout
    return value if binary else value.decode("utf-8", errors="replace").strip()


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/carmind-ui.xml")
    root = ET.fromstring(adb("exec-out", "cat", "/sdcard/carmind-ui.xml"))
    if any("Android Keyboard" in item.get("text", "") for item in root.iter("node")):
        deny = next((item for item in root.iter("node")
                     if item.get("resource-id", "").endswith(":id/permission_deny_button")), None)
        if deny is not None:
            bounds = [int(value) for value in re.findall(r"\d+", deny.get("bounds"))]
            adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
            time.sleep(0.5)
            return tree()
    return root


def node(resource):
    expected = f"{package}:id/{resource}"
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        found = next((item for item in tree().iter("node") if item.get("resource-id") == expected), None)
        if found is not None:
            return found
        time.sleep(0.3)
    raise AssertionError(f"Missing {resource} on screen")


def tap(resource):
    bounds = [int(value) for value in re.findall(r"\d+", node(resource).get("bounds"))]
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))


def type_into(resource, value):
    tap(resource)
    tree()  # Dismiss the fresh AOSP keyboard's optional contacts prompt first.
    adb("shell", "input", "text", value.replace(" ", "%s"))


def hide_keyboard():
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.5)


def wait_for(text, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if any(text in item.get("text", "") for item in tree().iter("node")):
            return
        time.sleep(0.3)
    raise AssertionError(f"Expected screen text: {text}")


def launch():
    adb("shell", "am", "force-stop", package)
    adb("shell", "am", "start", "-n", f"{package}/.LoginActivity")
    time.sleep(0.5)


def screenshot(name):
    hide_keyboard()
    args.screenshots.mkdir(parents=True, exist_ok=True)
    (args.screenshots / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))


def passed(case):
    results.append(case)
    print("PASS", case, flush=True)


def seed(email, password):
    body = json.dumps({"email": email, "password": password, "returnSecureToken": True}).encode()
    request = Request("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-api-key",
                      data=body, headers={"Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=10) as response:
            assert response.status == 200
    except HTTPError as failure:
        payload = json.loads(failure.read())
        if payload.get("error", {}).get("message") != "EMAIL_EXISTS":
            raise


assert args.device.startswith("emulator-"), "Use an isolated Android emulator"
assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Physical devices are unsupported"
seed("driver@example.com", "DemoPass123")
seed("spaces@example.com", "  DemoPass123  ")
adb("shell", "pm", "clear", package)
launch()
wait_for("Welcome back")
screenshot("login")

tap("sign_in")
wait_for("Please enter your email address.")
passed("LOGIN-01 blank form")

launch()
type_into("email", "driver")
hide_keyboard()
tap("sign_in")
wait_for("Please enter a valid email address.")
passed("LOGIN-02 malformed email")

launch()
type_into("email", "driver@example.com")
hide_keyboard()
tap("sign_in")
wait_for("Please enter your password.")
passed("LOGIN-03 empty password")

launch()
type_into("email", "driver@example.com")
type_into("password", "WrongPass123")
assert node("password").get("password") == "true", "Password should start masked"
passed("LOGIN-04 masked password")
hide_keyboard()
tap("sign_in")
wait_for("Email or password is incorrect.")
screenshot("invalid-credentials")
passed("LOGIN-05 wrong password")

launch()
type_into("email", "driver@example.com")
type_into("password", "DemoPass123")
hide_keyboard()
tap("sign_in")
wait_for("Welcome to CarMind")
wait_for("driver@example.com")
screenshot("home")
passed("LOGIN-06 valid local credentials")

launch()
wait_for("driver@example.com")
passed("LOGIN-07 restored session after app restart")

tap("sign_out")
wait_for("Welcome back")
adb("shell", "input", "keyevent", "4")
assert "Welcome to CarMind" not in ET.tostring(tree(), encoding="unicode")
launch()
wait_for("Welcome back")
passed("LOGIN-08 sign-out and navigation guard")

type_into("email", "spaces@example.com")
type_into("password", "  DemoPass123  ")
hide_keyboard()
tap("sign_in")
wait_for("spaces@example.com")
passed("LOGIN-09 exact password with surrounding spaces")
tap("sign_out")
wait_for("Welcome back")

report = args.screenshots.parent / "device-check-results.json"
report.write_text(json.dumps({"device": args.device,
                              "api_level": adb("shell", "getprop", "ro.build.version.sdk"),
                              "backend": "local Firebase Auth emulator, demo-carmind",
                              "passed": results}, indent=2) + "\n", encoding="utf-8")
print(f"{len(results)} device checks passed. Evidence: {report}", flush=True)
