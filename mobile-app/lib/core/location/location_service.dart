import 'package:geolocator/geolocator.dart';

/// 위치 권한/GPS 활성화를 확인하고 현재 위치를 조회한다.
/// 출근/퇴근 처리와 홈 화면의 실시간 거리 표시가 이 로직을 공유한다.
Future<Position> getCurrentPosition() async {
  final serviceEnabled = await Geolocator.isLocationServiceEnabled();
  if (!serviceEnabled) {
    throw Exception('위치 서비스가 비활성화되어 있습니다. 기기 설정에서 위치를 켜주세요.');
  }

  var permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied) {
      throw Exception('위치 권한이 거부되었습니다.');
    }
  }
  if (permission == LocationPermission.deniedForever) {
    throw Exception('위치 권한이 영구 거부되었습니다. 기기 설정에서 직접 허용해주세요.');
  }

  // geolocator의 LocationSettings.timeLimit이 일부 기기/에뮬레이터 환경(GPS 픽스가
  // 전혀 오지 않는 경우)에서 지켜지지 않아 무한 대기할 수 있으므로, 별도 타임아웃으로
  // 반드시 종료되도록 이중 안전장치를 둔다.
  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(
      accuracy: LocationAccuracy.high,
      timeLimit: Duration(seconds: 15),
    ),
  ).timeout(
    const Duration(seconds: 17),
    onTimeout: () => throw Exception('GPS 위치를 확인하지 못했습니다. 실외에서 다시 시도해주세요.'),
  );
}
