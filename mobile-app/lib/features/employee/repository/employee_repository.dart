import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/employee_model.dart';
import '../model/organization_model.dart';
import '../model/user_device_model.dart';
import '../../work_schedule/model/work_schedule_model.dart';
import '../../workplace/model/workplace_detail_model.dart';

class EmployeePage {
  EmployeePage({required this.content, required this.number, required this.totalPages});
  final List<Employee> content;
  final int number;
  final int totalPages;
}

/// 관리자웹 직원 관리 화면과 동일한 API. 목록/단건 조회는 인증만 있으면 되지만 서버가
/// 조직 계층 기준으로 응답 범위를 좁힌다(SYSTEM_ADMIN·HR_ADMIN 전체, MANAGER 하위 조직,
/// EMPLOYEE는 본인만). 등록/수정/잠금/퇴사/비밀번호 관리는 서버가 role 기준으로 최종 검증한다.
class EmployeeRepository {
  final Dio _dio = DioClient.instance;

  Future<EmployeePage> getUsers({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get(
        ApiConstants.users,
        queryParameters: {'page': page, 'size': size, 'sort': 'id,desc'},
      );
      final data = response.data['data'] as Map<String, dynamic>;
      final content = (data['content'] as List<dynamic>)
          .map((e) => Employee.fromJson(e as Map<String, dynamic>))
          .toList();
      return EmployeePage(
        content: content,
        number: data['number'] as int? ?? page,
        totalPages: data['totalPages'] as int? ?? 1,
      );
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<Employee> createUser(EmployeeCreatePayload payload) async {
    try {
      final response = await _dio.post(ApiConstants.users, data: payload.toJson());
      return Employee.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<Employee> updateProfile(int userId, EmployeeProfileUpdatePayload payload) async {
    try {
      final response = await _dio.patch('${ApiConstants.users}/$userId/profile', data: payload.toJson());
      return Employee.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> changeRole(int userId, UserRole role) async {
    try {
      await _dio.patch('${ApiConstants.users}/$userId/role', data: {'role': role.toApi()});
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> lockUser(int userId) async {
    try {
      await _dio.post('${ApiConstants.users}/$userId/lock');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> unlockUser(int userId) async {
    try {
      await _dio.post('${ApiConstants.users}/$userId/unlock');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 물리 삭제가 아닌 퇴사 처리 (CLAUDE.md 데이터 규칙: 소프트 삭제/비활성화). 복구 API는 없다.
  Future<void> resignUser(int userId, String resignDate) async {
    try {
      await _dio.post('${ApiConstants.users}/$userId/resign', data: {'resignDate': resignDate});
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<String> resetPassword(int userId) async {
    try {
      final response = await _dio.post('${ApiConstants.users}/$userId/reset-password');
      return response.data['data']['temporaryPassword'] as String;
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> setPassword(int userId, String newPassword) async {
    try {
      await _dio.patch('${ApiConstants.users}/$userId/password', data: {'newPassword': newPassword});
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<List<UserDevice>> listDevices(int userId) async {
    try {
      final response = await _dio.get('${ApiConstants.users}/$userId/devices');
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => UserDevice.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> revokeDevice(int userId, String deviceId) async {
    try {
      await _dio.delete('${ApiConstants.users}/$userId/devices/$deviceId');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  // 조회 대상 회사는 서버가 로그인한 사용자의 소속 회사로 강제한다(클라이언트가 companyId를 지정할 수 없음).
  Future<List<Organization>> getOrganizations() async {
    try {
      final response = await _dio.get(ApiConstants.organizations);
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => Organization.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 직원 배정 관리 시트용: 특정 직원에게 배정된 근무지 목록.
  Future<List<WorkplaceDetail>> getWorkplacesForUser(int userId) async {
    try {
      final response = await _dio.get('${ApiConstants.workplaces}/users/$userId');
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkplaceDetail.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 직원 배정 관리 시트용: 특정 직원의 현재 근무제(없으면 null).
  Future<WorkSchedule?> getCurrentWorkScheduleForUser(int userId) async {
    try {
      final response = await _dio.get('${ApiConstants.workSchedules}/users/$userId/current');
      final data = response.data['data'];
      if (data == null) return null;
      return WorkSchedule.fromJson(data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> assignWorkScheduleToUser(int userId, int workScheduleId) async {
    try {
      await _dio.put('${ApiConstants.workSchedules}/users/$userId', data: {'workScheduleId': workScheduleId});
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
