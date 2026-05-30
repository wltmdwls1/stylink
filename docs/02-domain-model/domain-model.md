# 도메인 모델 정의서 (Domain Model)

> 각 도메인의 책임 범위와 경계를 정의합니다.
> 상세 필드는 ERD 문서에서 다룹니다.

---

## 도메인 목록

| 도메인 | 한글명 | 오픈 단계 |
|--------|--------|----------|
| Member | 고객 | 1차/2차 공통 |
| Admin | 관리자 | 1차/2차 공통 |
| Product | 상품 | 1차/2차 공통 |
| ProductHistory | 상품 이력 | 1차/2차 공통 |
| Wishlist | 찜 | 1차/2차 공통 |
| Cart | 장바구니 | 1차 |
| CartItem | 장바구니 상품 | 1차 |
| Order | 주문 | 1차 |
| OrderItem | 주문 상품 | 1차 |
| OrderHistory | 주문 이력 | 1차 |
| Inventory | 재고 | 1차/2차 공통 |
| InventoryLog | 재고 변경 이력 | 1차/2차 공통 |
| Payment | 결제 | 1차/2차 공통 (Mock) |
| Delivery | 배송 | 1차 (Mock) |
| Stylist | 스타일리스트 | 2차 |
| StylistSchedule | 스타일리스트 가능 일정 | 2차 |
| Reservation | 예약 | 2차 |
| StylingSession | 스타일링 세션 | 2차 |
| OutboundApiLog | 외부 API 요청 로그 | 1차/2차 공통 |
| InboundApiLog | 외부 API 수신 로그 | 1차/2차 공통 |

---

## 1차 오픈 도메인

### Member (고객)
**책임:** 고객 계정 및 인증/등급 관리
- 회원가입, 로그인, 본인인증(Mock) 처리
- 회원 상태(status), 인증 상태(authStatus), 등급(grade) 독립 관리
- fo-api를 통해 접근

**주요 관계:**
- `Wishlist` 소유
- `Cart` 소유
- `Order` 생성
- `Reservation` 신청 (2차)

---

### Admin (관리자)
**책임:** 관리자 계정 및 권한 관리
- bo-api를 통해 접근
- 권한에 따라 접근 가능한 기능 범위 제한

**권한 구분:**
| 권한 | 한글명 | 접근 범위 |
|------|--------|----------|
| `SUPER_ADMIN` | 최고관리자 | 전체 접근 |
| `MANAGER` | 매니저 | 주문/재고/예약/스타일리스트 배정 관리 |

---

### Product (상품)
**책임:** 상품 식별자 및 변하지 않는 기본 정보 관리
- 상품 ID, 카테고리 등 변하지 않는 정보만 보유
- 가격, 상품명, 모델코드 등 변경 가능한 정보는 `ProductHistory`에서 관리
- 상품 등록/수정은 bo-api(Admin)만 가능
- 상품 조회는 fo-api(Member) 가능

**주요 관계:**
- `ProductHistory` 보유 (1:N)
- `Inventory` 와 연결
- `Wishlist` 에 담김
- `CartItem` 에 포함
- `OrderItem` 에 포함

---

### ProductHistory (상품 이력)
**책임:** 상품 정보 변경 이력 관리 (SCD Type 2 패턴)
- 가격, 상품명, 모델코드, 이미지 등 변경 가능한 정보 버전 관리
- 판매시작일 / 판매종료일 / 현재여부(Y/N)로 현재 상품 정보 식별
- 현재여부 = Y인 레코드가 현재 유효한 상품 정보
- 정보 변경 시 기존 레코드 종료일 업데이트 + 새 레코드 생성

**이력 구조 예시:**
```
ID | 상품명   | 가격   | 판매시작일   | 판매종료일   | 현재여부
1  | 린넨셔츠 | 39,000 | 2026-03-01  | 2026-04-01  | N
2  | 린넨셔츠 | 42,000 | 2026-04-02  | 9999-12-31  | Y  ← 현재
```

**주요 관계:**
- `Product` 에 속함 (N:1)

---

### Wishlist (찜)
**책임:** 고객의 관심 상품 관리
- 고객이 관심 있는 상품을 저장하는 기능 (1차/2차 공통)
- 1차: 온라인 쇼핑몰에서 관심 상품 저장 용도
- 2차: 스타일링 예약 시 찜 목록 기반으로 스타일리스트가 상품 준비 → 예약 트리거 역할

