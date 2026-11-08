# 06. 스프링 데이터 JPA 분석

## 1. SimpleJpaRepository 구현체

```java
@Repository
@Transactional(readOnly = true)   // 조회는 기본 readOnly
public class SimpleJpaRepository<T, ID> {

    @Transactional    // 변경(등록/수정/삭제)은 readOnly 덮어씀
    public <S extends T> S save(S entity) {
        if (entityInformation.isNew(entity)) {
            em.persist(entity);
            return entity;
        } else {
            return em.merge(entity);
        }
    }
}
```

**핵심 동작 원리**:
- `@Transactional(readOnly = true)`: 클래스 레벨 기본값 → flush 생략으로 약간의 성능 향상
- 변경 메서드(`save`, `delete` 등)는 `@Transactional`로 readOnly 오버라이드
- 서비스에서 트랜잭션 시작 시 → 리포지토리가 해당 트랜잭션에 참여 (전파)
- 서비스에서 트랜잭션 없으면 → 리포지토리가 트랜잭션 자체 시작

---

## 2. 새로운 엔티티를 구별하는 방법 (매우 중요)

### `save()` 동작

| 상황 | 동작 |
|------|------|
| 새로운 엔티티 | `em.persist()` |
| 기존 엔티티 | `em.merge()` — DB 조회 후 병합 |

### 기본 판단 기준

| 식별자 타입 | 신규 판단 기준 |
|------------|--------------|
| 객체 (`Long`, `String` 등) | `null`이면 신규 |
| 자바 기본 타입 (`long`, `int`) | `0`이면 신규 |

### 문제 상황: `@Id` 직접 할당 시

```java
// @GeneratedValue 없이 직접 id 할당
Item item = new Item("itemA");  // id = "itemA" (null이 아님)
itemRepository.save(item);       // → merge() 호출 → DB 조회 → 없으면 insert
                                 // 불필요한 DB 조회가 먼저 발생!
```

### 해결: `Persistable` 구현 + `@CreatedDate` 활용

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Item implements Persistable<String> {
    @Id
    private String id;

    @CreatedDate
    private LocalDateTime createdDate;

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() {
        return createdDate == null;  // createdDate가 null이면 신규 엔티티
    }
}
```

`@CreatedDate`는 `persist` 직전(`@PrePersist`)에 세팅되므로:
- save() 호출 → isNew() 확인 → createdDate == null → 신규 → persist 호출 → @PrePersist → createdDate 세팅

---

## stylink 적용 포인트

- stylink 엔티티는 `@GeneratedValue` 사용 → 기본 신규 판단 로직으로 정상 작동
- 만약 UUID나 사용자 정의 ID 전략을 쓰게 되면 반드시 `Persistable` 구현 필요
- `@Transactional(readOnly = true)`: 조회 전용 Service 메서드에 적용 → `SimpleJpaRepository`와 동일한 전략, flush 생략으로 성능 향상
- 서비스 계층에서 `@Transactional` 시작 후 Repository 호출 → 트랜잭션 전파로 하나의 트랜잭션에서 처리됨 (당연한 JPA 동작이지만 내부적으로 이 구조임을 이해)
