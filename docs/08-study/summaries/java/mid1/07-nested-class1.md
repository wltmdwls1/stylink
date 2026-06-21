# 중첩 클래스, 내부 클래스1

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
중첩 클래스 4종류
  1. 정적 중첩 클래스 (static nested class): 외부 클래스와 독립적
  2. 내부 클래스 (inner class): 외부 클래스 인스턴스에 종속
  3. 지역 클래스 (local class): 메서드 안에서 선언
  4. 익명 클래스 (anonymous class): 이름 없이 선언과 동시에 인스턴스 생성

언제 쓰는가?
  → 특정 클래스에서만 쓰이는 클래스를 외부에 노출하지 않고 캡슐화
  → 외부 클래스의 멤버에 쉽게 접근

람다 이전의 익명 클래스 → Java 8+에서는 람다로 대체 가능
```

---

## 1. 정적 중첩 클래스 (static nested class)

`static`이 붙어 외부 클래스의 인스턴스 없이 독립적으로 존재할 수 있다.  
외부 클래스의 **정적** 멤버에만 접근할 수 있다.

```java
public class Order {
    private static final String SYSTEM_CODE = "SYS001";  // static 멤버

    // 정적 중첩 클래스 — Order 없이도 생성 가능
    static class OrderItem {
        private String productName;
        private int price;

        void print() {
            System.out.println(SYSTEM_CODE + " - " + productName);  // 외부 static 접근 가능
        }
    }
}

// 사용
Order.OrderItem item = new Order.OrderItem();  // Order 인스턴스 불필요
```

**언제 쓰나**: 외부 클래스와 논리적으로 연관되어 있지만 독립적인 헬퍼/빌더 클래스.  
예: `Builder 패턴`, 에러 코드 클래스, DTO 클래스.

---

## 2. 내부 클래스 (inner class)

`static`이 없어 외부 클래스의 인스턴스에 종속된다.  
외부 클래스의 모든 멤버(private 포함)에 접근할 수 있다.

```java
public class Reservation {
    private String customerName;  // instance 멤버
    private int price;

    // 내부 클래스 — Reservation 인스턴스 없이 생성 불가
    class PaymentInfo {
        void print() {
            // 외부 인스턴스(private 포함) 접근 가능
            System.out.println(customerName + ": " + price);
        }
    }
}

// 사용 — 반드시 외부 인스턴스가 먼저 있어야 함
Reservation res = new Reservation(...);
Reservation.PaymentInfo info = res.new PaymentInfo();
```

**언제 쓰나**: 외부 클래스의 인스턴스 상태에 강하게 의존하는 경우.  
`Iterator` 구현 등에서 내부 클래스 패턴을 볼 수 있음.

---

## 3. 지역 클래스 (local class)

메서드 블럭 안에서 선언. 해당 블럭 안에서만 사용 가능.  
지역 변수처럼 취급되어 메서드 밖에서는 보이지 않는다.

```java
public void processOrder() {
    class OrderValidator {  // 이 메서드 안에서만 사용
        boolean validate(Order order) {
            return order.getAmount() > 0;
        }
    }

    OrderValidator validator = new OrderValidator();
    if (!validator.validate(order)) throw new IllegalArgumentException();
}
```

**언제 쓰나**: 매우 드물게 사용. 특정 메서드에서만 일회성으로 필요한 클래스.

---

## 4. 익명 클래스 (anonymous class)

이름 없이 선언과 동시에 인스턴스를 생성.  
Java 8 이전에 람다/Comparator 대신 자주 쓰였다.

```java
// 익명 클래스로 Comparator 구현 (레거시 방식)
List<String> names = new ArrayList<>(Arrays.asList("Charlie", "Alice", "Bob"));
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.compareTo(b);
    }
});

// Java 8+ 람다로 대체
names.sort((a, b) -> a.compareTo(b));
// 또는
names.sort(Comparator.naturalOrder());
```

---

## 외부 변수 캡처 주의

지역 클래스와 익명 클래스에서 메서드의 지역 변수를 참조할 때는 **effectively final**이어야 한다. (람다와 동일)

```java
public void process(String message) {
    // message는 effectively final이어야 함
    Runnable r = new Runnable() {
        @Override
        public void run() {
            System.out.println(message);  // 캡처
        }
    };
    // message = "changed";  // 이 줄이 있으면 컴파일 에러
}
```

---

## 면접 Q&A

**Q: 정적 중첩 클래스와 내부 클래스의 차이는?**  
A: 정적 중첩 클래스는 `static`이 붙어 외부 클래스 인스턴스 없이 생성할 수 있고 외부 클래스의 static 멤버에만 접근할 수 있다. 내부 클래스는 외부 클래스 인스턴스에 종속되어 있고 외부 클래스의 모든 멤버(private 포함)에 접근할 수 있다.

**Q: 중첩 클래스를 쓰는 이유는?**  
A: 특정 클래스에서만 쓰이는 클래스를 외부로 노출하지 않고 캡슐화하기 위해서다. 가독성과 응집도를 높이고, 외부 클래스의 멤버에 쉽게 접근할 수 있는 장점도 있다.