> 찜(Wishlist)과 장바구니(Cart)는 다른 개념
> - 찜: 관심 저장 (구매 의사 미확정)
> - 장바구니: 구매 직전 단계 (구매 의사 확정)

**주요 관계:**
- `Member` 소유
- `Product` 참조
- `Reservation` 에서 찜 목록 참조 (2차)

---

### Cart (장바구니)
**책임:** 고객의 구매 예정 상품 임시 저장
- 고객이 구매하려는 상품을 담아두는 공간
- 주문 생성 시 Cart → Order 전환
- 주문 완료 후 해당 CartItem 삭제

**주요 관계:**
- `Member` 소유 (1:1)
- `CartItem` 포함 (1:N)

---

### CartItem (장바구니 상품)
**책임:** 장바구니 내 개별 상품 관리
- 담은 상품, 수량, 옵션 정보 관리

**주요 관계:**
- `Cart` 에 속함 (N:1)
- `Product` 참조

---

### Order (주문)
**책임:** 주문 생성 및 상태 관리
- 주문 단위의 전체 흐름 관리 (PENDING → DELIVERED)
- 하나의 주문번호로 전체 진행 상황 추적
- IN_DELIVERY 이후 취소 불가

**주요 관계:**
- `Member` 가 생성
- `OrderItem` 포함 (1:N)
- `OrderHistory` 로 이력 관리
- `Payment` 와 1:1
- `Delivery` 와 1:1

---

### OrderItem (주문 상품)
**책임:** 주문 내 개별 상품 관리
- 주문 시점의 상품명, 가격, 수량 스냅샷 저장
- `ProductHistory`의 현재 유효 레코드 기준으로 스냅샷 생성
- 상품 정보가 나중에 변경되어도 주문 당시 정보 유지

**주요 관계:**
- `Order` 에 속함 (N:1)
- `Product` 참조
- `Inventory` RESERVED/SOLD 처리 연동

---

### OrderHistory (주문 이력)
**책임:** 주문 상태 변경 이력 관리
- 하나의 주문번호에 대한 전체 진행 상황 기록
- 상태 변경 시마다 이력 적재 (타임스탬프 포함)
- 고객 문의 대응 및 운영 추적 용도

**주요 관계:**
- `Order` 에 속함 (N:1)

---

### Inventory (재고) ⭐ 핵심
**책임:** 상품별 재고 수량 및 상태 관리
- 단순 수량이 아닌 **상태 기반** 재고 관리 (AVAILABLE / RESERVED / IN_TRANSIT / SOLD)
- 동시 주문/예약 발생 시 RESERVED 중복 방지 (동시성 제어)
- 물류센터 단일 가정

**주요 관계:**
- `Product` 와 연결
- `InventoryLog` 로 변경 이력 관리
- `OrderItem` RESERVED/SOLD 연동
- `Reservation` RESERVED/IN_TRANSIT 연동 (2차)

---

### InventoryLog (재고 변경 이력)
**책임:** 재고 상태 변경 이력 관리
- AVAILABLE → RESERVED → IN_TRANSIT → SOLD 등 모든 상태 변경 기록
- 변경 사유, 관련 주문/예약 번호 포함
- 재고 불일치 발생 시 추적 용도

**주요 관계:**
- `Inventory` 에 속함 (N:1)

---

### Payment (결제) - Mock
**책임:** 결제 처리 및 상태 관리 (1차/2차 공통)
- PG(Toss Payments) Mock 기반 결제 처리
- 성공/실패/취소/환불 시나리오 처리
- IN_DELIVERY 이전 → 취소 / IN_DELIVERY 이후 → 환불 구분
- **1차**: 고객이 장바구니 → 주문 후 직접 결제
- **2차**: 매니저가 bo-api로 구매 상품 확정 후 Payment 프로세스 트리거 (PDA 역할 대체)

**주요 관계:**
- `Order` 와 1:1

---

### Delivery (배송) - Mock
**책임:** 배송 상태 관리
- 배송업체 Mock 기반 상태 변경 시뮬레이션
- 반품 요청/완료 처리

**주요 관계:**
- `Order` 와 1:1

---

## 2차 오픈 도메인

