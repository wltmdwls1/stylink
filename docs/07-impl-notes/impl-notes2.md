# stylink 비즈니스 로직 구현 계획

## Context

Entity 20종, Enum 9종 완료 상태. Repository/Service/Controller 전부 미구현.
포트폴리오 핵심은 **재고 상태 흐름(AVAILABLE→RESERVED→SOLD)** + **InventoryLog 추적성** + **주문-결제-재고 오케스트레이터 패턴**.
1차(온라인 쇼핑) 완성 후 2차(O2O 출장) 순서.

---

## 패키지 위치

```
domain/src/main/java/com/stylink/domain/
├── repository/     ← Claude가 JPA Repository 인터페이스 생성 (Phase A)
└── service/        ← 사용자가 핵심 Service 구현 (Phase B~C)

fo-api/src/main/java/com/stylink/fo/
├── controller/     ← 사용자가 Controller 구현
└── dto/            ← 사용자가 Request/Response DTO 구현

external-mock/src/main/java/com/stylink/mock/
├── (인터페이스는 domain 모듈에 선언 — DIP 적용)
└── impl/           ← Mock 구현체 (Claude 담당)
```

---

## Phase A — Claude 먼저 준비

### A-1. @Version 적용 대상 (Inventory, Order, StylistSchedule)
- `Inventory`: 동시 주문/예약 시 같은 재고 중복 RESERVED 방지
- `Order`: 결제 API(PENDING→PAID)와 결제대기 만료 배치(PENDING→FAILED)가 같은 주문을 동시에 바꾸는 것 방지 (V4 추가)
- `StylistSchedule`: 같은 일정 중복 예약 방지 (2차)
- 나머지 Entity(Payment 등)에는 추가 안 함 — 한 사용자가 순차 처리하는 흐름이라 동시 변경 경로가 없음
- 면접 대응: "왜 이 세 개만인가?" → 동시에 바꾸는 경로가 실제로 있는 곳에만 적용

### A-2. Repository 인터페이스 일괄 생성
`domain/repository/` 아래 각 Entity별 JpaRepository 생성. 비즈니스에서 필요한 쿼리 메서드 시그니처 포함.

| Repository | 주요 메서드 |
|-----------|------------|
| `MemberRepository` | `findByEmail`, `findByPhoneHash` |
| `ProductRepository` | `findByCategoryId`, `findByStatus` |
| `InventoryRepository` | `findByProductId`, `findByStatus` |
| `InventoryLogRepository` | `findByInventoryId` |
| `OrderRepository` | `findByMemberId`, `findByStatus` |
| `OrderItemRepository` | `findByOrderId` |
| `PaymentRepository` | `findByOrderId` |
| `DeliveryRepository` | `findByOrderId` |
| `ReservationRepository` | `findByMemberId`, `findByStatus` |
| `ReservationItemRepository` | `findByReservationId` (2차) |
| `CartRepository` | `findByMemberId` |
| `CartItemRepository` | `findByCartId` |
| `WishlistRepository` | `findByMemberId` |
| `StylistRepository` | `findByAvailableTrue` |

### A-3. InventoryLog 동반 저장 패턴 확립
재고 상태가 변경될 때마다 InventoryLog를 **반드시** 함께 저장하는 구조.
모든 Service 구현의 기준이 되므로 Phase A에서 패턴을 먼저 정의.

```
재고 상태 변경 시 InventoryLog 저장 항목:
- inventoryId  (어떤 재고)
- fromStatus   (이전 상태)
- toStatus     (바뀐 상태)
- changeReason (InventoryChangeReason Enum)
- changedBy    (memberId 또는 시스템 식별자)
```

면접 대응: "데이터 정합성이 깨졌을 때 어떻게 추적하나?" → 로그로 전 이력 추적 가능

### A-4. external-mock 인터페이스 + 구현체 (DIP 적용)
인터페이스는 `domain` 모듈, 구현체는 `external-mock` 모듈.
나중에 진짜 PG사 API로 교체 시 구현체만 바꾸면 됨 → 의존성 역전 원칙(DIP) 준수.

```
// domain 모듈 (인터페이스)
PgGateway           — charge(amount), cancel(paymentKey)
DeliveryGateway     — requestDelivery(orderId), getStatus(trackingNo)
NotificationGateway — send(memberId, message)
AuthGateway         — sendCode(phone), verify(phone, code)

// external-mock 모듈 (구현체)
MockPgGateway           — 성공/실패/취소 시나리오 시뮬레이션
MockDeliveryGateway     — 상태 변경 시뮬레이션 (PREPARING→SHIPPING→DELIVERED)
MockNotificationGateway — 이벤트 기반 로그 출력
MockAuthGateway         — 전화번호 기반 인증 (USER → VERIFIED)
```

