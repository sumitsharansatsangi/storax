enum StoraxEventType { usbAttached, usbDetached, safPicked }

class StoraxEvent {
  final StoraxEventType type;
  final String? payload;

  const StoraxEvent(this.type, {this.payload});
}
