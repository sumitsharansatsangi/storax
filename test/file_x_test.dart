import 'package:flutter_test/flutter_test.dart';
import 'package:file_x/file_x.dart';
import 'package:file_x/file_x_platform_interface.dart';
import 'package:file_x/file_x_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

/// A mock platform implementation used to verify that
/// [FileX] delegates calls to [FileXPlatform.instance].
class MockFileXPlatform
    with MockPlatformInterfaceMixin
    implements FileXPlatform {
  @override
  Future<List<Map<String, dynamic>>> getNativeRoots() async {
    return [
      {
        'type': 'native',
        'name': 'Mock storage',
        'path': '/mock/path',
        'total': 100,
        'free': 50,
        'used': 50,
        'writable': true,
      },
    ];
  }

  @override
  Future<List<Map<String, dynamic>>> getAllRoots() async {
    return await getNativeRoots();
  }

  @override
  Future<List<Map<String, dynamic>>> listDirectory({
    required String target,
    required bool isSaf,
    Map<String, dynamic>? filters,
  }) async {
    return [
      {
        'name': 'mock.txt',
        'path': '/mock/path/mock.txt',
        'uri': null,
        'isDirectory': false,
        'size': 123,
        'lastModified': 1700000000000,
        'mime': 'text/plain',
        'storageType': 'native',
      },
    ];
  }

  @override
  Future<List<Map<String, dynamic>>> traverseDirectory({
    required String target,
    required bool isSaf,
    int maxDepth = 10,
    Map<String, dynamic>? filters,
  }) async {
    return await listDirectory(target: target, isSaf: isSaf);
  }

  @override
  Future<void> openSafFolderPicker() async {}

  @override
  Future<bool> hasAllFilesAccess() async => true;

  @override
  Future<void> requestAllFilesAccess() async {}

  @override
  Future<Map<String, dynamic>> detectOEM() async {
    return {
      'manufacturer': 'Mock',
      'brand': 'MockBrand',
      'model': 'MockModel',
      'sdk': '34',
    };
  }

  @override
  Future<Map<String, dynamic>> permissionHealthCheck() async {
    return {'allFilesAccess': true, 'sdk': 34, 'oem': 'Mock'};
  }
  
  @override
  Future<void> openFile({String? path, String? mime, String? uri}) {
    return Future.value();
  }
}

void main() {
  final FileXPlatform initialPlatform = FileXPlatform.instance;

  test('$MethodChannelFileX is the default platform implementation', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFileX>());
  });

  test('FileX delegates calls to the platform interface', () async {
    final fileX = FileX();
    final fakePlatform = MockFileXPlatform();

    // Inject mock platform
    FileXPlatform.instance = fakePlatform;

    final roots = await fileX.getNativeRoots();
    final files = await fileX.listDirectory(target: '/mock/path', isSaf: false);
    final hasAccess = await fileX.hasAllFilesAccess();
    final oem = await fileX.detectOEM();

    expect(roots.first['name'], 'Mock storage');
    expect(files.first['name'], 'mock.txt');
    expect(hasAccess, isTrue);
    expect(oem['manufacturer'], 'Mock');

    // Restore original platform
    FileXPlatform.instance = initialPlatform;
  });
}
