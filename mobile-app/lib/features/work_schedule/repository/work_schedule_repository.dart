import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/work_schedule_model.dart';

/// 관리자웹 근무제 관리 화면과 동일한 API. 서버가 역할(role) 기준으로 최종 권한을
/// 검증하므로(목록/조회: MANAGER 이상, 등록/수정/삭제: MANAGER 이상, 배정: HR_ADMIN·SYSTEM_ADMIN),
/// 여기서의 화면 잠금은 UX 편의일 뿐이다.
class WorkScheduleRepository {
  final Dio _dio = DioClient.instance;

  Future<List<WorkSchedule>> getWorkSchedules() async {
    try {
      final response = await _dio.get(ApiConstants.workSchedules);
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkSchedule.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 로그인한 직원 본인의 근무제(단건)를 조회한다. 목록 화면과 형태를 맞추기 위해
  /// 단일 항목 리스트로 감싸서 반환한다(관리자웹의 getMyWorkSchedule 래핑과 동일).
  Future<List<WorkSchedule>> getMyWorkSchedule() async {
    try {
      final response = await _dio.get(ApiConstants.assignedWorkSchedule);
      final data = response.data['data'];
      if (data == null) return [];
      return [WorkSchedule.fromJson(data as Map<String, dynamic>)];
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 변경요청 시 선택 가능한 활성 근무제 목록(인증된 사용자 누구나 조회 가능).
  Future<List<WorkSchedule>> getWorkScheduleOptions() async {
    try {
      final response = await _dio.get(ApiConstants.workScheduleOptions);
      final content = response.data['data'] as List<dynamic>;
      return content.map((e) => WorkSchedule.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<WorkSchedule> createWorkSchedule(WorkSchedulePayload payload) async {
    try {
      final response = await _dio.post(ApiConstants.workSchedules, data: payload.toJson());
      return WorkSchedule.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<WorkSchedule> updateWorkSchedule(int id, WorkSchedulePayload payload) async {
    try {
      final response = await _dio.put('${ApiConstants.workSchedules}/$id', data: payload.toJson());
      return WorkSchedule.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 물리 삭제가 아닌 비활성화 처리 (CLAUDE.md 데이터 규칙: 소프트 삭제/비활성화).
  /// 근무제는 활성화(복구) API가 없으므로 삭제는 단방향 전환이다.
  Future<void> deactivateWorkSchedule(int id) async {
    try {
      await _dio.delete('${ApiConstants.workSchedules}/$id');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
