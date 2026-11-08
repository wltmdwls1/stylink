# 상속 관계 설계

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
문제: 객체지향 상속 → RDBMS에는 상속 개념 없음
  슈퍼타입-서브타입(Supertype/Subtype) 모델로 해결
  ↓
3가지 전략
  ┌─────────────────────────────────────────────────────────┐
  │ 1. 구현 클래스마다 테이블 (Roll-down)                    │
  │    자식마다 별도 테이블 + 공통 속성 중복 포함             │
  │    → 실무에서 거의 사용 안 함 (FK 불가, UNION 지옥)      │
  ├─────────────────────────────────────────────────────────┤
  │ 2. 단일 테이블 (Roll-up)                                 │
  │    모든 속성을 하나의 테이블에 통합 + product_type 구분자 │
  │    → 조회 성능 최고, NULL 많음, 구조 단순할 때            │
  ├─────────────────────────────────────────────────────────┤
  │ 3. 조인 전략 (One-to-One)                                │
  │    부모(공통) + 자식(고유) 분리, PK 공유 1:1 관계         │
  │    → 정석, 정규화, 확장 용이, 무결성 보장                 │
  └─────────────────────────────────────────────────────────┘

실무: 단일 테이블(단순) vs 조인 전략(정석) 중 선택
      구현 클래스마다 테이블은 거의 사용 안 함
```

---

## 문제 상황

```
[객체지향]            [RDBMS]
Product               테이블/FK만 있음
  ├── Book            상속 개념 없음
  ├── Electronics     → 어떻게 표현?
  └── Clothing
```

**공통 속성**: 상품명, 가격, 재고수량, 등록일  
**Book 전용**: 저자, ISBN, 출판사  
**Electronics 전용**: 제조사, 보증기간, 전력소비량  
**Clothing 전용**: 사이즈, 색상, 소재

이 구조를 관계형 DB에 표현하는 방법 → **슈퍼타입-서브타입 모델**, 3가지 전략으로 구현.

| 개발자 용어 | DB 모델링 용어 |
|------------|--------------|
| 구현 클래스마다 테이블 | 서브타입별 개별 테이블 (Roll-down) |
| 단일 테이블 | 슈퍼타입 통합 테이블 (Roll-up) |
| 조인 전략 | 슈퍼/서브타입 개별 테이블 (One-to-One) |

---

## 1. 구현 클래스마다 테이블 전략

자식마다 별도 테이블을 만들고, **부모의 공통 속성을 각 테이블에 중복 포함**.

```
book                    electronics             clothing
─────────────────       ─────────────────       ─────────────────
product_id (PK)         product_id (PK)         product_id (PK)
name ← 공통             name ← 공통             name ← 공통
price ← 공통            price ← 공통            price ← 공통
author                  manufacturer            size
isbn                    warranty_months         color
publisher               power_consumption       material
```

### 장점

- 구조 단순, 직관적
- 유형별 조회 시 해당 테이블만 스캔 → 성능 좋음
- NOT NULL 제약조건 자유롭게 가능

### 단점 (치명적)

**전체 상품 조회**: 모든 테이블 UNION ALL 필요 → 상품 유형 늘수록 쿼리 복잡

```sql
SELECT product_id, name, 'BOOK' AS type FROM book
UNION ALL
SELECT product_id, name, 'ELECTRONICS' FROM electronics
UNION ALL
SELECT product_id, name, 'CLOTHING' FROM clothing;
```

**상품 ID로 조회 불가**: 어느 테이블에 있는지 몰라 3개를 다 뒤져야 함

**FK 설정 불가**: `orders.product_id`가 어느 테이블을 참조해야 하는지 DB가 알 수 없음

**상품 ID 중복**: 각 테이블이 독립적으로 AUTO_INCREMENT → book의 1번 ≠ electronics의 1번

**공통 속성 변경**: 할인율 추가 시 3개 테이블 모두 ALTER

> **결론**: 치명적 단점이 많아 실무에서 거의 사용하지 않는다.

---

## 2. 단일 테이블 전략

모든 부모+자식 속성을 **하나의 테이블에 통합**. 유형 구분을 위해 `product_type` (DTYPE) 컬럼 필수.

```sql
CREATE TABLE product (
    product_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_type VARCHAR(20) NOT NULL,  -- 'BOOK', 'ELECTRONICS', 'CLOTHING'

    -- 공통 속성
    name          VARCHAR(200) NOT NULL,
    price         INT NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,

    -- Book 전용
    author VARCHAR(100),
    isbn   VARCHAR(13),
    publisher VARCHAR(100),

    -- Electronics 전용
    manufacturer     VARCHAR(100),
    warranty_months  INT,
    power_consumption INT,

    -- Clothing 전용
    size     VARCHAR(10),
    color    VARCHAR(50),
    material VARCHAR(100),

    INDEX idx_product_type (product_type)
);
```

### 조회 패턴

```sql
-- 전체 상품: UNION 없이 단일 쿼리
SELECT product_id, product_type, name, price FROM product;

