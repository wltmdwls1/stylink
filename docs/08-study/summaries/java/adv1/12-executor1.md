# Executor 프레임워크1

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터들] 스레드 직접 생성의 3가지 문제
  → 문제1: 매번 new Thread() → 생성 비용 큼
  → 문제2: 스레드 수 제한 없음 → 폭발적 트래픽에 OOM
  → 문제3: Runnable의 run()은 결과를 반환 못 함

Executor 프레임워크
  → 스레드 풀(Pool)로 스레드를 미리 만들고 재사용
  → Callable<T>로 결과값 반환 가능
  → Future<T>로 비동기 결과를 나중에 받기
```

---

## 스레드 직접 생성의 3가지 문제

```java
// 문제가 있는 방식
for (Request req : requests) {
    new Thread(() -> process(req)).start();  // 요청마다 스레드 생성
}
```

1. **스레드 생성 비용**: 스레드 하나를 만드는 데 OS 자원(1MB+ 스택 메모리, 커널 자원)이 소요된다. 요청마다 생성하면 성능이 나쁘다.
2. **스레드 수 무제한**: 트래픽이 갑자기 10만으로 치솟으면 스레드 10만 개 → OOM 또는 서버 다운.
3. **결과 반환 불가**: `Runnable.run()`은 `void`. 스레드에서 계산한 결과를 돌려받을 방법이 없다.

---

## Executor 프레임워크 구조

```
ExecutorService
  ├── submit(Runnable) → Future<?>  (결과 없음)
  ├── submit(Callable<T>) → Future<T>  (결과 있음)
  ├── shutdown()  (우아한 종료)
  └── shutdownNow()  (강제 종료)
        ↓
   스레드 풀 (미리 만들어진 스레드들)
        ↓
   BlockingQueue (작업 대기)
```

스레드를 직접 만드는 대신 ExecutorService에 작업(Callable/Runnable)을 제출한다.  
스레드 풀의 스레드가 작업을 꺼내 실행하고, 끝나면 다음 작업을 위해 대기한다.

---

## Callable — 결과를 반환하는 작업

```java
// Runnable: 결과 없음, checked exception 불가
public interface Runnable {
    void run();
}

// Callable: 결과 반환, checked exception 가능
public interface Callable<V> {
    V call() throws Exception;
}
```

```java
// 1부터 100까지 합계를 구하는 Callable
Callable<Integer> sumTask = () -> {
    int sum = 0;
    for (int i = 1; i <= 100; i++) sum += i;
    return sum;  // 결과 반환!
};
```

---

## Future — 나중에 결과 받기

`ExecutorService.submit(callable)`은 즉시 `Future<T>`를 반환한다.  
실제 계산은 스레드 풀에서 비동기로 진행되고, 결과가 필요한 시점에 `future.get()`으로 받는다.

```java
ExecutorService es = Executors.newFixedThreadPool(4);

Future<Integer> future = es.submit(() -> {
    Thread.sleep(2000);  // 2초 걸리는 작업
    return 5050;
});

// 이 사이에 다른 일을 할 수 있음 (비동기)
doOtherWork();

Integer result = future.get();  // 결과가 준비될 때까지 WAITING (블로킹)
log("결과: " + result);  // 5050
```

**Future 상태 확인:**
```java
future.isDone();    // 완료됐으면 true
future.cancel(true);  // 취소 시도 (진행 중이면 인터럽트)
```

**future.get() 주의사항:**
```java
try {
    Integer result = future.get(3, TimeUnit.SECONDS); // 최대 3초만 기다림
} catch (TimeoutException e) {
    log("3초 초과, 타임아웃");
    future.cancel(true);
} catch (ExecutionException e) {
    log("작업 중 예외 발생: " + e.getCause());  // Callable 내부 예외는 여기로
}
```

Callable 내부에서 예외가 발생해도 즉시 알 수 없다. `get()` 호출 시점에 `ExecutionException`으로 받는다.

---

## ExecutorService 생성

```java
// 고정 크기 스레드 풀 (스레드 4개)
ExecutorService fixed = Executors.newFixedThreadPool(4);

// 필요할 때 생성, 60초 미사용 시 제거 (동적 크기)
ExecutorService cached = Executors.newCachedThreadPool();

// 스레드 1개 (순차 실행 보장)
ExecutorService single = Executors.newSingleThreadExecutor();
```

---

## 여러 작업 병렬 처리 — invokeAll

```java
List<Callable<Integer>> tasks = List.of(
    () -> computePart1(),
    () -> computePart2(),
    () -> computePart3()
);

// 모두 제출하고 전체 완료될 때까지 기다림
List<Future<Integer>> futures = es.invokeAll(tasks);

int total = 0;
for (Future<Integer> f : futures) {
    total += f.get();
}
```

---

## 면접 Q&A

**Q: Executor 프레임워크를 쓰는 이유는?**  
A: 스레드를 매번 직접 생성하면 비용이 크고, 수 제한이 없어 OOM 위험이 있으며, 결과값을 받을 수 없다. Executor 프레임워크는 스레드 풀로 재사용, 최대 스레드 수 제한, Callable로 결과 반환을 모두 해결한다.

**Q: Callable과 Runnable의 차이는?**  
A: `Runnable.run()`은 void를 반환하고 checked exception을 던질 수 없다. `Callable.call()`은 제네릭 타입 결과를 반환하고 `throws Exception`이 선언되어 checked exception도 던질 수 있다.

**Q: Future.get()은 언제 블로킹이 풀리나?**  
A: 해당 Callable의 실행이 완료되거나, 예외가 발생하거나, 취소되거나, 타임아웃이 발생하면 블로킹이 풀린다. 완료되지 않은 상태에서 get()을 호출하면 현재 스레드는 WAITING 상태로 기다린다.

## stylink 실전 적용

```java
// 주문 처리에서 재고 확인 + 결제 병렬 처리
Future<Boolean> inventoryFuture = executorService.submit(() -> 
    inventoryService.holdItems(orderItems));
Future<PaymentResult> paymentFuture = executorService.submit(() -> 
    paymentService.prepare(paymentInfo));

boolean inventoryHeld = inventoryFuture.get();   // 재고 확인 완료 대기
PaymentResult payment = paymentFuture.get();      // 결제 준비 완료 대기
```
