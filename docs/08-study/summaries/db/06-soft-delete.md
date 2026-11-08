# SOFT DELETE

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
HARD DELETE(물리 삭제)의 문제
  복구 불가 / FK 충돌 / 법적 보관 의무 / 삭제 시점 불명
  ↓
SOFT DELETE - is_deleted 방식
  UPDATE SET is_deleted = TRUE
  장점: 간단, 즉시 복구
  단점: 삭제 시점 모름, 기간 정책 적용 불가
  ↓
SOFT DELETE - deleted_at 방식 (실무 권장)
  UPDATE SET deleted_at = NOW()
  장점: 삭제 시점 추적, 기간별 분석, 배치 영구 삭제
  주의: JOIN 시 모든 테이블에 IS NULL 조건 추가 필수
  ↓
[실무 관점] 삭제 전략 3종 선택
  STATUS → 생명주기가 복잡한 핵심 데이터 (상품, 주문)
  SOFT DELETE → 단순 존재/복구 목적 (회원, 댓글)
  HARD DELETE → 보관 가치 없는 데이터 (장바구니, 로그)
  ↓
이력 테이블과의 관계
  FK 없는 테이블: HARD DELETE + 이력 보관 (용량 최적화)
  FK 있는 테이블: SOFT DELETE + 이력 보관 (무결성 유지)
  ↓
인덱스 설계 주의
  deleted_at 을 인덱스 선두 컬럼으로 두지 말 것
  UNIQUE 컬럼 충돌 → 탈퇴 시 식별자 변경으로 해결
```

---

## HARD DELETE의 문제점

### 복구 불가

`DELETE` 로 삭제한 데이터는 물리적으로 사라진다. DB 백업·로그 복구는 가능하지만 DBA 개입 + 시간 필요.

### 연관 데이터 처리 복잡

FK 제약이 있으면 부모 삭제 시 자식 처리 방법이 3가지뿐이다:

| 방법 | 결과 | 문제 |
|------|------|------|
| 자식 먼저 삭제 | 주문 데이터 소실 | 매출 분석 불가 |
| ON DELETE CASCADE | 연관 데이터 자동 삭제 | 대량 삭제 위험 |
| ON DELETE SET NULL | 주문의 member_id = NULL | "이 주문 누가 했나?" 불명 |

### 법적·비즈니스 요구사항

- 전자상거래법: 거래 기록 **5년 보관** 의무
- 금융·의료: 감사(Audit) 추적 가능해야 함
- 탈퇴 회원 포함 매출 분석 필요

### 삭제 시점 불명

`DELETE` 후에는 "언제 탈퇴했나요?"에 대답 불가.

---

## SOFT DELETE - is_deleted 방식

DELETE 대신 UPDATE로 "삭제됨" 표시만 남기는 방식.

```sql
ALTER TABLE member ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

-- 삭제
UPDATE member SET is_deleted = TRUE WHERE member_id = 3;

-- 활성 데이터 조회
SELECT * FROM member WHERE is_deleted = FALSE;

-- 복구
UPDATE member SET is_deleted = FALSE WHERE member_id = 3;
```

**장점**: 구현 간단, UPDATE 한 번으로 즉시 복구, FK 관계 유지하며 삭제 처리, 분석에 삭제 데이터 포함 가능

**단점**:

| 한계 | 설명 |
|------|------|
| 삭제 시점 불명 | created_at은 가입일 → 탈퇴일 알 수 없음 |
| 보관 기간 정책 적용 불가 | "탈퇴 1년 후 영구 삭제" 불가 |
| 복구 이력 추적 불가 | 탈퇴→복구→재탈퇴 이력 없음 |

---

## SOFT DELETE - deleted_at 방식

`is_deleted` 의 모든 한계를 해결. **하나의 컬럼으로 삭제 여부 + 삭제 시점 동시 관리**.

```sql
deleted_at DATETIME DEFAULT NULL
-- NULL = 활성, 값이 있으면 = 해당 시점에 삭제됨
```

```sql
-- 삭제
UPDATE member SET deleted_at = NOW() WHERE member_id = 3;

