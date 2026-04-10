# Wireless Debugging Runbook (Mac + Android)

This is the exact, repeatable process for pairing and connecting an Android phone to this Mac over wireless `adb`.

## One-Time Prereqs

1. Phone:
- Enable `Developer options`.
- Enable `Wireless debugging`.
2. Mac:
- `adb` installed and in PATH (or use `scripts/env-android.sh` which prefers SDK `adb`).

## Quick Success Path

1. On phone, open `Developer options` -> `Wireless debugging` -> `Pair device with pairing code`.
2. Keep that pairing screen open.
3. Copy from phone:
- `IP:PAIRING_PORT`
- `6-digit pairing code`
4. On Mac:

```bash
cd ~/Code/WhisperClient
source ./scripts/env-android.sh
adb pair <IP:PAIRING_PORT> <PAIRING_CODE>
```

Example:

```bash
adb pair 100.110.240.42:41899 086658
```

5. On phone, from the same Wireless Debugging page, copy the connect endpoint:
- `IP:ADB_PORT` (this is usually different from pairing port)
6. On Mac:

```bash
adb connect <IP:ADB_PORT>
adb devices
```

You are connected when `adb devices` shows:

```text
<ip>:<port>    device
```

## Deploy/Launch After Connect

```bash
cd ~/Code/WhisperClient
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew installDebug
adb shell am start -n com.wispr.client.debug/com.wispr.client.MainActivity
```

Note: debug builds use package `com.wispr.client.debug`, not `com.wispr.client`.

## What We Learned (Failure Modes + Fixes)

1. Pairing port can rotate quickly:
- If `adb pair` fails, reopen `Pair device with pairing code` and use fresh port+code.

2. Pairing and connect endpoints are different:
- `adb pair` uses `IP:PAIRING_PORT`.
- `adb connect` uses `IP:ADB_PORT`.

3. Reachability matters more than network type:
- VPN IP can work if Mac can route to phone and port is open.
- Verify before retrying:

```bash
ping -c 2 <phone-ip>
nc -vz <phone-ip> <port>
```

4. `Connection refused` on pairing port:
- Usually expired pairing screen or wrong/old pairing port.
- Reopen pairing screen and retry immediately.

5. `adb` appears to hang in some shells:
- Restart server and retry:

```bash
adb kill-server
adb start-server
adb devices
```

6. Gradle install fails with "Unable to locate a Java Runtime":
- Set `JAVA_HOME` explicitly to Homebrew OpenJDK 17 for the current shell:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

7. App launch fails with `Error type 3`:
- Wrong app id/activity, especially if using a debug suffix.
- Use:

```bash
adb shell am start -n com.wispr.client.debug/com.wispr.client.MainActivity
```

## Reconnect Later (Without Re-pairing)

If already paired and phone changed networks/ports:

1. Open phone `Wireless debugging`.
2. Read current `IP:ADB_PORT`.
3. Run:

```bash
adb connect <IP:ADB_PORT>
adb devices
```

If that fails repeatedly, re-run full pairing flow.

## Cleanup (Avoid Duplicate Devices / Stale Installs)

If `adb devices` shows both an `ip:port` entry and an `adb-..._adb-tls-connect._tcp` entry for the same phone, disconnect the mDNS alias:

```bash
adb disconnect <adb-mdns-entry>
adb devices -l
```

Example:

```bash
adb disconnect adb-R5CW41XCM9W-WtbXau._adb-tls-connect._tcp
```

Only one device entry should remain for stable test runs.

To uninstall older app variants from the phone:

```bash
adb shell pm list packages | rg com.wispr.client
adb uninstall com.wispr.client.debug
adb uninstall com.wispr.client
```

Reinstall latest debug build:

```bash
./gradlew installDebug
```
