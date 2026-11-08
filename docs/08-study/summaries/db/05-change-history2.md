# 데이터 변경 이력 설계2 — 이력 테이블

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
이전 편 한계: 변경 추적 컬럼만으로는 "이전 값"을 알 수 없음
  ↓
1단계: 이전 값 컬럼 추가 (SCD Type 3)
  → previous_price, price_changed_at
  → 한계: 직전 1개만 보관, 2회 이상 변경 시 과거 소실
  ↓
2단계: 현재 테이블에 이력 함께 저장 (is_current 플래그)
  → UPDATE 대신 새 행 INSERT
  → 한계: 시점 조회 쿼리 복잡, 전체 통계 서브쿼리 필요, 테이블 비대
  ↓
3단계: 유효 기간 추가 (valid_from / valid_to)
  → 범위 조건 WHERE만으로 시점 조회 단순화
  → 한계: 테이블 비대 미해결
  ↓
[실무 정석] 현재 테이블 + 전용 이력 테이블 분리
  → product (현재만) + product_history (전체 이력)
  → 현재 조회 99%는 product 테이블에서 빠르게 처리
  ↓
[추가 최적화] 이력 테이블에도 valid_from/to 추가 (마스터 데이터에만)
  ↓
[특수 케이스] 컬럼 단위 변경 로그 / 공통 이력 테이블 (보조용)
```

---

## 이전 값 컬럼 방식 (SCD Type 3)

이전 값을 현재 테이블에 컬럼으로 추가하는 방식.

```
price          → 현재 가격
previous_price → 이전 가격
price_changed_at → 가격 변경 시점
```

가격 변경 시: `SET previous_price = price, price = 새값`

**장점**: 구현 간단, 조인 없이 한 테이블에서 조회  
**단점**: 직전 값 1개만 보관 → 2회 이상 변경 시 초기 값 소실. 추적 컬럼이 늘면 `previous_xxx` 컬럼이 폭발적으로 증가.

> SCD(Slowly Changing Dimension) Type 3 = 이전 값을 별도 컬럼에 저장하는 방식

**사용 케이스**: 변경이 거의 없고, 직전 값만 알면 되는 데이터

---

## 현재 테이블 이력 관리 (is_current)

UPDATE 대신 새 행을 INSERT하고 `is_current`로 현재/과거 구분.

```sql
-- 이력 구조
history_id BIGINT PK AUTO_INCREMENT   -- 행 고유 ID
product_id BIGINT NOT NULL             -- 비즈니스 상품 ID
is_current BOOLEAN DEFAULT TRUE        -- 현재 유효 여부

-- 인덱스
INDEX idx_product_id (product_id)
INDEX idx_is_current (is_current)
INDEX idx_product_id_created_at (product_id, created_at)
```

**변경 절차**:
1. 기존 행: `SET is_current = FALSE`
2. 새 행: `INSERT` with `is_current = TRUE`

**현재 데이터 조회**: `WHERE is_current = TRUE AND product_id = 1`

### 단점 1: 시점 조회 복잡

단일 상품 특정 시점 조회 (내림차순 + LIMIT 1):
```sql
SELECT * FROM product
WHERE product_id = 1
  AND created_at <= '2026-03-10 23:59:59'
ORDER BY created_at DESC
LIMIT 1;
```

### 단점 2: 전체 통계 서브쿼리 필요

```sql
-- 각 상품별 해당 시점 최신 데이터 찾기
SELECT SUM(p.stock_quantity) AS total_stock
FROM product p
INNER JOIN (
    SELECT product_id, MAX(created_at) AS max_created_at
    FROM product
    WHERE created_at <= '2026-03-10 23:59:59'
    GROUP BY product_id
) latest ON p.product_id = latest.product_id
         AND p.created_at = latest.max_created_at;
```

서브쿼리 + JOIN + 집계 조합 → 성능 최적화 어려움. 상품 10만 개 × 평균 10번 변경 = 100만 행 풀스캔.

### 단점 3: 테이블 비대

현재 조회(99%)와 이력 조회(1%)가 한 테이블에 섞임 → 일반 조회도 이력 데이터 스캔.

---

## 현재 테이블 이력 관리 + 유효 기간

`is_current` 문제를 해결하기 위해 각 행에 유효 기간 명시.

```sql
valid_from DATETIME NOT NULL                              -- 유효 시작
valid_to   DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59' -- 유효 종료 (현재 = 미래 날짜)