---

## Phase B — 사용자 구현 순서 (1차 온라인)

### Step 1: 회원/인증
**왜 먼저?** JWT 로그인 완성 전엔 인증 필요한 API를 Swagger로 테스트 불가

- `MemberService` — 회원가입, 조회, 등급 계산
- `AuthService` — 로그인, JWT 발급, `AuthGateway`(Mock) 전화번호 인증 연동
- `AuthController`, `MemberController` (fo-api)

검증: 회원가입 → 로그인 → JWT 발급 → Authorization 헤더로 인증 API 호출

---

### Step 2: 상품/재고 조회
**왜 먼저?** 주문 전에 상품을 볼 수 있어야 함. 단순 조회 → 코딩 워밍업.

- `ProductService` — 상품 목록(필터/정렬), 상품 상세, 카테고리 조회
- `InventoryService.getStock()` — 재고 현황 조회만 (상태 전환 로직은 Step 3)
- `ProductController` (fo-api)

---

### Step 3: InventoryService 핵심 ← 포트폴리오 핵심
면접관이 가장 많이 물어볼 부분. 낙관적 락 + InventoryLog 동반 저장이 차별점.

```java
/*
// AVAILABLE → RESERVED
void reserve(Long inventoryId, Long memberId)
  try { 상태 변경 }
  catch (OptimisticLockException e) { throw new BusinessException(INVENTORY_NOT_AVAILABLE) }
  // 성공 시: InventoryLog(AVAILABLE→RESERVED, ORDER_CREATED, memberId) 저장

// RESERVED → AVAILABLE (주문 취소 / 결제 실패 / 배치 만료)
void release(Long inventoryId, Long memberId, InventoryChangeReason reason)
  // InventoryLog(RESERVED→AVAILABLE, reason) 저장
  // 외부 호출 가능 구조 유지 → 나중에 배치 스케줄러가 만료 건 bulk release 할 진입점

// RESERVED → SOLD (1차: 배송 시작 시)
void sell(Long inventoryId)
  // InventoryLog(RESERVED→SOLD, ORDER_SHIPPED) 저장

// 1차 반품 검수 (BO): SOLD → AVAILABLE(정상) / UNSELLABLE(불량)
//   reason: RESTORED_BY_RETURN(반품 정상) / UNSELLABLE_BY_RETURN(반품 불량)
//   "정상→AVAILABLE / 불량→UNSELLABLE" 부분은 2차 반납 확인과 공유 가능 (출발 상태·reason만 다름)
//   메서드 이름/나누는 방식은 구현 시 결정 (사용자 담당)
```

검증: 동시에 같은 재고에 reserve 2건 → 1건 성공, 1건 INVENTORY_NOT_AVAILABLE

---

### Step 4: OrderService (오케스트레이터)
**@Transactional 경계 핵심:** reserve() 실패 시 Order가 DB에 저장되지 않아야 함

```
createOrder():
  1. inventoryService.reserve() 호출 → 실패 시 즉시 예외, Order 미생성
  2. Order(PENDING) + OrderItem 저장
  3. OrderHistory 기록

cancelOrder():
  1. Order 상태 검증 (IN_DELIVERY 이후면 취소 불가 → BusinessException)
  2. inventoryService.release() 호출 (사유: ORDER_CANCELLED)
  3. Order(CANCELLED) + OrderHistory 기록
```

주의: InventoryService에서 REQUIRES_NEW 같은 다른 전파 속성 쓰면 트랜잭션 꼬임 → 기본 REQUIRED 유지

---

### Step 5: PaymentService + MockPgGateway
- `pay(orderId)` — 주문 PENDING 확인 (아니면 거절) → MockPgGateway.charge() → 성공: Order PAID / 실패: Order FAILED + release()
  - PG 성공인데 주문이 이미 FAILED(만료 배치가 먼저 처리) → MockPgGateway.cancel()로 전액 환불 + 안내
