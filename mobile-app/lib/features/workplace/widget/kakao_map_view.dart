import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../../../core/constants/api_constants.dart';

/// 관리자웹 근무지관리 화면과 동일한 카카오맵 경험을 WebView로 재사용한다(관리자웹의
/// KakaoMap.tsx/geocodeAddress()를 그대로 로드 — 지도 로직을 모바일에 이중 구현하지 않는다).
/// editable=true면 지도 클릭/마커 드래그/주소검색으로 좌표를 지정할 수 있고,
/// editable=false면 좌표/반경을 표시만 하는 읽기 전용 미리보기로 쓴다.
class KakaoMapView extends StatefulWidget {
  const KakaoMapView({
    super.key,
    required this.latitude,
    required this.longitude,
    required this.radiusMeters,
    this.editable = false,
    this.height = 180,
    this.deviceLatitude,
    this.deviceLongitude,
    this.onPositionChanged,
    this.onAddressResolved,
    this.onError,
  });

  final double latitude;
  final double longitude;
  final int radiusMeters;
  final bool editable;
  final double height;
  /// 근무지(고정 좌표)와 별개로 표시할 "내 위치" 파란 점의 초기 좌표. 이후 위치가 바뀔 때는
  /// 위젯을 다시 만들지 말고 [KakaoMapViewState.setDevicePosition]을 호출해서 갱신한다
  /// (지도를 다시 그리지 않고 점만 이동).
  final double? deviceLatitude;
  final double? deviceLongitude;
  final void Function(double latitude, double longitude)? onPositionChanged;
  final void Function(double latitude, double longitude, String addressName)? onAddressResolved;
  final void Function(String message)? onError;

  @override
  State<KakaoMapView> createState() => KakaoMapViewState();
}

class KakaoMapViewState extends State<KakaoMapView> {
  late final WebViewController _controller;
  bool _loading = true;
  bool _failed = false;
  // 메인 문서가 한 번이라도 로드에 성공하면 true로 고정한다. webview_flutter_android의
  // 레거시 onReceivedError 콜백은 isForMainFrame을 무조건 true로 보고하는 버그가 있어서,
  // 페이지 로드 후 Vite HMR 웹소켓이 잠깐 끊겼다가 재연결되는 것 같은 서브리소스성 오류에도
  // "메인 프레임 오류"로 잘못 보일 수 있다. 문서가 이미 정상 로드된 뒤의 오류는 무시한다.
  bool _pageLoadedOnce = false;

  @override
  void initState() {
    super.initState();
    final uri = Uri.parse('${ApiConstants.webAppBaseUrl}${ApiConstants.kakaoMapEmbedPath}').replace(
      queryParameters: {
        'lat': widget.latitude.toString(),
        'lng': widget.longitude.toString(),
        'radius': widget.radiusMeters.toString(),
        'editable': widget.editable.toString(),
        if (widget.deviceLatitude != null) 'mylat': widget.deviceLatitude.toString(),
        if (widget.deviceLongitude != null) 'mylng': widget.deviceLongitude.toString(),
      },
    );
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..addJavaScriptChannel('FlutterBridge', onMessageReceived: _onMessage)
      ..setNavigationDelegate(NavigationDelegate(
        onPageFinished: (_) {
          _pageLoadedOnce = true;
          if (mounted) setState(() => _loading = false);
        },
        onWebResourceError: (error) {
          // 서브리소스(파비콘, 소스맵 등) 오류나, 문서가 이미 로드된 뒤 뒤늦게 들어오는
          // 오류로 지도 전체를 실패 처리하지 않는다.
          if (_pageLoadedOnce || error.isForMainFrame == false) return;
          if (mounted) setState(() { _loading = false; _failed = true; });
        },
      ))
      ..loadRequest(uri);
  }

  void _onMessage(JavaScriptMessage message) {
    try {
      final data = jsonDecode(message.message) as Map<String, dynamic>;
      switch (data['type']) {
        case 'position':
          widget.onPositionChanged?.call(
            (data['latitude'] as num).toDouble(),
            (data['longitude'] as num).toDouble(),
          );
          break;
        case 'geocodeResult':
          widget.onAddressResolved?.call(
            (data['latitude'] as num).toDouble(),
            (data['longitude'] as num).toDouble(),
            data['addressName'] as String,
          );
          break;
        case 'geocodeError':
          widget.onError?.call(data['message'] as String);
          break;
      }
    } catch (_) {
      // 브릿지 메시지 형식이 예상과 다르면 조용히 무시한다(지도 표시 자체에는 영향 없음).
    }
  }

  /// 주소 검색 — 관리자웹 "주소 검색" 버튼과 동일한 동작. 결과는 onAddressResolved/onError로 전달된다.
  void searchAddress(String address) {
    final encoded = jsonEncode(address);
    _controller.runJavaScript('window.geocodeAddress && window.geocodeAddress($encoded)');
  }

  /// 반경(m) 변경을 지도의 원(circle)에 즉시 반영한다.
  void updateRadius(int radiusMeters) {
    _controller.runJavaScript('window.setRadius && window.setRadius($radiusMeters)');
  }

  /// 위도/경도 직접 입력을 지도 마커 위치에 즉시 반영한다.
  void setPosition(double latitude, double longitude) {
    _controller.runJavaScript('window.setPosition && window.setPosition($latitude, $longitude)');
  }

  /// 근무지 마커·원과는 별개인 "내 위치" 파란 점을 이동시킨다. [setPosition]과 달리 지도를
  /// 다시 그리지 않고 점 위치만 갱신하므로 실시간 위치처럼 자주 호출해도 깜빡이지 않는다.
  void setDevicePosition(double latitude, double longitude) {
    _controller.runJavaScript('window.setDevicePosition && window.setDevicePosition($latitude, $longitude)');
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: widget.height,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Stack(
          children: [
            WebViewWidget(controller: _controller),
            if (_loading)
              const Positioned.fill(
                child: ColoredBox(
                  color: Color(0xFFF1F5F9),
                  child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                ),
              ),
            if (_failed)
              Positioned.fill(
                child: ColoredBox(
                  color: const Color(0xFFFEF2F2),
                  child: Center(
                    child: Text('지도 로드 실패', style: TextStyle(color: Colors.red.shade400, fontSize: 13)),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
