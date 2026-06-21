# 중첩 클래스, 내부 클래스2

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] 4종류 중첩 클래스 소개

심화 내용
  → 중첩 클래스와 외부 변수 캡처 상세
  → static 내부 클래스 권장 이유 (메모리 누수 방지)
  → Builder 패턴 → 정적 중첩 클래스 활용
  → 실전: 내부 이터레이터 패턴, 콜백 패턴
```

---

## 내부 클래스 메모리 누수 주의

내부 클래스(static 없는)는 외부 클래스 인스턴스에 대한 **숨겨진 참조**를 항상 유지한다.

```java
public class OuterClass {
    class InnerClass {
        // 컴파일러가 자동으로 OuterClass.this 참조를 추가함
        // → OuterClass가 GC되지 않음 (InnerClass가 살아있는 동안)
    }
}
```

**문제 상황**: 내부 클래스 인스턴스가 외부 클래스보다 오래 살 때 메모리 누수 발생.

```java
// 문제: Activity(안드로이드) 또는 큰 외부 클래스가 내부 클래스 때문에 GC 안 됨
List<Runnable> callbacks = new ArrayList<>();
callbacks.add(new Runnable() {  // 익명 내부 클래스
    @Override
    public void run() {
        // 외부 인스턴스를 암묵적으로 참조
    }
});
// callbacks가 살아있는 동안 외부 클래스도 GC 불가
```

**해결**: static 중첩 클래스 사용 (외부 참조 없음)
```java
public class OrderService {
    // 정적 중첩 — 외부 인스턴스 참조 없음
    static class OrderValidator {
        boolean validate(Order order) { ... }
    }
}
```

---

## Builder 패턴 — 정적 중첩 클래스 활용

생성자 파라미터가 많을 때 가독성 있게 객체를 생성.

```java
public class Order {
    private final Long userId;
    private final List<Long> itemIds;
    private final String address;
    private final PaymentType paymentType;

    private Order(Builder builder) {
        this.userId = builder.userId;
        this.itemIds = builder.itemIds;
        this.address = builder.address;
        this.paymentType = builder.paymentType;
    }

    // 정적 중첩 빌더 클래스
    public static class Builder {
        private Long userId;
        private List<Long> itemIds;
        private String address;
        private PaymentType paymentType;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder itemIds(List<Long> itemIds) { this.itemIds = itemIds; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder paymentType(PaymentType type) { this.paymentType = type; return this; }
        public Order build() { return new Order(this); }
    }
}

// 사용 — 가독성 좋음
Order order = new Order.Builder()
    .userId(1L)
    .itemIds(List.of(101L, 102L))
    .address("서울시 강남구")
    .paymentType(PaymentType.CARD)
    .build();
```

Lombok `@Builder` 어노테이션이 이 패턴을 자동 생성해준다.

---

## 콜백 패턴 — 익명 클래스 (레거시)

버튼 클릭, 이벤트 처리 등에서 과거에 익명 클래스를 썼다.

```java
// 레거시 방식 (Java 8 이전)
button.setOnClickListener(new OnClickListener() {
    @Override
    public void onClick() {
        System.out.println("클릭됨");
    }
});

// 현대 방식 (Java 8+)
button.setOnClickListener(() -> System.out.println("클릭됨"));
```

---

## 이터레이터 패턴 — 내부 클래스 활용

컬렉션 내부 데이터를 순회하는 이터레이터를 내부 클래스로 구현.

```java
public class MyList<T> implements Iterable<T> {
    private T[] data;
    private int size;

    // 내부 클래스로 이터레이터 구현 — data와 size 직접 접근
    class MyIterator implements Iterator<T> {
        private int cursor = 0;

        @Override
        public boolean hasNext() { return cursor < size; }

        @Override
        public T next() { return data[cursor++]; }
    }

    @Override
    public Iterator<T> iterator() {
        return new MyIterator();
    }
}
```

내부 클래스가 외부 클래스의 private 필드에 직접 접근하는 전형적인 패턴.

---

## 면접 Q&A

**Q: 내부 클래스가 메모리 누수를 일으킬 수 있는 이유는?**  
A: 내부 클래스(비정적)는 컴파일러가 자동으로 외부 클래스 인스턴스에 대한 참조를 추가한다. 내부 클래스 인스턴스가 어딘가에 저장되어 살아있으면 외부 클래스도 GC될 수 없다. 특히 콜백이나 리스너로 내부 클래스를 등록할 때 주의해야 한다.

**Q: Builder 패턴에서 정적 중첩 클래스를 쓰는 이유는?**  
A: 빌더는 외부 클래스 인스턴스와 독립적으로 생성되어야 한다(`new Order.Builder()`). 비정적 내부 클래스면 `new order.Builder()`처럼 외부 인스턴스가 먼저 있어야 한다. 또한 비정적이면 외부 인스턴스 참조를 불필요하게 유지하게 된다.
