import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/dio_client.dart';
import '../model/workplace_model.dart';

class WorkplaceRepository {
  final Dio _dio = DioClient.instance;

  /// 로그인한 사용자에게 배정된 근무지 목록을 조회한다.
  Future<List<Workplace>> getAssignedWorkplaces() async {
    try {
      final response = await _dio.get(ApiConstants.assignedWorkplaces);
      final list = response.data['data'] as List<dynamic>;
      return list
          .map((e) => Workplace.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
