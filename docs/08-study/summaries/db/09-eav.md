# 동적 설계 - EAV

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
문제: 상속 관계(슈퍼타입-서브타입)의 한계
  ├── 카테고리 폭발 (200개 → 200개 테이블)
  ├── 동적 속성 변경 (매주 ALTER TABLE)
  ├── 예측 불가 속성 (와인, 캠핑용품)
  └── 셀러마다 다른 속성
  ↓
EAV 패턴 도입 (Entity-Attribute-Value)
  속성을 컬럼이 아닌 행(Row)으로 저장
  product(Entity) + product_attribute(Attribute=행, Value=행)
  ↓
개선: 속성 정의 테이블 (attribute_definition)
  attr_name 문자열 직접 저장 → attr_def_id 참조 (정규화)
  타입/필수여부/옵션 메타데이터 관리
  ↓
주의: EAV는 최후의 수단
  RDBMS 강점(무결성/타입/FK/인덱스) 대부분 포기
  현대적 대안 → JSON 컬럼 (다음 강의)
```

---

## 상속 관계의 한계 — EAV가 필요한 상황

### 4가지 문제

**1. 카테고리 폭발**: 카테고리마다 서브타입 테이블이 필요 → 200개 카테고리 = 200개 테이블 → 관리 불가

**2. 동적 속성 변경**: 속성 추가 요청이 계속 온다 → 운영 중 `ALTER TABLE`은 위험하고 번거롭다

**3. 예측 불가능한 속성**: 카테고리마다 필요한 속성이 전혀 다르다
- 와인: 빈티지, 포도품종, 알코올도수, 음용온도
- 캠핑: 수용인원, 방수등급, 설치시간
- 악기: 조율방식, 음역대, 연주난이도

**4. 셀러마다 다른 속성**: 오픈마켓처럼 같은 카테고리에서도 셀러마다 입력하고 싶은 속성이 다르다

### 상속 vs EAV 선택 기준

| 조건 | 선택 |
|------|------|
| 서브타입 종류가 명확하고 제한적 | 상속 관계 |
| 속성이 안정적이고 자주 변경되지 않음 | 상속 관계 |
| 서브타입별 제약조건(NOT NULL, 타입)이 중요 | 상속 관계 |
| 서브타입이 계속 늘어날 수 있음 | EAV |
| 속성이 동적으로 추가/수정/삭제되어야 함 | EAV |
| 사용자(셀러, 관리자)가 직접 속성을 정의해야 함 | EAV |
| 유연성이 데이터 무결성보다 중요 | EAV |

---

## EAV 패턴 소개

**EAV = Entity-Attribute-Value**

| 요소 | 의미 | 예시 |
|------|------|------|
| Entity | 속성을 가지는 주체 | 상품, 사용자 |
| Attribute | 특성의 이름 | screen_size, ram |
| Value | 실제 값 | 6.2인치, 12GB |

**전통 방식**: Attribute → 컬럼명, Value → 셀의 값  
**EAV 방식**: Attribute와 Value 모두 행의 데이터로 저장

```sql
-- 전통 방식: 속성 추가 시 ALTER TABLE 필요
CREATE TABLE product_phone (
    product_id  BIGINT PRIMARY KEY,
    screen_size VARCHAR(20),
    ram         VARCHAR(20),
    storage     VARCHAR(20)
    -- 5G 추가하려면? ALTER TABLE...
);

