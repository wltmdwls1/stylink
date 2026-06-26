# 데이터 접근 기술 - JPA

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
JPA = ORM (Object-Relational Mapping)
  SQL Mapper(JdbcTemplate, MyBatis): SQL 개발자가 직접 작성
  ORM(JPA):                          SQL JPA가 자동 생성

핵심 구성
  @Entity + @Id + @Column → 객체-테이블 매핑
  EntityManager → 모든 JPA 동작의 진입점
  JPQL → 객체(엔티티) 기준 쿼리 언어

자동 처리
  em.persist(item)   → INSERT SQL 자동 생성
  findItem.setXxx()  → UPDATE SQL 자동 생성 (변경 감지, 더티 체킹)
  em.find()          → SELECT SQL 자동 생성

예외 변환
  @Repository → AOP 프록시 → JPA PersistenceException → DataAccessException
```

---

## 설정

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
// spring-boot-starter-jdbc 포함 → 별도 추가 불필요
```

```properties
# application.properties (main + test 둘 다)
# Boot 2.x / Hibernate 5
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# Boot 3.x / Hibernate 6
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

> 스프링 부트가 `EntityManagerFactory`, `JpaTransactionManager`, DataSource를 자동 설정.

---

## 엔티티 매핑

```java
@Data
@Entity
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // DB auto_increment
    private Long id;

    @Column(name = "item_name", length = 10)  // 컬럼명 명시 (생략 시 camelCase → snake_case 자동)
    private String itemName;

    private Integer price;     // @Column 생략: price → price 그대로
    private Integer quantity;

    public Item() {}  // JPA 필수: public/protected 기본 생성자
    public Item(String itemName, Integer price, Integer quantity) { ... }
}
```

| 어노테이션 | 역할 |
|-----------|------|
| `@Entity` | JPA 관리 대상 엔티티 |
| `@Id` | PK 매핑 |
| `@GeneratedValue(IDENTITY)` | DB auto_increment |
| `@Column(name=...)` | 컬럼명 명시 (스프링 부트: camelCase → snake_case 자동 변환) |

---

## JpaItemRepositoryV1

```java
@Slf4j
@Repository
@Transactional  // JPA 데이터 변경은 트랜잭션 필수 (보통은 서비스 계층에 걸지만 예제라 리포지토리에)
public class JpaItemRepositoryV1 implements ItemRepository {
    private final EntityManager em;  // 스프링이 주입, 내부에 DataSource 보유

    public JpaItemRepositoryV1(EntityManager em) {
        this.em = em;
    }

    @Override
    public Item save(Item item) {
        em.persist(item);  // INSERT SQL 자동 생성 + 생성된 PK → item.id에 자동 세팅
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        Item findItem = em.find(Item.class, itemId);
        findItem.setItemName(updateParam.getItemName());
        findItem.setPrice(updateParam.getPrice());
        findItem.setQuantity(updateParam.getQuantity());
        // em.update() 호출 없음! 트랜잭션 커밋 시점에 변경 감지(더티 체킹) → UPDATE SQL 자동 실행
    }

