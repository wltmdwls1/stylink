# 06. 실무 활용 - 스프링 데이터 JPA와 Querydsl

## 1. 사용자 정의 리포지토리 패턴 (핵심)

스프링 데이터 JPA (`JpaRepository`)는 Querydsl 동적 쿼리를 직접 작성할 수 없다.
→ **사용자 정의 리포지토리** 3단계로 해결:

```
1. 인터페이스 작성 (MemberRepositoryCustom)
2. 구현체 작성    (MemberRepositoryImpl — implements MemberRepositoryCustom)
3. JpaRepository 상속 시 Custom 인터페이스도 함께 상속
```

### 구조

```
JpaRepository ◄──── MemberRepository ───extends───► MemberRepositoryCustom
                                                           ▲
                                                     MemberRepositoryImpl
```

### 코드

```java
// 1. 커스텀 인터페이스
public interface MemberRepositoryCustom {
    List<MemberTeamDto> search(MemberSearchCondition condition);
    Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable);
    Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable);
}

// 2. 구현체 — 네이밍 규칙: 리포지토리명 + "Impl"
public class MemberRepositoryImpl implements MemberRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    public MemberRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<MemberTeamDto> search(MemberSearchCondition condition) {
        return queryFactory
            .select(new QMemberTeamDto(member.id, member.username, member.age, team.id, team.name))
            .from(member)
            .leftJoin(member.team, team)
            .where(usernameEq(condition.getUsername()),
                   teamNameEq(condition.getTeamName()),
                   ageGoe(condition.getAgeGoe()),
                   ageLoe(condition.getAgeLoe()))
            .fetch();
    }

    private BooleanExpression usernameEq(String username) {
        return isEmpty(username) ? null : member.username.eq(username);
    }
    private BooleanExpression teamNameEq(String teamName) {
        return isEmpty(teamName) ? null : team.name.eq(teamName);
    }
    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe == null ? null : member.age.goe(ageGoe);
    }
    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe == null ? null : member.age.loe(ageLoe);
    }
}

// 3. 스프링 데이터 리포지토리에 상속
public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {
    List<Member> findByUsername(String username);
}
```

---

## 2. 페이징 연동

### 방법 1 — searchPageSimple: fetchResults() (단순)

```java
@Override
public Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable) {
    QueryResults<MemberTeamDto> results = queryFactory
        .select(new QMemberTeamDto(...))
        .from(member).leftJoin(member.team, team)
        .where(...)
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetchResults();  // 데이터 쿼리 + count 쿼리 2번 실행

    return new PageImpl<>(results.getResults(), pageable, results.getTotal());
}
```

> `fetchResults()`: count 쿼리 실행 시 불필요한 `ORDER BY` 자동 제거

### 방법 2 — searchPageComplex: 데이터/카운트 분리 (권장)

```java
@Override
public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
    // 데이터 조회 쿼리 (join 포함, offset/limit)
    List<MemberTeamDto> content = queryFactory
        .select(new QMemberTeamDto(...))
        .from(member).leftJoin(member.team, team)
        .where(...)
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    // 카운트 쿼리 (join 최소화 가능 → 성능 최적화 포인트)
    long total = queryFactory
        .select(member)
        .from(member)
        .leftJoin(member.team, team)
        .where(...)
        .fetchCount();

    return new PageImpl<>(content, pageable, total);
}
```

**분리 이유**: count 쿼리에서 불필요한 join을 제거하면 성능이 크게 향상될 수 있다.

---

## 3. CountQuery 최적화 — PageableExecutionUtils

```java
JPAQuery<Member> countQuery = queryFactory
    .select(member)
    .from(member)
    .leftJoin(member.team, team)
    .where(...);

return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchCount);
```

**count 쿼리가 자동 생략되는 경우**:
- 첫 페이지이면서 컨텐츠 크기 < 페이지 크기일 때
- 마지막 페이지일 때 (offset + content size로 전체 크기 계산 가능)

→ `new PageImpl<>(content, pageable, total)` 대신 이 방식을 기본으로 사용

---

## 4. 컨트롤러 API

```java
@RestController
@RequiredArgsConstructor
public class MemberController {
    private final MemberJpaRepository memberJpaRepository;  // 순수 JPA
    private final MemberRepository memberRepository;         // 스프링 데이터 JPA

    @GetMapping("/v1/members")
    public List<MemberTeamDto> searchMemberV1(MemberSearchCondition condition) {
        return memberJpaRepository.search(condition);
    }

    @GetMapping("/v2/members")
    public Page<MemberTeamDto> searchMemberV2(MemberSearchCondition condition, Pageable pageable) {
        return memberRepository.searchPageSimple(condition, pageable);
    }

    @GetMapping("/v3/members")
    public Page<MemberTeamDto> searchMemberV3(MemberSearchCondition condition, Pageable pageable) {
        return memberRepository.searchPageComplex(condition, pageable);
    }
}
// GET /v2/members?size=5&page=2&teamName=teamB
```

---

## 5. 스프링 데이터 Sort → Querydsl OrderSpecifier 변환

```java
JPAQuery<Member> query = queryFactory.selectFrom(member);

for (Sort.Order o : pageable.getSort()) {
    PathBuilder pathBuilder = new PathBuilder(member.getType(), member.getMetadata());
    query.orderBy(new OrderSpecifier(
        o.isAscending() ? Order.ASC : Order.DESC,
        pathBuilder.get(o.getProperty())
    ));
}
```

> **주의**: 루트 엔티티 범위를 넘는 복잡한 동적 정렬은 `Sort` 대신 파라미터를 직접 받아 처리 권장

---

## stylink 적용 패턴

**ItemRepository 구성 예시**:
```java
// 커스텀 인터페이스
public interface ItemRepositoryCustom {
    List<ItemDto> search(ItemSearchCondition condition);
    Page<ItemDto> searchPage(ItemSearchCondition condition, Pageable pageable);
}

// 구현체 (ItemRepositoryImpl)
public class ItemRepositoryImpl implements ItemRepositoryCustom {
    // Querydsl 동적 쿼리 + 페이징 구현
}

// 최종 리포지토리
public interface ItemRepository extends JpaRepository<Item, Long>, ItemRepositoryCustom {
    // 메서드 이름 쿼리는 여기
}
```

**페이징 전략 결정 기준**:
- 단순 목록 → `searchPageSimple` (fetchResults)
- 성능 중요 (join이 복잡한 경우) → `searchPageComplex` (쿼리 분리)
- 실무 기본값 → `PageableExecutionUtils.getPage()` (count 쿼리 최적화 자동 적용)
