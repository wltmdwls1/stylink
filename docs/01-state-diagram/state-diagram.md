# 상태 다이어그램 (State Diagram)

> 각 도메인의 상태 전이 흐름을 정의합니다.
> 필드 정의는 ERD 문서에서 다룹니다.

---

## 공통 정책

### 네이밍 규칙
- 결과 상태 → 단어 하나 : `PAID`, `DELIVERED`, `SOLD`, `CONFIRMED`
- 과정 상태 → 언더스코어 최대 1개 : `IN_PROGRESS`, `IN_PREPARATION`, `RETURN_REQUESTED`
- 도메인별 독립 네이밍 적용 (억지로 통일하지 않음)
- 모든 상태값은 **Enum + 한글 설명** 방식으로 관리
  ```java
  public enum OrderStatus {
      PENDING("결제대기"),
      PAID("결제완료"),
      ...
      private final String description;
  }
  ```

### 취소 제한 정책
| 도메인 | 취소 불가 기준 | 이후 처리 |
|--------|--------------|----------|
| 주문(Order) | `SHIPPED` 이후 | 반품만 가능 |
| 예약(Reservation) | `IN_PROGRESS` 이후 | 취소 불가 |
| 결제(Payment) | `SHIPPED` 이전 → 취소 / 이후 → 환불 | CANCEL / REFUND 구분 |

### 기타
- 물류센터는 단일 센터로 가정
- 결제이탈(ABANDONED) 상태 미사용 → 결제실패(FAILED)로만 처리
- **SHIPPED** = 1차 온라인 주문의 배송 시작 (Order/Delivery 상태)
- **TRANSFER** = 2차 출장 서비스의 코디네이터 재고 이동 (Inventory 상태)

---

## 1. 재고 (Inventory) ⭐ 핵심

> 1차(온라인 주문), 2차(출장 서비스) 모두 적용

```
                    주문/예약 발생
AVAILABLE ──────────────────────────────────► HOLD
    ▲                                           │
    │  취소 / 만료 / 결제실패                     │ 코디 출장 이동 (2차)
    │◄───────────────────────────────────────   │
    │                                           ▼
    │                                       TRANSFER
    │                                           │
    │  예약 취소 (이동 후)                        │ 현장 판매 완료 / 배송 완료
    │◄───────────────────────────────────────   │
    │                                           ▼
    │              반품 완료                   SOLD
    │◄──────────────────────────────────────────│
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `AVAILABLE` | 판매가능 | 주문/예약 가능한 정상 재고 |
| `HOLD` | 선점됨 | 주문 또는 예약으로 선점된 재고 (1차/2차 공통) |
| `TRANSFER` | 이동중 | 코디네이터 출장을 위해 이동 중인 재고 **(2차 전용)** |
| `SOLD` | 판매완료 | 온라인 주문 또는 현장 판매로 최종 판매 완료 |

**정책**
- HOLD 상태 일정 시간 초과 시 → 자동으로 AVAILABLE 복구 (배치 처리)
- TRANSFER 중 예약 취소 시 → 이동된 위치 기준으로 AVAILABLE 복구
- 동시 주문 발생 시 → 한 건만 HOLD 성공, 나머지는 실패 처리 (동시성 제어)
- 반품 완료 시 → SOLD → AVAILABLE 복구

---

## 2. 주문 (Order)

> 1차 온라인 쇼핑몰 중심

```
PENDING ──결제요청──► PAID ──준비──► IN_PREPARATION ──출고──► SHIPPED ──완료──► DELIVERED
   │                   │                                         │                  │
   │ 결제실패           │ 취소 가능                                │ 취소불가           │ 반품요청
   ▼                   ▼                                         ▼                  ▼
FAILED             CANCELLED                               (취소 불가)        RETURN_REQUESTED
                                                                                    │
                                                                              반품완료│
                                                                                    ▼
                                                                                RETURNED
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `PENDING` | 결제대기 | 주문 생성 완료, 결제 대기 중 |
| `PAID` | 결제완료 | 결제 완료 |
| `IN_PREPARATION` | 상품준비중 | 상품 준비 중 |
| `SHIPPED` | 배송중 | 배송 시작 → 이후 취소 불가 |
| `DELIVERED` | 배송완료 | 배송 완료 |
| `FAILED` | 결제실패 | 결제 실패 |
| `CANCELLED` | 주문취소 | 주문 취소 (SHIPPED 이전만 가능) |
| `RETURN_REQUESTED` | 반품요청 | 배송 완료 후 반품 요청 |
| `RETURNED` | 반품완료 | 반품 완료 → 재고 AVAILABLE 복구 + 환불 처리 연계 |

**정책**
- PENDING → 결제 실패 시 재고 HOLD 해제 (AVAILABLE 복구)
- PAID, IN_PREPARATION 상태에서만 취소 가능
- **SHIPPED 이후 취소 불가** → DELIVERED 후 반품 요청만 가능
- 반품 완료 시 → 재고 AVAILABLE 복구 + 환불(REFUND) 처리

---

## 3. 예약 (Reservation)

> 2차 O2O 출장 스타일링 서비스

