# 모니터링 메트릭 활용

## 전체 흐름 한눈에 보기

```
기술 메트릭(CPU/메모리/커넥션 풀): 액츄에이터가 자동 등록
비즈니스 메트릭(주문수/취소수/재고): 직접 등록해야 함

직접 등록 방법:
  Counter 직접: Counter.builder("name").tag(...).register(registry).increment()
  Counter AOP:  @Counted("name") + CountedAspect 빈 등록
  Timer 직접:   Timer.builder("name").register(registry) → timer.record(() -> {...})
  Timer AOP:    @Timed("name") + TimedAspect 빈 등록
  Gauge:        Gauge.builder("name", obj, fn).register(registry) 또는 MeterBinder

핵심 원칙:
  - 비즈니스 메트릭 → 기술 메트릭으로 발견 못한 문제 조기 인지
  - Tag 카디널리티: 낮아야 함 (상태/수단 등 그룹화 단위만)
  - 메트릭은 정확한 수치 아님 → 추세 파악 목적
```

---

## 비즈니스 메트릭이 필요한 이유

기술 메트릭만으로는 비즈니스 문제를 감지할 수 없다.

예: 택배회사 장애 → 고객 취소 급증  
→ CPU/메모리는 정상 → 시스템 메트릭으로 감지 불가  
→ `취소수 카운터` 있으면 즉시 감지 가능

비즈니스 메트릭 예시:
- 주문수, 취소수 (단조 증가 → **카운터**)
- 재고 수량 (오르내림 → **게이지**)

---

## MeterRegistry

마이크로미터 핵심 컴포넌트. 스프링 빈으로 자동 등록됨.

```java
@RequiredArgsConstructor
public class OrderServiceV1 {
    private final MeterRegistry registry;
    // Counter.builder(...).register(registry)
    // Gauge.builder(...).register(registry)
}
```

---

## Counter 직접 등록 (V1)

```java
Counter.builder("my.order")
        .tag("class", this.getClass().getName())
        .tag("method", "order")          // 주문/취소 구분
        .description("order")
        .register(registry)
        .increment();
```

프로메테우스 결과:
```
# TYPE my_order_total counter
my_order_total{method="order"} 1.0
my_order_total{method="cancel"} 1.0
```

단점: 메트릭 코드가 비즈니스 로직에 침투

---

## @Counted (V2) — AOP 방식

```java
@Counted("my.order")
public void order() {
    stock.decrementAndGet();
}

@Counted("my.order")
public void cancel() {
    stock.incrementAndGet();
}
```

설정:
```java
@Bean
public CountedAspect countedAspect(MeterRegistry registry) {
    return new CountedAspect(registry);  // 반드시 등록
}
```

**CountedAspect 빈 없으면 @Counted AOP 동작 안 함.**

자동 추가 tag: `result`(success), `exception`(none), `method`, `class`

---

## Timer 직접 등록 (V3)

Timer = Counter + 실행 시간 측정. 3가지를 한 번에:

| 메트릭 | 설명 | 타입 |
|--------|------|------|
| `_seconds_count` | 누적 실행 수 | 카운터 |
| `_seconds_sum` | 실행 시간의 합 | sum |
| `_seconds_max` | 최대 실행 시간 | 게이지 (1~3분마다 재계산) |

평균 실행시간 = `seconds_sum / seconds_count`

```java
Timer timer = Timer.builder("my.order")
        .tag("class", this.getClass().getName())
        .tag("method", "order")
        .register(registry);

timer.record(() -> {
    // 측정 대상 코드
    stock.decrementAndGet();
    sleep(500);
});
```

프로메테우스 결과:
```
# TYPE my_order_seconds summary
my_order_seconds_count{method="order"} 3.0
my_order_seconds_sum{method="order"} 1.518434959
# TYPE my_order_seconds_max gauge
my_order_seconds_max{method="order"} 0.509926375
```

그라파나 PromQL:
```promql
# 주문수 (분당)
increase(my_order_seconds_count{method="order"}[1m])

# 최대 실행시간
my_order_seconds_max

# 평균 실행시간
increase(my_order_seconds_sum[1m]) / increase(my_order_seconds_count[1m])
```

---

## @Timed (V4) — AOP 방식

```java
@Timed("my.order")       // 클래스에 적용 → 모든 public 메서드에 타이머
@Slf4j
public class OrderServiceV4 implements OrderService {
    public void order() { ... }
    public void cancel() { ... }
    public AtomicInteger getStock() { ... }  // getStock에도 타이머 적용됨 주의
}
```

설정:
```java
@Bean
public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);  // 반드시 등록
}
```

**TimedAspect 빈 없으면 @Timed AOP 동작 안 함.**  
생성 메트릭/프로메테우스 포맷은 V3와 동일. tag에 `exception` 추가됨.

---

## Gauge — 재고 수량 (V1: @PostConstruct)

```java
@Configuration
public class StockConfigV1 {
    @Bean
    public MyStockMetric myStockMetric(OrderService orderService, MeterRegistry registry) {
        return new MyStockMetric(orderService, registry);
    }

    static class MyStockMetric {
        // ...
        @PostConstruct
        public void init() {
            Gauge.builder("my.stock", orderService, service ->
                service.getStock().get()   // 메트릭 확인 요청마다 호출됨
            ).register(registry);
        }
    }
}
```

