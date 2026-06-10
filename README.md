# ADB Bridge

An Android foreground service that executes shell commands as root through the device's local adbd, letting other apps trigger privileged actions without holding privileges themselves. It starts automatically on boot and is driven over a broadcast interface. Targets Android 11 (API 30).

## Waking the service from another app

Any app — regardless of signing key or granted permissions — can **wake** (start) the service. Waking is intentionally unguarded: it only brings the foreground service up and runs no command. Command execution is a separate signature-protected path.

Send an **explicit** broadcast to the START action:

```kotlin
val intent = Intent("com.example.adbbridge.action.START").apply {
    setPackage("com.example.adbbridge")             // implicit broadcasts can't reach a manifest receiver on Android 8+
    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)  // deliver even when ADB Bridge is in the stopped state
}
context.sendBroadcast(intent)
```

Both lines matter on the sender side:

- **Explicit target** — set `setPackage(...)` (or an explicit `ComponentName`). Since Android 8 an action-only implicit broadcast is not delivered to a manifest-declared receiver.
- **`FLAG_INCLUDE_STOPPED_PACKAGES`** — a freshly installed or force-stopped app receives no broadcasts; this flag wakes it anyway and needs no special permission. The service also auto-starts on boot, so this mainly covers the killed or stopped fallback.

On Android 11 a background receiver may start a foreground service freely. On Android 12+ that path is restricted and would need a separate exemption.

### Wake vs. run

The open wake path and the privileged command path are deliberately separate:

- **Wake** (`…action.START`) — exported, no permission, never carries a command.
- **Run** (`…action.RUN`) — exported, signature-level permission, carries the command.

The wake path must never accept or forward a command. Otherwise any app could bypass the signature gate and run root commands.

## Running a command (signed apps only)

Running a command is gated by a **signature-level permission**, so only an app signed with the same key as ADB Bridge can drive it. The bridge declares:

```xml
<permission
    android:name="com.example.adbbridge.permission.RUN_COMMAND"
    android:protectionLevel="signature" />
```

A caller declares the matching `<uses-permission>` and sends an **explicit** broadcast to the RUN action with the command in the `cmd` extra:

```kotlin
val intent = Intent("com.example.adbbridge.action.RUN").apply {
    setPackage("com.example.adbbridge")
    putExtra("cmd", "input keyevent 26")  // e.g. toggle the screen
}
context.sendBroadcast(intent)
```

The service runs the command as root through the local adbd and writes the output to logcat. A returned result channel is not implemented yet.

> **Local testing caveat.** `adb shell am broadcast …action.RUN --es cmd "…"` runs as `uid 0`, which is granted every permission — so it exercises the receiver wiring and command execution but does **not** prove the signature gate. Confirming that a differently-signed app is rejected requires a second app signed with another key.
