# 04. 쿼리 메소드 기능 (스프링 데이터 JPA)

## 쿼리 메소드 기능 3가지

1. 메소드 이름으로 쿼리 생성
2. JPA NamedQuery 호출
3. `@Query` — 리포지토리 인터페이스에 쿼리 직접 정의

---

## 1. 메소드 이름으로 쿼리 생성

```java
// 이것만 선언하면 끝 — JPQL 자동 생성
List<Member> findByUsernameAndAgeGreaterThan(String username, int age);
```

| 키워드 | 예시 |
|--------|------|
| 조회 | `find…By`, `read…By`, `get…By` |
| 카운트 | `count…By` → `long` |
| 존재 여부 | `exists…By` → `boolean` |
| 삭제 | `delete…By`, `remove…By` |
| DISTINCT | `findDistinct`, `findMemberDistinctBy` |
| 상위 N건 | `findFirst3By`, `findTop3By` |

**핵심 장점**: 필드명이 변경되면 메서드 이름을 바꾸지 않으면 **애플리케이션 시작 시점에 오류 발생** → 런타임 전에 오류 발견 가능.

**한계**: 파라미터 증가 시 메서드 이름이 길어짐 → 실무에서는 `@Query` 선호.

---

## 2. @Query — 실무 권장

```java
@Query("select m from Member m where m.username = :username and m.age = :age")
List<Member> findUser(@Param("username") String username, @Param("age") int age);
```

- 이름 없는 Named 쿼리 = **애플리케이션 시작 시점에 문법 오류 발견** (매우 큰 장점)
- 파라미터 바인딩: **이름 기반(`@Param`)만 사용** (위치 기반 `?0`은 순서 실수 위험)
- 컬렉션 IN절도 지원:
  ```java
  @Query("select m from Member m where m.username in :names")
  List<Member> findByNames(@Param("names") List<String> names);
  ```

---

## 3. @Query — 값/DTO 직접 조회

```java
// 단순 값 조회
@Query("select m.username from Member m")
List<String> findUsernameList();

// DTO 직접 조회 — new 명령어 + 패키지 경로 필수
@Query("select new study.datajpa.dto.MemberDto(m.id, m.username, t.name) from Member m join m.team t")
List<MemberDto> findMemberDto();
```

---

## 4. 반환 타입

```java
List<Member> findByUsername(String name);          // 컬렉션 — 결과 없으면 빈 컬렉션
Member findByUsername(String name);                // 단건 — 결과 없으면 null, 2건 이상 예외
Optional<Member> findByUsername(String name);      // 단건 Optional — 권장
```

---

## 5. 페이징과 정렬

```java
// Repository 선언
Page<Member> findByAge(int age, Pageable pageable);   // count 쿼리 포함
Slice<Member> findByAge(int age, Pageable pageable);  // count 없이 다음 페이지 여부만
List<Member> findByAge(int age, Pageable pageable);   // count 없이 결과만
```

```java
// 사용 (페이지는 0부터 시작)
PageRequest pageRequest = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "username"));
Page<Member> page = memberRepository.findByAge(10, pageRequest);

page.getContent();          // 조회된 데이터
page.getTotalElements();    // 전체 데이터 수
page.getTotalPages();       // 전체 페이지 수
page.getNumber();           // 현재 페이지 번호
page.isFirst();             // 첫 페이지 여부
page.hasNext();             // 다음 페이지 여부
```

**count 쿼리 분리** (복잡한 조인 쿼리에서 필수):
```java
@Query(value = "select m from Member m left join m.team t",
       countQuery = "select count(m) from Member m")
Page<Member> findByAge(int age, Pageable pageable);
```
전체 count 쿼리는 매우 무거움 → 복잡한 SQL에서 count만 단순하게 분리해야 성능 나옴.

**Page → DTO 변환**:
```java
Page<MemberDto> dtoPage = page.map(m -> new MemberDto(m.getId(), m.getUsername(), null));
```

---

## 6. 벌크성 수정 쿼리

```java
@Modifying(clearAutomatically = true)   // 실행 후 영속성 컨텍스트 자동 초기화
@Query("update Member m set m.age = m.age + 1 where m.age >= :age")
int bulkAgePlus(@Param("age") int age);
```

- `@Modifying` 없으면 `QueryExecutionRequestException` 발생
- `clearAutomatically = true` 필수: 벌크 연산은 영속성 컨텍스트를 무시하고 DB 직접 수정 → 이후 조회 시 과거 값이 캐시에 남을 수 있음
- 권장: 영속성 컨텍스트에 엔티티가 없는 상태에서 벌크 실행, 또는 실행 직후 `clearAutomatically`로 초기화

---

## 7. @EntityGraph — fetch join 간편 버전

```java
// 공통 메서드 오버라이드
@Override
@EntityGraph(attributePaths = {"team"})
List<Member> findAll();

// 메서드 이름 쿼리 + EntityGraph
@EntityGraph(attributePaths = {"team"})
List<Member> findByUsername(String username);

// @Query + EntityGraph
@EntityGraph(attributePaths = {"team"})
@Query("select m from Member m")
List<Member> findMemberEntityGraph();
```

- 사실상 fetch join의 편의 기능 (내부적으로 LEFT OUTER JOIN)
- 간단한 fetch join에는 `@EntityGraph`, 복잡한 경우엔 JPQL `join fetch` 직접 작성

---

## 8. JPA Hint & Lock

```java
// readOnly 힌트: 스냅샷 안 만들어 메모리 절약, 변경 감지 비활성화
@QueryHints(value = @QueryHint(name = "org.hibernate.readOnly", value = "true"))
Member findReadOnlyByUsername(String username);

// 비관적 락: select ... for update
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Member> findByUsername(String name);
```

- readOnly 힌트: 조회만 하는 쿼리에서 스냅샷 생성을 막아 약간의 성능 향상. 실무에서 성능이 정말 중요한 조회 API에 선택적 적용.
- Lock: 재고 동시성 제어 등 비관적 락이 필요한 경우 사용

---

## stylink 적용 포인트

- 단순 조건 1~2개: 메서드 이름으로 쿼리 생성
- 파라미터 3개 이상 or 복잡한 조건: `@Query` + 이름 기반 파라미터
- 목록 API 페이징: `Page<T>` + countQuery 분리
- 모바일 무한 스크롤: `Slice<T>` (count 쿼리 불필요)
- 벌크 업데이트 (ex: 재고 일괄 복구): `@Modifying(clearAutomatically = true)`
- 재고 동시성 제어: `@Lock(LockModeType.PESSIMISTIC_WRITE)` (비관적 락)
- 조회 전용 Service: `@Transactional(readOnly = true)` + readOnly QueryHint 조합
