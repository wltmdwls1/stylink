# 08. 스프링 부트 3.x(2.6 이상), Querydsl 5.0 지원 방법

> 강의 이후에 추가된 내용. 스프링 부트 2.6+ / 3.x 사용 시 반드시 확인해야 할 변경 사항 3가지.

---

## 변경 사항 요약

| 항목 | 변경 내용 |
|------|----------|
| `build.gradle` | 스프링 부트 3.x용 Querydsl 설정 방식 변경 |
| `PageableExecutionUtils` | 패키지 이동 (기능 자체는 유지) |
| `fetchResults()`, `fetchCount()` | **Deprecated → 직접 count 쿼리 작성 필요** |

---

## 1. PageableExecutionUtils 패키지 변경

기능 자체가 없어진 것이 아니라 **패키지 위치만 변경**됨.

```java
// 기존 (구버전)
import org.springframework.data.repository.support.PageableExecutionUtils;

// 신규 (스프링 부트 3.x / 2.6+)
import org.springframework.data.support.PageableExecutionUtils;
```

---

## 2. fetchResults(), fetchCount() Deprecated

**이유**: 내부에서 select 쿼리를 count용으로 단순 변환하는 방식이라 복잡한 쿼리에서 오동작.
→ Querydsl이 향후 지원하지 않기로 결정 (당장 제거되지는 않으나 사용 금지 권고)

### count 쿼리 대체 방법

```java
// count(*) → Wildcard.count
// count(member.id) → member.count()
Long totalCount = queryFactory
    .select(member.count())   // select count(member.id)
    // .select(Wildcard.count) // select count(*)
    .from(member)
    .fetchOne();              // 숫자 단건이므로 fetchOne()
```

### searchPageComplex 최신 버전 (권장 패턴)

```java
import org.springframework.data.support.PageableExecutionUtils; // 신규 패키지

public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
    // 데이터 조회
    List<MemberTeamDto> content = queryFactory
        .select(new QMemberTeamDto(
            member.id.as("memberId"),
            member.username,
            member.age,
            team.id.as("teamId"),
            team.name.as("teamName")))
        .from(member)
        .leftJoin(member.team, team)
        .where(usernameEq(condition.getUsername()),
               teamNameEq(condition.getTeamName()),
               ageGoe(condition.getAgeGoe()),
               ageLoe(condition.getAgeLoe()))
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    // count 쿼리 — 별도 작성 (fetchCount() 대신 select(member.count()).fetchOne())
    JPAQuery<Long> countQuery = queryFactory
        .select(member.count())
        .from(member)
        .leftJoin(member.team, team)
        .where(usernameEq(condition.getUsername()),
               teamNameEq(condition.getTeamName()),
               ageGoe(condition.getAgeGoe()),
               ageLoe(condition.getAgeLoe()));

    return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    //                                                                  ^^^^^^^^^^
    //                              fetchCount() → fetchOne() 으로 변경
}
```

**핵심 변경점**:
- `countQuery.fetchCount()` → `countQuery::fetchOne` (메서드 레퍼런스)
- count 쿼리 타입: `JPAQuery<Member>` → `JPAQuery<Long>`
- select 절: `.select(member)` → `.select(member.count())`

---

## stylink 적용 체크리스트

**스프링 부트 3.x 기준으로 처음부터 적용해야 할 사항:**

- [ ] `PageableExecutionUtils` import 경로: `org.springframework.data.support.PageableExecutionUtils`
- [ ] `fetchCount()` 사용 금지 → `select(entity.count()).fetchOne()` 또는 `select(Wildcard.count).fetchOne()`
- [ ] `fetchResults()` 사용 금지 → 데이터 쿼리(`.fetch()`)와 count 쿼리(`.fetchOne()`) 분리 작성
- [ ] `PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne)` 패턴 사용

**count 쿼리 작성 기준**:
```java
// 일반적인 경우 (특정 컬럼 기준 count)
.select(item.count())        // count(item.id)

// 전체 행 수 카운트
.select(Wildcard.count)      // count(*)
```
