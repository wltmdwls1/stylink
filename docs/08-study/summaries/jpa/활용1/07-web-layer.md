# 07. 웹 계층 개발

## 전체 흐름 한눈에 보기

```
폼 객체(MemberForm/BookForm) ← 웹 계층 전용
엔티티(Member/Item) ← 핵심 비즈니스 로직 전용

Controller → Service (id + 변경 데이터 전달)
Service에서 영속 엔티티 조회 후 변경 감지로 UPDATE
```

---

## 1. 폼 객체 vs 엔티티 직접 사용

**원칙**: 엔티티는 핵심 비즈니스 로직만, 화면 로직은 폼 객체/DTO로 분리.

```java
// 웹 계층 전용 폼
public class MemberForm {
    @NotEmpty(message = "회원 이름은 필수 입니다")
    private String name;
    private String city;
    private String street;
    private String zipcode;
}
```

- 엔티티를 화면에 맞게 변형하면 엔티티가 점점 화면에 종속됨 → 유지보수 어려워짐
- 단순 화면에서는 엔티티 직접 사용도 가능하지만, 실무에서는 항상 폼 객체 분리

---

## 2. 변경 감지와 병합(merge) — 실무에서 매우 중요

### 준영속 엔티티

영속성 컨텍스트가 관리하지 않는 엔티티. 식별자(id)는 있지만 더 이상 영속 상태가 아닌 것.

```java
// 수정 POST 요청에서 만들어진 book — 준영속 엔티티
Book book = new Book();
book.setId(form.getId());  // id가 있음 → 준영속
book.setName(form.getName());
itemService.saveItem(book);  // 이걸 어떻게 처리?
```

### 방법 1: 병합(merge) — 쓰지 마라

```java
em.merge(item);  // 모든 필드를 교체 → null로 덮어쓸 위험
```

- 준영속 엔티티의 **모든 필드**를 영속 엔티티에 교체
- 값이 없는 필드는 `null`로 업데이트될 수 있음 → **실무에서 매우 위험**

### 방법 2: 변경 감지(Dirty Checking) — 권장

```java
// Controller
@PostMapping("/items/{itemId}/edit")
public String updateItem(@PathVariable Long itemId, BookForm form) {
    itemService.updateItem(itemId, form.getName(), form.getPrice(), form.getStockQuantity());
    return "redirect:/items";
}

// Service
@Transactional
public void updateItem(Long id, String name, int price, int stockQuantity) {
    Item item = itemRepository.findOne(id);  // 영속 엔티티 조회
    item.setName(name);      // 변경
    item.setPrice(price);
    item.setStockQuantity(stockQuantity);
    // 별도 save 불필요 — 트랜잭션 커밋 시 변경 감지 자동 작동
}
```

### 핵심 원칙

1. **컨트롤러에서 엔티티 생성하지 마세요** (준영속 엔티티 생성 금지)
2. 서비스 계층에 **식별자 + 변경할 데이터**만 명확하게 전달 (파라미터 or DTO)
3. 서비스에서 **영속 엔티티 조회 후 직접 변경**
4. 트랜잭션 커밋 시 변경 감지 자동 실행

---

## 3. 컨트롤러 패턴

```java
// 주문 — 식별자만 받아서 서비스에 위임
@PostMapping("/order")
public String order(@RequestParam Long memberId, @RequestParam Long itemId,
                    @RequestParam int count) {
    orderService.order(memberId, itemId, count);
    return "redirect:/orders";
}

// 주문 취소
@PostMapping("/orders/{orderId}/cancel")
public String cancelOrder(@PathVariable Long orderId) {
    orderService.cancelOrder(orderId);
    return "redirect:/orders";
}
```

컨트롤러는 최대한 얇게 — 식별자나 파라미터를 받아서 서비스에 넘기고 리다이렉트.

---

## stylink 적용 포인트

**강의 권장 방식**: 폼 객체/DTO로 웹 계층 분리, 변경 감지 사용, 컨트롤러는 얇게.

**stylink 적용**:
- Request DTO → Service(id + 변경값) → 영속 엔티티 조회 → 변경 감지 → 자동 UPDATE
- 상품 수정, 예약 상태 변경 등 모든 수정 작업에 **변경 감지 방식 적용**, `merge` 금지
- Controller에서는 `@Valid` + DTO 수신 → Service에 id와 값만 전달
- 엔티티에 setter 대신 비즈니스 메서드(`changeStatus()`, `updateInfo()` 등)로 변경 — 변경 감지는 그 메서드 호출로 발동됨
