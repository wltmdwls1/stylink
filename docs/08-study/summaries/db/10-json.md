# 동적 설계 - JSON 컬럼

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
EAV의 한계 (쿼리 복잡, 타입 불안전, 인덱스 어려움)
  ↓
JSON 컬럼 등장 (MySQL 5.7.8+)
  스키마 없이 동적 속성 저장
  EAV처럼 테이블 분리 불필요 → 같은 행에 JSON으로 보관
  ↓
JSON 함수로 조회/수정
  JSON_VALUE, JSON_EXTRACT(->>), JSON_SET, JSON_ARRAYAGG
  ↓
Generated Column + 함수 인덱스로 성능 확보
  자주 검색하는 속성만 가상 컬럼으로 인덱싱
  ↓
선택 기준
  완전 고정 속성 → 일반 컬럼
  동적·희소 속성 → JSON 컬럼 (EAV 대신)
  속성 수백 개·복잡 검색 → EAV or NoSQL 검토
```

---

## JSON 컬럼이란

관계형 DB에서 하나의 컬럼에 JSON 문서(객체·배열)를 저장하는 방식.  
MySQL 5.7.8부터 네이티브 JSON 타입 지원 → 내부적으로 바이너리 포맷으로 저장해 파싱 최적화.

### 전통 방식 vs JSON 컬럼

| 비교 항목 | 전통 컬럼 방식 | EAV 방식 | JSON 컬럼 |
|-----------|--------------|---------|-----------|
| 스키마 변경 | ALTER TABLE 필요 | INSERT로 추가 가능 | INSERT/UPDATE로 추가 가능 |
| 쿼리 단순성 | 단순 | 복잡 (서브쿼리) | 중간 (JSON 함수) |
| 타입 안전성 | DB 레벨 보장 | 불가 | 부분 가능 (CHECK 제약) |
| 인덱싱 | 컬럼 인덱스 직접 | attr_name + attr_value 인덱스 | Generated Column 인덱스 |
| 테이블 수 | 많음 | 많음 | 적음 |

---

## 기본 사용법

### 테이블 정의

```sql
CREATE TABLE product (
    product_id  BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    price       INT NOT NULL,
    attributes  JSON,           -- 동적 속성
    created_at  DATETIME NOT NULL
);
```

### 데이터 삽입

```sql
INSERT INTO product (name, price, attributes, created_at) VALUES
('갤럭시 S24', 1200000,
 JSON_OBJECT(
     'screen_size', '6.2인치',
     'ram',         '12GB',
     'storage',     '256GB',
     'supports_5g', true
 ),
 NOW()),
('아이폰 15', 1350000,
 '{"screen_size": "6.1인치", "ram": "6GB", "supports_5g": true}',
 NOW());
```

---

## JSON 조회 함수

### JSON_EXTRACT / ->> 연산자

```sql
-- JSON_EXTRACT: JSON 타입 반환
SELECT JSON_EXTRACT(attributes, '$.ram') FROM product WHERE product_id = 1;
-- 결과: "12GB" (따옴표 포함)

-- ->> 연산자 (JSON_UNQUOTE + JSON_EXTRACT): 문자열 반환
SELECT attributes->>'$.ram' FROM product WHERE product_id = 1;
-- 결과: 12GB (따옴표 없음) → WHERE 조건에 유리
```

### 조건 검색

```sql
-- ram이 12GB인 상품
SELECT product_id, name
FROM product
WHERE attributes->>'$.ram' = '12GB';

-- 5G 지원 상품
SELECT product_id, name
FROM product
WHERE JSON_VALUE(attributes, '$.supports_5g') = 'true';

-- 가격 범위 + JSON 조건 혼합
SELECT product_id, name, price
FROM product
WHERE price < 1300000
  AND attributes->>'$.screen_size' LIKE '6%';