```
PENDING ──확정──► CONFIRMED ──코디배정──► ASSIGNED ──출장시작──► IN_PROGRESS ──완료──► COMPLETED
   │                  │                     │                        │
   │ 취소              │ 취소                 │ 취소                    │ 취소불가
   ▼                  ▼                     ▼                        ▼
CANCELLED          CANCELLED            CANCELLED              (취소 불가)
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `PENDING` | 예약신청 | 고객 예약 신청 완료, 확정 대기 |
| `CONFIRMED` | 예약확정 | 예약 확정, 재고 HOLD 처리 |
| `ASSIGNED` | 코디배정완료 | 코디네이터 배정 완료, 재고 TRANSFER |
| `IN_PROGRESS` | 진행중 | 코디네이터 출장 중 → 이후 취소 불가 |
| `COMPLETED` | 완료 | 코디 세션 종료 (개별 재고별 판매/미판매 처리) |
| `CANCELLED` | 취소 | 예약 취소 (IN_PROGRESS 이전만 가능) |

**정책**
- CONFIRMED 시점 → 재고 HOLD 처리
- ASSIGNED 시점 → 재고 TRANSFER 처리
- **IN_PROGRESS 이후 취소 불가** (현장 진행 중)
- COMPLETED = 코디 세션 종료를 의미 (모든 상품 판매 완료가 아님)
  - 구매 확정 상품 → 현장 주문 생성 → 재고 SOLD
  - 미구매 상품 → 재고 AVAILABLE 복구
- 취소 시 재고 복구
  - CONFIRMED 취소 → HOLD 해제 → AVAILABLE
  - ASSIGNED 취소 → TRANSFER 해제 → AVAILABLE (이동된 위치 기준)

---

## 4. 회원 (Member)

> 세 가지 속성으로 독립 관리

### 4-1. 회원 상태 (status)
```
ACTIVE ──탈퇴──► WITHDRAWN
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `ACTIVE` | 정상 | 정상 이용 가능 |
| `WITHDRAWN` | 탈퇴 | 탈퇴 처리 |

### 4-2. 인증 상태 (authStatus)
```
UNVERIFIED ──본인인증(Mock)──► VERIFIED
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `UNVERIFIED` | 인증대기 | 가입 완료, 본인인증 미완료 |
| `VERIFIED` | 인증완료 | 전화번호 기반 본인인증 완료 (Mock 처리) |

### 4-3. 회원 등급 (grade)
```
NORMAL ──누적구매 100만원 이상──► VIP
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `NORMAL` | 일반회원 | 기본 등급 |
| `VIP` | VIP | 누적 구매 100만원 이상 달성 |

**정책**
- 본인인증은 전화번호 기반 Mock 처리
- VIP 등급은 주문 완료 시점 체크 또는 배치 처리
- 탈퇴 시 개인정보 처리 정책 별도 정의 필요
- UNVERIFIED 상태에서도 로그인은 가능하나, 주문/예약 등 핵심 기능 제한

---

## 5. 결제 (Payment)

> Mock 기반 처리 / 1차(온라인) 중심

```
PENDING ──결제요청──► SUCCESS
   │
   │ 실패
   ▼
FAILED

[SHIPPED 이전 - 취소]
SUCCESS ──취소요청──► CANCEL_REQUESTED ──취소완료──► CANCELLED

[SHIPPED 이후 - 환불]
SUCCESS ──환불요청──► REFUND_REQUESTED ──환불완료──► REFUNDED
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `PENDING` | 결제대기 | 결제 요청 중 |
| `SUCCESS` | 결제완료 | 결제 승인 완료 |
| `FAILED` | 결제실패 | 결제 실패 → 주문 FAILED + 재고 HOLD 해제 |
| `CANCEL_REQUESTED` | 취소요청 | 취소 요청 중 (SHIPPED 이전) |
| `CANCELLED` | 취소완료 | 결제 취소 완료 |
| `REFUND_REQUESTED` | 환불요청 | 환불 요청 중 (SHIPPED 이후 반품) |
| `REFUNDED` | 환불완료 | 환불 완료 |

**정책 (Mock 기준)**
- **SHIPPED 이전** → 취소(CANCEL) 처리
- **SHIPPED 이후** → 환불(REFUND) 처리 (취소와 명확히 구분)
- 결제 실패 시 → 주문 FAILED + 재고 HOLD 해제 (전체 롤백)

---

## 6. 배송 (Delivery)

> Mock 기반 처리 / 1차(온라인) 중심

```
READY ──배송시작──► SHIPPED ──완료──► DELIVERED
                                         │
                                         │ 반품요청
                                         ▼
                                  RETURN_REQUESTED ──반품완료──► RETURNED
```

| 상태 | 한글명 | 설명 |
|------|--------|------|
| `READY` | 배송준비 | 배송 준비 중 |
| `SHIPPED` | 배송중 | 배송 시작 |
| `DELIVERED` | 배송완료 | 배송 완료 |
| `RETURN_REQUESTED` | 반품요청 | 반품 요청 |
| `RETURNED` | 반품완료 | 반품 완료 → Order RETURNED + 재고 AVAILABLE 복구 + 환불 처리 연계 |

**정책 (Mock 기준)**
- 배송 상태는 Mock으로 순차 변경 시뮬레이션
- RETURNED 시 → Order 상태 변경 + 재고 복구 + 환불 처리 연계

---

## 상태 전이 요약

| 도메인 | 핵심 상태 흐름 | 취소 제한 | 오픈 단계 |
|--------|-------------|----------|----------|
| 재고 | AVAILABLE → HOLD → TRANSFER → SOLD | 반품 시 AVAILABLE 복구 | 1차/2차 공통 |
| 주문 | PENDING → PAID → IN_PREPARATION → SHIPPED → DELIVERED | SHIPPED 이후 취소 불가 | 1차 |
| 예약 | PENDING → CONFIRMED → ASSIGNED → IN_PROGRESS → COMPLETED | IN_PROGRESS 이후 취소 불가 | 2차 |
| 회원 | status / authStatus / grade 독립 관리 | - | 1차/2차 공통 |
| 결제 | PENDING → SUCCESS → CANCELLED / REFUNDED | SHIPPED 기준 취소/환불 구분 | 1차 (Mock) |
| 배송 | READY → SHIPPED → DELIVERED → RETURNED | - | 1차 (Mock) |
