# 구현 전 체크리스트

> 각 도메인 구현 시작 전 반드시 읽고 들어갈 것.
> 설계 단계에서 결정한 내용들 — 구현 시 놓치기 쉬운 포인트 모음.

---

## 1. Entity 상태 전이 메서드 패턴

상태를 직접 변경하지 말 것. Entity에 전이 메서드를 두고 규칙을 강제할 것.

```java
// ❌ 금지
order.setStatus(OrderStatus.PAID);

// ✅ 올바른 방식
order.pay();
```

각 메서드에서 현재 상태 검증 후 전이. 잘못된 전이 시 BusinessException 던질 것.

**구현 대상:**
- `Order`: pay() / prepare() / ship() / deliver() / cancel() / requestReturn() / returned()
- `Reservation`: confirm() / start() / complete() / cancel()
- `Inventory`: reserve() / transit() / sell() / restore()

---

## 2. order_number 생성 로직

형식: `ORD` + 날짜 8자리 + 시퀀스 5자리 = 16자리
예) `ORD2024123100001`

- 날짜는 `LocalDate.now()` 기준
- 시퀀스는 당일 주문 건수 기반 (DB에서 당일 max 조회 후 +1)
- 동시 주문 시 중복 방지 필요 → 유니크 제약으로 최종 방어
- OrderService에서 주문 생성 시 자동 발급

---

## 3. 전화번호 암호화 처리 (AES256 + SHA-256)

저장 시:
```
전화번호 원문 → AES256(CBC/GCM) 암호화 → phone 저장
전화번호 원문 → SHA-256 해시           → phone_hash 저장
```

조회/인증 시:
```
입력된 전화번호 → SHA-256 해시 → phone_hash 로 DB 조회
```

화면 표시 시:
```
phone → AES256 복호화 → 원문 반환
```

- Member, Stylist 둘 다 동일하게 적용
- AES256 키는 환경변수로 관리 (`${AES_SECRET_KEY}`)
- 암호화/복호화 유틸 클래스를 common 모듈에 작성

---

## 4. 낙관적 락 예외 처리

Inventory.reserve() 호출 시 동시 요청이 들어오면 `OptimisticLockException` 발생.

```java
try {
    inventory.reserve();
} catch (OptimisticLockException e) {
    throw new BusinessException(ErrorCode.INVENTORY_NOT_AVAILABLE);
}
```

- Service 레이어에서 catch 후 사용자 친화적 메시지로 변환
- 재시도 로직은 구현하지 않음 (실패 즉시 응답)

---

## 5. InventoryLog 기록 시점

Inventory 상태 변경 시 반드시 InventoryLog도 함께 기록할 것.

```java
inventory.reserve();
inventoryLogRepository.save(new InventoryLog(
    inventory.getId(),
    InventoryStatus.AVAILABLE,
    InventoryStatus.RESERVED,
    InventoryChangeReason.RESERVED_BY_ORDER,
    "주문번호: " + order.getOrderNumber()  // description
));
```

- InventoryService 내부에서 상태 변경과 로그 기록을 항상 함께 처리
- description은 선택이지만 주문번호/예약번호 등 맥락 정보 넣으면 추적에 유리

---

## 6. append-only 테이블 주의사항

`BaseLogEntity` 사용 테이블은 UPDATE 금지.

- `ProductHistory` — 상품 변경 시 INSERT만
- `OrderItem` — 주문 생성 시 INSERT만
- `OrderHistory` — 상태 변경 시 INSERT만
- `InventoryLog` — 상태 변경 시 INSERT만
- `Wishlist` — 찜 추가 시 INSERT만 (삭제는 DELETE)

---

## 7. 트랜잭션 경계 (사용자 담당)

핵심 흐름은 단일 트랜잭션으로 묶을 것.

- 주문 생성: 재고 RESERVED + Order 생성 + OrderHistory 기록
- 결제 완료: Payment SUCCESS + Order PAID + OrderHistory 기록
- 결제 실패: 전체 롤백 → 재고 자동 복구
- 반품 완료: 재고 AVAILABLE 복구 + 환불 처리 연계

부가 작업(알림, 로그)은 트랜잭션 밖에서 처리.
