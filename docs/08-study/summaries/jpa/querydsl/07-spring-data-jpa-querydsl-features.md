# 07. 스프링 데이터 JPA가 제공하는 Querydsl 기능

> **핵심 결론**: 이 챕터에서 소개하는 스프링 데이터 제공 기능들은 **복잡한 실무에서 사용하기 부족**하다. 한계를 파악하고 대안(직접 만든 지원 클래스)을 선택해야 한다.

---

## 1. QuerydslPredicateExecutor — 실무 비추천

```java
// 리포지토리에 상속 추가
interface MemberRepository extends JpaRepository<Member, Long>,
    QuerydslPredicateExecutor<Member> {}

// 사용 — Predicate를 직접 전달
Iterable<Member> result = memberRepository.findAll(
    member.age.between(10, 40).and(member.username.eq("member1"))
);
```

**제공 메서드**: `findById(Predicate)`, `findAll(Predicate)`, `count(Predicate)`, `exists(Predicate)` — Pageable, Sort 지원

**한계**:
- LEFT JOIN 불가 (묵시적 조인만 가능)
- 서비스/클라이언트 레이어가 Querydsl 구현 기술에 직접 의존
- 복잡한 실무 조건 처리 불가

---

## 2. Querydsl Web 지원 — 실무 비추천

**한계**:
- 단순 조건만 가능
- 조건 커스텀 기능이 복잡하고 명시적이지 않음
- 컨트롤러가 Querydsl에 의존
- 복잡한 실무환경에서 사용 불가

---

## 3. QuerydslRepositorySupport — 한계 있음

**장점**: `applyPagination()` 페이징 편리, `EntityManager` 제공, `from()`으로 시작 가능

**한계** (사용하지 않는 이유):
- Querydsl **3.x** 기준으로 만들어짐
- `JPAQueryFactory`로 `select()`로 시작 불가 (`from()`으로만 시작)
- `QueryFactory` 미제공
- 스프링 데이터 `Sort` 정상 동작 안 함

---

## 4. Querydsl4RepositorySupport — 직접 만든 지원 클래스 (권장)

`QuerydslRepositorySupport`의 한계를 극복하기 위해 직접 구현한 추상 클래스.

**장점**:
- `select()`, `selectFrom()`으로 시작 가능
- 스프링 데이터 `Sort` 지원
- 페이징 + 카운트 쿼리 분리 가능
- `EntityManager`, `QueryFactory` 모두 제공

### 구현체 핵심

```java
@Repository
public abstract class Querydsl4RepositorySupport {
    private final Class domainClass;
    private Querydsl querydsl;
    private EntityManager entityManager;
    private JPAQueryFactory queryFactory;

    public Querydsl4RepositorySupport(Class<?> domainClass) {
        this.domainClass = domainClass;
    }

    @Autowired
    public void setEntityManager(EntityManager entityManager) {
        // EntityManager 주입 + Querydsl, JPAQueryFactory 초기화
        this.entityManager = entityManager;
        this.querydsl = new Querydsl(entityManager, new PathBuilder<>(...));
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    // 편의 메서드
    protected <T> JPAQuery<T> select(Expression<T> expr) {
        return getQueryFactory().select(expr);
    }
    protected <T> JPAQuery<T> selectFrom(EntityPath<T> from) {
        return getQueryFactory().selectFrom(from);
    }

    // 페이징 — 카운트 쿼리 공유 (단순)
    protected <T> Page<T> applyPagination(Pageable pageable,
            Function<JPAQueryFactory, JPAQuery> contentQuery) {
        JPAQuery jpaQuery = contentQuery.apply(getQueryFactory());
        List<T> content = getQuerydsl().applyPagination(pageable, jpaQuery).fetch();
        return PageableExecutionUtils.getPage(content, pageable, jpaQuery::fetchCount);
    }

    // 페이징 — 카운트 쿼리 분리 (최적화)
    protected <T> Page<T> applyPagination(Pageable pageable,
            Function<JPAQueryFactory, JPAQuery> contentQuery,
            Function<JPAQueryFactory, JPAQuery> countQuery) {
        JPAQuery jpaContentQuery = contentQuery.apply(getQueryFactory());
        List<T> content = getQuerydsl().applyPagination(pageable, jpaContentQuery).fetch();
        JPAQuery countResult = countQuery.apply(getQueryFactory());
        return PageableExecutionUtils.getPage(content, pageable, countResult::fetchCount);
    }
}
```

### 사용 예시

```java
@Repository
public class MemberTestRepository extends Querydsl4RepositorySupport {
    public MemberTestRepository() { super(Member.class); }

    public List<Member> basicSelect() {
        return select(member).from(member).fetch();
    }

    // 단순 페이징 (카운트 쿼리 공유)
    public Page<Member> applyPagination(MemberSearchCondition condition, Pageable pageable) {
        return applyPagination(pageable, contentQuery -> contentQuery
            .selectFrom(member)
            .leftJoin(member.team, team)
            .where(usernameEq(condition.getUsername()),
                   teamNameEq(condition.getTeamName()),
                   ageGoe(condition.getAgeGoe()),
                   ageLoe(condition.getAgeLoe())));
    }

    // 복잡한 페이징 (카운트 쿼리 분리)
    public Page<Member> applyPagination2(MemberSearchCondition condition, Pageable pageable) {
        return applyPagination(pageable,
            contentQuery -> contentQuery
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(usernameEq(...), teamNameEq(...), ageGoe(...), ageLoe(...)),
            countQuery -> countQuery
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(usernameEq(...), teamNameEq(...), ageGoe(...), ageLoe(...))
        );
    }
}
```

---

## 기능별 실무 선택 가이드

| 기능 | 실무 사용 | 이유 |
|------|----------|------|
| `QuerydslPredicateExecutor` | X | LEFT JOIN 불가, 서비스 의존 |
| `Querydsl Web` | X | 복잡한 조건 처리 불가, 컨트롤러 의존 |
| `QuerydslRepositorySupport` | X | Querydsl 3.x 기준, Sort 버그 |
| `Querydsl4RepositorySupport` (직접 구현) | O | 모든 한계 해결 |
| 사용자 정의 리포지토리 + `JPAQueryFactory` | O | 가장 범용적, 명시적 |

---

## stylink 적용 포인트

- `QuerydslPredicateExecutor`, `Querydsl Web`은 사용하지 않음
- `Querydsl4RepositorySupport`는 복잡한 페이징 쿼리가 많은 Repository에 도입 고려
  - 예: `ItemQueryRepository`, `ReservationQueryRepository`
- 그러나 **대부분의 경우 사용자 정의 리포지토리 + `JPAQueryFactory` 필드 방식**이 더 명시적이고 이해하기 쉬움
- 포트폴리오 목적이면 두 방식 모두 알고 있다는 것을 보여주기 위해 각 1개씩 구현하는 것도 고려
