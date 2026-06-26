# 데이터 접근 기술 - Querydsl

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
Querydsl = 타입 안전한 동적 쿼리 빌더 (JPA 기반)

기존 문제
  JPQL: 문자열 → 오타 시 런타임에야 발견
  스프링 데이터 JPA: 동적 쿼리 지원 약함

Querydsl 해결
  자바 코드로 쿼리 작성 → 컴파일 시점 오타 감지
  where(null) → 조건 무시 → 동적 쿼리 간결

핵심 구성
  Q타입: @Entity → Querydsl이 APT로 QItem.java 자동 생성
  JPAQueryFactory: 쿼리 빌더, EntityManager 필요
  BooleanExpression: 조건 조각, null이면 무시

예외 변환
  @Repository → DataAccessException 자동 변환 (JPA와 동일)
```

---

## 설정

```groovy
// build.gradle (Boot 2.x)
implementation 'com.querydsl:querydsl-jpa'
annotationProcessor "com.querydsl:querydsl-apt:${dependencyManagement.importedProperties['querydsl.version']}:jpa"
annotationProcessor "jakarta.annotation:jakarta.annotation-api"
annotationProcessor "jakarta.persistence:jakarta.persistence-api"

// build.gradle (Boot 3.x) - jpa → jakarta
implementation 'com.querydsl:querydsl-jpa:5.0.0:jakarta'
annotationProcessor "com.querydsl:querydsl-apt:${dependencyManagement.importedProperties['querydsl.version']}:jakarta"
annotationProcessor "jakarta.annotation:jakarta.annotation-api"
annotationProcessor "jakarta.persistence:jakarta.persistence-api"

// src/main/generated 자동 삭제 (IntelliJ IDEA 빌드 옵션일 때)
clean {
    delete file('src/main/generated')
}
```

### Q타입 생성

```
Gradle 빌드 옵션:
  ./gradlew clean compileJava
  → build/generated/.../QItem.java 생성

IntelliJ IDEA 빌드 옵션:
  Build → Build Project
  → src/main/generated/.../QItem.java 생성

주의: Q타입은 컴파일 시 자동 생성 → .gitignore에 추가 (버전관리 불필요)
```

---

## JpaItemRepositoryV3 — Querydsl 적용

```java
import static hello.itemservice.domain.QItem.*;  // QItem.item을 item으로 간략하게 사용

@Repository
@Transactional
public class JpaItemRepositoryV3 implements ItemRepository {
    private final EntityManager em;
    private final JPAQueryFactory query;

    public JpaItemRepositoryV3(EntityManager em) {
        this.em = em;
        this.query = new JPAQueryFactory(em);  // JPAQueryFactory 생성 (스프링 빈 등록도 가능)
    }

    @Override
    public Item save(Item item) {
        em.persist(item);
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        Item findItem = findById(itemId).orElseThrow();
        findItem.setItemName(updateParam.getItemName());
        findItem.setPrice(updateParam.getPrice());
        findItem.setQuantity(updateParam.getQuantity());
    }

    @Override
    public Optional<Item> findById(Long id) {
        Item item = em.find(Item.class, id);
        return Optional.ofNullable(item);
    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {
        String itemName = cond.getItemName();
        Integer maxPrice = cond.getMaxPrice();

        return query
                .select(item)
                .from(item)
                .where(likeItemName(itemName), maxPrice(maxPrice))
                // where(A, B): AND 조건. null이면 해당 조건 무시 → 동적 쿼리 자동 처리
                .fetch();
    }

    private BooleanExpression likeItemName(String itemName) {
        if (StringUtils.hasText(itemName)) {
            return item.itemName.like("%" + itemName + "%");
        }
        return null;  // null → 조건 무시
    }

    private BooleanExpression maxPrice(Integer maxPrice) {
        if (maxPrice != null) {
            return item.price.loe(maxPrice);  // loe = less than or equal (<=)
        }
        return null;  // null → 조건 무시
    }
}
```

---

## 동적 쿼리 비교

```java
// 기존 방식 (JPQL 문자열 조합) — 복잡하고 오타 위험
String jpql = "select i from Item i";
if (...) jpql += " where ...";
if (...) jpql += " and ...";

// BooleanBuilder 방식 — 명시적이지만 약간 장황
BooleanBuilder builder = new BooleanBuilder();
if (StringUtils.hasText(itemName)) builder.and(item.itemName.like("%" + itemName + "%"));
if (maxPrice != null) builder.and(item.price.loe(maxPrice));
query.select(item).from(item).where(builder).fetch();

// BooleanExpression + null 방식 (권장) — 깔끔 + 재사용 가능
query.select(item).from(item)
     .where(likeItemName(itemName), maxPrice(maxPrice))
     .fetch();
```

> `where(null, expr)` → null은 무시, `expr`만 적용  
> `where(null, null)` → WHERE절 없음 (전체 조회)  
> `likeItemName()`, `maxPrice()` 메서드는 다른 쿼리에서도 재사용 가능

---

## Querydsl 장점 정리

| 기능 | 내용 |
|------|------|
| **타입 안전** | 자바 코드 → 컴파일 시점에 오타/타입 오류 감지 |
| **동적 쿼리** | `null` 조건 무시 → 간결하고 안전한 동적 쿼리 |
| **재사용** | `BooleanExpression` 메서드 → 여러 쿼리에서 공유 |
| **가독성** | 자바 코드 스타일 → 메서드 체인, 자동완성 지원 |

---

## 실무 조합

```
스프링 데이터 JPA (기본 CRUD, 단순 조회)
  +
Querydsl (복잡한 동적 쿼리, DTO 조회)
  +
JPA EntityManager (save/find, 간단한 경우)
```

---

## 면접 Q&A

**Q: Querydsl이 JPQL보다 나은 이유는?**
A: 두 가지 핵심 이유가 있다. 첫째, JPQL은 문자열이라서 오타가 있어도 컴파일 시점에 발견할 수 없고 런타임에 예외가 발생한다. Querydsl은 자바 코드로 쿼리를 작성하므로 오타나 존재하지 않는 필드를 참조하면 컴파일 에러로 즉시 발견된다. 둘째, 동적 쿼리에서 JPQL은 문자열을 직접 조합해야 해서 복잡하지만, Querydsl은 `where(null)` 조건 무시 특성을 이용해 `BooleanExpression` 메서드를 조합하는 방식으로 깔끔하게 작성할 수 있다.

**Q: Querydsl의 Q타입이란 무엇이고 어떻게 생성되는가?**
A: Q타입은 Querydsl이 `@Entity` 어노테이션이 붙은 클래스를 분석해서 APT(Annotation Processing Tool)로 자동 생성하는 메타 클래스다. `Item` 엔티티에서 `QItem`이 생성되고, `QItem.item.itemName`, `QItem.item.price`처럼 타입 안전하게 필드를 참조할 수 있다. `./gradlew compileJava`나 IntelliJ에서 `Build → Build Project`를 실행하면 생성된다. Q타입은 소스 코드가 아니라 빌드 산출물이므로 `.gitignore`에 추가해서 버전관리에서 제외하는 것이 일반적이다.

**Q: Querydsl에서 예외 변환은 어떻게 처리되는가?**
A: Querydsl은 별도의 스프링 예외 추상화를 제공하지 않는다. 대신 리포지토리에 `@Repository`를 붙이면 JPA와 동일한 방식으로 AOP 프록시가 `PersistenceException`을 `DataAccessException`으로 자동 변환한다. Querydsl은 내부적으로 JPA의 `EntityManager`와 JPQL을 사용하므로 JPA 예외 변환 메커니즘을 그대로 활용한다.
