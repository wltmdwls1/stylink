# 프록시 패턴과 데코레이터 패턴

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
한계(챕터 3): 어떤 패턴도 원본 코드 수정 필요
  ↓
프록시: Client → Proxy → Server (간접 호출, 원본 무수정)
  프록시 = 서버와 같은 인터페이스 구현 → 클라이언트는 프록시인지 실제인지 모름
  ↓
프록시의 2가지 역할 (GOF 패턴 2개로 분리):
  프록시 패턴   → 접근 제어 (캐싱, 권한, 지연 로딩)
  데코레이터 패턴 → 부가 기능 추가 (로그, 실행 시간)
  ※ 코드 구조는 같고 의도(intent)로 구분
  ↓
실제 적용:
  V1 (인터페이스 기반): proxy implements 인터페이스 → 설정 파일에서 프록시를 스프링 빈으로 등록
  V2 (클래스 기반): proxy extends 구체클래스 → super(null) 필요
  ↓
한계: 클래스 N개 → 프록시 N개 수작업 → 동적 프록시(챕터 5)로 해결
```

---

## 프록시 개념

**직접 호출**: `Client → Server`  
**간접 호출**: `Client → Proxy → Server`

### 프록시가 되기 위한 조건

```
1. 클라이언트는 프록시인지 실제 서버인지 몰라야 함 (투명성)
2. 서버와 프록시는 같은 인터페이스를 구현
3. DI로 주입하면 클라이언트 코드 변경 없이 프록시 교체 가능
```

**프록시 체인**: `Client → Proxy1 → Proxy2 → Server`  
대리자가 또 다른 대리자에게 요청을 위임할 수 있음

### 프록시의 2가지 기능

| 기능 | 예시 |
|------|------|
| **접근 제어** | 권한 차단, 캐싱, 지연 로딩 |
| **부가 기능 추가** | 로그 추적, 실행 시간 측정 |

---

## 프록시 패턴 — 접근 제어 (캐싱 예제)

```java
// 실제 객체 — 1초 소요
public class RealSubject implements Subject {
    @Override
    public String operation() {
        sleep(1000);
        return "data";
    }
}

// CacheProxy — 최초 1번만 실제 호출, 이후 캐시 반환
public class CacheProxy implements Subject {
    private final Subject target;
    private String cacheValue;

    @Override
    public String operation() {
        if (cacheValue == null) {
            cacheValue = target.operation(); // 실제 객체 호출
        }
        return cacheValue; // 캐시 반환
    }
}
```

```java
// 조립: RealSubject 코드 한 줄도 변경 없음
Subject realSubject = new RealSubject();
Subject cacheProxy = new CacheProxy(realSubject);
client.execute(); // 1초
client.execute(); // 즉시
client.execute(); // 즉시
```

**핵심**: `RealSubject`와 클라이언트 코드 무수정으로 캐시 기능 추가

---

## 데코레이터 패턴 — 부가 기능 추가

```java
// MessageDecorator — 응답값 변환
public class MessageDecorator implements Component {
    private final Component component;

    @Override
    public String operation() {
        String result = component.operation();
        return "*****" + result + "*****";
    }
}

// TimeDecorator — 실행 시간 측정
public class TimeDecorator implements Component {
    private final Component component;

    @Override
    public String operation() {
        long startTime = System.currentTimeMillis();
        String result = component.operation();
        long resultTime = System.currentTimeMillis() - startTime;
        log.info("resultTime={}ms", resultTime);
        return result;
    }
}
```

```java
// 프록시 체인으로 조립
// client → timeDecorator → messageDecorator → realComponent
Component real = new RealComponent();
Component messageDecorator = new MessageDecorator(real);
Component timeDecorator = new TimeDecorator(messageDecorator);
client.execute(timeDecorator);
```

---

## 프록시 패턴 vs 데코레이터 패턴

**코드 구조는 거의 동일 — 의도(intent)로 구분**

| | 프록시 패턴 | 데코레이터 패턴 |
|---|---|---|
| **의도** | 접근 제어 | 부가 기능 추가 |
| **GOF 정의** | "다른 개체에 대한 접근 제어를 위해 대리자를 제공" | "객체에 동적으로 책임을 추가, 기능 확장의 유연한 대안" |
| **예시** | CacheProxy, 권한 프록시 | MessageDecorator, TimeDecorator |

> 둘 다 같은 인터페이스를 구현하고 target을 필드로 갖는 구조 — 어떤 목적인지가 패턴 이름을 결정

---

## 실제 적용: 인터페이스 기반 프록시 (V1 App)

요구사항: **원본 코드 수정 없이** LogTrace 적용

```java
// 프록시 — 인터페이스 구현
public class OrderRepositoryInterfaceProxy implements OrderRepositoryV1 {
    private final OrderRepositoryV1 target;
    private final LogTrace logTrace;

