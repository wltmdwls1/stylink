# 03. Querydsl 기본 문법

## 1. JPQL vs Querydsl

| 항목 | JPQL | Querydsl |
|------|------|----------|
| 쿼리 작성 | 문자열 | 자바 코드 |
| 오류 발견 | 실행 시점 | **컴파일 시점** |
| 파라미터 바인딩 | 직접 | 자동 처리 |

```java
// JPQL — 문자열, 오타 발견 못함
String qlString = "select m from Member m where m.username = :username";
em.createQuery(qlString, Member.class).setParameter("username", "member1");

// Querydsl — 코드, 컴파일 오류
queryFactory.selectFrom(member).where(member.username.eq("member1")).fetchOne();
```

## 2. JPAQueryFactory 설정

```java
// 필드로 선언 (권장) — 동시성 문제 없음: 스프링이 트랜잭션마다 별도 영속성 컨텍스트 제공
@PersistenceContext EntityManager em;
JPAQueryFactory queryFactory;

@BeforeEach
void before() {
    queryFactory = new JPAQueryFactory(em);
}
```

## 3. Q-Type 활용

```java
QMember m = new QMember("m");     // 별칭 직접 지정 (같은 테이블 셀프 조인 시)
QMember m = QMember.member;       // 기본 인스턴스 사용 (권장)

// static import로 더 간결하게 — 실무 기본 패턴
import static study.querydsl.entity.QMember.*;
queryFactory.selectFrom(member).where(member.username.eq("member1")).fetchOne();
```

## 4. 검색 조건

```java
// 메서드 체인 AND/OR
.where(member.username.eq("member1").and(member.age.eq(10)))

// 파라미터 AND (null 무시 → 동적 쿼리 핵심)
.where(member.username.eq("member1"), member.age.eq(10))
```

**검색 조건 목록**:
```java
eq("v")       // =
ne("v")       // !=
isNotNull()   // is not null
in(10, 20)    // in (10, 20)
notIn(10, 20) // not in (10, 20)
between(10, 30)
goe(30)  // >= 30
gt(30)   // > 30
loe(30)  // <= 30
lt(30)   // < 30
like("member%")
contains("member")    // like '%member%'
startsWith("member")  // like 'member%'
```

## 5. 결과 조회

```java
.fetch()        // List — 없으면 빈 리스트
.fetchOne()     // 단건 — 없으면 null, 2건 이상 NonUniqueResultException
.fetchFirst()   // limit(1).fetchOne()
.fetchResults() // QueryResults — total count 포함 (페이징 시)
.fetchCount()   // count만 조회
```

## 6. 정렬

```java
.orderBy(member.age.desc(), member.username.asc().nullsLast())
// nullsFirst() — null을 앞에
// nullsLast()  — null을 뒤에
```

## 7. 페이징

```java
// 데이터 조회만
.offset(1).limit(2).fetch()

// 전체 count 포함 (fetchResults — count 쿼리 추가 실행됨)
QueryResults<Member> results = queryFactory.selectFrom(member)
    .offset(1).limit(2).fetchResults();
results.getTotal();   // 전체 수
results.getResults(); // 결과 리스트
```

> 실무 주의: count 쿼리가 원본과 동일한 join을 수행 → 성능 문제 시 count 쿼리를 별도 작성

## 8. 집합 함수 + GroupBy

```java
// 집합: count, sum, avg, max, min
List<Tuple> result = queryFactory
    .select(member.count(), member.age.sum(), member.age.avg(), member.age.max(), member.age.min())
    .from(member).fetch();

// GroupBy + Having
queryFactory.select(team.name, member.age.avg())
    .from(member).join(member.team, team)
    .groupBy(team.name)
    .having(team.name.ne("teamB"))
    .fetch();
```

## 9. 조인

```java
// 기본 조인: join(조인대상, Q타입 별칭)
.join(member.team, team)      // inner join
.leftJoin(member.team, team)  // left outer join
.rightJoin(member.team, team) // right outer join

// 세타 조인 (연관관계 없는 조인 — from에 여러 엔티티)
queryFactory.select(member).from(member, team)
    .where(member.username.eq(team.name)).fetch();

// ON절 1 — 조인 대상 필터링 (내부조인이면 where와 동일 → where 사용 권장)
.leftJoin(member.team, team).on(team.name.eq("teamA"))

// ON절 2 — 연관관계 없는 엔티티 외부 조인 (leftJoin에 엔티티 하나만)
.leftJoin(team).on(member.username.eq(team.name))

// 페치 조인
.join(member.team, team).fetchJoin()
```

## 10. 서브 쿼리

```java
QMember memberSub = new QMember("memberSub");  // 메인 쿼리와 별칭 다르게

// where 서브쿼리 (eq, goe, in)
.where(member.age.eq(
    JPAExpressions.select(memberSub.age.max()).from(memberSub)
))

// select 서브쿼리
.select(member.username, JPAExpressions.select(memberSub.age.avg()).from(memberSub))

// static import 활용
import static com.querydsl.jpa.JPAExpressions.select;
.where(member.age.eq(select(memberSub.age.max()).from(memberSub)))
```

**from 절 서브쿼리(인라인 뷰) 한계**: JPA/Querydsl 미지원
- 해결: join으로 변경 / 쿼리 2번 분리 / nativeSQL

## 11. Case 문

```java
// 단순
.select(member.age.when(10).then("열살").when(20).then("스무살").otherwise("기타"))

// 복잡 — CaseBuilder
.select(new CaseBuilder()
    .when(member.age.between(0, 20)).then("0~20살")
    .when(member.age.between(21, 30)).then("21~30살")
    .otherwise("기타"))

// orderBy에서 Case 변수 재사용
NumberExpression<Integer> rankPath = new CaseBuilder()
    .when(member.age.between(0, 20)).then(2)
    .when(member.age.between(21, 30)).then(1)
    .otherwise(3);
queryFactory.select(member.username, member.age, rankPath)
    .from(member).orderBy(rankPath.desc()).fetch();
```

## 12. 상수, 문자 더하기

```java
// 상수
.select(member.username, Expressions.constant("A"))

// 문자 concat (타입 다를 때 stringValue() 사용)
.select(member.username.concat("_").concat(member.age.stringValue()))
// → "member1_10"
// stringValue()는 ENUM을 문자로 변환할 때도 자주 사용
```

---

## stylink 적용 포인트

- `JPAQueryFactory`를 `@Bean`으로 등록하거나 Service/Repository 필드로 주입
- `where(조건1, 조건2, ...)` — null 무시 특성이 동적 쿼리의 핵심 (중급 문법에서 상세)
- 페이징 시 count 쿼리 분리 패턴 적용 (실무 필수)
- fetch join: `join(entity.relation, QRelation.relation).fetchJoin()`
- Enum 값 비교: `.stringValue().eq(status.name())` 또는 `eq(status)` 직접 비교
