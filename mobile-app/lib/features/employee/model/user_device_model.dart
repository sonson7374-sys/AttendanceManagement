/// 관리자웹 UserDevice와 동일한 필드 구성.
class UserDevice {
  UserDevice({
    required this.id,
    required this.deviceId,
    this.devicePlatform,
    this.deviceName,
    required this.active,
    required this.registeredAt,
    this.lastSeenAt,
  });

  final int id;
  final String deviceId;
  final String? devicePlatform;
  final String? deviceName;
  final bool active;
  final String registeredAt;
  final String? lastSeenAt;

  factory UserDevice.fromJson(Map<String, dynamic> json) {
    return UserDevice(
      id: json['id'] as int,
      deviceId: json['deviceId'] as String,
      devicePlatform: json['devicePlatform'] as String?,
      deviceName: json['deviceName'] as String?,
      active: json['active'] as bool,
      registeredAt: json['registeredAt'] as String,
      lastSeenAt: json['lastSeenAt'] as String?,
    );
  }
}
