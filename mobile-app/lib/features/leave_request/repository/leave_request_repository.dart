import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/leave_request_model.dart';

class LeaveRequestRepository {
  final Dio _dio = DioClient.instance;

  Future<LeaveRequestItem> submit(LeaveRequestSubmit request) async {
    try {
      final path = request.isOutsideWork
          ? ApiConstants.outsideWorkRequests
          : ApiConstants.leaveRequests;
      final response = await _dio.post(path, data: request.toJson());
      return LeaveRequestItem.fromJson(response.data['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  /// 휴가 신청과 외근·출장·재택 신청은 백엔드에서 별도 도메인이므로
  /// 두 목록을 병렬로 조회한 뒤 제출일 기준으로 병합해 하나의 목록으로 보여준다.
  Future<List<LeaveRequestItem>> getMyRequests() async {
    try {
      final results = await Future.wait([
        _dio.get(ApiConstants.myLeaveRequests),
        _dio.get(ApiConstants.myOutsideWorkRequests),
      ]);
      final items = [
        ...(results[0].data['data'] as List<dynamic>),
        ...(results[1].data['data'] as List<dynamic>),
      ].map((e) => LeaveRequestItem.fromJson(e as Map<String, dynamic>)).toList();
      items.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      return items;
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
