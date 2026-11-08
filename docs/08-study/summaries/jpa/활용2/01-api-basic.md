# 01. API 개발 기본

## 전체 흐름 한눈에 보기

```
V1: 엔티티 직접 노출 (절대 금지)
V2: DTO 사용 — Request DTO 수신 / Response DTO 반환
결론: 엔티티는 API 스펙에 절대 노출하지 않는다
```

---

## 1. 엔티티를 직접 받는 V1 — 쓰지 마라

```java
// 절대 이렇게 하지 마라
@PostMapping("/api/v1/members")
public CreateMemberResponse saveMemberV1(@RequestBody @Valid Member member) {
    Long id = memberService.join(member);
    return new CreateMemberResponse(id);
}
```

**문제점**:
- 엔티티에 `@NotEmpty` 같은 API 검증 로직이 들어감
- 엔티티 필드 변경 시 API 스펙이 바뀜 (클라이언트 장애)
- 한 엔티티에 여러 API 요구사항을 담을 수 없음

---

## 2. DTO 사용 V2 — 권장

```java
@PostMapping("/api/v2/members")
public CreateMemberResponse saveMemberV2(@RequestBody @Valid CreateMemberRequest request) {
    Member member = new Member();
    member.setName(request.getName());
    Long id = memberService.join(member);
    return new CreateMemberResponse(id);
}

@Data
static class CreateMemberRequest { private String name; }

@Data
static class CreateMemberResponse {
    private Long id;
    public CreateMemberResponse(Long id) { this.id = id; }
}
```

엔티티가 변해도 API 스펙 불변. 요청/응답 필드가 명확히 드러남.

---

## 3. 수정 API — 변경 감지 사용

```java
@PutMapping("/api/v2/members/{id}")
public UpdateMemberResponse updateMemberV2(@PathVariable Long id,
                                           @RequestBody @Valid UpdateMemberRequest request) {
    memberService.update(id, request.getName());
    Member findMember = memberService.findOne(id);
    return new UpdateMemberResponse(findMember.getId(), findMember.getName());
}

// Service
@Transactional
public void update(Long id, String name) {
    Member member = memberRepository.findOne(id);
    member.setName(name);  // 변경 감지 → 자동 UPDATE
}
```

- `update()`는 `void` — 커맨드와 쿼리를 분리 (CQS 패턴)
- 수정 후 응답 데이터는 별도 조회로 가져옴
- 부분 업데이트는 `PATCH` 사용이 REST 원칙에 맞음 (강의는 PUT 사용, 실무에서는 주의)

---

## 4. 조회 API — 컬렉션을 Result로 감싸기

```java
// V1: 리스트 직접 반환 — 금지 (향후 필드 추가 불가)
@GetMapping("/api/v1/members")
public List<Member> membersV1() { return memberService.findMembers(); }

// V2: Result 클래스로 감싸기
@GetMapping("/api/v2/members")
public Result membersV2() {
    List<MemberDto> collect = memberService.findMembers().stream()
            .map(m -> new MemberDto(m.getName()))
            .collect(Collectors.toList());
    return new Result(collect);
}

@Data @AllArgsConstructor
static class Result<T> { private T data; }

@Data @AllArgsConstructor
static class MemberDto { private String name; }
```

**Result 래퍼 이유**: JSON 배열을 직접 반환하면 나중에 `count`, `totalCount` 같은 필드 추가 불가 → `{ "data": [...] }` 구조로 감싸면 확장 가능.

---

## stylink 적용 포인트

**강의 권장 방식**: 엔티티 절대 노출 금지, Request/Response DTO 분리, Result 래퍼, 변경 감지 사용.

**stylink 적용**:
- 모든 Controller에서 Request DTO(`@RequestBody`) → Service(id + 값) → Response DTO 반환 패턴 적용
- `ApiResponse<T>` 래퍼가 이미 `common` 모듈에 있음 → 컬렉션 응답도 `ApiResponse<List<XxxDto>>`로 반환
- 엔티티에 `@JsonIgnore`, `@NotEmpty` 등 API 어노테이션 절대 금지
- 수정 API는 `PATCH /resource/{id}` 방식 사용 (부분 업데이트)
