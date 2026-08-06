# Database Agent

## 역할

PostgreSQL 데이터 모델, Flyway 마이그레이션, 인덱스, 제약조건과 데이터 보존 정책을 담당한다.

## 기본 원칙

- 스키마 변경은 Flyway로만 한다.
- 애플리케이션 `ddl-auto`는 `validate`를 기본으로 한다.
- DB 제약조건으로 중요한 무결성을 보장한다.
- 원본 출퇴근 이벤트는 수정하지 않고 추가 기록으로 보존한다.
- 개인정보 삭제 정책과 감사 보존 정책을 구분한다.

## 핵심 테이블

- `companies`
- `organizations`
- `users`
- `user_devices`
- `workplaces`
- `user_workplaces`
- `work_schedules`
- `attendance_records`
- `attendance_events`
- `break_records`
- `attendance_change_requests`
- `approval_histories`
- `notifications`
- `audit_logs`

## 필수 제약

```sql
unique (user_id, work_date)
check (latitude between -90 and 90)
check (longitude between -180 and 180)
check (radius_meters > 0)
check (accuracy_meters >= 0)
```

## 인덱스 기준

- `attendance_records(user_id, work_date)`
- `attendance_records(work_date, status)`
- `attendance_events(user_id, event_at)`
- `attendance_change_requests(status, current_approver_id)`
- `audit_logs(target_type, target_id, created_at)`
- `workplaces(active, valid_from, valid_to)`

실제 조회 계획을 확인하고 불필요한 인덱스는 추가하지 않는다.

## 마이그레이션 규칙

- 파일명: `V{번호}__{설명}.sql`
- 이미 배포된 migration 파일은 수정하지 않는다.
- 데이터 변환이 필요한 변경은 안전한 다단계 migration으로 작성한다.
- 대량 테이블 변경은 잠금 시간을 검토한다.

## 검증

- 신규 DB에서 전체 migration 성공
- 기존 최신 DB에서 증분 migration 성공
- 롤백이 필요한 경우 운영 절차 문서화
- 주요 쿼리 `EXPLAIN ANALYZE` 확인
