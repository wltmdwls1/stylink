# 03. API 개발 고급 - 지연 로딩과 조회 성능 최적화

## 전체 흐름 한눈에 보기

```
V1: 엔티티 직접 노출 (절대 금지 — 양방향 무한루프, LAZY 프록시 직렬화 오류)
V2: 엔티티 → DTO 변환 (N+1 문제 발생)
V3: 엔티티 → DTO + fetch join (쿼리 1번, 실무 권장)
V4: JPA에서 DTO 직접 조회 (select 절 최적화, 재사용성 낮음)
```

---

## 1. N+1 문제 — V2의 치명적 단점

```java
// V2: 엔티티를 DTO로 변환하지만 N+1 발생
@GetMapping("/api/v2/simple-orders")
public List<SimpleOrderDto> ordersV2() {
    List<Order> orders = orderRepository.findAllByString(new OrderSearch());
    return orders.stream().map(SimpleOrderDto::new).collect(toList());
}

@Data
static class SimpleOrderDto {
    private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;

    public SimpleOrderDto(Order order) {
        orderId = order.getId();
        name = order.getMember().getName();       // LAZY 로딩 → 쿼리 1번
        orderDate = order.getOrderDate();
        orderStatus = order.getStatus();
        address = order.getDelivery().getAddress(); // LAZY 로딩 → 쿼리 1번
    }
}
```

**쿼리 횟수**: `1 (order 조회) + N (member) + N (delivery)` = 최악 `1 + 2N`번  
주문 2개면 최악 5번 쿼리. 주문 100개면 201번.

---

## 2. V3 — fetch join으로 해결 (실무 권장)

```java
// Repository
public List<Order> findAllWithMemberDelivery() {
    return em.createQuery(
        "select o from Order o" +
        " join fetch o.member m" +
        " join fetch o.delivery d", Order.class)
        .getResultList();
}

// Controller
@GetMapping("/api/v3/simple-orders")
public List<SimpleOrderDto> ordersV3() {
    List<Order> orders = orderRepository.findAllWithMemberDelivery();
    return orders.stream().map(SimpleOrderDto::new).collect(toList());
}
```

쿼리 **1번**으로 Order + Member + Delivery를 한 번에 조회. fetch join 덕에 LAZY 로딩 무시.

---

## 3. V4 — JPA에서 DTO 직접 조회

```java
// 조회 전용 Repository (별도 패키지 분리)
@Repository
@RequiredArgsConstructor
public class OrderSimpleQueryRepository {
    private final EntityManager em;

    public List<OrderSimpleQueryDto> findOrderDtos() {
        return em.createQuery(
            "select new jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto" +
            "(o.id, m.name, o.orderDate, o.status, d.address)" +
            " from Order o" +
            " join o.member m" +
            " join o.delivery d", OrderSimpleQueryDto.class)
            .getResultList();
    }
}
```

| 항목 | V3 (fetch join) | V4 (DTO 직접 조회) |
|------|-----------------|-------------------|
| 쿼리 수 | 1번 | 1번 |
| select 절 | 엔티티 전체 컬럼 | 필요한 컬럼만 |
| 재사용성 | 높음 (엔티티 반환) | 낮음 (DTO 스펙 종속) |
| 네트워크 | 컬럼 약간 더 전송 | 최소 컬럼만 전송 |

네트워크 최적화 효과는 생각보다 미비 → **V3이 실무 1순위**.

---

## 4. 쿼리 방식 선택 권장 순서

1. **엔티티 → DTO 변환** (V2 기본)
2. **fetch join으로 성능 최적화** (V3, 대부분 해결)
3. **DTO 직접 조회** (V4, 그래도 성능 부족할 때)
4. **네이티브 SQL / JDBC Template** (최후의 수단)

---

## 중요 원칙

- **LAZY를 피하려고 EAGER로 바꾸지 마라** — EAGER는 성능 튜닝 불가능하게 만든다
- **항상 LAZY 기본**, 성능 최적화 필요 시 fetch join
- 엔티티 직접 노출 시 양방향 연관관계는 반드시 한 쪽에 `@JsonIgnore` — 안 하면 무한 루프
- 조회 전용 Repository는 별도 패키지로 분리 (`repository.order.simplequery`)

---

## stylink 적용 포인트

**강의 권장 방식**: LAZY 기본 → DTO 변환 → fetch join으로 N+1 해결 → 성능 부족 시 DTO 직접 조회.

**stylink 적용**:
- 주문 목록 조회 API: `Order + Member + Delivery` fetch join 쿼리 1번으로 처리
- `OrderQueryRepository` 같은 조회 전용 리포지토리 별도 분리 (일반 OrderRepository는 엔티티 중심 유지)
- 예약 조회 API (Reservation + Stylist + Product): 동일한 V3 패턴으로 fetch join 적용
- `@BatchSize`는 컬렉션 조회 시 사용 (다음 챕터에서 상세 학습)
