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

### A-1. @Version 확인 (Inventory만 유지)
- `Inventory` 엔티티에 이미 `@Version` 적용되어 있음 → 그대로 유지
- 나머지 Entity에는 추가 안 함
- 근거: 동시 주문이 몰리는 재고에만 낙관적 락이 필요. Order/Payment 등은 한 사용자가 순차 처리하는 흐름이라 의도적으로 제외 → 면접 질문 "왜 Inventory만인가?"에 설계 근거로 답변 가능

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

// SOLD → AVAILABLE (반품 완료)
void restore(Long inventoryId)
  // InventoryLog(SOLD→AVAILABLE, ORDER_RETURNED) 저장
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
- `pay(orderId)` — MockPgGateway.charge() → 성공: Order PAID / 실패: Order FAILED + release()
- `refund(paymentId)` — MockPgGateway.cancel() 호출

검증: 결제 성공 플로우 / 결제 실패 → Inventory AVAILABLE 복구

---

### Step 6: DeliveryService + MockDeliveryGateway
- `startShipping(orderId)` — Order IN_DELIVERY + `inventoryService.sell()` (RESERVED→SOLD)
- `completeDelivery(deliveryId)` — Order DELIVERED
- 반품: RETURN_REQUESTED→RETURNED + `inventoryService.restore()` (SOLD→AVAILABLE)

검증: 회원가입 → 로그인 → 상품조회 → 주문 → 결제 → 배송시작(SOLD) → 완료 (1차 E2E)

---

### Step 7: 부가 기능
- `CartService` — 장바구니 담기/삭제/조회
- `WishlistService` — 찜 추가/삭제/조회

---

## Phase C — 2차 O2O (1차 완성 후)

1. `InventoryService.transit()` 추가 — RESERVED→IN_TRANSIT + InventoryLog
2. `ReservationService`:
   - 예약 생성 = 즉시 CONFIRMED: StylistSchedule BOOKED + `reserve()`
   - CONFIRMED → IN_PROGRESS: `transit()`
   - IN_PROGRESS → COMPLETED: 현장 판매 건 `sell()` / 미구매 건 `release()`
3. Stylist 계정 인증 (bo-api):
   - Stylist 로그인 엔드포인트 추가 (Admin 로그인 패턴 재사용)
   - Security 설정: Stylist 접근 가능 엔드포인트 분리 (담당 세션 조회 + 구매확정)
4. `StylingSessionService` (bo-api, Stylist 계정으로 호출):
   - 구매확정 상품 → `OrderService.createOrder()` → Payment 프로세스 (1차 동일)
   - 미구매 상품 → `InventoryService.release()`

---

## 핵심 참조 파일

- Entity: `domain/src/main/java/com/stylink/domain/entity/`
- Enum: `domain/src/main/java/com/stylink/domain/enums/`
- ErrorCode: `common/src/main/java/com/stylink/common/exception/ErrorCode.java`
- BusinessException: `common/src/main/java/com/stylink/common/exception/BusinessException.java`
- JwtProvider: `common/src/main/java/com/stylink/common/security/JwtProvider.java`
- 설계 문서: `docs/01-state-diagram/`, `docs/02-domain-model/`, `docs/03-erd/`