INDEX idx_product_id_valid_range (product_id, valid_from, valid_to)
```

**변경 절차**:
1. 기존 행: `SET valid_to = 변경시점, is_current = FALSE`
2. 새 행: `valid_from = 변경시점, valid_to = '9999-12-31'`

**시점 조회 (서브쿼리 없이 단순)**:
```sql
WHERE product_id = 1
  AND '2026-03-10' >= valid_from
  AND '2026-03-10' < valid_to;
```

**전체 통계도 단순**:
```sql
SELECT SUM(stock_quantity)
FROM product
WHERE '2026-03-10' >= valid_from AND '2026-03-10' < valid_to;
```

### is_current와 valid_to 중복 보관 이유 (반정규화)

이론적으로 `valid_to = 9999-12-31`이면 `is_current = TRUE`와 동일 → 중복. 하지만 실무에서 `is_current`를 함께 두는 이유:

1. **가독성**: `WHERE is_current = TRUE`가 `WHERE valid_to = '9999-12-31 23:59:59'` 보다 명확
2. **인덱스 효율**: BOOLEAN 동등비교(`=`)가 범위 검색(`>`)보다 인덱스 효율적
3. **미래 확장성**: 날짜 정책 변경 시 `is_current` 쿼리는 수정 불필요

**미해결**: 단일 테이블 비대 문제 (현재 + 이력 혼재)

---

## 전체 행 스냅샷 이력 테이블 분리 (실무 정석)

현재 테이블과 이력 테이블을 물리적으로 분리.

```sql
-- 현재 데이터 (최신만)
CREATE TABLE product (
    product_id  BIGINT PK AUTO_INCREMENT,
    name, price, stock_quantity, status,
    created_at, created_by, updated_at, updated_by
);

-- 이력 데이터 (전체 변경 이력)
CREATE TABLE product_history (
    history_id         BIGINT PK AUTO_INCREMENT,
    product_id         BIGINT NOT NULL,
    name, price, stock_quantity, status,
    created_at         DATETIME NOT NULL,      -- 상품 생성일 (원본 그대로 복사)
    created_by         VARCHAR(100) NOT NULL,
    history_created_at DATETIME DEFAULT NOW(), -- 이력이 기록된 시점
    history_created_by VARCHAR(100) NOT NULL,
    change_type        VARCHAR(50),
    change_reason      VARCHAR(500),

    INDEX idx_product_id (product_id),
    INDEX idx_history_created_at (history_created_at)
);
```

### 이력 테이블의 두 가지 시간

| 컬럼 | 의미 | 질문 예시 |
|------|------|----------|
| `created_at` | 원본 상품이 처음 생성된 날짜 (변하지 않음) | "이 상품은 언제 등록됐나요?" |
| `history_created_at` | 이 이력 행이 INSERT된 순간 | "가격이 언제 변경됐나요?" |

### 변경 처리 패턴

```sql
-- 1. 이력 테이블에 변경 후 상태 저장
INSERT INTO product_history (..., change_type, change_reason)
VALUES (..., 'PRICE_CHANGE', '봄맞이 할인 이벤트');

-- 2. 현재 테이블 업데이트
UPDATE product SET price = 12000, updated_by = 'admin' WHERE product_id = 1;
```

### 핵심 주의점: 첫 데이터(INSERT)부터 이력 저장

많은 개발자가 "변경 시점부터만 이력 저장"하면 공간을 아낄 수 있다고 생각한다. 하지만 이렇게 하면:

```sql
-- 잘못된 방식: 이력 조회 시 반드시 UNION 필요
SELECT product_id, price FROM product WHERE product_id = 1  -- 현재
UNION ALL
SELECT product_id, price FROM product_history WHERE product_id = 1  -- 과거
ORDER BY updated_at;
```

**올바른 방식**: 등록(INSERT) 시점에도 이력 남기기

```sql
-- 상품 등록
INSERT INTO product (...) VALUES (...);

