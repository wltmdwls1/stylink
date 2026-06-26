# 템플릿 메서드 패턴과 콜백 패턴

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
문제: 로그 추적기 적용 시 부가 기능 코드가 핵심 기능보다 많아짐 (V3)
  ↓
템플릿 메서드 패턴: AbstractTemplate(부모) + call() 추상메서드(자식)
  → 변하지 않는 코드(로그) = 부모, 변하는 코드(비즈니스) = 자식
  → 단점: 상속 강결합 — 자식이 부모 기능 안 써도 의존해야 함
  ↓
전략 패턴: Context + Strategy 인터페이스 → 상속 대신 위임
  ContextV1: 필드 주입 (선 조립, 후 실행)
  ContextV2: 파라미터 전달 (실행 시마다 유연)
  ↓
템플릿 콜백 패턴: ContextV2 방식 = Template + Callback (스프링 용어)
  → JdbcTemplate, TransactionTemplate 등
  → 최종 형태: TraceTemplate + TraceCallback<T> + 람다
  ↓
한계: 어떤 패턴도 원본 코드 수정 필요 → 프록시로 해결
```

---

## 핵심 기능 vs 부가 기능

```java
// V3 — 모든 메서드에 이 패턴이 반복됨
TraceStatus status = null;
try {
    status = trace.begin("message");
    // 핵심 기능 (이것만 다름)
    trace.end(status);
} catch (Exception e) {
    trace.exception(status, e);
    throw e;  // 반드시 다시 던질 것
}
```

**좋은 설계**: 변하는 것(비즈니스 로직)과 변하지 않는 것(로그 코드)을 분리

---

## 템플릿 메서드 패턴

**핵심**: 부모 클래스에 변하지 않는 템플릿, 자식 클래스에 변하는 부분

```java
// 부모 — 변하지 않는 코드
public abstract class AbstractTemplate<T> {
    private final LogTrace trace;

    public T execute(String message) {
        TraceStatus status = null;
        try {
            status = trace.begin(message);
            T result = call();   // 변하는 부분은 자식에 위임
            trace.end(status);
            return result;
        } catch (Exception e) {
            trace.exception(status, e);
            throw e;
        }
    }

    protected abstract T call();  // 자식에서 구현
}
```

```java
// Controller 적용 (익명 내부 클래스)
AbstractTemplate<String> template = new AbstractTemplate<>(trace) {
    @Override
    protected String call() {
        orderService.orderItem(itemId);
        return "ok";
    }
};
return template.execute("OrderController.request()");

// 반환값 없을 때 — AbstractTemplate<Void> + return null
```

**SRP 달성**: 로그 로직 변경 시 `AbstractTemplate`만 수정하면 됨

### 단점

```
자식 클래스가 부모 클래스 기능을 전혀 안 써도 상속 강제
→ 부모 수정 시 자식 영향 가능
→ 익명 내부 클래스/별도 클래스 추가 필요
```

---

## 전략 패턴

**핵심**: 상속 대신 위임 — Context는 Strategy 인터페이스에만 의존

```java
public interface Strategy {
    void call();
}
```

### ContextV1 — 필드 주입 (선 조립, 후 실행)

```java
public class ContextV1 {
    private Strategy strategy;

    public ContextV1(Strategy strategy) { this.strategy = strategy; }

    public void execute() {
        // 변하지 않는 코드
        strategy.call();  // 위임 — 변하는 부분
        // 변하지 않는 코드
    }
}

// 사용 (람다)
ContextV1 context = new ContextV1(() -> log.info("비즈니스 로직1 실행"));
context.execute();
```

조립 후 전략 변경이 번거로움 (싱글톤 사용 시 동시성 이슈)

### ContextV2 — 파라미터 전달 (실행 시마다 유연)

```java
public class ContextV2 {
    public void execute(Strategy strategy) {
        // 변하지 않는 코드
        strategy.call();  // 위임
        // 변하지 않는 코드
    }
}

