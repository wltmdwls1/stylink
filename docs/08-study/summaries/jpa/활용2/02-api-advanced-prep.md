# 02. API 개발 고급 - 준비

## 전체 흐름 한눈에 보기

```
샘플 데이터 세팅 → 이후 챕터의 N+1, 컬렉션 조회 최적화 실습 기반
```

---

## 샘플 데이터 초기화 패턴

`@PostConstruct` + `@Transactional`을 분리하는 이유: 스프링의 `@PostConstruct`와 `@Transactional`을 같은 메서드에 함께 쓰면 AOP 프록시 타이밍 문제가 발생할 수 있음 → `@PostConstruct`는 호출만 하고 실제 트랜잭션 작업은 별도 `@Component`에 위임.

```java
@Component
@RequiredArgsConstructor
public class InitDb {
    private final InitService initService;

    @PostConstruct
    public void init() {
        initService.dbInit1();  // 트랜잭션 처리는 InitService에 위임
        initService.dbInit2();
    }

    @Component
    @Transactional
    @RequiredArgsConstructor
    static class InitService {
        private final EntityManager em;

        public void dbInit1() {
            Member member = createMember("userA", "서울", "1", "1111");
            em.persist(member);
            Book book1 = createBook("JPA1 BOOK", 10000, 100);
            em.persist(book1);
            // OrderItem.createOrderItem() 정적 팩토리 사용
            OrderItem orderItem1 = OrderItem.createOrderItem(book1, 10000, 1);
            Order order = Order.createOrder(member, createDelivery(member), orderItem1);
            em.persist(order);
        }
    }
}
```

**핵심**: `@PostConstruct` + `@Transactional` 분리 패턴 — 트랜잭션이 필요한 초기화 작업은 별도 빈에 위임.

---

## stylink 적용 포인트

**강의 권장 방식**: 초기 데이터 세팅은 `@PostConstruct` + 별도 `@Transactional` 빈 위임 패턴.

**stylink 적용**:
- 개발/테스트 환경 샘플 데이터 초기화가 필요하면 이 패턴 사용
- `DataInitializer` 같은 별도 컴포넌트로 분리하여 `@Profile("local")` 조건부 실행
