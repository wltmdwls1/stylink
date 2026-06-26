# 데이터 접근 기술 - 스프링 데이터 JPA

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
스프링 데이터 JPA = JPA를 편리하게 쓰는 도구

핵심 기능
  ① 공통 인터페이스: JpaRepository<T, ID> 상속 → 기본 CRUD 자동
  ② 쿼리 메서드: 메서드 이름 → JPQL 자동 생성
  ③ @Query: JPQL 직접 작성

어댑터 패턴
  ItemService → ItemRepository (인터페이스)
                      ↑
             JpaItemRepositoryV2 (어댑터)
                      ↓
     SpringDataJpaItemRepository (프록시, 스프링 빈)

예외 변환
  스프링 데이터 JPA가 만드는 프록시 → DataAccessException 자동 변환
  (@Repository 없어도 됨)

단점: 동적 쿼리 지원 매우 약함 → Querydsl로 해결
```

---

## JpaRepository — 공통 인터페이스

```java
public interface SpringDataJpaItemRepository extends JpaRepository<Item, Long> {
    // 기본 CRUD 자동 제공:
    // save(T), findById(ID), findAll(), deleteById(ID), count() 등
}
```

> `JpaRepository<엔티티, PK타입>`만 상속하면 스프링 데이터 JPA가 프록시 구현체를 자동 생성해서 스프링 빈으로 등록.

---

## 쿼리 메서드

```java
public interface SpringDataJpaItemRepository extends JpaRepository<Item, Long> {
    // 메서드 이름 → JPQL 자동 생성
    List<Item> findByItemNameLike(String itemName);
    // → select i from Item i where i.itemName like ?

    List<Item> findByPriceLessThanEqual(Integer price);
    // → select i from Item i where i.price <= ?

    List<Item> findByItemNameLikeAndPriceLessThanEqual(String itemName, Integer price);
    // → select i from Item i where i.itemName like ? and i.price <= ?
    // 조건 2개만 돼도 메서드 이름이 길어지는 단점

    // @Query: JPQL 직접 작성 (복잡한 쿼리에 사용)
    @Query("select i from Item i where i.itemName like :itemName and i.price <= :price")
    List<Item> findItems(@Param("itemName") String itemName, @Param("price") Integer price);
}
```

### 쿼리 메서드 키워드

| 기능 | 키워드 예시 |
|------|-----------|
| 조회 | `findBy`, `readBy`, `queryBy`, `getBy` |
| COUNT | `countBy` → `long` |
| EXISTS | `existsBy` → `boolean` |
| 삭제 | `deleteBy`, `removeBy` |
| DISTINCT | `findDistinctBy` |
| LIMIT | `findFirst3`, `findTop3` |

---

## 어댑터 패턴 — JpaItemRepositoryV2

```java
@Repository
@Transactional
@RequiredArgsConstructor
public class JpaItemRepositoryV2 implements ItemRepository {
    private final SpringDataJpaItemRepository repository;  // 프록시 주입

    @Override
    public Item save(Item item) {
        return repository.save(item);
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        Item findItem = repository.findById(itemId).orElseThrow();
        findItem.setItemName(updateParam.getItemName());
        findItem.setPrice(updateParam.getPrice());
        findItem.setQuantity(updateParam.getQuantity());
        // 변경 감지 → 트랜잭션 커밋 시 UPDATE 자동 실행
    }

    @Override
    public Optional<Item> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {
        String itemName = cond.getItemName();
        Integer maxPrice = cond.getMaxPrice();

        // 동적 쿼리 불가 → 조건 조합으로 직접 분기 (비효율, Querydsl로 해결)
        if (StringUtils.hasText(itemName) && maxPrice != null) {
            return repository.findItems("%" + itemName + "%", maxPrice);
        } else if (StringUtils.hasText(itemName)) {
            return repository.findByItemNameLike("%" + itemName + "%");
        } else if (maxPrice != null) {
            return repository.findByPriceLessThanEqual(maxPrice);
        } else {
            return repository.findAll();
        }
    }
}
```

### 의존 관계

```
ItemService → ItemRepository (인터페이스)
                    ↑ implements
          JpaItemRepositoryV2 (어댑터: ItemRepository 구현 + SpringDataJpaItemRepository 사용)
                    ↓ uses
     SpringDataJpaItemRepository (프록시, JpaRepository 상속)
```

> `ItemService` 코드 변경 없이 `JpaItemRepositoryV2`만 추가해서 스프링 데이터 JPA 통합.

---

## 예외 변환

```
스프링 데이터 JPA 프록시
  → JPA 예외 발생 시 DataAccessException으로 자동 변환
  → @Repository 어노테이션 없어도 변환됨 (프록시가 이미 처리)
```

---

## 한계 — 동적 쿼리

```
스프링 데이터 JPA 동적 쿼리 지원: 매우 약함
  findAll()에서 조건 조합 → if/else 4가지 분기 필요
  조건이 늘어날수록 메서드 이름이 폭발적으로 길어짐

→ 해결책: Querydsl (다음 챕터)
```

---

## 정리

| 항목 | 내용 |
|------|------|
| **장점** | 기본 CRUD 자동, 쿼리 메서드로 간단한 쿼리 생성, 페이징 지원 |
| **단점** | 동적 쿼리 지원 약함 |
| **실무 조합** | 스프링 데이터 JPA(기본 CRUD) + Querydsl(동적 쿼리) |

---

## 면접 Q&A

**Q: 스프링 데이터 JPA의 쿼리 메서드는 어떻게 동작하는가?**
A: 스프링 데이터 JPA는 `JpaRepository`를 상속한 인터페이스의 메서드 이름을 런타임에 분석한다. `findByItemNameLike`처럼 규칙에 맞게 작성된 메서드 이름을 파싱해서 `select i from Item i where i.itemName like ?` 같은 JPQL을 자동으로 만들고, JPA가 이를 DB SQL로 변환해서 실행한다. 조건이 복잡해지면 메서드 이름이 너무 길어지므로 `@Query("JPQL...")` 어노테이션으로 직접 JPQL을 작성하는 것이 낫다.

**Q: 스프링 데이터 JPA를 기존 서비스 코드 변경 없이 적용하려면?**
A: 어댑터 패턴을 사용한다. `ItemService`는 `ItemRepository` 인터페이스에 의존하고 있으므로, `SpringDataJpaItemRepository`(JpaRepository 상속)를 직접 사용할 수 없다. 중간에 `JpaItemRepositoryV2`를 만들어서 `ItemRepository`를 구현하고 내부에서 `SpringDataJpaItemRepository`를 사용하도록 한다. 이렇게 하면 `ItemService` 코드는 한 줄도 바꾸지 않고 스프링 데이터 JPA로 구현체를 교체할 수 있다.

**Q: 스프링 데이터 JPA의 동적 쿼리 한계와 해결 방법은?**
A: 스프링 데이터 JPA는 동적 쿼리 지원이 매우 약하다. 조건에 따라 다른 메서드를 호출해야 하므로 조건 N개면 2^N가지 if/else 분기가 필요하고, 메서드 이름도 길어진다. `Example` 기능이 있지만 실무에서 쓰기 어렵다. 해결책은 Querydsl이다 — 타입 안전한 동적 쿼리 빌더를 제공해서 `where` 조건을 `null` 체크로 간결하게 조합할 수 있다.