-- EAV 방식: INSERT만으로 속성 추가
CREATE TABLE product (
    product_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE product_attribute (
    attribute_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id   BIGINT NOT NULL,
    attr_name    VARCHAR(100) NOT NULL,
    attr_value   VARCHAR(500),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);
```

**5G 지원 속성 추가**: ALTER TABLE 없이 INSERT만으로 완료

```sql
INSERT INTO product_attribute (product_id, attr_name, attr_value) VALUES
(1, 'supports_5g', 'Y'),
(2, 'supports_5g', 'Y');
```

---

## EAV 실습 — 쇼핑몰 상품 속성 관리

### 기본 테이블 구조

```sql
-- Entity: 모든 상품의 공통 정보
CREATE TABLE product (
    product_id  BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name        VARCHAR(200) NOT NULL,
    price       INT NOT NULL,
    status      VARCHAR(20) DEFAULT 'ACTIVE',
    created_at  DATETIME NOT NULL
);

-- Attribute-Value: 카테고리별 고유 속성
CREATE TABLE product_attribute (
    attribute_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id   BIGINT NOT NULL,
    attr_name    VARCHAR(100) NOT NULL,
    attr_value   VARCHAR(500),
    created_at   DATETIME NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- 인덱스 (필수)
CREATE INDEX idx_product_attribute_product ON product_attribute(product_id);
CREATE INDEX idx_product_attribute_name    ON product_attribute(attr_name);
```

### 데이터 조회 패턴

**특정 상품 전체 속성 조회**:
```sql
SELECT p.name, pa.attr_name, pa.attr_value
FROM product p
JOIN product_attribute pa ON p.product_id = pa.product_id
WHERE p.product_id = 1
ORDER BY pa.attr_name;
```

**특정 속성 값으로 검색** (attr_name + attr_value 두 조건 필수):
```sql
SELECT p.product_id, p.name, pa.attr_value AS ram
FROM product p
JOIN product_attribute pa ON p.product_id = pa.product_id
WHERE pa.attr_name = 'ram'
  AND pa.attr_value = '12GB';
```

**다중 속성 AND 검색** (서브쿼리 필요):
```sql
SELECT p.product_id, p.name
FROM product p
WHERE p.product_id IN (
    SELECT product_id FROM product_attribute
    WHERE attr_name = 'screen_size' AND attr_value LIKE '6%'
)
AND p.product_id IN (
    SELECT product_id FROM product_attribute
    WHERE attr_name = 's_pen' AND attr_value = 'Y'
);
```

**피벗 (행 → 열 변환)**:
```sql
SELECT p.product_id, p.name, p.price,
       MAX(CASE WHEN pa.attr_name = 'screen_size'      THEN pa.attr_value END) AS screen_size,
       MAX(CASE WHEN pa.attr_name = 'battery_capacity' THEN pa.attr_value END) AS battery,
       MAX(CASE WHEN pa.attr_name = 'ram'              THEN pa.attr_value END) AS ram
FROM product p
LEFT JOIN product_attribute pa ON p.product_id = pa.product_id
WHERE p.category_id = 2
GROUP BY p.product_id, p.name, p.price;
```

---

## EAV 패턴 개선 — 속성 정의 테이블

기본 EAV는 `attr_name`을 문자열로 직접 입력 → 오타, 파편화, 어떤 속성이 있는지 파악 불가.

### 속성 정의 테이블 추가

```sql
-- 속성 메타데이터 관리
CREATE TABLE attribute_definition (
    attr_def_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id   BIGINT,
    attr_name     VARCHAR(100) NOT NULL,     -- 코드용 이름
    attr_label    VARCHAR(100) NOT NULL,     -- 화면 표시용 이름
    attr_type     VARCHAR(20)  NOT NULL,     -- TEXT, NUMBER, BOOLEAN, SELECT
    is_required   BOOLEAN DEFAULT FALSE,
    is_searchable BOOLEAN DEFAULT FALSE,
    display_order INT DEFAULT 0,
    options       VARCHAR(500),             -- SELECT 타입 선택지 (CSV)
    created_at    DATETIME NOT NULL,
    FOREIGN KEY (category_id) REFERENCES category(category_id)
);

-- 개선된 속성 값 테이블: attr_name 대신 attr_def_id 참조
CREATE TABLE product_attribute (
    attribute_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id   BIGINT NOT NULL,
    attr_def_id  BIGINT NOT NULL,           -- attr_name 대신
    attr_value   VARCHAR(500),
    created_at   DATETIME NOT NULL,
    FOREIGN KEY (product_id)  REFERENCES product(product_id),
    FOREIGN KEY (attr_def_id) REFERENCES attribute_definition(attr_def_id)
);
```

**중복 입력 방지**: `UNIQUE(product_id, attr_def_id)` 제약 추가 가능

### 개선된 구조의 장점

1. **오타 방지**: attr_name 문자열이 아닌 FK로 참조 → 없는 속성 입력 불가
2. **프론트엔드 동적 폼 생성**: `attr_type`(TEXT/SELECT/BOOLEAN)에 따라 입력 컴포넌트 자동 렌더링
3. **카테고리별 속성 목록 명확화**: 스마트폰 등록 시 필요한 속성 폼 정보를 DB에서 조회해 제공
4. **정규화**: attr_name 중복 제거

### 개선된 조회 (조인 하나 추가)

```sql
SELECT p.name, ad.attr_label, pa.attr_value
FROM product p
JOIN product_attribute pa ON p.product_id = pa.product_id
JOIN attribute_definition ad ON pa.attr_def_id = ad.attr_def_id
WHERE p.product_id = 1
ORDER BY ad.display_order;
```

---

## EAV의 장단점

### 장점

| 항목 | 내용 |
|------|------|
| 스키마 유연성 | ALTER TABLE 없이 INSERT만으로 새 속성 추가 |
| 희소 데이터 효율 | 값 있는 속성만 저장 (전통 방식은 NULL 낭비) |
| 엔티티별 다른 속성 | 같은 카테고리라도 상품마다 다른 속성 가능 |
| 런타임 속성 정의 | 관리자/운영팀이 개발자 도움 없이 속성 추가 |

### 단점 (치명적)

| 항목 | 내용 |
|------|------|
| 쿼리 복잡성 | 다중 조건 → 서브쿼리/다중 JOIN 필요 |
| 타입 검증 불가 | 모든 값이 VARCHAR → 숫자 컬럼에 문자 입력 가능 |
| FK 제약 어려움 | 값이 다른 테이블을 참조해야 할 때 DB 레벨 강제 불가 |
| 인덱싱 제한 | 특정 속성(예: ram)에만 인덱스 걸기 어려움 |
| 조인 성능 저하 | 속성 많을수록 다중 JOIN/서브쿼리 성능 악화 |

### 실무 팁 1: 타입별 컬럼 분리

```sql
CREATE TABLE product_attribute_typed (
    attribute_id BIGINT     PRIMARY KEY AUTO_INCREMENT,
    product_id   BIGINT     NOT NULL,
    attr_name    VARCHAR(100) NOT NULL,
    val_text     VARCHAR(255) NULL,   -- 문자열
    val_int      INT          NULL,   -- 숫자
    val_date     DATETIME     NULL    -- 날짜
);
```

- 숫자 컬럼에 문자 입력 차단 (DB 레벨 타입 보장)
- 숫자 범위 검색(`BETWEEN`), 정렬 정확도 향상 (`'10' < '2'` 문제 방지)
- 미사용 타입 컬럼에 NULL이 생기는 점은 감수

### 실무 팁 2: 하이브리드 접근 (권장)

```sql
-- 핵심 속성: 메인 테이블 컬럼
CREATE TABLE product (
    product_id  BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    price       INT NOT NULL,        -- 핵심, 자주 검색
    brand       VARCHAR(100),        -- 핵심
    status      VARCHAR(20),         -- 핵심
    created_at  DATETIME NOT NULL
);

-- 부가 속성: EAV
CREATE TABLE product_attribute (
    ...
);
```

핵심 속성(자주 조회, 검색 조건, 무결성 중요) → 컬럼  
동적으로 변하는 부가 속성 → EAV

---

## 실무 활용 사례

| 사례 | 이유 |
|------|------|
| 쇼핑몰 상품 상세 스펙 | 카테고리마다 속성이 다르고, 계속 늘어남 |
| 설문조사/폼 빌더 | 질문이 동적으로 생성되고 응답 형태가 다양함 |
| 의료 환자 데이터 | 검사 항목 수천 가지, 환자마다 받는 검사 다름 |
| 사용자 프로필 확장 | github_url, blog_url 등 임의 추가 속성 |

---

## EAV 선택에 대한 중요한 조언

> **"가급적이면 EAV를 사용하지 마라"**

EAV는 RDBMS의 강점 대부분을 포기한다:
1. **데이터 무결성 포기**: NOT NULL, 타입 체크, FK 제약 DB 레벨 적용 불가
2. **SQL 복잡도 급증**: 단순 조회도 서브쿼리 필요, 유지보수 어려움
3. **성능 저하**: 대용량에서 특정 속성 검색/정렬 시 인덱스 효율 저하

**사용해야 하는 상황**: "불확실성이 확실한" 상황 (카테고리가 폭발적으로 늘어나고, 속성이 예측 불가능하게 변하는 것이 확실한 경우)  
**사용하면 안 되는 상황**: "나중에 속성이 바뀔지도 몰라"라는 막연한 걱정

### 현대적 대안: JSON 컬럼

```sql
CREATE TABLE product (
    product_id BIGINT PRIMARY KEY,
    name       VARCHAR(200),
    attributes JSON  -- 속성을 JSON으로 저장
);
```

최신 RDBMS(MySQL 5.7.8+)는 JSON 인덱싱/검색 지원 → EAV보다 쿼리 단순, 유연성 유지.  
EAV를 고려할 상황이라면 JSON 컬럼을 먼저 검토하자.

---

## 면접 Q&A

**Q: EAV 패턴이란 무엇이고, 언제 사용하나?**
A: Entity-Attribute-Value의 약자로, 속성 이름과 값을 테이블의 컬럼이 아닌 행으로 저장하는 패턴이다. 전통적인 방식에서는 속성 추가 시 ALTER TABLE이 필요하지만, EAV는 INSERT만으로 새 속성을 추가할 수 있다. 카테고리가 폭발적으로 늘어나거나, 속성이 동적으로 변하거나, 셀러마다 다른 속성이 필요한 경우처럼 상속 관계 설계로 해결이 어려운 상황에서 사용한다. 단 RDBMS의 타입 검증, FK 제약, 효율적 인덱싱 등의 강점을 대부분 포기하므로 최후의 수단으로만 고려한다.

**Q: EAV에서 여러 속성 조건으로 AND 검색하는 방법은?**
A: EAV에서 AND 조건은 같은 컬럼에 여러 값이 혼재하기 때문에 WHERE절에 직접 쓸 수 없고, 각 조건마다 서브쿼리를 만들어 IN으로 교집합을 구한다. 예를 들어 "화면 6인치 이상이면서 S펜 지원"을 찾으려면 screen_size 조건 서브쿼리와 s_pen 조건 서브쿼리를 각각 만들어 `AND product_id IN (...)`으로 연결한다. 이 복잡성이 EAV의 가장 큰 단점 중 하나다.

**Q: EAV의 타입 검증 문제를 실무에서 어떻게 해결하나?**
A: 두 가지 접근이 있다. 첫 번째는 타입별 컬럼 분리로, `val_text`, `val_int`, `val_date`를 각각 분리해 DB 레벨에서 타입을 보장한다. 숫자 컬럼에 문자가 들어오는 것을 막고 범위 검색도 정확해진다. 두 번째는 애플리케이션 레벨 검증으로, attribute_definition에 저장된 attr_type 정보를 바탕으로 저장 전에 검증한다. 근본적인 해결은 불가능하므로 두 방법을 병행하거나 JSON 컬럼으로 대체하는 것이 더 낫다.

**Q: 속성 정의 테이블(attribute_definition)을 EAV에 추가하는 이유는?**
A: 기본 EAV는 attr_name을 문자열로 직접 저장하기 때문에 오타가 발생하면 데이터가 파편화된다(color와 colour가 별개의 속성으로 저장). 속성 정의 테이블을 두고 attr_def_id로 FK 참조하면 정의된 속성만 사용 가능하여 오타를 방지하고 정규화된다. 추가로 attr_type, is_required, display_order 같은 메타데이터를 관리할 수 있어 프론트엔드에서 카테고리별 입력 폼을 동적으로 생성하는 데 활용할 수 있다.

**Q: stylink 프로젝트 적용 포인트는?**
A: stylink는 초기 단계에서 상품 카테고리가 제한적이므로 조인 전략(상속 관계)이나 단일 테이블로 충분하다. EAV는 적용하지 않는 것이 권장된다. 다만 2차 출장 스타일링으로 확장할 때 스타일리스트가 코디한 상품들의 부가 속성(착장 포인트, 스타일링 메모 등)을 유연하게 관리할 필요가 생기면 하이브리드 방식(핵심 속성=컬럼, 부가 속성=EAV 또는 JSON 컬럼)을 고려할 수 있다. 현 시점에서는 MySQL 5.7.8+ JSON 컬럼이 EAV보다 쿼리가 단순하고 인덱싱도 지원되므로 더 나은 선택이다.
