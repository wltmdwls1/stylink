# 07. 고급 매핑

## 전체 흐름 한눈에 보기

```
객체 상속 ──────────────────────────────────────────────────────┐
                                                                ↓
DB 슈퍼타입/서브타입  →  3가지 전략 선택  →  @Inheritance(strategy)
                         ├── JOINED (조인)
                         ├── SINGLE_TABLE (단일 테이블)  ← 기본값
                         └── TABLE_PER_CLASS (비추천)

공통 필드 공유 (등록일/수정일)  →  @MappedSuperclass
```

---

## 1. 상속관계 매핑

관계형 DB는 상속이 없다. **슈퍼타입/서브타입** 모델링 기법으로 객체 상속과 매핑한다.

### 3가지 전략 비교

| 전략 | 어노테이션 | 특징 |
|------|-----------|------|
| 조인 전략 | `JOINED` | 부모/자식 각각 테이블, JOIN으로 조회 |
| 단일 테이블 전략 | `SINGLE_TABLE` | 한 테이블에 모든 컬럼 (JPA 기본값) |
| 구현 클래스마다 테이블 | `TABLE_PER_CLASS` | DB/ORM 전문가 모두 비추천 |

### 주요 어노테이션

```java
@Entity
@Inheritance(strategy = InheritanceType.JOINED)  // 부모에 선언
@DiscriminatorColumn(name = "DTYPE")             // 구분 컬럼 (조인 전략에서 권장)
public abstract class Item { ... }

@Entity
@DiscriminatorValue("ALBUM")                     // DTYPE에 들어갈 값
public class Album extends Item { ... }
```

---

## 2. 전략별 장단점

### 조인 전략 (JOINED) — 김영한 권장

```
ITEM 테이블 (공통: id, name, price, DTYPE)
   ├── ALBUM 테이블 (ITEM_ID FK, artist)
   ├── MOVIE 테이블 (ITEM_ID FK, director, actor)
   └── BOOK 테이블  (ITEM_ID FK, author, isbn)
```

**장점**
- 테이블 정규화
- FK 참조 무결성 제약조건 활용 가능
- 저장공간 효율

**단점**
- 조회 시 JOIN 필요 → 쿼리 복잡
- INSERT SQL 2번 호출 (부모 + 자식)

---

### 단일 테이블 전략 (SINGLE_TABLE) — 단순할 때 유리

```
ITEM 테이블 하나에 모든 컬럼
(ITEM_ID, NAME, PRICE, DTYPE, ARTIST, DIRECTOR, ACTOR, AUTHOR, ISBN)
```

**장점**
- JOIN 없이 단순 조회 → 성능 빠름
- 쿼리 단순

**단점**
- 자식 엔티티 컬럼은 모두 null 허용
- 테이블이 커지면 오히려 성능 저하 가능

---

### 구현 클래스마다 테이블 (TABLE_PER_CLASS) — 사용 금지

```
ALBUM (id, name, price, artist)
MOVIE (id, name, price, director, actor)
BOOK  (id, name, price, author, isbn)
```

**단점**
- `Item` 타입으로 조회하면 UNION ALL → 성능 최악
- 자식 테이블 통합 쿼리 불가
- **DB 설계자 + ORM 전문가 모두 비추천**

---

## 3. @MappedSuperclass

상속관계 매핑과 다르다. **테이블 없음, 엔티티 아님**. 공통 매핑 정보(컬럼)만 자식에게 물려줄 때 사용.

```java
@MappedSuperclass
public abstract class BaseEntity {  // 추상 클래스 권장
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Entity
public class Member extends BaseEntity { ... }  // MEMBER 테이블에 createdAt, updatedAt 컬럼 추가
```

**특징**
- 테이블 매핑 X → `em.find(BaseEntity.class, id)` 불가
- 직접 생성할 일 없으므로 `abstract` 클래스 권장
- `@Entity`는 `@Entity` 또는 `@MappedSuperclass`만 상속 가능

**주 용도**: 등록일, 수정일, 등록자, 수정자 같은 공통 감사(Auditing) 필드

---

## 실무 선택 기준

| 상황 | 선택 |
|------|------|
| 테이블 수가 많지 않고 확장 가능성 있음 | 조인 전략 |
| 단순하고 확장 가능성 거의 없음 | 단일 테이블 전략 |
| 구현 클래스마다 테이블 | 사용하지 말 것 |

---

## stylink 적용 포인트

**강의 권장 방식**: 기본은 조인 전략, 단순하면 단일 테이블 전략.

**stylink 적용**:
- stylink의 `Item` 계층(`Product` 등)이 있다면 **단일 테이블 전략** 유력
  - 상품 종류가 고정적이고 속성 수가 적다면 JOIN 없이 빠른 조회가 유리
  - 단, 자식 전용 컬럼이 많아 테이블이 너무 넓어지면 조인 전략으로 전환
- `BaseEntity` (`createdAt`, `updatedAt`)는 이미 `@MappedSuperclass`로 구현 완료
  - 강의 방식 그대로 적용