    @Override
    public void save(String itemId) {
        TraceStatus status = null;
        try {
            status = logTrace.begin("OrderRepository.save()");
            target.save(itemId);   // 실제 객체 위임
            logTrace.end(status);
        } catch (Exception e) {
            logTrace.exception(status, e);
            throw e;
        }
    }
}
```

```java
// 설정 파일 — 프록시를 스프링 빈으로 등록 (실제 객체는 빈 아님)
@Configuration
public class InterfaceProxyConfig {
    @Bean
    public OrderControllerV1 orderController(LogTrace logTrace) {
        OrderControllerV1Impl impl = new OrderControllerV1Impl(orderService(logTrace));
        return new OrderControllerInterfaceProxy(impl, logTrace);
        // 스프링 빈 = 프록시 / 실제 객체는 프록시 내부에서만 참조
    }
}
```

**핵심 포인트**: 스프링 컨테이너에는 프록시만 등록됨, 실제 객체는 DI 대상이 아님

---

## 실제 적용: 구체 클래스 기반 프록시 (V2 App)

인터페이스 없어도 클래스 상속으로 프록시 구현 가능

```java
// 프록시 — 클래스 상속
public class OrderRepositoryConcreteProxy extends OrderRepositoryV2 {
    private final OrderRepositoryV2 target;
    private final LogTrace logTrace;

    public OrderRepositoryConcreteProxy(OrderRepositoryV2 target, LogTrace logTrace) {
        super(null); // 부모 생성자 호출 필요 — 기능은 사용 안 하므로 null
        this.target = target;
        this.logTrace = logTrace;
    }

    @Override
    public void save(String itemId) {
        // ... 인터페이스 기반과 동일한 로직
        target.save(itemId);
    }
}
```

---

## 인터페이스 기반 vs 클래스 기반 프록시

| | 인터페이스 기반 | 클래스 기반 |
|---|---|---|
| **인터페이스 필요** | 필요 | 불필요 |
| **제약** | 없음 | `super(null)` 필요, `final` 클래스/메서드 불가 |
| **유연성** | 인터페이스만 같으면 어디서든 교체 | 해당 클래스에만 적용 |
| **권장 시점** | 변경 가능성 있는 경우 | 인터페이스 없는 레거시 코드 |

> **`super(null)` 이유**: 클래스 기반 프록시는 부모 생성자를 호출해야 하지만 부모 기능을 쓰지 않으므로 null 전달. 프록시의 모든 동작은 `target`에게 위임하기 때문

---

## 한계: 프록시 클래스 폭발

```
OrderRepository → OrderRepositoryProxy 직접 작성 필요
OrderService    → OrderServiceProxy 직접 작성 필요
OrderController → OrderControllerProxy 직접 작성 필요
...
클래스 100개 → 프록시 100개 → 비효율적이고 중복 심함
```

→ **해결**: 동적 프록시 기술 (챕터 5 JDK 동적 프록시 / CGLIB)

---

## stylink 적용 포인트

**이 챕터 내용 자체는 stylink에 직접 적용하지 않음**  
- 수동 프록시 클래스 방식은 실무에서 거의 사용 안 함 (다음 챕터 AOP로 대체)
- LogTrace 로그 추적은 `@Around` AOP로 원본 코드 무수정 적용 예정

**하지만 이 챕터에서 이해해야 할 핵심 사고방식:**
- AOP는 결국 프록시다 — 스프링 AOP는 `@Around` 메서드를 감싸는 프록시 객체를 자동 생성
- `@Transactional`도 프록시: 스프링이 서비스 클래스의 프록시를 만들어 트랜잭션 경계를 처리
- "내가 짠 서비스 코드가 왜 트랜잭션에 들어가냐"의 답이 이 챕터

---

## 면접 Q&A

**Q: 프록시 패턴과 데코레이터 패턴의 차이는?**  
A: 코드 구조는 거의 동일하고 의도(intent)로 구분한다. 프록시 패턴은 다른 객체에 대한 접근을 제어(캐싱, 권한 차단, 지연 로딩)하는 것이 목적이고, 데코레이터 패턴은 객체에 새로운 책임을 동적으로 추가(로그, 실행 시간 측정)하는 것이 목적이다.

**Q: 스프링에서 프록시를 어떻게 활용하나?**  
A: `@Transactional`, `@Cacheable`, AOP(`@Around`) 모두 스프링이 대상 클래스의 프록시를 자동 생성하는 방식으로 동작한다. 원본 코드를 수정하지 않고 트랜잭션 경계, 캐싱, 로그 추적 등의 부가 기능을 적용할 수 있다.

**Q: 인터페이스 기반 프록시와 클래스 기반 프록시의 차이는?**  
A: 인터페이스 기반은 같은 인터페이스를 구현해서 대체 가능성과 유연성이 높다. 클래스 기반은 인터페이스 없이 클래스 상속만으로 프록시를 만들 수 있지만, `super(null)` 호출이 필요하고 `final` 클래스/메서드에는 적용 불가다. 스프링 AOP는 인터페이스 있으면 JDK 동적 프록시(인터페이스 기반), 없으면 CGLIB(클래스 기반)를 자동으로 선택한다.

**Q: 직접 프록시 클래스를 작성하는 방식의 한계는?**  
A: 적용 대상 클래스 N개마다 프록시 클래스도 N개를 직접 작성해야 한다. 100개 클래스면 100개의 거의 동일한 프록시 코드가 생긴다. 이 문제를 해결하는 것이 동적 프록시(JDK 동적 프록시, CGLIB)이고, 스프링은 이를 추상화한 AOP를 제공한다.