-- 동시에 이력에도 저장
INSERT INTO product_history (..., change_type, change_reason)
SELECT ..., 'CREATE', '신규 상품 등록' FROM product WHERE product_id = LAST_INSERT_ID();
```

이렇게 하면 `product_history`만 조회해도 탄생부터 현재까지 완벽한 타임라인 확인 가능. 원본 테이블이 실수로 삭제되어도 복원 가능.

---

## 이력 테이블 + 유효 기간

이력 테이블에도 `valid_from`, `valid_to` 추가하면 시점 조회 쿼리가 단순해짐.

분리된 테이블 구조에서는 현재 테이블을 직접 보면 되므로 **`is_current` 컬럼 불필요**.

**유효 기간 적용 기준**:

| 데이터 성격 | 예시 | 유효 기간 |
|------------|------|----------|
| 마스터 데이터 (상태 유지) | 상품, 회원 등급, 카테고리 | **권장** |
| 트랜잭션 데이터 (사건 기록) | 주문, 결제, 로그 | **비권장** |

트랜잭션 데이터는 "1시부터 2시까지 유효했다"는 개념이 어색하고, 양이 많아 UPDATE 비용이 크다.

---

## 전체 행 스냅샷 한계 및 대안

### 한계

- **용량**: 컬럼 1개만 변경해도 전체 행 복사 (20컬럼 × 연 50회 × 10만 상품 = 연 10GB)
- **중복**: 변경 안 된 컬럼도 모두 중복 저장
- **변경 파악**: 이전 행과 비교해야 알 수 있음

### 그럼에도 기본으로 사용하는 이유

> 개발 비용 > 스토리지 비용

- 복원 간단: `SELECT * FROM product_history WHERE history_id = 3`
- 쿼리 단순: 이력 조회, 시점 조회 모두 직관적
- 다양한 분석 가능 (`GROUP BY change_type`, 시점 집계 등)

---

## 컬럼 단위 변경 로그

변경된 컬럼만 저장하는 방식 (Field-Level Tracking).

```sql
CREATE TABLE product_change_log (
    log_id       BIGINT PK AUTO_INCREMENT,
    product_id   BIGINT NOT NULL,
    column_name  VARCHAR(100) NOT NULL,  -- 변경된 컬럼명
    old_value    VARCHAR(1000),          -- 변경 전 값 (모두 문자열)
    new_value    VARCHAR(1000),          -- 변경 후 값
    changed_at   DATETIME DEFAULT NOW(),
    changed_by   VARCHAR(100) NOT NULL,
    change_reason VARCHAR(500)
);
```

**장점**: 용량 절약 (20컬럼 × 3변경 = 60값 → 실제 변경된 3값), 변경 내용 명확

**심각한 단점**:
- 특정 시점 전체 복원: 컬럼마다 개별 쿼리 필요 (컬럼 20개 → 20번 서브쿼리)
- 타입 손실: 모든 값이 VARCHAR → 숫자 연산, 집계 어려움
- 첫 등록 시 모든 컬럼 INSERT 필요

**JSON 변형**: `changes JSON NOT NULL` 컬럼으로 여러 컬럼 한 행에 저장 가능하지만 시점 복원 문제는 동일.

**언제 사용**: 특정 필드 이력만 필요하고 전체 복원이 불필요한 경우, 용량이 감당 불가 수준인 경우

---

## 공통 이력 테이블 (audit_log)

모든 테이블의 변경 이력을 하나의 테이블로 중앙 집중 관리.

```sql
CREATE TABLE audit_log (
    audit_id     BIGINT PK AUTO_INCREMENT,
    table_name   VARCHAR(100) NOT NULL,  -- 변경된 테이블
    record_id    VARCHAR(100) NOT NULL,  -- PK 값 (문자열)
    action       VARCHAR(20) NOT NULL,   -- INSERT / UPDATE / DELETE
    old_data     JSON,                   -- 변경 전 전체 행
    new_data     JSON,                   -- 변경 후 전체 행
    changed_at   DATETIME DEFAULT NOW(),
    changed_by   VARCHAR(100) NOT NULL,
    source_system VARCHAR(50),
    client_ip    VARCHAR(50),
    request_id   VARCHAR(100),           -- MSA 분산 추적용

    INDEX idx_table_record (table_name, record_id),
    INDEX idx_changed_at (changed_at)
);
```

**장점**: 하나의 테이블로 전체 시스템 감사, 특정 사용자의 모든 활동 조회 가능

**단점**: JSON 파싱 필요, 상세 조회 불편, 테이블 빠르게 팽창

**적절한 용도**: 감사(Compliance), 원인 추적, 디버깅  
**비즈니스 중요 데이터**는 전용 이력 테이블 별도 생성 필수

### 실무 주의: 로그와 DB

> 모든 로그를 DB에 남기지 말 것

- **비즈니스 이력** (audit_log, product_history 등) → DB에 저장 (실시간 조회, 트랜잭션 정합성 필요)
- **시스템 로그** (접속 기록, 에러, 디버깅) → **파일** (ELK Stack으로 별도 관리)
- audit_log는 일정 기간(예: 6개월) 후 **아카이빙** 필수 — 계속 쌓으면 DB 장애

---

## 방식별 비교

| 방식 | 과거 값 | 시점 조회 | 구현 | 용량 | 실무 빈도 |
|------|--------|---------|------|------|---------|
| 변경 추적 컬럼만 | X | X | 매우 쉬움 | 최소 | 높음 (기본) |
| 이전 값 컬럼 (SCD3) | 1개만 | X | 쉬움 | 최소 | 낮음 |
| 현재 테이블+is_current | O | 어려움 | 보통 | 많음 | 낮음 |
| 현재 테이블+valid | O | 쉬움 | 보통 | 많음 | 보통 |
| **이력 테이블 분리** | **O** | **어려움** | **보통** | **많음** | **매우 높음** |
| **이력 테이블+valid** | **O** | **쉬움** | **보통** | **많음** | **매우 높음** |
| 컬럼 단위 로그 | O | 매우 어려움 | 높음 | 적음 | 낮음 |
| 공통 이력 테이블 | O | 어려움 | 보통 | 많음 | 보통 (보조) |

**실무 권장 조합**:
1. 모든 테이블 → 기본 변경 추적 컬럼 (`created_at`, `updated_at`, `created_by`, `updated_by`)
2. 중요 테이블 (가격, 상태, 재고, 권한) → 전용 이력 테이블 (전체 행 스냅샷)
3. 전체 시스템 → 공통 이력 테이블 (선택, 감사용)

---

## 면접 Q&A

**Q: 현재 테이블에 이력을 함께 저장하는 방식과 이력 테이블을 분리하는 방식의 차이는?**
A: 현재 테이블에 함께 저장하는 방식은 구현이 단순하지만, 조회 트래픽의 99%를 차지하는 현재 데이터 조회에서도 이력 데이터를 함께 스캔해야 해서 성능이 나쁘다. 이력 테이블을 분리하면 현재 테이블은 항상 가볍고 빠르며, 이력은 필요할 때만 별도 테이블에서 조회한다. 실무에서는 분리 방식이 기본이다.

**Q: 이력 테이블에 첫 INSERT부터 저장해야 하는 이유는?**
A: 변경 시점부터만 이력을 저장하면 이력 테이블만으로 완전한 타임라인을 만들 수 없다. 이력 조회 시 항상 현재 테이블과 UNION해야 하고, 테이블에 컬럼이 많으면 쿼리가 극도로 복잡해진다. 처음 등록 시점부터 `change_type = 'CREATE'`로 이력을 저장하면 이력 테이블 하나만 조회해도 탄생부터 현재까지 완벽한 이력이 나온다. 원본 테이블 데이터가 실수로 삭제되어도 복원이 가능하다.

**Q: 전체 행 스냅샷 방식의 한계와 그럼에도 사용하는 이유는?**
A: 컬럼 하나만 변경되어도 전체 행을 복사하므로 중복 데이터가 발생하고 용량이 많이 필요하다. 그럼에도 실무에서 기본으로 사용하는 이유는 개발 비용이 스토리지 비용보다 훨씬 비싸기 때문이다. 전체 행 스냅샷은 특정 시점 복원이 간단하고, 이력 조회 쿼리가 직관적이며, 다양한 통계 분석이 가능하다. 컬럼 단위 변경 로그는 용량은 절약하지만 시점 복원이 사실상 불가능하고 쿼리가 극도로 복잡해진다.

**Q: `valid_from/valid_to`를 사용해야 하는 경우와 그렇지 않은 경우는?**
A: 마스터 데이터(상품 가격, 회원 등급, 카테고리 등)처럼 데이터가 일정 기간 상태를 유지하고 "특정 시점의 값이 무엇이었나"를 자주 조회해야 할 때 사용한다. 반면 트랜잭션 데이터(주문, 결제, 로그)는 "기간"의 개념이 어색하고 데이터 양이 많아 변경마다 이전 행을 UPDATE하는 비용이 크므로 INSERT만 하는 방식이 유리하다.

**Q: stylink 프로젝트 적용 포인트는?**
A: 재고 상태(AVAILABLE→RESERVED→IN_TRANSIT→SOLD), 예약 상태, 주문 상태는 비즈니스 분쟁 시 이전 값 확인이 필요한 핵심 데이터이므로 전용 이력 테이블(전체 행 스냅샷) 방식이 적합하다. 특히 상품 가격은 "주문 당시 가격이 얼마였나"를 증명해야 하는 상황이 생기므로 `product_history` 테이블을 두고 등록 시점부터 이력을 관리해야 한다. 재고 테이블(`inventory_history`)도 동일하게 적용하면 "취소 시 재고 위치 기준 AVAILABLE 복구" 로직의 근거를 추적할 수 있다.
