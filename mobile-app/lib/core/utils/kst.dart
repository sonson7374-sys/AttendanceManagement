/// 서버가 UTC로 내려주는 시각을 기기 타임존과 무관하게 Asia/Seoul(UTC+9, DST 없음) 기준으로
/// 변환한다. `DateTime.toLocal()`은 기기 설정 타임존을 따르기 때문에, 에뮬레이터/기기 시계가
/// KST가 아닌 값으로 설정되면(예: GMT로 되돌아가는 경우) 출근·퇴근 시각이 잘못 표시된다.
extension KstDateTime on DateTime {
  DateTime toKst() => toUtc().add(const Duration(hours: 9));
}