- **결제 실패 = 정상 저장 (예외 롤백 아님)** — Payment FAILED + `failCode`/`failMessage` → Order FAILED + OrderHistory → `release()` (reason: `RELEASED_BY_ORDER_FAIL`, description에 fail_code)
  - PG 거절 (도난카드/한도초과/잔액부족/비밀번호 오류) → 바로 FAILED, failCode = PG 에러코드 그대로
  - 전송 실패 (연결 안 됨) → 바로 FAILED, failCode = `PG_CONNECTION_FAILED`
  - 타임아웃 (결과 모름) → **망취소** `MockPgGateway.cancel()` 후 FAILED, failCode = `PG_TIMEOUT`
    - 망취소도 실패 → 관리자 확인용 로그/알림 (PG 거래 조회 배치는 확장 포인트)
  - PG 요청/응답 원문 → `outbound_api_log` (api_type = PG), 별도 트랜잭션(`REQUIRES_NEW`)
- `refund(paymentId)` — MockPgGateway.cancel() 호출
- MockPgGateway 시나리오: 성공 / 거절(사유별 코드) / 전송 실패 / 타임아웃
- 구현 시 결정 (사용자 담당): 실패를 결과값으로 받을지 예외 catch로 받을지, PG 호출을 트랜잭션 안/밖 어디에 둘지, Payment 행을 PG 호출 전(PENDING)에 만들지 후에 만들지

검증: 결제 성공 플로우 / 결제 실패(사유별) → Payment FAILED + fail_code 저장 + Inventory AVAILABLE 복구 / 타임아웃 → 망취소 호출 + FAILED / 실패해도 outbound_api_log 남음 / 만료된 주문 결제 요청 거절 / 결제 중 만료 → 환불

### Step 5-1: batch — 결제대기 만료 (1차 전용)
- 주문 PENDING 15분 초과 **+ `orderType = ONLINE`** → Order FAILED + `release()` (reason: RELEASED_BY_BATCH_EXPIRED)
- 화면 타이머는 10분 (안내용), 서버 기준은 15분 (5분 여유 — 결제 진행 중 만료 방지)
- **결제 vs 배치 동시 처리**: 주문 PENDING→PAID / PENDING→FAILED 중 먼저 바꾼 쪽만 성공해야 함
  - `Order`에 `@Version` 적용 (V4) → 늦게 저장하는 쪽에서 낙관적 락 예외 발생
    - 결제가 늦으면: 예외 잡아서 PG 취소(전액 환불) + 안내
    - 배치가 늦으면: 예외 잡아서 그 주문은 건너뜀 (재고도 건드리지 않음)
  - 주의: 배치를 벌크 UPDATE(JPQL/네이티브)로 하면 @Version 확인 안 됨 → 주문을 하나씩 읽어서 엔티티로 변경
  - 예외 처리, PG 호출을 트랜잭션 안/밖 어디에 둘지는 구현 시 결정 (사용자 담당)

검증: 결제 없이 15분 경과 → FAILED + 재고 AVAILABLE / 결제와 배치 동시 실행 → 한쪽만 성공

---

### Step 6: DeliveryService + MockDeliveryGateway
- `startShipping(orderId)` — Order IN_DELIVERY + `inventoryService.sell()` (RESERVED→SOLD)
- `completeDelivery(deliveryId)` — Order DELIVERED + `Delivery.deliveredAt` 기록 (V8, 반품 7일·구매확정 기준)
- 반품 (1차 전용):
  1. 반품 요청: 주문 DELIVERED → RETURN_REQUESTED, 배송 RETURN_REQUESTED — **배송완료 후 7일 이내만** (넘으면 거절)
  2. 택배 회수 도착 (Mock): 배송 RETURNED만 변경 — 주문/재고/환불은 그대로
  3. BO 반품 검수 (센터 직원):
     - 대상: 배송 RETURNED + 주문 RETURN_REQUESTED (검수 대기 상태 따로 없음)
     - 입력: 정상 / 불량 — 2차 반납 확인 API와 같은 입력값으로 맞춤 (Enum 위치는 구현 시 결정)
     - 정상 → 재고 SOLD→AVAILABLE (reason: RESTORED_BY_RETURN) / 불량 → SOLD→UNSELLABLE (reason: UNSELLABLE_BY_RETURN)
     - 공통: 주문 RETURNED + OrderHistory + 환불(`refund()`) — 불량이어도 환불 (고객 과실 판단/반품 거절은 확장 포인트)
  - 반품 지연 알림 없음 (택배사 회수 — 2차 반납 지연 알림과 다름)

