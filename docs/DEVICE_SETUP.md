# Device Setup And Connectivity

This document covers phone setup for `WhisperClient`, including no-USB workflows.
For a full Mac+phone wireless pairing playbook, see [WIRELESS_DEBUGGING_RUNBOOK.md](./WIRELESS_DEBUGGING_RUNBOOK.md).

## Current Constraint (No USB + No Same Wi-Fi)

If your Mac and phone are not on the same local network, wireless `adb` is usually blocked.
Being on the same VPN may still fail because many VPN profiles isolate client-to-client traffic.

Use Mac-only loop for now:

```bash
cd ~/Code/WhisperClient
./scripts/dev-mac-loop.sh
```

Then use this guide when you can do local pairing.

## Option A: USB First (Most Reliable)

1. On phone, enable Developer Options (tap Build Number 7 times).
2. Enable `USB debugging`.
3. Connect phone by USB and accept the trust prompt.
4. Verify:

```bash
cd ~/Code/WhisperClient
source ./scripts/env-android.sh
adb devices
```

## Option B: Wireless Debugging Without USB

Prerequisites:
- Phone and Mac must be IP-reachable to each other.
- On most setups, this means same local Wi-Fi LAN.

Steps:

1. On phone, enable `Developer options` and `Wireless debugging`.
2. Tap `Pair device with pairing code`.
3. Note the shown values:
- `IP:PAIRING_PORT`
- `PAIRING_CODE`
- `IP:ADB_PORT` (separate from pairing port)
4. On Mac:

```bash
cd ~/Code/WhisperClient
source ./scripts/env-android.sh
adb pair <IP:PAIRING_PORT>
# enter PAIRING_CODE from phone
adb connect <IP:ADB_PORT>
adb devices
```

You should see your device state as `device`.

## Install, Launch, And Select IME

Once connected (USB or wireless):

```bash
cd ~/Code/WhisperClient
./scripts/dev-doctor.sh
./scripts/dev-install.sh
./scripts/dev-launch-app.sh
./scripts/dev-set-ime.sh
./scripts/dev-logcat.sh
```

## Troubleshooting

- If `adb devices` shows `unauthorized`: unlock phone and accept USB/Wireless debug prompt.
- If `adb connect` times out: network path is blocked (VPN/client isolation/firewall).
- If pairing succeeds but reconnect fails later: repeat `adb connect <IP:ADB_PORT>` from current phone network details.
- If `adb pair` returns `connection refused`: pairing screen likely expired or port rotated; generate a fresh pairing code and port.
- If `./gradlew installDebug` says "Unable to locate a Java Runtime": export `JAVA_HOME` to OpenJDK 17 in the shell.
