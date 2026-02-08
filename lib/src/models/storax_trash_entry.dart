class StoraxTrashEntry {
  final String id;
  final String name;
  final bool isSaf;
  final DateTime trashedAt;

  // Native
  final String? originalPath;
  final String? trashedPath;

  // SAF
  final String? originalUri;
  final String? trashedUri;
  final String? safRootUri;

  StoraxTrashEntry({
    required this.id,
    required this.name,
    required this.isSaf,
    required this.trashedAt,
    this.originalPath,
    this.trashedPath,
    this.originalUri,
    this.trashedUri,
    this.safRootUri,
  });

  factory StoraxTrashEntry.fromMap(Map<String, dynamic> map) {
    return StoraxTrashEntry(
      id: map['id'] as String,
      name: map['name'] as String,
      isSaf: map['isSaf'] as bool,
      trashedAt: DateTime.fromMillisecondsSinceEpoch(map['trashedAt'] as int),
      originalPath: map['originalPath'] as String?,
      trashedPath: map['trashedPath'] as String?,
      originalUri: map['originalUri'] as String?,
      trashedUri: map['trashedUri'] as String?,
      safRootUri: map['safRootUri'] as String?,
    );
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'isSaf': isSaf,
    'trashedAt': trashedAt.millisecondsSinceEpoch,
    'originalPath': originalPath,
    'trashedPath': trashedPath,
    'originalUri': originalUri,
    'trashedUri': trashedUri,
    'safRootUri': safRootUri,
  };
}
