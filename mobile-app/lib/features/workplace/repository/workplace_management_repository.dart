import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/assignable_user_model.dart';
import '../model/workplace_detail_model.dart';

/// 관리자웹 근무지 관리 화면과 동일한 CRUD·직원 배정 API. 서버가 역할(role) 기준으로
/// 최종 권한을 검증하므로(목록/조회: MANAGER 이상, 등록/수정/배정: HR_ADMIN·SYSTEM_ADMIN,
/// 삭제/복구: SYSTEM_ADMIN), 여기서의 화면 잠금은 UX 편의일 뿐이다.
class WorkplaceManagementRepository {
  final Dio _dio = DioClient.instance;

  Future<List<WorkplaceDetail>> getWorkplaces(int companyId, {bool includeInactive = false}) async {
    try {
      final response = await _dio.get(
        ApiConstants.workplaces,
        queryParameters: {'companyId': companyId, 'includeInactive': includeInactive},
      );
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkplaceDetail.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 로그인한 사용자에게 배정된 근무지 상세 목록을 조회한다(직원용 화면).
  Future<List<WorkplaceDetail>> getMyAssignedWorkplaceDetails() async {
    try {
      final response = await _dio.get(ApiConstants.assignedWorkplaces);
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkplaceDetail.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<WorkplaceDetail> createWorkplace(WorkplaceDetailPayload payload) async {
    try {
      final response = await _dio.post(ApiConstants.workplaces, data: payload.toJson());
      return WorkplaceDetail.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<WorkplaceDetail> updateWorkplace(int id, WorkplaceDetailPayload payload) async {
    try {
      final response = await _dio.put('${ApiConstants.workplaces}/$id', data: payload.toJson());
      return WorkplaceDetail.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 물리 삭제가 아닌 비활성화 처리 (CLAUDE.md 데이터 규칙: 소프트 삭제/비활성화)
  Future<void> deactivateWorkplace(int id) async {
    try {
      await _dio.delete('${ApiConstants.workplaces}/$id');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> activateWorkplace(int id) async {
    try {
      await _dio.post('${ApiConstants.workplaces}/$id/activate');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<List<AssignableUser>> getAssignedUsers(int workplaceId) async {
    try {
      final response = await _dio.get('${ApiConstants.workplaces}/$workplaceId/users');
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => AssignableUser.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> assignUserToWorkplace(int workplaceId, int userId) async {
    try {
      await _dio.post('${ApiConstants.workplaces}/$workplaceId/users/$userId');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> removeUserFromWorkplace(int workplaceId, int userId) async {
    try {
      await _dio.delete('${ApiConstants.workplaces}/$workplaceId/users/$userId');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 직원 배정 대상 후보 목록(활성 직원 전체, 페이지네이션은 첫 페이지 100명까지만 조회).
  Future<List<AssignableUser>> getActiveUsers() async {
    try {
      final response = await _dio.get(ApiConstants.users, queryParameters: {'page': 0, 'size': 100});
      final content = response.data['data']['content'] as List<dynamic>;
      return content.map((e) => AssignableUser.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
