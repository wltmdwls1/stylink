# 04. API 개발 고급 - 컬렉션 조회 최적화

## 전체 흐름 한눈에 보기

```
컬렉션(OneToMany) 조회 문제 → 해결 단계적 접근
V2: DTO 변환 (N+1 대량 발생)
V3: 컬렉션 fetch join (쿼리 1번, 페이징 불가)
V3.1: ToOne fetch join + BatchSize (페이징 가능, 실무 최고 권장)
V4: DTO 직접 조회 (1+N쿼리)
V5: DTO 직접 조회 + IN절 최적화 (1+1쿼리, 대량 데이터 시 V3.1 대안)
V6: 플랫 데이터 (쿼리 1번, 페이징 불가, 중복 데이터 많음)
```

---

## 1. 컬렉션 fetch join — V3 (페이징 불가)

```java
// Repository
public List<Order> findAllWithItem() {
    return em.createQuery(
        "select distinct o from Order o" +
        " join fetch o.member m" +
        " join fetch o.delivery d" +
        " join fetch o.orderItems oi" +   // 컬렉션 fetch join
        " join fetch oi.item i", Order.class)
        .getResultList();
}
```

- `distinct` 필수: 1:N 조인으로 Order row가 중복 증가 → JPA가 애플리케이션 레벨에서 중복 제거
- **페이징 불가**: 하이버네이트가 모든 데이터를 메모리에 올려서 페이징 → 장애 위험
- 컬렉션 fetch join은 **1개만 가능** (둘 이상은 데이터 부정합)

---

## 2. V3.1 — 페이징 + 컬렉션: 실무 최고 권장

### 전략
1. **ToOne 관계는 fetch join** (row 수 증가 없음 → 페이징에 영향 없음)
2. **컬렉션은 LAZY + BatchSize**

```java
// Repository — ToOne만 fetch join, 페이징 적용
public List<Order> findAllWithMemberDelivery(int offset, int limit) {
    return em.createQuery(
        "select o from Order o" +
        " join fetch o.member m" +
        " join fetch o.delivery d", Order.class)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
}
```

```yaml
# application.yml — 글로벌 BatchSize 설정
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 1000
```

### BatchSize 동작
- 컬렉션 LAZY 로딩 시 `WHERE id IN (?,?,?,...)`로 한 번에 조회
- 쿼리 수: `1(Order) + 1(OrderItem IN) + 1(Item IN)` = **3번** (N+1 → 1+1로 최적화)
- 100~1000 사이 권장 (DB 순간 부하 고려)
- 스프링 부트 3.1+: `WHERE IN` 대신 `array_contains` 사용 (SQL 캐싱 최적화)

---

## 3. DTO 직접 조회 V5 — IN절 최적화 (대량 데이터 시 V3.1 대안)

```java
public List<OrderQueryDto> findAllByDto_optimization() {
    // 1. ToOne 먼저 조회
    List<OrderQueryDto> result = findOrders();

    // 2. orderId 목록 추출
    List<Long> orderIds = result.stream().map(o -> o.getOrderId()).collect(toList());

    // 3. 컬렉션을 IN절 한 방에 조회 → Map으로 변환
    Map<Long, List<OrderItemQueryDto>> orderItemMap = em.createQuery(
        "select new ...OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
        " from OrderItem oi join oi.item i" +
        " where oi.order.id in :orderIds", OrderItemQueryDto.class)
        .setParameter("orderIds", orderIds)
        .getResultList()
        .stream().collect(groupingBy(OrderItemQueryDto::getOrderId));

    // 4. Map으로 컬렉션 세팅 (O(1) 매칭)
    result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));
    return result;
}
```

쿼리 **1+1번**. V4(1+N번)의 개선. 코드는 복잡하지만 대량 데이터 환경에서 100배+ 성능 차이 가능.

---

## 4. 최종 권장 순서

| 순서 | 방식 | 설명 |
|------|------|------|
| 1 | V3.1 | ToOne fetch join + default_batch_fetch_size, 페이징 가능 |
| 2 | V3 | 페이징 불필요하고 컬렉션 조인 OK인 경우 |
| 3 | V5 | DTO 직접 조회, 대량 데이터, 1+1 쿼리 |
| 4 | V4 | 단건 조회 or 소량 데이터 |
| 5 | V6 | 페이징 불필요 + 쿼리 1번 필수 (중복 많아 실익 적음) |
| 최후 | Native SQL / JdbcTemplate | 위 모두 부족 시 |

**핵심 원칙**:
- 엔티티 조회 방식 우선 (fetch join + BatchSize로 코드 변경 최소)
- DTO 직접 조회는 성능 최적화 시 코드 변경량 큼

---

## stylink 적용 포인트

**강의 권장 방식**: ToOne fetch join + `default_batch_fetch_size: 1000` 조합이 페이징 지원하면서 성능도 좋은 최고 권장 패턴.

**stylink 적용**:
- `application.yml`에 `default_batch_fetch_size: 1000` 글로벌 설정 필수
- 주문 목록 조회 (OrderItem 컬렉션 포함): V3.1 패턴 — `Order + Member + Delivery` fetch join, `OrderItem`은 BatchSize로 처리
- 예약 조회 (Reservation + ReservationItem 컬렉션): 동일 패턴
- 페이징이 필요 없는 단건 상세 조회: V3 컬렉션 fetch join 사용 가능
- 조회 전용 Repository(`OrderQueryRepository`)는 도메인 Repository와 별도 패키지로 분리