### Step 6-1: batch — 구매확정 + 누적 구매금액 (1차 전용)
- 대상: 주문 DELIVERED + `orderType = ONLINE` + `delivery.deliveredAt` 7일 경과 (RETURN_REQUESTED는 자연히 제외)
- 처리: Order PURCHASE_CONFIRMED + OrderHistory → `member.totalPurchaseAmount += 주문 상품 금액` → 100만원 이상이면 VIP
  - 등급은 그 자리에서 재계산, 강등 없음 (구매확정 후엔 반품 불가라 차감할 일 없음)
- **반품 요청 vs batch 동시 처리**: 주문 `@Version`(V4) → 늦은 쪽 낙관적 락 예외 (Step 5-1과 같은 패턴, 벌크 UPDATE 금지)
- **누적액 동시 갱신** (batch vs 2차 현장 결제): `+금액` 원자적 UPDATE 또는 락으로 갱신 손실 방지 — 방식은 구현 시 결정 (사용자 담당)
- 고객 안내: 회원 API 응답에 "반영 예정 금액"(구매확정 전 DELIVERED 주문 합계) → 화면에서 "7일 뒤 반영됩니다"
- 확장 포인트: 고객 직접 구매확정, 연간 등급 갱신

검증: 회원가입 → 로그인 → 상품조회 → 주문 → 결제 → 배송시작(SOLD) → 완료 → 7일 후 구매확정 + 누적 반영 (1차 E2E) / 8일째 반품 요청 거절

---

### Step 7: 부가 기능
- `CartService` — 장바구니 담기/삭제/조회
  - 추후 결정: 결제 만료(FAILED) 후 장바구니로 돌아왔을 때, 담긴 재고 한 벌(`cart_item.inventory_id`)이 이미 팔렸으면 화면 표시/재주문 처리 방법
- `WishlistService` — 찜 추가/삭제/조회

---

## Phase C — 2차 O2O (1차 완성 후)

1. `InventoryService` 2차 메서드 추가 (1차 `reserve()`/`sell()`은 AVAILABLE/RESERVED 기준이라 현장 재고(IN_TRANSIT)에 못 씀)
   - `transit()` — RESERVED→IN_TRANSIT + InventoryLog (reason: IN_TRANSIT_BY_RESERVATION)
   - 현장 판매용 메서드 — IN_TRANSIT→SOLD + InventoryLog (reason: SOLD_BY_FIELD_SALE) / 이름은 구현 시 결정
   - 현장 결제 실패에 `release()` 쓰지 말 것 (AVAILABLE이 되면 차에 있는 옷이 온라인에서 팔림)
2. `ReservationService`:
   - 예약 생성 전: 출장지 우편번호 검증 (서울만 — 앞 두 자리 01~08, 아니면 예외)
   - 예약 생성 전: 찜 목록 중 고른 옷 개수 ≤ `member.grade.maxStylingItems` (일반 5 / VIP 10), 넘으면 예외
   - 예약 생성 = 즉시 CONFIRMED: StylistSchedule BOOKED + 고른 상품마다 AVAILABLE 재고 1벌 `reserve()` + `ReservationItem` 저장
     - 출장비: VIP + `freeTravelUsed = false` → `travelFee = 0` + `freeTravelUsed = true` / 그 외 → `travelFee = 설정값(20,000)`
     - 동시 무료 예약 2건 → `false → true`가 한 번만 성공하도록 (조건부 UPDATE 또는 락)
   - CONFIRMED → CANCELLED: `ReservationItem`으로 재고 찾아 `release()` (RESERVED→AVAILABLE)
     - 무료 예약이었으면: 고객 취소 + 출장 24시간 전까지 → `freeTravelUsed = false` 복구 / 24시간 이내 → 소진 / MANAGER 취소 → 항상 복구
   - CONFIRMED → IN_PROGRESS: `ReservationItem`으로 재고 찾아 `transit()` + `startedAt` 기록
   - IN_PROGRESS → COMPLETED: `completedAt` 기록 / 재고는 이 시점에 안 바꿈 (판매 건은 결제 성공 때 이미 SOLD, 나머지는 IN_TRANSIT 유지)
3. Stylist 계정 인증 (bo-api):
   - Stylist 로그인 엔드포인트 추가 (Admin 로그인 패턴 재사용)
   - Security 설정: Stylist 접근 가능 엔드포인트 분리 (담당 예약 조회 + 현장 결제)