-- 활성 데이터 조회
SELECT * FROM member WHERE deleted_at IS NULL;

-- 삭제된 데이터만 조회
SELECT * FROM member WHERE deleted_at IS NOT NULL;

-- 복구
UPDATE member SET deleted_at = NULL WHERE member_id = 3;
```

### 삭제 시점 활용 패턴

**탈퇴 시점 즉시 조회**:
```sql
SELECT username, deleted_at FROM member WHERE member_id = 3;
-- park  2026-01-20 14:30:00
```

**배치 영구 삭제 (개인정보보호법 대응)**:
```sql
DELETE FROM member
WHERE deleted_at IS NOT NULL
  AND deleted_at < DATE_SUB(NOW(), INTERVAL 1 YEAR);
```

**월별 탈퇴 현황 분석**:
```sql
SELECT DATE_FORMAT(deleted_at, '%Y-%m') AS month, COUNT(*) AS churned_count
FROM member
WHERE deleted_at IS NOT NULL
GROUP BY DATE_FORMAT(deleted_at, '%Y-%m')
ORDER BY month;
```

### 조인 시 주의: 양쪽 모두 IS NULL 확인

```sql
-- 잘못된 예: member만 확인 → 취소된 주문이 포함됨
SELECT * FROM member m JOIN orders o ON m.member_id = o.member_id
WHERE m.deleted_at IS NULL;

-- 올바른 예: 모든 테이블에 IS NULL 조건 추가
SELECT * FROM member m JOIN orders o ON m.member_id = o.member_id
WHERE m.deleted_at IS NULL AND o.deleted_at IS NULL;
```

### 뷰(View)로 실수 방지

```sql
CREATE VIEW active_member AS
SELECT member_id, username, email, created_at
FROM member WHERE deleted_at IS NULL;

CREATE VIEW active_orders AS
SELECT order_id, member_id, product_name, amount, order_date
FROM orders WHERE deleted_at IS NULL;

-- 이후 뷰끼리 조인하면 IS NULL 조건 신경 불필요
SELECT m.username, o.product_name
FROM active_member m JOIN active_orders o ON m.member_id = o.member_id;
```

### is_deleted vs deleted_at 비교

| 항목 | is_deleted | deleted_at |
|------|-----------|-----------|
| 저장 정보 | 삭제 여부만 | 삭제 여부 + 삭제 시점 |
| 활성 조회 | `= FALSE` | `IS NULL` |
| 삭제 시점 | 알 수 없음 | 정확히 알 수 있음 |
| 보관 기간 관리 | 불가 | 가능 |
| 기간별 분석 | 불가 | 가능 |
| 저장 공간 | 1바이트 | 8바이트 |

> 실무에서는 `deleted_at` 방식 권장. 저장 공간 차이보다 삭제 시점 정보의 가치가 훨씬 크다.

---

## SOFT vs HARD vs STATUS

### 핵심 인사이트: 삭제가 아니라 상태 변경

비즈니스에서 "삭제"는 사실 "상태 변경"인 경우가 많다.

**상품**: "삭제" 요청 → 실제로는 판매 중지

```sql
-- deleted_at 방식의 한계: 판매중지/품절/임시중단 구분 불가
-- 이진법 (NULL/값)으로 다양한 상태 표현 불가

-- 올바른 방식: STATUS 컬럼
ALTER TABLE product ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE';
-- ON_SALE / SOLD_OUT / STOP_SALE

UPDATE product SET status = 'STOP_SALE' WHERE product_id = 1;