-- 도서만 조회
SELECT product_id, name, author, publisher
FROM product WHERE product_type = 'BOOK';

-- 상품 ID로 바로 조회 (어느 테이블인지 알 필요 없음)
SELECT product_id, product_type, name FROM product WHERE product_id = 5;

-- FK 설정도 가능
FOREIGN KEY (product_id) REFERENCES product(product_id)
```

### 장점

| 항목 | 내용 |
|------|------|
| 조회 성능 | 가장 우수 (조인 없음, 단일 테이블 접근) |
| 쿼리 단순성 | UNION/JOIN 불필요 |
| FK 설정 | 가능 |
| 상품 ID | 전역 유일 (하나의 AUTO_INCREMENT) |
| 공통 속성 변경 | 테이블 1개만 수정 |

### 단점

**NULL 값 폭증**: 도서 행에는 manufacturer, size 등이 모두 NULL

| product_id | name | product_type | author | manufacturer | size |
|-----------|------|-------------|--------|-------------|------|
| 1 | 클린 코드 | BOOK | 로버트 마틴 | **NULL** | **NULL** |
| 4 | 갤럭시 | ELECTRONICS | **NULL** | 삼성전자 | **NULL** |
| 7 | 오버핏 맨투맨 | CLOTHING | **NULL** | **NULL** | L |

**NOT NULL 제약 불가**: author는 BOOK만 필수지만 테이블 레벨에서 NOT NULL 걸면 Electronics 입력 실패  
→ 자식 속성 무결성은 애플리케이션 레벨에서 관리 필요

**테이블 비대화**: 상품 유형 늘수록 컬럼 수 급증

### 선택 기준

- 유형 종류가 적고, 앞으로 크게 늘어나지 않을 때
- 유형별 고유 속성이 적을 때
- 전체 조회가 빈번하고 성능이 중요할 때
- 구조를 단순하게 유지하고 싶을 때

---

## 3. 조인 전략 (실무 정석)

**부모(공통)와 자식(고유)을 분리**. 자식 테이블의 PK = 부모 PK를 참조하는 FK (완벽한 1:1 관계).

```sql
-- 부모 테이블: 공통 속성
CREATE TABLE product (
    product_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_type VARCHAR(20) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    price        INT NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_type (product_type)
);