### Stylist (스타일리스트)
**책임:** 스타일리스트 프로필 관리
- 경력, 나이, 성별, 포트폴리오 이미지(S3) 등 프로필 정보 관리
- 고객이 예약 전 스타일리스트 프로필 조회 가능
- 등록/수정은 bo-api(MANAGER)만 가능

**주요 관계:**
- `StylistSchedule` 보유 (1:N)
- `Reservation` 에 배정

---

### StylistSchedule (스타일리스트 가능 일정)
**책임:** 스타일리스트 가능 일정 관리
- 스타일리스트별 예약 가능한 날짜/시간 관리
- `is_booked` (boolean)으로 슬롯 예약 여부 관리
- 예약 확정 시 is_booked = true 처리 (동시성 제어 필수 — 중복 예약 방지)
- 예약 취소 시 is_booked = false 복구

**주요 관계:**
- `Stylist` 에 속함 (N:1)
- `Reservation` 과 연결

---

### Reservation (예약)
**책임:** 스타일링 예약 생성 및 상태 관리
- 고객이 스타일리스트와 가능 일정을 선택하면 즉시 자동 확정 (PENDING 없음)
- 확정 시점에 StylistSchedule 슬롯 BOOKED + 찜 목록 기반 재고 RESERVED 처리
- IN_PROGRESS 이후 취소 불가
- 관리자(MANAGER) 취소 시 → CONFIRMED → CANCELLED + 알림(Mock)

**주요 관계:**
- `Member` 가 신청
- `Stylist` 배정
- `StylistSchedule` 연결
- `Wishlist` 기반 상품 목록 참조
- `Inventory` RESERVED/IN_TRANSIT 처리
- `StylingSession` 으로 진행

---

### StylingSession (스타일링 세션)
**책임:** 현장 스타일링 진행 1회 기록 및 판매 관리
- 스타일리스트 출장 현장에서의 스타일링 세션 1회 기록
- 매니저가 bo-api로 상품별 구매/미구매 결정 (PDA 역할 대체)
  - 구매 확정 → 현장 주문(Order) 생성 → Payment 프로세스(PENDING→SUCCESS) → 재고 SOLD
  - 미구매 → 재고 AVAILABLE 복구
- 스타일링 세션 완료 처리

**주요 관계:**
- `Reservation` 에서 시작 (1:1)
- `Order` 생성 (현장 판매 시)
- `Inventory` SOLD / AVAILABLE 처리

---

### OutboundApiLog (외부 API 요청 로그)
**책임:** 우리가 외부 Mock으로 보낸 API 요청 기록
- PG 결제 요청, 배송 요청, 알림 발송, 본인인증 요청 등 기록
- 요청/응답 값 저장으로 Mock API 디버깅 및 흐름 추적 가능
- 실패 시 원인 파악 용도
- 상세 필드는 ERD에서 정의

---

### InboundApiLog (외부 API 수신 로그)
**책임:** 외부 Mock에서 우리에게 들어온 콜백 기록
- 배송 상태 변경 콜백, 알림 수신, 스타일리스트 완료 알림 등 기록
- 추후 트래픽 증가 시 소스별(배송/스타일리스트/알림) 테이블 분리 확장 포인트
- 상세 필드는 ERD에서 정의

---

## 도메인 관계 요약

```
[1차 온라인 쇼핑몰]

Member ──── Wishlist ──────────────── Product ──── ProductHistory
  │                                      │
  ├──── Cart ──── CartItem               Inventory ──── InventoryLog
  │
  └──── Order ──── OrderItem
          │
          ├──── OrderHistory
          ├──── Payment
          └──── Delivery


[2차 O2O 출장 스타일링]

Member ──── Reservation ──── Stylist ──── StylistSchedule
                │
                ├──── Wishlist (찜 목록 참조)
                ├──── Inventory (RESERVED / IN_TRANSIT)
                └──── StylingSession ──── Order (현장 판매)
                                     └── Inventory (SOLD / AVAILABLE)
```

---

## 도메인 경계 원칙
- 각 도메인은 자신의 상태만 직접 변경
- 도메인 간 상태 변경은 Service 레이어에서 조율
- 1차(온라인)와 2차(출장) 도메인은 명확히 분리하되, Inventory는 공통으로 사용
- `ProductHistory`의 현재여부(Y) 레코드가 항상 유일하게 유지되어야 함