-- 판매 중인 상품
SELECT * FROM product WHERE status = 'ON_SALE';
-- 관리자: 모든 상태 포함
SELECT * FROM product;  -- 조건 불필요!
```

**주문**: CANCELLED 상태가 이미 "삭제"를 의미

```sql
-- 나쁜 설계: status = 'CANCELLED' + deleted_at 이중 관리
-- 좋은 설계: status = 'CANCELLED' 하나로 충분
```

### 삭제 전략 결정 기준

| 상황 | 전략 | 예시 |
|------|------|------|
| 비즈니스 생명주기가 복잡한 핵심 데이터 | **STATUS 컬럼** | 상품(판매중/품절/중지), 주문(결제대기/완료/취소/환불) |
| 단순 존재/부재, 복구 가능성 필요 | **SOFT DELETE** | 회원(탈퇴), 댓글, 게시글 |
| 보관 가치 없는 데이터 | **HARD DELETE** | 장바구니, API 로그, 알림 |

### 테이블별 전략 예시

| 테이블 | 전략 | 이유 |
|--------|------|------|
| member | SOFT DELETE | 복구 요청, 주문 내역 참조 |
| product | STATUS (실무) / SOFT | 판매 중지/품절 등 다양한 상태 |
| orders | STATUS (실무) / SOFT | 주문 상태가 이미 생명주기 관리 |
| review | SOFT DELETE | 신고 처리 후 복구 가능성 |
| cart_item | HARD DELETE | 임시 데이터 |
| notification | HARD DELETE | 일정 기간 후 불필요 |
| api_log | HARD DELETE | 로그성 데이터 |

> **삭제는 기술이 아니라 정책이다.** 핵심 도메인(주문/결제/정산)은 보통 "상태"로 관리하고, SOFT DELETE는 편하지만 쿼리/인덱스/휴먼에러/용량 비용을 치른다.

---

## SOFT DELETE와 이력 테이블의 관계

### "이력 테이블이 있으면 HARD DELETE 해도 되나요?"

**결론: 테이블의 FK 관계에 따라 다르다. 둘은 대체재가 아니라 보완재.**

| 구분 | FK 없는 테이블 (로그, 단순 설정) | FK 있는 핵심 테이블 (회원, 상품, 주문) |
|------|-------------------------------|--------------------------------------|
| 삭제 전략 | **HARD DELETE + 이력 보관** | **SOFT DELETE(또는 상태) + 이력 보관** |
| 이유 | 용량 절약 + 조회 쿼리 단순화 | FK 제약 → 부모를 HARD DELETE 불가 |
| 이력 테이블 역할 | 삭제 전 마지막 상태 보관 (아카이빙) | 모든 변경 이력 기록 |

**HARD DELETE + 이력 보관 패턴** (FK 없는 경우):
```sql
START TRANSACTION;
-- 1. 이력에 삭제 직전 상태 저장
INSERT INTO config_history (config_id, value, change_type, history_created_at)
SELECT config_id, value, 'DELETE', NOW() FROM config WHERE config_id = 100;
-- 2. 메인 테이블에서 물리 삭제
DELETE FROM config WHERE config_id = 100;
COMMIT;
-- 메인 테이블에는 활성 데이터만 남음 → 조회 빠름, IS NULL 조건 불필요
```

---

## SOFT DELETE와 인덱스 설계

### 잘못된 인덱스 설계

```sql
-- 금지: deleted_at을 선두 컬럼으로
CREATE INDEX idx_deleted_name ON product (deleted_at, name);
```

**이유**: 전체 데이터의 90%+ 가 `NULL` (활성) → 변별력(Cardinality) 극히 낮음. 인덱스 효과 없음.

### 올바른 인덱스 설계

**일반적인 경우**: 기존 비즈니스 인덱스를 그대로 사용. 옵티마이저가 비즈니스 조건으로 먼저 걸러낸 후 `IS NULL` 필터링.

**커버링 인덱스가 필요한 경우**:
```sql
-- 비즈니스 컬럼 선두, deleted_at 후미에 추가
CREATE INDEX idx_name_deleted ON product (name, deleted_at);
-- 테이블 접근 없이 인덱스만으로 deleted_at 확인 가능
```

### UNIQUE 제약조건 충돌 해결

```
1. userA 가입 → 2. userA 탈퇴 (is_deleted=TRUE / deleted_at 설정) → 3. 새로운 userA 재가입 시도
→ Error: Duplicate entry 'userA' for key 'member.uq_login_id'
```

**해결 방법 (추천): 탈퇴 시 식별자 변경**

```sql
UPDATE member
SET deleted_at = NOW(),
    login_id = CONCAT(login_id, '_deleted_', UNIX_TIMESTAMP())
