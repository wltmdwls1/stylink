# 04. Querydsl 중급 문법

## 1. 프로젝션과 결과 반환

### DTO 조회 3가지 방법

```java
// 1. 프로퍼티 접근 (Setter 필요, 기본 생성자 필요)
Projections.bean(MemberDto.class, member.username, member.age)

// 2. 필드 직접 접근 (Setter 불필요, 필드명 일치 필요)
Projections.fields(MemberDto.class, member.username, member.age)

// 3. 생성자 사용 (타입 기반 매칭)
Projections.constructor(MemberDto.class, member.username, member.age)
```

**DTO 필드명이 다를 때** — `as()` 또는 `ExpressionUtils.as()`:
```java
Projections.fields(UserDto.class,
    member.username.as("name"),         // 필드명 매핑
    ExpressionUtils.as(                  // 서브쿼리에 별칭
        JPAExpressions.select(memberSub.age.max()).from(memberSub), "age")
)
```

### @QueryProjection (가장 타입 안전한 방법)

```java
// DTO 생성자에 어노테이션 추가 후 compileQuerydsl 실행 → QMemberDto 생성
@QueryProjection
public MemberDto(String username, int age) { ... }

// 사용 — 컴파일 시점에 타입 체크
List<MemberDto> result = queryFactory
    .select(new QMemberDto(member.username, member.age))
    .from(member).fetch();
```

| 방법 | 타입 체크 | Q파일 필요 | 비고 |
|------|----------|-----------|------|
| bean/fields/constructor | X | X | 런타임 오류 가능 |
| @QueryProjection | O (컴파일) | O (QMemberDto) | DTO에 Querydsl 의존 발생 |

**트레이드오프**: `@QueryProjection`은 가장 안전하나 DTO가 Querydsl에 종속됨.

---

## 2. 동적 쿼리 (핵심)

### 방법 1: BooleanBuilder

```java
BooleanBuilder builder = new BooleanBuilder();
if (usernameCond != null) builder.and(member.username.eq(usernameCond));
if (ageCond != null)      builder.and(member.age.eq(ageCond));

queryFactory.selectFrom(member).where(builder).fetch();
```

### 방법 2: Where 다중 파라미터 (실무 권장)

```java
private List<Member> searchMember(String usernameCond, Integer ageCond) {
    return queryFactory
        .selectFrom(member)
        .where(usernameEq(usernameCond), ageEq(ageCond))  // null 자동 무시
        .fetch();
}

private BooleanExpression usernameEq(String cond) {
    return cond != null ? member.username.eq(cond) : null;
}
private BooleanExpression ageEq(Integer cond) {
    return cond != null ? member.age.eq(cond) : null;
}

// 조합도 가능 — 재사용성 극대화
private BooleanExpression allEq(String usernameCond, Integer ageCond) {
    return usernameEq(usernameCond).and(ageEq(ageCond));  // null 주의
}
```

**Where 다중 파라미터 장점**:
- `null` 값 자동 무시 → 조건 없으면 해당 where 조건 제거
- 메서드 재활용 가능 (`usernameEq()` 여러 쿼리에서 공유)
- 쿼리 가독성 향상

---

## 3. 수정/삭제 벌크 연산

```java
// 벌크 업데이트
long count = queryFactory
    .update(member)
    .set(member.username, "비회원")
    .where(member.age.lt(28))
    .execute();

// 숫자 연산
.set(member.age, member.age.add(1))    // +1
.set(member.age, member.age.multiply(2)) // x2

// 벌크 삭제
queryFactory.delete(member).where(member.age.gt(18)).execute();
```

> **주의**: 벌크 연산은 영속성 컨텍스트를 무시하고 DB 직접 수정 → 실행 후 반드시 `em.flush(); em.clear();`

---

## 4. SQL Function 호출

```java
// Dialect에 등록된 함수만 호출 가능
Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
    member.username, "member", "M")

// ANSI 표준 함수는 Querydsl 내장 메서드로 대체 가능
.where(member.username.eq(member.username.lower()))  // lower() 직접 사용
```

---

## stylink 적용 포인트

**DTO 조회 전략**:
- 단순 조회 → `Projections.constructor(XxxDto.class, ...)`
- 포트폴리오 목적이므로 `@QueryProjection`도 활용 고려 (타입 안전성 강조)
- 응답 DTO는 Controller 레이어에서 변환하거나 Repository 레이어에서 직접 반환 중 선택

**동적 쿼리 전략 (핵심)**:
```java
// 상품 목록 조회 예시 (status, categoryId 등 조건이 있을 수도 없을 수도)
private BooleanExpression statusEq(ItemStatus status) {
    return status != null ? item.status.eq(status) : null;
}
private BooleanExpression categoryEq(Long categoryId) {
    return categoryId != null ? item.category.id.eq(categoryId) : null;
}
// → .where(statusEq(status), categoryEq(categoryId)) 로 깔끔하게 처리
```

**벌크 연산 주의사항**:
- 재고 일괄 상태 변경 시 (`AVAILABLE → RESERVED` 등) 벌크 업데이트 후 반드시 `em.clear()`
- 트랜잭션 내에서 벌크 후 같은 엔티티를 다시 조회할 일이 있으면 특히 중요
