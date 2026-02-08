enum StoraxEventType { usbAttached, usbDetached, safPicked, transferProgress }

class StoraxEvent {
  final StoraxEventType type;
  final dynamic payload; // ✅ FIXED

  const StoraxEvent(this.type, {this.payload});
}
