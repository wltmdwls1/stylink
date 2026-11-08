# 06. 주문 도메인 개발

## 전체 흐름 한눈에 보기

```
Order 엔티티 (생성 메서드 + 비즈니스 로직 + 조회 로직)
  → OrderItem 엔티티 (생성 메서드 + 취소 로직)
  → OrderService (엔티티에 위임 — 도메인 모델 패턴)
  → 동적 검색: JPQL 문자열 조합 (임시) → 실무에서는 QueryDSL
```

---

## 1. Order 엔티티 — 생성/비즈니스/조회 로직 분리

```java
public class Order {
    // 연관관계 편의 메서드
    public void setMember(Member member) {
        this.member = member;
        member.getOrders().add(this);
    }

    // 생성 메서드 — 복잡한 생성 로직 캡슐화
    public static Order createOrder(Member member, Delivery delivery, OrderItem... orderItems) {
        Order order = new Order();
        order.setMember(member);
        order.setDelivery(delivery);
        for (OrderItem orderItem : orderItems) order.addOrderItem(orderItem);
        order.setStatus(OrderStatus.ORDER);
        order.setOrderDate(LocalDateTime.now());
        return order;
    }

    // 비즈니스 로직 — 주문 취소
    public void cancel() {
        if (delivery.getStatus() == DeliveryStatus.COMP) {
            throw new IllegalStateException("이미 배송완료된 상품은 취소가 불가능합니다.");
        }
        this.setStatus(OrderStatus.CANCEL);
        for (OrderItem orderItem : orderItems) orderItem.cancel();
    }

    // 조회 로직 — 전체 가격
    public int getTotalPrice() {
        return orderItems.stream().mapToInt(OrderItem::getTotalPrice).sum();
    }
}
```

### OrderItem 생성/취소

```java
public class OrderItem {
    // 생성 메서드 — 재고 감소도 여기서 처리
    public static OrderItem createOrderItem(Item item, int orderPrice, int count) {
        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setOrderPrice(orderPrice);
        orderItem.setCount(count);
        item.removeStock(count);  // 재고 감소
        return orderItem;
    }

    // 취소 — 재고 복원
    public void cancel() { getItem().addStock(count); }

    public int getTotalPrice() { return getOrderPrice() * getCount(); }
}
```

---

## 2. OrderService — 도메인 모델 패턴

```java
@Service @Transactional(readOnly = true) @RequiredArgsConstructor
public class OrderService {
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public Long order(Long memberId, Long itemId, int count) {
        Member member = memberRepository.findOne(memberId);
        Item item = itemRepository.findOne(itemId);

        Delivery delivery = new Delivery();
        delivery.setAddress(member.getAddress());
        delivery.setStatus(DeliveryStatus.READY);

        OrderItem orderItem = OrderItem.createOrderItem(item, item.getPrice(), count);
        Order order = Order.createOrder(member, delivery, orderItem);
        orderRepository.save(order);  // cascade ALL로 orderItem, delivery도 함께 저장
        return order.getId();
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findOne(orderId);
        order.cancel();  // 비즈니스 로직은 엔티티에 위임
    }
}
```

**핵심**: `cancelOrder`는 단 두 줄. 실제 로직은 `Order.cancel()` → `OrderItem.cancel()` → `Item.addStock()` 체인으로 처리됨. **더티 체킹(변경 감지)**으로 별도 UPDATE 쿼리 없이 자동 반영.

### 도메인 모델 패턴 vs 트랜잭션 스크립트 패턴

| 패턴 | 설명 |
|------|------|
| 도메인 모델 패턴 | 비즈니스 로직이 엔티티 안에 — JPA에서 주로 사용 |
| 트랜잭션 스크립트 패턴 | 비즈니스 로직이 서비스 계층에 — MyBatis 등에서 주로 사용 |

---

## 3. Cascade와 트랜잭션

`Order` 저장 하나로 `OrderItem`, `Delivery` 자동 저장:
- `Order → OrderItem`: `cascade = CascadeType.ALL`
- `Order → Delivery`: `cascade = CascadeType.ALL`

→ `orderRepository.save(order)` 한 번으로 전체 저장.

---

## 4. 동적 검색 — 결론: QueryDSL

| 방법 | 평가 |
|------|------|
| JPQL 문자열 조합 | 번거롭고 버그 위험 |
| JPA Criteria | 너무 복잡, 실무 사용 불가 수준 |
| **QueryDSL** | **실무 표준, 별도 강의에서 학습** |

---

## stylink 적용 포인트

**강의 권장 방식**: 생성 메서드 + 비즈니스 로직을 엔티티에, 서비스는 위임만, 더티 체킹으로 자동 UPDATE, 동적 검색은 QueryDSL.

**stylink 적용**:
- `Order.createOrder()` 패턴 → stylink의 `Order.create(member, delivery, items...)` 정적 팩토리 메서드 구현
- `cancelOrder()` → `order.cancel()` 위임 → 내부에서 `Inventory` 상태 복원 처리
- cascade: `Order → OrderItem` 은 ALL, `Inventory`는 독립 엔티티이므로 cascade 금지
- 더티 체킹 활용: 주문 상태 변경(`ORDER → CANCEL`) 시 별도 save 호출 불필요, 트랜잭션 커밋 시 자동 반영
- 동적 주문 검색 (상태/날짜 필터) → QueryDSL로 구현 예정