// 사용 (람다)
ContextV2 context = new ContextV2();
context.execute(() -> log.info("비즈니스 로직1 실행"));
context.execute(() -> log.info("비즈니스 로직2 실행"));
```

매 실행마다 전략 지정 필요 — 코드 조각을 파라미터로 넘기는 느낌

### ContextV1 vs ContextV2

| | ContextV1 | ContextV2 |
|---|---|---|
| 전략 전달 | 생성자 주입 (필드) | 파라미터 |
| 변경 시점 | 생성 시 (선 조립) | 실행 시 (유연) |
| 적합 용도 | 스프링 DI 스타일 | 코드 조각 전달 |

우리 목적(실행 시 코드 조각 전달)에는 **ContextV2가 적합**

---

## 템플릿 콜백 패턴

콜백(callback): 다른 코드의 인수로 넘겨주는 실행 가능한 코드  
→ `call`은 되는데 코드를 넘겨준 곳의 `back`에서 실행됨

**스프링 용어**: ContextV2 방식 = 템플릿 콜백 패턴 (GOF 패턴 아님)

```
Context   → Template
Strategy  → Callback
```

스프링의 `XxxTemplate` = 모두 템플릿 콜백 패턴  
`JdbcTemplate`, `RestTemplate`, `TransactionTemplate`, `RedisTemplate`

### 실제 구현

```java
public interface TraceCallback<T> {
    T call();
}

public class TraceTemplate {
    private final LogTrace trace;

    public <T> T execute(String message, TraceCallback<T> callback) {
        TraceStatus status = null;
        try {
            status = trace.begin(message);
            T result = callback.call();
            trace.end(status);
            return result;
        } catch (Exception e) {
            trace.exception(status, e);
            throw e;
        }
    }
}
```

```java
// Controller — 익명 내부 클래스
return template.execute("OrderController.request()", new TraceCallback<>() {
    @Override
    public String call() {
        orderService.orderItem(itemId);
        return "ok";
    }
});

// Service — 람다
template.execute("OrderService.orderItem()", () -> {
    orderRepository.save(itemId);
    return null;
});
```

---

## 세 패턴 비교

| 패턴 | 방식 | 단점 |
|------|------|------|
| 템플릿 메서드 | 상속 + 오버라이딩 | 부모에 강결합, 별도 클래스 필요 |
| 전략(필드) | 위임, 생성자 주입 | 조립 후 변경 번거로움 |
| 전략(파라미터) | 위임, 실행 시 전달 | 매 실행마다 전략 지정 |
| **템플릿 콜백** | 위임, 람다 | **원본 코드 수정 필요** |

**공통 한계**: 어떤 패턴이든 로그 추적기 적용을 위해 원본 코드를 수정해야 함  
→ 클래스 수백 개면 수백 개를 수정  
→ **근본 해결: 프록시** (다음 챕터)

---

## stylink 적용 포인트

**강의 권장 방식**: TraceTemplate 직접 구현 → 패턴 원리 체득

**stylink 적용 포인트**: 이 챕터 내용 자체는 stylink에 직접 적용하지 않음  
로그 추적 문제는 프록시/AOP(`@Around`)로 해결할 예정 — 원본 코드 무수정  
하지만 JdbcTemplate/TransactionTemplate 사용 시 이 패턴을 알면 동작 방식 이해에 도움

---

## 면접 Q&A

**Q: 템플릿 메서드 패턴과 전략 패턴의 차이는?**
A: 둘 다 변하는 것과 변하지 않는 것을 분리하는 목적이지만 방식이 다르다. 템플릿 메서드 패턴은 상속과 오버라이딩을 이용하고, 전략 패턴은 인터페이스 위임을 이용한다. 템플릿 메서드 패턴은 자식 클래스가 부모에 강하게 결합되는 문제가 있어, 전략 패턴이 더 유연하다.

**Q: 템플릿 콜백 패턴이란?**
A: 전략 패턴에서 전략(Strategy)을 파라미터로 전달하는 방식(ContextV2)에 스프링이 붙인 이름이다. Context가 Template, Strategy가 Callback이 된다. GOF 공식 패턴은 아니고 스프링 내부 용어다. JdbcTemplate, TransactionTemplate 등이 이 방식으로 구현되어 있다.

**Q: JdbcTemplate을 사용할 때 람다를 쓰는 이유는?**
A: JdbcTemplate은 템플릿 콜백 패턴으로 구현되어 있다. `jdbcTemplate.query(sql, (rs, rowNum) -> {...})` 에서 람다가 콜백이다. 변하지 않는 부분(커넥션 관리, 예외 처리, 리소스 반환)은 JdbcTemplate 내부에, 변하는 부분(SQL 결과를 객체로 변환하는 로직)은 콜백으로 분리되어 있다.

**Q: 이런 패턴들의 공통 한계는?**
A: 어떤 패턴을 써도 원본 코드를 수정해야 한다는 점이다. 클래스가 수백 개면 수백 개를 수정해야 한다. 이 한계를 극복하는 것이 프록시다. 프록시는 원본 코드를 건드리지 않고 부가 기능을 적용할 수 있다.
