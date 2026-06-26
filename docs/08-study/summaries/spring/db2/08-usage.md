# 데이터 접근 기술 - 활용 방안

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
핵심 질문: 어떤 구조가 더 나은가?

방식 A — 어댑터 패턴 (구조의 안정성)
  ItemService → ItemRepository (인터페이스)
                      ↑
             JpaItemRepositoryV2 (어댑터)
                      ↓
     SpringDataJpaItemRepository (프록시)
  장점: DI/OCP 원칙 준수, ItemService 코드 불변
  단점: 어댑터 추가 코드 유지, 구조 복잡

방식 B — 실용적 구조 (단순함)
  ItemServiceV2 → ItemRepositoryV2 (스프링 데이터 JPA)
               → ItemQueryRepositoryV2 (Querydsl)
  장점: 구조 단순, 각 기술의 장점 최대 활용
  단점: OCP 포기, 서비스 코드 변경 필요

→ 정답 없음. 프로젝트 규모·속도·팀 역량에 따라 선택
```

---

## 어댑터 패턴 vs 실용적 구조 — 트레이드 오프

| 항목 | 어댑터 패턴 | 실용적 구조 |
|------|-----------|-----------|
| OCP/DI 원칙 | 유지 | 포기 |
| 구조 복잡도 | 복잡 (어댑터 추가) | 단순 |
| 코드 양 | 많음 | 적음 |
| 기술 변경 시 서비스 코드 | 변경 불필요 | 변경 필요 |
| 추천 상황 | 대규모, 기술 교체 가능성 높음 | 프로토타입, 소규모, 빠른 개발 |

> **핵심 원칙**: 추상화도 비용이 든다. 추상화의 이점이 유지보수 비용을 넘어설 때만 도입하라.

---

## 실용적 구조 — 스프링 데이터 JPA + Querydsl 분리

```java
// 기본 CRUD: 스프링 데이터 JPA
public interface ItemRepositoryV2 extends JpaRepository<Item, Long> {
    // save, findById, findAll 등 자동 제공
}

// 복잡한 동적 쿼리: Querydsl 전담
@Repository
public class ItemQueryRepositoryV2 {
    private final JPAQueryFactory query;

    public ItemQueryRepositoryV2(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    public List<Item> findAll(ItemSearchCond cond) {
        return query.select(item)
                .from(item)
                .where(maxPrice(cond.getMaxPrice()), likeItemName(cond.getItemName()))
                .fetch();
    }

    private BooleanExpression likeItemName(String itemName) {
        return StringUtils.hasText(itemName) ? item.itemName.like("%" + itemName + "%") : null;
    }

    private BooleanExpression maxPrice(Integer maxPrice) {
        return maxPrice != null ? item.price.loe(maxPrice) : null;
    }
}

// 서비스: 두 리포지토리 직접 참조 (어댑터 없음)
@Service
@Transactional
@RequiredArgsConstructor
public class ItemServiceV2 implements ItemService {
    private final ItemRepositoryV2 itemRepositoryV2;       // 기본 CRUD
    private final ItemQueryRepositoryV2 itemQueryRepositoryV2;  // 복잡한 조회

    @Override
    public Item save(Item item) { return itemRepositoryV2.save(item); }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        Item findItem = findById(itemId).orElseThrow();
        findItem.setItemName(updateParam.getItemName());
        findItem.setPrice(updateParam.getPrice());
        findItem.setQuantity(updateParam.getQuantity());
    }

    @Override
    public Optional<Item> findById(Long id) { return itemRepositoryV2.findById(id); }

    @Override
    public List<Item> findItems(ItemSearchCond cond) {
        return itemQueryRepositoryV2.findAll(cond);
    }
}
```

---

## 데이터 접근 기술 조합 전략

```
실무 권장 조합:
  95%: JPA + 스프링 데이터 JPA + Querydsl
   5%: 복잡한 통계/리포트 쿼리 → JdbcTemplate or MyBatis 병용

기술 선택 기준:
  SQL 직접 작성 편한 팀 → JdbcTemplate / MyBatis (학습 곡선 낮음)
  개발 생산성 중시, JPA 학습 의지 → JPA + 스프링 데이터 JPA + Querydsl
  복잡한 통계 쿼리 많음 → JdbcTemplate / MyBatis 비중 높임
```

---

## 트랜잭션 매니저 혼용 시 주의점

```
JPA 기술: JpaTransactionManager
JDBC 기술(JdbcTemplate/MyBatis): DataSourceTransactionManager

→ 함께 사용 시 트랜잭션 문제?
  걱정 없음: JpaTransactionManager는 DataSourceTransactionManager 기능도 제공
  → JpaTransactionManager 하나로 JPA + JdbcTemplate + MyBatis 모두 하나의 트랜잭션으로 묶기 가능

주의 - JPA 플러시 타이밍:
  JPA는 트랜잭션 커밋 시점에 변경사항을 DB에 반영 (지연 쓰기)
  JPA로 변경 후 → JdbcTemplate으로 조회 시 → JPA 변경 내용 미반영 상태
  해결: JPA 변경 후 em.flush() 호출하면 JdbcTemplate이 변경된 데이터 읽을 수 있음
```

---

## 정리

| 선택 | 상황 |
|------|------|
| 어댑터 패턴 | 대규모 프로젝트, 기술 교체 가능성, OCP 중요 |
| 실용적 구조 | 소규모·프로토타입·빠른 개발, 팀원이 트레이드 오프 인지 |

> **결론**: 추상화의 비용을 알고, 현재 상황에 맞는 선택을 하는 개발자가 좋은 개발자.

---

## 면접 Q&A

**Q: JPA와 JdbcTemplate을 함께 사용할 때 트랜잭션은 어떻게 관리하는가?**
A: `JpaTransactionManager`는 `DataSourceTransactionManager`가 제공하는 기능도 함께 제공한다. JPA가 내부적으로 JDBC 커넥션을 사용하기 때문이다. 따라서 `JpaTransactionManager` 하나를 스프링 빈으로 등록하면 JPA, JdbcTemplate, MyBatis 모두를 하나의 트랜잭션으로 묶을 수 있고, 함께 커밋/롤백도 된다. 단, JPA의 변경 감지는 트랜잭션 커밋 시점에 플러시되므로, JPA로 변경한 뒤 같은 트랜잭션 내에서 JdbcTemplate으로 해당 데이터를 읽으려면 `em.flush()`를 명시적으로 호출해야 한다.

**Q: 어댑터 패턴으로 OCP를 지키는 것과 단순 구조를 선택하는 것 중 어느 것이 좋은가?**
A: 정답이 없는 트레이드 오프 문제다. 어댑터 패턴은 `ItemService` 코드를 변경하지 않고 구현체를 교체할 수 있어 OCP/DI 원칙을 지킬 수 있지만, 어댑터 클래스와 실제 구현 클래스 두 곳을 유지보수해야 한다. 단순 구조는 어댑터 없이 서비스가 리포지토리를 직접 사용하므로 코드가 적고 이해하기 쉽지만, 기술이 바뀌면 서비스 코드도 함께 변경해야 한다. 추상화도 비용이 들기 때문에, 프로젝트 규모가 작거나 기술 교체 가능성이 낮다면 단순 구조가 더 나을 수 있다.