WHERE member_id = 3;
-- userA → userA_deleted_1209381209
-- 원래 userA 공간이 비워져서 재가입 가능
```

**비추천 방법**: `(login_id, deleted_at)` 복합 UNIQUE 인덱스 → NULL 처리 DB별 차이, 재탈퇴 시 관리 복잡.

---

## 면접 Q&A

**Q: SOFT DELETE가 필요한 이유와 HARD DELETE의 문제점은?**
A: HARD DELETE는 데이터를 물리적으로 제거하므로 복구가 사실상 불가능하고, FK 제약으로 인해 연관 데이터 처리가 복잡하다. 또한 전자상거래법에 따른 거래 기록 5년 보관 의무 등 법적 요구사항을 충족하기 어렵고, 탈퇴 시점처럼 "언제" 삭제됐는지 알 수 없다. SOFT DELETE는 실제로 삭제하지 않고 삭제 표시만 남겨 복구와 이력 추적을 모두 가능하게 한다.

**Q: is_deleted와 deleted_at 방식의 차이는?**
A: `is_deleted`는 삭제 여부(TRUE/FALSE)만 저장하므로 삭제 시점을 알 수 없고, "탈퇴 1년 후 영구 삭제" 같은 기간 정책을 적용할 수 없다. `deleted_at`은 삭제 시점을 직접 저장하므로 하나의 컬럼으로 삭제 여부와 시점을 모두 알 수 있다. 배치로 `WHERE deleted_at < DATE_SUB(NOW(), INTERVAL 1 YEAR)` 같은 영구 삭제 정책도 쉽게 구현할 수 있다. 실무에서는 `deleted_at` 방식을 권장한다.

**Q: 모든 테이블에 SOFT DELETE를 적용해야 하는가?**
A: 아니다. 비즈니스 생명주기가 복잡한 상품(판매중/품절/판매중지)이나 주문(완료/취소/환불)은 `deleted_at` 이진 구분보다 `status` 컬럼이 훨씬 적합하다. 장바구니, API 로그처럼 보관 가치 없는 데이터는 HARD DELETE가 맞다. 테이블마다 비즈니스 요구사항에 맞는 전략을 선택해야 한다.

**Q: SOFT DELETE와 인덱스 설계 시 주의점은?**
A: 전체 데이터의 90% 이상이 `deleted_at IS NULL` (활성 상태)이므로, `deleted_at`을 인덱스의 선두 컬럼으로 두면 변별력이 극히 낮아 인덱스 효과가 없다. 비즈니스 키(name, category_id 등)를 선두로 두고, 커버링 인덱스가 필요한 경우에만 `deleted_at`을 마지막에 추가한다. 또한 UNIQUE 컬럼이 있으면 탈퇴 시 `login_id_deleted_타임스탬프` 형태로 변경해 재가입 충돌을 방지해야 한다.

**Q: stylink 프로젝트 적용 포인트는?**
A: `member` 테이블은 탈퇴 복구 가능성과 주문 내역 참조 때문에 `deleted_at` SOFT DELETE가 적합하다. `product` 테이블은 재고 상태(AVAILABLE/RESERVED/IN_TRANSIT/SOLD)가 이미 존재하므로 `deleted_at`보다 `status` 컬럼으로 판매 중단 처리하는 것이 더 명확하다. `order`, `reservation` 등 핵심 트랜잭션 테이블도 상태가 이미 생명주기를 표현하므로 `status`로 관리하고 `deleted_at`은 추가하지 않는다. `cart_item` 같은 임시 데이터는 HARD DELETE. 탈퇴 시 이메일/전화번호에 UNIQUE 제약이 있으면 `_deleted_타임스탬프` 변경 처리가 필요하다.