4. 현장 결제 (bo-api, Stylist 계정으로 호출) — 옛 `StylingSessionService` 역할 (V6에서 `styling_session` 삭제)
   - **출장 끝날 때 1회**: 고른 옷 전부 + 출장비를 FIELD 주문 1건으로 결제 (고객 결제 1번)
   - 현장 주문 생성 — 1차 `createOrder()`와 **별도 메서드** (재고를 새로 잡지 않음)
     - 검증: 예약 IN_PROGRESS + 이 예약의 `ReservationItem`에 있는 옷 + 재고 IN_TRANSIT + `returnedAt IS NULL` + 이 예약에 PAID 현장 주문 없음
     - Order(`orderType = FIELD`, `reservationId`, PENDING, 총액 = 옷 합계 + `reservation.travelFee`) + OrderItem + OrderHistory
     - 산 옷 없음 → 출장비만 (OrderItem 없음) / 출장비 0원 + 산 옷 없음 → 주문 안 만듦
   - 결제 → Payment 프로세스 (1차 `pay()` 저장 방식 동일)
     - 성공: Order PAID(끝) + 재고 IN_TRANSIT → SOLD + 누적 구매금액 반영(옷 금액만, 출장비 제외) + 등급 재계산
     - 실패: Payment/Order FAILED + fail_code 저장, **재고는 IN_TRANSIT 그대로** (`release()` 금지)
     - 다시 결제하려면 현장 주문 새로 생성 (주문 1건 = 결제 1건)
   - 미구매 → 주문 만들지 않음, 상태 변경 없음 (IN_TRANSIT 유지, 반납 확인 대기)
     - 미구매를 결제 실패로 기록하지 말 것 (가짜 주문/결제, 결제 실패 통계 오염)
   - 1차 결제대기 만료 배치는 `orderType = ONLINE`만 조회하도록 (Step 5-1)
   - 현장 판매 반품 불가 — 확장 포인트
5. 반납 확인 (bo-api, 센터 직원이 실물 검수 후 호출):
   - 대상: 예약 COMPLETED + 재고 IN_TRANSIT + `ReservationItem.returnedAt IS NULL` (가는 중인 재고는 거부)
   - 정상 → IN_TRANSIT→AVAILABLE (reason: RESTORED_BY_RETURN_CHECK 반납 정상)
   - 불량 → IN_TRANSIT→UNSELLABLE (reason: UNSELLABLE_BY_RETURN_CHECK 반납 불량)
   - 공통: `ReservationItem.returnedAt` 기록
   - 스타일리스트 계정으로는 호출 불가 — 옷을 가진 사람이 직접 반납 완료 처리하지 않음 (넘겨주는 사람 ≠ 확인하는 사람)
   - 입력값(정상/불량)은 1차 반품 검수 API와 같게
6. batch: 반납 지연 알림
   - `reservation.completedAt` 후 일정 시간(예: 24시간) 지나도 IN_TRANSIT + `returnedAt IS NULL`인 재고 → **해당 스타일리스트 + 담당 매니저**(`stylist.managerId`) 알림(Mock)
   - 상태는 바꾸지 않음 (실물 확인은 사람이 해야 함)
   - 같은 옷이 매일 다시 잡혀 알림이 반복되는 것(중복 알림) 처리는 구현 시 결정
7. 스타일리스트 등록/수정 (bo-api, MANAGER):
   - 담당 매니저 `managerId` 필수 — MANAGER 권한 Admin만 지정 가능 (검증)
   - 매니저 퇴사/팀 이동 시 수정 → 이후 알림은 새 매니저에게
- 확장 포인트 (범위 밖): UNSELLABLE 이후 처리(세탁/수선/폐기), 분실 처리, 현장 판매 반품(예: 결제 후 7일 이내 — 1차 반품 검수 재사용), 미구매 사유 통계, 단계별 알림(24시간 스타일리스트 → 48시간 매니저), 지역 관리, 매니저 소속 변경 이력, 일반회원 직전 취소 수수료, 출장비 미수금 처리

---

## 핵심 참조 파일

- Entity: `domain/src/main/java/com/stylink/domain/entity/`
- Enum: `domain/src/main/java/com/stylink/domain/enums/`
- ErrorCode: `common/src/main/java/com/stylink/common/exception/ErrorCode.java`
- BusinessException: `common/src/main/java/com/stylink/common/exception/BusinessException.java`
- JwtProvider: `common/src/main/java/com/stylink/common/security/JwtProvider.java`
- 설계 문서: `docs/01-state-diagram/`, `docs/02-domain-model/`, `docs/03-erd/`