    @Override
    public Optional<Item> findById(Long id) {
        Item item = em.find(Item.class, id);  // PK 기준 단건 조회 → SELECT SQL 자동 생성
        return Optional.ofNullable(item);
    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {
        // 복잡한 조건 조회: JPQL 사용
        String jpql = "select i from Item i";
        // 동적 쿼리 (JPA도 JPQL 동적 쿼리는 코드로 직접 조합 → Querydsl로 해결)
        if (StringUtils.hasText(itemName) || maxPrice != null) {
            jpql += " where";
        }
        if (StringUtils.hasText(itemName)) {
            jpql += " i.itemName like concat('%',:itemName,'%')";
        }
        if (maxPrice != null) {
            jpql += " i.price <= :maxPrice";
        }

        TypedQuery<Item> query = em.createQuery(jpql, Item.class);
        if (StringUtils.hasText(itemName)) query.setParameter("itemName", itemName);
        if (maxPrice != null) query.setParameter("maxPrice", maxPrice);
        return query.getResultList();
    }
}
```

---

## JPQL

```sql
-- SQL: 테이블 대상
SELECT * FROM item WHERE item_name LIKE '%test%'

-- JPQL: 엔티티 객체 대상
select i from Item i where i.itemName like concat('%',:itemName,'%')
-- Item: 엔티티 클래스명 (대소문자 구분)
-- i.itemName: 엔티티 필드명
-- JPQL → Hibernate가 실제 DB SQL로 변환하여 실행
```

---

## JPA 동작 원리 정리

| 메서드 | 생성되는 SQL |
|--------|------------|
| `em.persist(item)` | `INSERT INTO item (item_name, price, quantity) VALUES (?, ?, ?)` |
| `findItem.setXxx()` | `UPDATE item SET item_name=?, price=?, quantity=? WHERE id=?` (트랜잭션 커밋 시) |
| `em.find(Item.class, id)` | `SELECT id, item_name, price, quantity FROM item WHERE id=?` |
| `em.createQuery(jpql).getResultList()` | JPQL → SQL 변환 후 실행 |

> **변경 감지(더티 체킹)**: JPA는 영속성 컨텍스트에서 엔티티를 조회할 때 스냅샷을 저장. 트랜잭션 커밋 시 스냅샷과 비교해 변경된 필드가 있으면 UPDATE SQL 자동 실행. 개발자가 `update()` 호출 불필요.

---

## @Repository 예외 변환 AOP

```
EntityManager → JPA 예외 발생 (PersistenceException)

@Repository 없을 때:
  PersistenceException이 그대로 서비스로 전파 → JPA 기술 의존

@Repository 있을 때:
  스프링이 AOP 프록시 생성
    → PersistenceException 발생 시 PersistenceExceptionTranslator가 감지
    → DataAccessException (스프링 예외 추상화)으로 자동 변환
    → 서비스 계층은 JPA 모름, 스프링 표준 예외만 처리

동작 흐름:
  em.persist() 예외 → @Repository AOP 프록시 → PersistenceExceptionTranslationPostProcessor
    → EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible()
    → DataAccessException (예: DataIntegrityViolationException)
```

---

## 정리

| 기능 | 내용 |
|------|------|
| **장점** | SQL 자동 생성, 변경 감지, 객체 중심 개발 |
| **단점** | 동적 쿼리 여전히 불편 → Querydsl로 해결 |
| **실무 조합** | JPA + 스프링 데이터 JPA + Querydsl |

---

## 면접 Q&A

**Q: JPA의 변경 감지(더티 체킹)란 무엇인가?**
A: JPA는 엔티티를 영속성 컨텍스트에서 조회할 때 해당 엔티티의 스냅샷을 저장한다. 트랜잭션이 커밋되는 시점에 현재 엔티티 상태와 스냅샷을 비교해서 변경된 필드가 있으면 UPDATE SQL을 자동으로 생성하고 실행한다. 이를 더티 체킹(dirty checking)이라고 한다. 개발자가 `em.update()` 같은 메서드를 따로 호출하지 않아도 된다. 단, 변경 감지는 트랜잭션 안에서만 동작한다.

**Q: JPQL이 SQL과 다른 점은?**
A: SQL은 테이블과 컬럼을 대상으로 쿼리를 작성하지만, JPQL은 엔티티 객체와 필드를 대상으로 작성한다. 예를 들어 `select i from Item i where i.itemName = :name`처럼 `Item`은 테이블명이 아닌 엔티티 클래스명이고, `i.itemName`도 컬럼명이 아닌 자바 필드명이다. JPQL을 실행하면 Hibernate가 엔티티 매핑 정보를 기반으로 실제 DB SQL로 변환해서 실행한다. 덕분에 DB 벤더가 바뀌어도 JPQL 코드는 그대로 유지된다.

**Q: @Repository 어노테이션이 JPA 예외 변환에서 하는 역할은?**
A: `@Repository`가 붙은 클래스는 스프링이 예외 변환 AOP 프록시를 생성한다. JPA의 `EntityManager`에서 `PersistenceException`(JPA 전용 예외)이 발생하면, AOP 프록시가 `PersistenceExceptionTranslator`를 통해 스프링의 `DataAccessException`으로 자동 변환한다. 덕분에 서비스 계층은 JPA에 의존하지 않고 스프링 표준 예외(`DuplicateKeyException` 등)만 처리하면 된다.
