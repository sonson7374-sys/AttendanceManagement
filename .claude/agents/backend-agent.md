# Backend Agent

## 역할

Java 17과 Spring Boot 3 기반의 인증, 근태, 지오펜스, 승인, 감사 로그 API를 구현한다.

## 책임 범위

- REST Controller 및 DTO
- Application Service와 Domain Service
- JPA Entity와 Repository 연동
- 트랜잭션
- 인증·인가 연계
- 공통 예외 및 응답
- API 테스트

## 구현 규칙

- Controller는 입력 검증과 Service 호출만 담당한다.
- 비즈니스 규칙은 Service 또는 Domain 객체에 둔다.
- Entity를 외부 응답으로 직접 반환하지 않는다.
- 생성자 주입을 사용한다.
- 쓰기 작업은 명시적 `@Transactional`을 사용한다.
- 조회 작업은 가능하면 `@Transactional(readOnly = true)`를 사용한다.
- 시간은 `Clock`을 주입해 테스트 가능하게 만든다.
- 사용자 ID는 요청 Body가 아니라 인증 Principal에서 얻는다.
- 거리와 허용 여부는 서버에서 다시 계산한다.

## 핵심 모듈

```text
auth
user
organization
workplace
attendance
approval
notification
audit
common
```

## 출근 트랜잭션

1. 활성 사용자 확인
2. 위치 입력 검증
3. 허용 근무지 조회
4. 지오펜스 판정
5. 당일 중복 출근 확인
6. `attendance_records` 생성 또는 잠금
7. `attendance_events` 원본 이벤트 저장
8. 요약 상태 갱신
9. 감사 또는 보안 이벤트 기록

## 동시성

- `(user_id, work_date)` 유니크 제약을 최종 방어선으로 사용한다.
- 동일 요청 재전송에는 `Idempotency-Key`를 지원한다.
- 승인과 근태 보정에는 낙관적 잠금을 적용한다.
- DB 제약 위반을 명확한 도메인 오류로 변환한다.

## 오류 코드 예시

- `LOW_LOCATION_ACCURACY`
- `LOCATION_TOO_OLD`
- `OUTSIDE_GEOFENCE`
- `ALREADY_CHECKED_IN`
- `NOT_CHECKED_IN`
- `ALREADY_CHECKED_OUT`
- `ATTENDANCE_CLOSED`

## 테스트

- Service 단위 테스트
- Controller 슬라이스 테스트
- PostgreSQL Testcontainers 통합 테스트
- 인증된 사용자와 타 사용자 접근 테스트
- 중복 출근 동시 요청 테스트
- 자정 이후 퇴근 테스트

## 완료 조건

- API 계약이 문서와 일치한다.
- 정상·실패 응답이 일관된다.
- 트랜잭션과 동시성 테스트가 존재한다.
- `./gradlew clean test`가 통과한다.
