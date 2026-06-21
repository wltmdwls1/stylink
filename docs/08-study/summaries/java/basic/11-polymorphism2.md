# 다형성 2 — 추상 클래스와 인터페이스

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

```
추상 클래스 (abstract class)
  → 일부 구현 + 일부 추상 메서드
  → 단일 상속만 가능
  → "is-a" 관계, 공통 구현 공유 목적

인터페이스 (interface)
  → 순수 계약 (what, not how) — 기본적으로 추상 메서드만
  → 다중 구현 가능
  → "can-do" 관계, 능력/역할 표현
  → Java 8+ default 메서드로 구현 포함 가능
```

---

## 추상 클래스

```java
abstract class Shape {
    String color;  // 공통 필드

    // 구현된 메서드 (공통 로직)
    public String getColor() { return color; }

    // 추상 메서드 (자식이 반드시 구현)
    public abstract double area();
}

class Circle extends Shape {
    double radius;
    @Override
    public double area() { return Math.PI * radius * radius; }
}

class Square extends Shape {
    double side;
    @Override
    public double area() { return side * side; }
}

Shape s = new Circle();  // 업캐스팅 가능
// new Shape();  // 에러! 추상 클래스 직접 인스턴스화 불가
```

---

## 인터페이스

```java
interface Flyable {
    void fly();  // 추상 메서드 (public abstract 생략)
    int MAX_SPEED = 300;  // 상수 (public static final 생략)

    default String describe() {  // Java 8+ default 메서드 (구현 포함)
        return "나는 날 수 있습니다";
    }
}

interface Swimmable {
    void swim();
}

// 인터페이스 다중 구현
class Duck implements Flyable, Swimmable {
    @Override public void fly() { System.out.println("날아요"); }
    @Override public void swim() { System.out.println("헤엄쳐요"); }
}

Flyable f = new Duck();  // 인터페이스 타입으로 참조
f.fly();
```

---

## 추상 클래스 vs 인터페이스 선택 기준

```
추상 클래스를 쓰는 경우:
  - 공통 상태(필드)를 공유해야 할 때
  - 공통 구현 코드를 상속해야 할 때
  - 강한 "is-a" 관계 (Dog is an Animal)

인터페이스를 쓰는 경우:
  - 관계 없는 클래스들에 공통 능력 부여 (List, Comparable 등)
  - 다중 구현이 필요할 때
  - 구현과 분리된 계약 정의 (의존성 역전)
  - 현대 자바에서는 인터페이스 우선 선호
```

---

## stylink에서의 활용

```java
// external-mock 인터페이스 (실제 연동과 Mock 구현 교체 가능)
public interface PaymentGateway {
    PaymentResult pay(PaymentRequest request);
    PaymentResult cancel(String paymentKey);
}

// Mock 구현체
@Component
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult pay(PaymentRequest request) {
        // 시뮬레이션 로직
        return PaymentResult.success("MOCK_" + UUID.randomUUID());
    }
}

// 실제 구현체로 교체해도 서비스 코드 변경 없음
public class PaymentService {
    private final PaymentGateway pg;  // 인터페이스에 의존
    // ...
}
```

---

## 면접 Q&A

**Q: 추상 클래스와 인터페이스 언제 쓰나?**  
A: 추상 클래스는 강한 is-a 관계와 공통 상태/구현을 공유할 때. 인터페이스는 다중 구현이 필요하거나 관련 없는 클래스에 공통 능력을 부여할 때. 의존성 역전을 위해 서비스 간 계약을 정의할 때는 인터페이스가 적합하다. 현대 자바에서는 대부분 인터페이스를 먼저 고려한다.
