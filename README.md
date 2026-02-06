# file_x

[![Pub Version](https://img.shields.io/pub/v/file_x.svg)](https://pub.dev/packages/file_x)
[![License](https://img.shields.io/github/license/sumitsharansatsangi/file_x.svg)](LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/sumitsharansatsangi/phone_parser.svg?style=social)](https://github.com/sumitsharansatsangi/file_x)

`file_x` is an **Android-focused Flutter plugin** that provides a **correct, OEM-aware, SAF-compliant file access layer** for real-world apps.

It is designed for developers who need **truthful filesystem access** instead of fragile shortcuts that break across devices.

> This is **not** a file picker wrapper.  
> It is a storage abstraction that understands Android’s actual security model.

---

## Why file_x exists

On modern Android:

- Paths may exist but be unreadable
- File explorers can see files your app cannot
- USB behaves differently across OEMs
- “All Files Access” does not mean *all files*
- SAF and native paths must coexist

Most plugins **hide these realities**.  
`file_x` embraces them and exposes a clean, predictable API.

---

## Core Capabilities

### 🔹 Unified Storage Roots

Retrieve a merged view of:

- Internal storage
- External SD card (where allowed)
- USB / OTG devices
- Adopted storage
- User-selected SAF folders

```dart
final roots = await fileX.getAllRoots();
````

Each root includes:

* Type (`native` / `saf`)
* Path or URI
* Read/write capability
* Storage statistics (native only)

---

### 🔹 Native + SAF Directory Browsing

List directories using either:

* Native filesystem paths **or**
* SAF tree URIs

```dart
await fileX.listDirectory(
  target: pathOrUri,
  isSaf: trueOrFalse,
);
```

✔ Non-recursive
✔ UI-safe
✔ OEM-tolerant

---

### 🔹 Recursive Traversal (Off UI Thread)

For search, indexing, analytics:

```dart
await fileX.traverseDirectory(
  target: pathOrUri,
  isSaf: true,
  maxDepth: 5,
  filters: {
    "extensions": ["pdf", "jpg"],
    "minSize": 1024,
  },
);
```

* Depth-limited
* Filter-aware
* Executed on a native worker thread (no ANRs)

---

### 🔹 Path → SAF Resolution (Important)

When opening a file by **path**, `file_x`:

1. Checks whether the path belongs to a persisted SAF tree
2. Transparently resolves it to a SAF document URI
3. Falls back to FileProvider only when valid

This avoids common crashes on Android 11+.

```dart
await fileX.openFile(path: "/storage/...");
```

---

### 🔹 File Opening (User-Safe)

Supports:

* Native paths
* SAF URIs
* `file://` URIs

With:

* MIME detection
* URI permission propagation
* Chooser-based opening

```dart
await fileX.openFile(
  path: filePath,
  mime: "application/pdf",
);
```

---

### 🔹 SAF Folder Picker

Used when native access is restricted:

```dart
await fileX.openSafFolderPicker();
```

* Persisted permissions
* Emits events when selected

---

### 🔹 USB Attach / Detach Events

Detects:

* USB device attach
* USB removal
* Filesystem mount/unmount

```dart
fileX.events.listen((event) {
  if (event.type == FileXEventType.usbAttached) {
    // Refresh roots or ask for SAF access
  }
});
```

⚠️ USB access is **never automatic** — user permission is required.

---

### 🔹 Permission Handling (Honest)

```dart
final hasAccess = await fileX.hasAllFilesAccess();
await fileX.requestAllFilesAccess();
```

* Correct for Android 11+
* Does **not** assume permission equals access
* SAF remains authoritative where required

---

### 🔹 OEM Diagnostics

```dart
final oem = await fileX.detectOEM();
final health = await fileX.permissionHealthCheck();
```

Useful for:

* Debug screens
* Support logs
* OEM-specific bug reports

---

## Architecture Highlights

* Single-threaded native IO executor
* No blocking on UI thread
* Defensive OEM handling
* SAF permission persistence
* Cache-assisted SAF path resolution
* Explicit error reporting (no silent failures)

---

## What file_x does NOT do

* ❌ No background filesystem scanning
* ❌ No silent access to USB storage
* ❌ No bypass of `/Android/data` or `/Android/obb`
* ❌ No fake “Recent files” reconstruction
* ❌ No OEM-only privileged APIs

These are **system-level restrictions** and cannot be bypassed reliably.

---

## Known Unavoidable Android/OEM Limitations

Some restrictions are enforced at:

* SELinux
* Kernel
* Privileged system app level

Including:

* Access to `/Android/data`
* Auto-reading USB storage
* Background directory traversal
* Consistency with OEM file explorers

`file_x` intentionally avoids unsafe workarounds.

---

## Supported Platforms

| Platform | Support         |
| -------- | --------------- |
| Android  | ✅ Yes           |
| iOS      | ❌ Not supported |
| Web      | ❌ Not supported |
| Desktop  | ❌ Not supported |

This plugin is **intentionally Android-only**.

---

## Who should use this plugin

* File managers
* Backup / restore tools
* Document-heavy apps
* Media utilities
* OEM / enterprise apps
* Power-user tools

If your app needs **correct file access**, not illusions — this is for you.

---

## License

MIT

---

## Philosophy

> Respect the OS.
> Fail loudly.
> Never lie about access.
---
## 👨‍💻 Author

[![Sumit Kumar](https://github.com/sumitsharansatsangi.png?size=100)](https://github.com/sumitsharansatsangi)  
**[Sumit Kumar](https://github.com/sumitsharansatsangi)**