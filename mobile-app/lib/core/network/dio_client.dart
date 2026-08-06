import 'dart:async';
import 'package:dio/dio.dart';
import '../constants/api_constants.dart';
import '../storage/secure_storage.dart';

class DioClient {
  static Dio? _instance;

  /// 저장된 refresh token으로도 재발급이 실패해 로컬 세션을 강제로 지운 시점에
  /// 호출된다. auth_provider가 앱 시작 시 이 콜백을 등록해 로그인 상태를
  /// 함께 갱신하고, 라우터가 로그인 화면으로 리다이렉트하도록 한다.
  static void Function()? onSessionExpired;

  /// 로그인/로그아웃마다 증가한다. 로그아웃 직전에 날아간 요청이 401을 받고
  /// 재발급을 시도하는 동안 사용자가 이미 재로그인을 마칠 수 있는데, 그 경우
  /// 뒤늦게 실패한 재발급이 방금 저장된 새 세션을 지워버리면 안 된다. 재발급
  /// 시작 시점의 epoch와 완료 시점의 epoch를 비교해 그런 낡은 실패를 무시한다.
  static int sessionEpoch = 0;

  static Dio get instance {
    _instance ??= _createDio();
    return _instance!;
  }

  static Dio _createDio() {
    final dio = Dio(
      BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        connectTimeout: ApiConstants.connectTimeout,
        receiveTimeout: ApiConstants.receiveTimeout,
        contentType: 'application/json',
      ),
    );

    dio.interceptors.add(_AuthInterceptor(dio));
    return dio;
  }
}

class _AuthInterceptor extends Interceptor {
  _AuthInterceptor(this._dio);

  final Dio _dio;

  // 홈 화면 동시 조회 등으로 여러 요청이 한꺼번에 401을 받을 수 있다. 그 경우 각 요청이
  // 개별적으로 /auth/refresh를 호출하면 리프레시 토큰이 회전(rotate)되면서 뒤늦게 도착한
  // 요청이 이미 무효화된 토큰으로 재시도해 실패한다. 진행 중인 갱신 요청을 공유해 이를 막는다.
  Completer<String>? _refreshCompleter;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await SecureStorage.getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  // 로그인 실패·계정 잠금 등 "자격 증명 자체가 틀림"을 뜻하는 401은 토큰 문제가
  // 아니므로 재발급을 시도하면 안 된다(재발급 성공 후 같은 요청을 재시도해도
  // 서버가 다시 같은 401을 반환해 무한 재시도 루프에 빠진다).
  static const _nonTokenAuthCodes = {'AUTH_001', 'AUTH_005', 'AUTH_006'};

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    // 재발급(refresh) 요청 자체가 실패한 경우: 다시 재발급을 시도하면 지금 막
    // 완료하려는 바로 그 Completer를 기다리게 되어 영원히 끝나지 않는다(교착 상태).
    // 이 경우는 무조건 로그아웃 처리로 넘긴다.
    if (err.requestOptions.path == ApiConstants.refresh) {
      handler.next(err);
      return;
    }

    if (err.response?.statusCode != 401) {
      handler.next(err);
      return;
    }

    final body = err.response?.data;
    if (body is Map<String, dynamic> &&
        _nonTokenAuthCodes.contains(body['code'])) {
      handler.next(err);
      return;
    }

    // 로그아웃 처리 중 SecureStorage.clear()가 이미 실행된 뒤, 그 시점까지
    // 화면에 남아 있던 provider가 무효화되며 다시 쏘는 요청들이 있다. 이런
    // 요청은 애초에 refresh token조차 없으므로 "세션이 예기치 않게 끊겼다"가
    // 아니라 "원래 로그인 상태가 아니다"에 해당한다. 재발급을 시도할 필요도,
    // 강제 로그아웃 콜백을 또 호출할 필요도 없이 그대로 실패시킨다.
    if (await SecureStorage.getRefreshToken() == null) {
      handler.next(err);
      return;
    }

    final epochAtStart = DioClient.sessionEpoch;
    try {
      final newAccessToken = await _refreshAccessToken();
      if (DioClient.sessionEpoch != epochAtStart) {
        // 재발급이 끝나기 전에 로그아웃/재로그인이 이미 일어났다. 이 요청은
        // 이전 세션 소속이므로 방금 만들어진 세션에 관여하지 않고 조용히 끝낸다.
        handler.next(err);
        return;
      }
      final retryOptions = err.requestOptions
        ..headers['Authorization'] = 'Bearer $newAccessToken';
      final retryResponse = await _dio.fetch(retryOptions);
      handler.resolve(retryResponse);
    } catch (_) {
      if (DioClient.sessionEpoch == epochAtStart) {
        await SecureStorage.clear();
        DioClient.onSessionExpired?.call();
      }
      handler.next(err);
    }
  }

  Future<String> _refreshAccessToken() {
    final inProgress = _refreshCompleter;
    if (inProgress != null) {
      return inProgress.future;
    }

    final completer = Completer<String>();
    _refreshCompleter = completer;

    () async {
      try {
        final refreshToken = await SecureStorage.getRefreshToken();
        if (refreshToken == null) {
          throw StateError('저장된 refresh token이 없습니다.');
        }

        final response = await _dio.post(
          ApiConstants.refresh,
          data: {'refreshToken': refreshToken},
          options: Options(headers: {'Authorization': null}),
        );

        final data = response.data['data'];
        final newAccessToken = data['accessToken'] as String;
        await SecureStorage.saveTokens(
          accessToken: newAccessToken,
          refreshToken: data['refreshToken'] as String,
        );
        completer.complete(newAccessToken);
      } catch (e, st) {
        completer.completeError(e, st);
      } finally {
        _refreshCompleter = null;
      }
    }();

    return completer.future;
  }
}

class ApiException implements Exception {
  const ApiException({required this.code, required this.message});

  final String code;
  final String message;

  factory ApiException.fromDioException(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      return ApiException(
        code: data['code'] as String? ?? 'UNKNOWN',
        message: data['message'] as String? ?? e.message ?? '알 수 없는 오류가 발생했습니다.',
      );
    }
    return ApiException(
      code: 'NETWORK_ERROR',
      message: e.message ?? '네트워크 오류가 발생했습니다.',
    );
  }

  @override
  String toString() => '[$code] $message';
}
