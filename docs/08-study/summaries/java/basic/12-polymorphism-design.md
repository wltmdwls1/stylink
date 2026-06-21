# 다형성과 설계 — OCP, 전략 패턴

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

```
다형성을 활용한 좋은 설계의 핵심 원칙:

OCP (Open-Closed Principle)
  → 기능 추가에는 열려 있고, 기존 코드 수정에는 닫혀 있어야 한다
  → 인터페이스로 추상화 → 새 구현체 추가만으로 기능 확장

전략 패턴 (Strategy Pattern)
  → 알고리즘/정책을 인터페이스로 추상화
  → 런타임에 원하는 구현체로 교체 가능

의존성 역전 (DIP)
  → 고수준 모듈이 저수준 모듈에 직접 의존하지 말고
    둘 다 추상화(인터페이스)에 의존
```

---

## OCP 위반 vs 준수

```java
// OCP 위반: 새 할인 방식 추가 시 기존 코드 수정 필요
class DiscountService {
    double discount(String type, double price) {
        if (type.equals("BASIC")) return price * 0.95;
        if (type.equals("VIP"))   return price * 0.80;
        // 새 타입 추가 시 이 if-else에 계속 추가해야 함
        return price;
    }
}

// OCP 준수: 새 구현체만 추가하면 됨
interface DiscountPolicy {
    double discount(double price);
}

class BasicDiscount implements DiscountPolicy {
    public double discount(double price) { return price * 0.95; }
}

class VipDiscount implements DiscountPolicy {
    public double discount(double price) { return price * 0.80; }
}

// 새 할인 정책 추가: 새 클래스만 만들면 됨 (기존 코드 변경 없음)
class SummerDiscount implements DiscountPolicy {
    public double discount(double price) { return price * 0.70; }
}
```

---

## 전략 패턴 (Strategy Pattern)

```java
class OrderService {
    private DiscountPolicy discountPolicy;  // 인터페이스에 의존

    // 생성자 주입 (DI)
    OrderService(DiscountPolicy discountPolicy) {
        this.discountPolicy = discountPolicy;
    }

    double calculatePrice(double price) {
        return discountPolicy.discount(price);
    }
}

// 사용 시 전략 선택
OrderService vipService = new OrderService(new VipDiscount());
OrderService basicService = new OrderService(new BasicDiscount());
```

Spring의 `@Autowired`도 이 전략 패턴의 활용.

---

## stylink에서의 적용

```java
// 알림 전략 (이메일 / SMS / 앱 푸시)
public interface NotificationSender {
    void send(String recipient, String message);
}

@Component("email")
class EmailSender implements NotificationSender { ... }

@Component("sms")
class SmsSender implements NotificationSender { ... }

// 알림 서비스는 전략에 의존
@Service
class NotificationService {
    private final Map<String, NotificationSender> senders;

    void notify(String type, String recipient, String message) {
        NotificationSender sender = senders.get(type);
        sender.send(recipient, message);
    }
}

// 새 채널 추가 시: 새 구현체 + @Component만 추가 (기존 코드 변경 없음)
```

---

## 핵심 원칙 요약

```
좋은 설계 = 변경에 강한 설계

1. 인터페이스로 추상화 (구현에 의존하지 말고 추상에 의존)
2. 생성자 주입으로 의존성 외부에서 주입 (DI)
3. 기능 확장 = 새 구현체 추가 (기존 코드 수정 최소화)
4. 이게 OCP + DIP + 전략 패턴의 본질
```

---

## 면접 Q&A

**Q: OCP를 지키는 코드를 어떻게 작성하나?**  
A: 변하는 부분(알고리즘, 전략)을 인터페이스로 추상화하고, 기존 코드는 인터페이스에만 의존하게 만든다. 새 기능은 인터페이스를 구현하는 새 클래스를 추가하는 방식으로 확장한다. if-else로 타입을 분기하는 코드가 있다면 OCP 위반의 신호다.

**Q: 전략 패턴과 Spring DI의 연관성은?**  
A: 전략 패턴은 알고리즘을 인터페이스로 캡슐화하고 런타임에 교체 가능하게 하는 패턴이다. Spring의 DI가 이 패턴을 자동화한다 — 생성자에 인터페이스 타입을 선언하면 Spring이 어떤 구현체를 주입할지 결정해준다. `@Primary`, `@Qualifier`, `@Conditional` 등으로 주입 전략을 제어할 수 있다.