```

### 여러 속성 동시 조회 (EAV 피벗 없이 단순)

```sql
SELECT
    product_id,
    name,
    attributes->>'$.screen_size' AS screen_size,
    attributes->>'$.ram'         AS ram,
    attributes->>'$.storage'     AS storage
FROM product
WHERE attributes->>'$.supports_5g' = 'true';
```

EAV에서 피벗이 필요했던 조회가 JSON에서는 단순 SELECT로 해결된다.

---

## JSON 수정 함수

```sql
-- 기존 값 수정
UPDATE product
SET attributes = JSON_SET(attributes, '$.ram', '16GB')
WHERE product_id = 1;

-- 새 속성 추가
UPDATE product
SET attributes = JSON_SET(attributes, '$.color', '팬텀 블랙')
WHERE product_id = 1;

-- 속성 삭제
UPDATE product
SET attributes = JSON_REMOVE(attributes, '$.color')
WHERE product_id = 1;

-- 여러 속성 동시 수정
UPDATE product
SET attributes = JSON_SET(
    attributes,
    '$.ram',     '16GB',
    '$.storage', '512GB'
)
WHERE product_id = 1;
```

---

## 인덱싱 전략

JSON 컬럼 자체에는 인덱스를 직접 걸 수 없다. → **Generated Column** 사용.

### Generated Column + 인덱스

```sql
ALTER TABLE product
    ADD COLUMN ram VARCHAR(20)
        AS (attributes->>'$.ram') VIRTUAL,
    ADD INDEX idx_ram (ram);
