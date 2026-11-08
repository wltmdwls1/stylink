# 05. API 개발 고급 - 실무 필수 최적화 (OSIV)

## OSIV란?

- **Open Session In View** (하이버네이트) = **Open EntityManager In View** (JPA)
- 관례상 OSIV라 부름
- Spring Boot 기본값: `spring.jpa.open-in-view: true`

---

## OSIV ON (기본값)

```
요청 → Filter/Interceptor → Controller → Service → Repository
        ↑_____________영속성 컨텍스트 생존 범위________________↑
                                         ↑___트랜잭션 범위___↑

Controller/View: 영속 상태지만 수정 불가
Service/Repository: 영속 상태 + 수정 가능
```

- DB 커넥션을 **요청 시작부터 응답 완료까지** 유지
- Controller, View Template에서도 LAZY 로딩 가능 → 편리함
- **문제**: 커넥션을 너무 오래 점유 → 실시간 트래픽 많을 때 커넥션 고갈 → **장애**
- 예: Controller에서 외부 API 호출 시, 외부 API 대기 시간 내내 커넥션 유지

---

## OSIV OFF

```yaml
spring:
  jpa:
    open-in-view: false
```

```
요청 → Filter/Interceptor → Controller → Service → Repository
                             ↑준영속↑    ↑__영속 컨텍스트 + 트랜잭션__↑
```

- 트랜잭션 종료 시 영속성 컨텍스트 닫힘 + DB 커넥션 즉시 반환
- **커넥션 낭비 없음** → 실시간 API 서버에 적합
- **단점**: 모든 LAZY 로딩을 **트랜잭션 안에서** 처리해야 함
  - Controller/View에서 LAZY 로딩 불가 (LazyInitializationException)
  - 트랜잭션 종료 전에 필요한 연관 데이터를 미리 로딩해야 함

---

## OSIV OFF 대응 전략 — 커맨드/쿼리 분리

OSIV OFF 상태에서 복잡성 관리 방법: **Service 계층을 둘로 분리**

```
OrderService        → 핵심 비즈니스 로직 (등록, 수정, 취소)
OrderQueryService   → 화면/API에 맞춘 조회 로직 (주로 @Transactional(readOnly=true))
```

- 두 서비스 모두 Service 계층에서 트랜잭션을 유지하므로 LAZY 로딩 사용 가능
- 비즈니스 로직(Command)과 조회 최적화 쿼리(Query)의 관심사가 명확히 분리
- 유지보수성 향상: 성능 최적화 쿼리 변경이 핵심 로직에 영향을 주지 않음

---

## 강사 실무 권장

| 상황 | OSIV |
|------|------|
| **고객 서비스 실시간 API** | **OFF** (커넥션 효율 중요) |
| **Admin 화면** (사용자 적음) | ON (편의성 우선) |

---

## stylink 적용 포인트

**stylink 결정**: `spring.jpa.open-in-view: false` + Service 분리 패턴 적용.

- `fo-api` (고객 실시간 API): OSIV OFF, 커넥션 효율 최대화
- `bo-api` (Admin): OSIV ON으로 두어도 무방 (트래픽 적음)
- 서비스 분리:
  - `OrderService` — 주문/취소 비즈니스 로직
  - `OrderQueryService` — 주문 목록/상세 조회 (fetch join, BatchSize 사용)
  - `ReservationService` — 예약/확정/취소 비즈니스 로직
  - `ReservationQueryService` — 예약 목록/상세 조회
- OSIV OFF 시 `LazyInitializationException` 방지: Service 내에서 필요한 연관 데이터 미리 로딩 (fetch join or `Hibernate.initialize()`)
