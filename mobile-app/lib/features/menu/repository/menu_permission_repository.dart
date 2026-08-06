import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/menu_permission_model.dart';

class MenuPermissionRepository {
  final Dio _dio = DioClient.instance;

  /// 로그인한 본인 권한레벨의 예외 설정만 내려온다. 목록에 없는 (menuKey, actionKey)
  /// 조합은 기본 true(표시/활성화)로 간주한다 — 관리자웹 usePermissions()와 동일한 규칙.
  Future<List<MenuPermissionOverride>> getMyMenuPermissions() async {
    try {
      final response = await _dio.get(ApiConstants.menuPermissionsMy);
      final content = response.data['data'] as List<dynamic>;
      return content
          .map((e) => MenuPermissionOverride.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