```

```sql
-- 인덱스 활용 (실행계획에서 idx_ram 사용)
SELECT product_id, name
FROM product
WHERE ram = '12GB';
```

**VIRTUAL**: 실제 저장 안 함, 조회 시 계산  
**STORED**: 디스크에 저장, INSERT/UPDATE 비용 증가, 인덱스 적용 가능

자주 검색하는 속성만 선택적으로 인덱싱 → 스토리지 낭비 없이 성능 확보.

---

## JSON_TABLE — JSON을 관계형처럼 조회

```sql
-- JSON 배열을 행으로 펼치기
SELECT p.product_id, p.name, jt.tag
FROM product p,
JSON_TABLE(
    p.attributes,
    '$.tags[*]' COLUMNS (
        tag VARCHAR(50) PATH '$'
    )
) AS jt
WHERE p.product_id = 1;
```

리포팅·집계 등 JSON 데이터를 일시적으로 테이블처럼 다룰 때 유용.

---

## JSON 컬럼 장단점

### 장점

| 항목 | 내용 |
|------|------|
| 스키마 유연성 | ALTER TABLE 없이 새 속성 추가/제거 |
| 쿼리 단순성 | EAV보다 쿼리 훨씬 단순 (서브쿼리 불필요) |
| 테이블 구조 단순화 | 별도 속성 테이블 불필요 |
| 희소 데이터 효율 | 없는 속성은 JSON에 키 없음 → NULL 컬럼 낭비 없음 |
| 부분 업데이트 | JSON_SET으로 특정 키만 수정 가능 |

### 단점

| 항목 | 내용 |
|------|------|
| 직접 인덱싱 불가 | Generated Column 우회 필요 |
| 타입 강제 약함 | 애플리케이션 레벨 검증 필요 |
| 복잡 AND 검색 | 다중 JSON 조건 시 쿼리 가독성 저하 |
| 대용량 JSON | 컬럼 크기 증가, 전체 JSON 파싱 비용 |
| DB 레벨 FK 불가 | JSON 내부 값으로 외래키 제약 걸 수 없음 |

---

## EAV vs JSON 컬럼 선택 기준

| 상황 | 선택 |
|------|------|
| 속성 수가 수십 개 이내 | JSON 컬럼 |
| 속성 검색이 단순 (=, LIKE) | JSON 컬럼 |
| 속성 수가 수백 개 이상 | EAV |
| 속성 간 정규화/메타데이터 관리 필요 | EAV + attribute_definition |
| 동적 폼 빌더 (attr_type, display_order) | EAV |
| 단순 부가 속성 저장 | JSON 컬럼 |

**일반적 권장**: EAV를 고려하는 상황이라면 JSON 컬럼을 먼저 검토.

---

## 하이브리드 설계 (권장 패턴)

```sql
CREATE TABLE product (
    product_id  BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,   -- 핵심 (항상 조회)
    price       INT NOT NULL,            -- 핵심 (검색 조건)
    brand       VARCHAR(100),            -- 핵심 (필터링)
    status      VARCHAR(20) NOT NULL,    -- 핵심 (상태 관리)
    attributes  JSON,                    -- 부가 (카테고리별 스펙)
    created_at  DATETIME NOT NULL,

    -- 자주 검색하는 JSON 속성만 Generated Column + 인덱스
    ram         VARCHAR(20) AS (attributes->>'$.ram') VIRTUAL,
    INDEX idx_brand (brand),
    INDEX idx_ram   (ram)
);
```

**규칙**: 자주 조회·검색·정렬하는 속성 → 컬럼 / 동적이고 희소한 부가 속성 → JSON

---

## 면접 Q&A

**Q: MySQL JSON 컬럼을 EAV 대신 쓰는 이유는?**  
A: EAV는 속성마다 행이 생겨 다중 속성 AND 검색에 서브쿼리가 필요하고 쿼리가 복잡해진다. JSON 컬럼은 하나의 행에 모든 속성을 저장하므로 `attributes->>'$.ram' = '12GB'` 처럼 단순한 WHERE 조건으로 검색할 수 있다. 별도 테이블도 필요 없어 JOIN 비용도 없다.

**Q: JSON 컬럼에 인덱스를 어떻게 거나?**  
A: JSON 컬럼 자체에는 인덱스를 직접 걸 수 없고, Generated Column을 만들어 우회한다. `ADD COLUMN ram VARCHAR(20) AS (attributes->>'$.ram') VIRTUAL` 로 가상 컬럼을 정의하고 그 컬럼에 인덱스를 건다. 쿼리에서 Generated Column을 사용하면 인덱스가 적용된다. 자주 검색하는 속성만 선택적으로 인덱싱해서 스토리지 낭비를 줄인다.

**Q: VIRTUAL과 STORED Generated Column 차이는?**  
A: VIRTUAL은 디스크에 저장하지 않고 조회 시 실시간 계산한다. 스토리지 비용은 없지만 조회마다 연산이 발생한다. STORED는 INSERT/UPDATE 시 계산한 값을 디스크에 저장한다. 쓰기 비용은 증가하지만 읽기 시 계산이 없다. 인덱스는 둘 다 걸 수 있으나 실무에서는 VIRTUAL + 인덱스 조합을 더 많이 쓴다.

**Q: JSON 컬럼을 쓰면 안 되는 상황은?**  
A: 속성 값으로 다른 테이블을 FK 참조해야 할 때, 속성 수가 수백 개 이상이어서 메타데이터 관리가 필요할 때, 속성 간 정규화가 필요할 때다. 또한 JSON 내 값에 NOT NULL 같은 DB 레벨 제약을 걸기 어려우므로 데이터 무결성이 매우 중요한 핵심 속성에는 일반 컬럼을 써야 한다.

**Q: stylink 프로젝트 적용 포인트는?**  
A: 2차 출장 스타일링에서 스타일리스트가 코디한 상품의 부가 스타일링 메모(착장 포인트, 코디 설명, 촬영 각도 등)를 저장할 때 JSON 컬럼이 적합하다. 상품 테이블의 핵심 속성(price, status, brand)은 일반 컬럼으로 유지하고, 카테고리별로 달라지는 스펙(소재, 사이즈 범위, 소재 상세 등)은 `attributes JSON` 컬럼 하나로 관리한다. EAV 도입 전에 JSON 컬럼으로 충분한지 먼저 검토하는 것이 권장 순서다.