-- 자식 테이블: 고유 속성만, PK = FK
CREATE TABLE book (
    product_id BIGINT PRIMARY KEY,
    author     VARCHAR(100) NOT NULL,
    isbn       VARCHAR(13),
    publisher  VARCHAR(100),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

CREATE TABLE electronics (
    product_id       BIGINT PRIMARY KEY,
    manufacturer     VARCHAR(100) NOT NULL,
    warranty_months  INT NOT NULL DEFAULT 12,
    power_consumption INT,
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);

CREATE TABLE clothing (
    product_id BIGINT PRIMARY KEY,
    size       VARCHAR(10) NOT NULL,
    color      VARCHAR(50) NOT NULL,
    material   VARCHAR(100),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);
```

### 데이터 입력 (2번 필요)

```sql
-- 1. 부모 INSERT
INSERT INTO product (product_type, name, price, stock_quantity)
VALUES ('BOOK', '클린 코드', 33000, 100);

-- 2. 자식 INSERT (LAST_INSERT_ID()로 부모 PK 참조)
INSERT INTO book (product_id, author, isbn, publisher)
VALUES (LAST_INSERT_ID(), '로버트 마틴', '9788966260959', '인사이트');
```

### 조회 패턴

```sql
-- 전체 공통 조회: 부모만 조회 (빠름)
SELECT product_id, product_type, name, price FROM product;

-- 도서 상세 조회: 부모 + 자식 JOIN
SELECT p.product_id, p.name, p.price, b.author, b.isbn, b.publisher
FROM product p
JOIN book b ON p.product_id = b.product_id;

-- 타입 모를 때: 모든 자식 LEFT JOIN
SELECT p.product_id, p.product_type, p.name,
       b.author, e.manufacturer, c.size
FROM product p
LEFT JOIN book        b ON p.product_id = b.product_id
LEFT JOIN electronics e ON p.product_id = e.product_id
LEFT JOIN clothing    c ON p.product_id = c.product_id
WHERE p.product_id = 5;
```

**LEFT JOIN 동작 원리**:
1. 부모-자식은 PK를 공유하는 1:1 관계 → PK끼리 조인으로 정확히 연결
2. 하나의 상품은 배타적(Book이면서 Electronics는 불가) → 해당 자식 외 나머지는 NULL
3. 애플리케이션에서 `product_type`으로 분기해 객체 매핑

### 새 유형 추가 (기존 테이블 수정 없음)

```sql
CREATE TABLE furniture (
    product_id BIGINT PRIMARY KEY,
    width INT NOT NULL, height INT NOT NULL, depth INT NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(product_id)
);
```

### 장단점

| 항목 | 내용 |
|------|------|
| 정규화 | 데이터 중복 없음, 저장 효율 높음 |
| NULL | 없음 (각 테이블에 필요한 컬럼만) |
| NOT NULL | 자식 테이블 필수 컬럼에 설정 가능 |
| FK | 부모 테이블 참조로 무결성 보장 |
| 확장성 | 새 유형은 새 자식 테이블 추가만 하면 됨 |
| **단점** | 상세 조회 시 JOIN 필요, INSERT 2번, 복잡 LEFT JOIN |

### 선택 기준

- 상품 유형이 많거나 앞으로 계속 늘어날 예정
- 유형별 고유 속성이 많을 때
- 데이터 무결성이 중요할 때
- 정규화된 깔끔한 구조 원할 때

---

## 3가지 전략 비교

| 특징 | 구현 클래스마다 | 단일 테이블 | 조인 전략 |
|------|--------------|-----------|---------|
| 테이블 수 | 자식 수만큼 | 1개 | 1 + 자식 수 |
| 전체 조회 | UNION ALL 필요 | 단순 | 공통 속성 단순 |
| 상세 조회 | 단순 | 단순 | JOIN 필요 |
| INSERT | 1회 | 1회 | **2회** |
| NULL 값 | 없음 | **많음** | 없음 |
| NOT NULL | 가능 | **불가** | 가능 |
| 외래 키 | **불가** | 가능 | 가능 |
| 확장성 | 낮음 | 중간 | **높음** |
| 저장 효율 | 낮음 (중복) | 낮음 (NULL) | 좋음 |
| 실무 사용 | **거의 없음** | 단순한 경우 | **정석** |

**선택 원칙**:
- 유형 간 차이가 크고 고유 속성이 많으면 → **조인 전략**
- 유형 간 차이가 적고 고유 속성이 적으면 → **단일 테이블**
- 구현 클래스마다 테이블 → **사용 안 함**

---

## JPA와의 관계

조인 전략에서 INSERT 2번, 조회 시 JOIN 직접 작성이 번거롭다 → **JPA가 해결**.

```java
@Entity
@Inheritance(strategy = InheritanceType.JOINED)  // 전략 한 줄로 선택
@DiscriminatorColumn(name = "product_type")
public class Product { ... }

@Entity
public class Book extends Product { ... }
```

JPA가 자동으로 INSERT 두 번, JOIN 쿼리 생성. 전략 변경도 어노테이션 하나만 바꾸면 됨.

> JPA는 도구일 뿐 — DB 설계를 이해해야 올바른 전략을 선택할 수 있다.

---

## 면접 Q&A

**Q: RDBMS에서 객체의 상속 구조를 표현하는 3가지 전략과 각각의 특징은?**
A: 구현 클래스마다 테이블, 단일 테이블, 조인 전략이 있다. 구현 클래스마다 테이블은 자식마다 별도 테이블을 만들어 공통 속성을 중복 포함하는 방식으로 전체 조회 시 UNION ALL이 필요하고 FK 설정이 불가능해 실무에서 거의 사용하지 않는다. 단일 테이블은 모든 속성을 하나의 테이블에 통합해 조회가 가장 빠르지만 NULL이 많고 자식 속성에 NOT NULL 제약을 걸 수 없다. 조인 전략은 부모(공통)와 자식(고유)을 분리하고 PK를 공유하는 1:1 관계로 연결하는 방식으로, 가장 정규화된 정석적인 방법이다.

**Q: 조인 전략에서 자식 테이블의 PK와 FK가 같은 이유는?**
A: 자식 테이블은 부모와 완벽한 1:1 관계를 가지며, 별도의 자체 ID가 필요 없다. 부모 테이블에서 AUTO_INCREMENT로 생성된 PK를 자식 테이블이 그대로 사용하면서 동시에 부모를 참조하는 FK 역할도 겸한다. 이를 통해 `ON product.product_id = book.product_id`처럼 PK끼리 단순하게 조인해도 데이터가 정확하게 연결된다. 결과적으로 상품 ID는 전체 테이블을 통틀어 유일하고, orders 같은 테이블에서 부모 테이블만 FK로 참조하면 된다.

**Q: 단일 테이블 전략과 조인 전략 중 어느 것을 선택해야 하나?**
A: 유형 간 차이가 크고 고유 속성이 많으면 조인 전략, 차이가 적고 고유 속성이 적으면 단일 테이블을 선택한다. 단일 테이블은 조회 성능이 가장 좋고 쿼리가 단순하지만 NULL이 많고 데이터 무결성을 DB 레벨에서 보장하기 어렵다. 조인 전략은 INSERT가 2번 필요하고 상세 조회에 JOIN이 필요하지만 정규화, NULL 없음, 확장성이 장점이다. 조인으로 인한 성능 저하는 인덱스로 대부분 해결 가능하므로 비즈니스가 복잡하다면 조인 전략이 권장된다.

**Q: stylink 프로젝트 적용 포인트는?**
A: stylink에서 Product 엔티티가 이미 단일 테이블에 가까운 구조로 설계되어 있다. 현재는 상품 유형이 단순하므로 단일 테이블 전략으로 충분하다. 1차에서는 `product_type`(BOOK/ELECTRONICS/CLOTHING) 컬럼을 추가한 단일 테이블, 또는 자식 유형별 특성이 뚜렷하면 조인 전략을 검토할 수 있다. 2차 출장 스타일링에서 Product와 ReservationProduct(예약 상품)가 별개의 생명주기를 가질 경우 조인 전략의 1:1 PK 공유 패턴을 적용할 수 있다. JPA를 쓰고 있으므로 `@Inheritance(strategy = ...)` 어노테이션 하나로 전략을 전환할 수 있어 나중에 필요하면 교체도 가능하다.