게이지 특성:
- 함수는 **외부에서 메트릭 확인할 때마다 호출** (프로메테우스가 수집할 때)
- 카운터와 달리 누적 불필요 → 현재 시점 값만 반환

## Gauge — MeterBinder 방식 (V2, 더 간단)

```java
@Bean
public MeterBinder stockSize(OrderService orderService) {
    return registry -> Gauge.builder("my.stock", orderService,
            service -> service.getStock().get()
    ).register(registry);
}
```

프로메테우스: `my_stock 101.0`  
그라파나 PromQL: `my_stock`

---

## 요약 비교표

| | Counter | Gauge | Timer |
|--|---------|-------|-------|
| 값의 성격 | 단조 증가 | 오르내림 | 카운터 + 시간 |
| 직접 등록 | `Counter.builder()` | `Gauge.builder()` | `Timer.builder()` |
| AOP 어노테이션 | `@Counted` | - | `@Timed` |
| AOP 빈 | `CountedAspect` | - | `TimedAspect` |
| 프로메테우스 접미사 | `_total` | 없음 | `_seconds_count/sum/max` |
| 그라파나 함수 | `increase()` / `rate()` | 현재값 그대로 | `increase()` / 나누기 |

---

## Tag 설계 원칙

Tag = 프로메테우스 레이블 = 데이터를 나누는 기준

**좋은 Tag (카디널리티 낮음):**
- 주문 상태: PENDING/CONFIRMED/COMPLETED
- 결제 수단: 신용카드/현금
- 메서드명: order/cancel

**나쁜 Tag (카디널리티 높음):**
- 주문번호 (PK): 시계열 폭발 → 메모리/성능 문제

---

## stylink 적용 포인트

| 메트릭 | 이름 | 타입 | 등록 방법 |
|--------|------|------|---------|
| 주문 수 | `stylink.order.count` | Counter | `@Counted` |
| 취소 수 | `stylink.order.cancel` | Counter | `@Counted` |
| 재고 수량 | `stylink.inventory.stock` | Gauge | `MeterBinder` |
| 주문 처리 시간 | `stylink.order.time` | Timer | `@Timed` |
| 예약 수 | `stylink.reservation.count` | Counter | `@Counted` |

Tag 설계:
- `method`: order / cancel
- `status`: CONFIRMED / IN_PROGRESS / COMPLETED

```java
// 재고 게이지 예시
@Bean
public MeterBinder inventoryStock(InventoryRepository inventoryRepository) {
    return registry -> Gauge.builder("stylink.inventory.stock",
            inventoryRepository,
            repo -> repo.countByStatus(InventoryStatus.AVAILABLE)
    ).register(registry);
}
```

---

## 실무 모니터링 3단계

```
대시보드 (가장 넓은 뷰)
  → 마이크로미터 + 프로메테우스 + 그라파나
  → 시스템/앱/비즈니스 메트릭

  문제 발생!
      ↓
애플리케이션 추적 (요청 단위)
  → 핀포인트(오픈소스), 스카우트, 와탭, 제니퍼
  → HTTP 요청별 추적, MSA 분산 추적

  더 자세히?
      ↓
로그 (가장 좁은 뷰)
  → Logback + MDC
  → 같은 요청을 requestId로 묶어서 추적
```

---

## 알람 설계 2단계

| 단계 | 대응 방식 | 예시 |
|------|---------|------|
| 경고 | 하루 1회 수동 확인 | 디스크 70%, CPU 40% |
| 심각 | 즉시 슬랙/문자/전화 | 디스크 80%, CPU 50% |

**핵심 원칙:**
- 거짓(False) 알람은 즉시 처리 (늑대 소년 효과)
- 경고/심각 명확히 분리 → 업무와 삶에 방해 최소화

---

## 면접 포인트

- **"비즈니스 메트릭을 왜 직접 등록하나?"**
  → 기술 메트릭(CPU/메모리)은 애플리케이션이 자동 제공하지만, 비즈니스 문제(취소 급증, 재고 소진)는 도메인 지식이 필요하므로 직접 정의해야 함

- **"@Counted와 @Timed의 차이는?"**
  → @Counted는 메서드 실행 횟수를 카운터로 측정. @Timed는 횟수 + 실행시간(count/sum/max)을 Timer로 측정. 각각 CountedAspect/TimedAspect 빈 등록 필수

- **"Timer가 측정하는 3가지는?"**
  → seconds_count(누적 실행 수), seconds_sum(실행시간 합), seconds_max(최대 실행시간, 게이지 타입으로 1~3분마다 재계산). 평균 = sum/count

- **"Gauge를 언제 쓰나?"**
  → 현재 상태를 보여주는 값(재고 수량, 활성 커넥션 수, 큐 크기). 카운터와 달리 감소할 수 있는 값. 측정 함수를 등록하면 메트릭 요청 시마다 호출됨

- **"Tag 설계 시 주의할 점은?"**
  → 카디널리티가 낮아야 함. 상태/종류처럼 몇 가지 값만 가지는 것을 tag로 사용. 주문번호/PK 같이 무한히 늘어나는 값을 tag로 쓰면 시계열이 폭발해 메모리/성능 문제 발생
