# 10. 객체지향 쿼리 언어(JPQL)

## 전체 흐름 한눈에 보기

```
JPA 쿼리 방법
├── JPQL          ← 기본, 모든 것의 뿌리
├── Criteria      ← 자바 코드 JPQL 빌더, 너무 복잡 → 쓰지 마라
├── QueryDSL     ← Criteria 대체, 실무 표준 (별도 강의)
├── 네이티브 SQL  ← DB 종속 쿼리 필요할 때만
└── JDBC/MyBatis  ← JPA와 함께 쓸 때 영속성 컨텍스트 수동 flush 필요
```

---

## 1. JPQL 기본 문법

- **엔티티 이름** 사용 (테이블명 아님)
- **별칭 필수** (`m` in `Member m`)
- 대소문자: 엔티티/속성은 구분 O, 키워드(SELECT/FROM/WHERE)는 구분 X

```jpql
select m from Member m where m.age > 18
```

### 결과 조회 API

| 메서드 | 설명 |
|--------|------|
| `getResultList()` | 결과 0~N건, 없으면 빈 리스트 |
| `getSingleResult()` | 결과 정확히 1건, 없으면 `NoResultException`, 2건 이상이면 `NonUniqueResultException` |

### 파라미터 바인딩

```jpql
-- 이름 기준 (권장)
SELECT m FROM Member m where m.username = :username

-- 위치 기준 (비권장)
SELECT m FROM Member m where m.username = ?1
```

---

## 2. 프로젝션

```jpql
SELECT m FROM Member m              -- 엔티티 프로젝션
SELECT m.team FROM Member m         -- 엔티티 프로젝션 (묵시적 조인 발생)
SELECT m.address FROM Member m      -- 임베디드 타입 프로젝션
SELECT m.username, m.age FROM Member m  -- 스칼라 타입 프로젝션
```

여러 값 조회 시 `new` 명령어로 DTO 직접 변환 가능:
```jpql
SELECT new com.example.MemberDto(m.username, m.age) FROM Member m
```

---

## 3. 페이징 API

```java
em.createQuery("select m from Member m order by m.name desc", Member.class)
  .setFirstResult(10)   // 시작 위치 (0-based)
  .setMaxResults(20)    // 조회할 수
  .getResultList();
```

JPA가 DB 방언에 맞게 자동 변환 (MySQL: `LIMIT ?`, Oracle: `ROWNUM`).

---

## 4. 조인

```jpql
-- 내부 조인
SELECT m FROM Member m [INNER] JOIN m.team t

-- 외부 조인
SELECT m FROM Member m LEFT [OUTER] JOIN m.team t

-- 세타 조인 (연관관계 없는 조인)
select count(m) from Member m, Team t where m.username = t.name

-- ON 절 (조인 대상 필터링 / 연관관계 없는 엔티티 외부 조인)
SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'A'
```

---

## 5. 서브 쿼리

```jpql
-- WHERE/HAVING 절 가능
select m from Member m where m.age > (select avg(m2.age) from Member m2)

-- EXISTS, ALL, ANY, IN 지원
select m from Member m where exists (select t from m.team t where t.name = '팀A')
```

**한계**: 기본 JPQL은 FROM 절 서브쿼리 불가 → 조인으로 해결. 하이버네이트6부터는 FROM 절 서브쿼리 지원.

---

## 6. 경로 표현식

| 종류 | 예시 | 특징 |
|------|------|------|
| 상태 필드 | `m.username` | 탐색의 끝, 더 이상 탐색 불가 |
| 단일 값 연관 필드 | `m.team` | 묵시적 내부 조인 발생, 탐색 가능 |
| 컬렉션 값 연관 필드 | `m.orders` | 묵시적 내부 조인 발생, 탐색 불가 |

**실무 조언**: 묵시적 조인 쓰지 말고 **명시적 조인** 사용. 묵시적 조인은 어디서 조인이 발생하는지 파악하기 어려움.

---

## 7. 페치 조인 ← 실무에서 정말 중요

JPQL 전용 기능. 연관된 엔티티/컬렉션을 SQL 한 번에 함께 조회.

```jpql
-- 엔티티 페치 조인
select m from Member m join fetch m.team

-- 컬렉션 페치 조인 (일대다)
select t from Team t join fetch t.members where t.name = '팀A'
```

### 일반 조인 vs 페치 조인 차이

| 구분 | 일반 조인 | 페치 조인 |
|------|----------|----------|
| SQL | JOIN 발생 | JOIN 발생 |
| 연관 엔티티 | **조회 안 함** (프록시) | **함께 조회** (즉시 로딩) |

### 페치 조인 한계

- 페치 조인 대상에 별칭 줄 수 없음 (하이버네이트 가능하지만 비권장)
- 둘 이상의 컬렉션 페치 조인 불가
- **컬렉션 페치 조인 + 페이징 동시 사용 불가** (하이버네이트는 경고 후 메모리 페이징 → 매우 위험)
- 하이버네이트6: DISTINCT 명령 없어도 자동으로 중복 제거

### 페치 조인 사용 전략

```
글로벌 로딩 전략: 모두 LAZY
성능 최적화 필요한 곳: fetch join으로 해결
엔티티 그래프 모양이 아닌 다른 결과 필요: 일반 조인 + DTO 반환
```

---

## 8. 다형성 쿼리

```jpql
-- TYPE: 특정 자식 타입으로 한정
select i from Item i where type(i) IN (Book, Movie)

-- TREAT: 부모 타입을 자식 타입으로 캐스팅
select i from Item i where treat(i as Book).author = 'kim'
```

---

## 9. Named 쿼리

애플리케이션 로딩 시점에 파싱/검증 → **컴파일 타임에 JPQL 오류 검출** 가능.

```java
@NamedQuery(
    name = "Member.findByUsername",
    query = "select m from Member m where m.username = :username"
)
public class Member { ... }

// 사용
em.createNamedQuery("Member.findByUsername", Member.class)
  .setParameter("username", "회원1")
  .getResultList();
```

Spring Data JPA의 `@Query` 어노테이션이 내부적으로 Named 쿼리 방식으로 동작.

---

## 10. 벌크 연산

UPDATE/DELETE를 한 번의 SQL로 여러 로우 처리.

```java
int resultCount = em.createQuery(
    "update Product p set p.price = p.price * 1.1 where p.stockAmount < :stockAmount")
    .setParameter("stockAmount", 10)
    .executeUpdate();
```

**주의**: 벌크 연산은 **영속성 컨텍스트를 무시**하고 DB에 직접 쿼리.

```
해결책:
1. 벌크 연산 먼저 실행 후 조회
2. 벌크 연산 후 em.clear()로 영속성 컨텍스트 초기화
```

Spring Data JPA에서는 `@Modifying` + `@Query`로 처리.

---

## stylink 적용 포인트

**강의 권장 방식**: 모든 연관관계 LAZY, N+1은 fetch join으로 해결, 페치 조인 + 페이징 동시 사용 금지.

**stylink 적용**:
- 상품 목록, 주문 내역 조회 등 연관 엔티티 함께 필요한 API → `join fetch` 적극 활용
- 컬렉션(OrderItem 목록 등) + 페이징 동시 필요 시 → `@BatchSize` 또는 DTO 직접 조회 방식 사용
- 재고 상태 일괄 변경(HOLD 만료 자동 해제 등) → 벌크 연산 후 영속성 컨텍스트 clear 필수
- 동적 검색 조건 (상품 필터링, 예약 조회 등) → 나중에 QueryDSL로 구현 예정
