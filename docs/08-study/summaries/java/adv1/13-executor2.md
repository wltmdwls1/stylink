# Executor 프레임워크2

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] ExecutorService 기본 — submit(), Future, Callable

스레드 풀 종료 전략
  → shutdown() vs shutdownNow()
  → graceful shutdown 패턴 (JVM shutdown hook)

스레드 풀 크기 전략
  → newFixedThreadPool: 고정 크기, 예측 가능
  → newCachedThreadPool: 동적 생성, 짧은 작업 최적화
  → ThreadPoolExecutor: 세밀한 커스터마이징

예외 정책 (RejectedExecutionHandler)
  → 큐가 꽉 찼을 때 어떻게 할 것인가?
```

---

## 스레드 풀 종료 전략

웹 서버나 배치 서버를 종료할 때, 진행 중인 작업을 어떻게 처리할지가 중요하다.

**shutdown() — 우아한 종료 (Graceful)**
```java
es.shutdown();
// - 새 작업 제출은 거부됨 (RejectedExecutionException)
// - 이미 제출된 작업(큐 포함)은 전부 완료될 때까지 계속 처리
// - 즉시 반환 (블로킹 아님)
```

**shutdownNow() — 강제 종료**
```java
List<Runnable> pending = es.shutdownNow();
// - 새 작업 제출 거부
// - 큐에 대기 중인 작업 목록 반환 (실행 안 됨)
// - 실행 중인 스레드에 interrupt() 신호 전송
```

---

## Graceful Shutdown 패턴 (실무 표준)

```java
// 서버 종료 시 사용하는 패턴
public void gracefulShutdown(ExecutorService es) {
    es.shutdown();  // 새 작업 거부, 진행 중 작업은 계속
    try {
        // 최대 30초 기다림
        if (!es.awaitTermination(30, TimeUnit.SECONDS)) {
            log("30초 초과, 강제 종료 시도");
            es.shutdownNow();
            // 강제 종료 후 추가 10초 기다림
            if (!es.awaitTermination(10, TimeUnit.SECONDS)) {
                log("강제 종료도 실패");
            }
        }
    } catch (InterruptedException e) {
        es.shutdownNow();
        Thread.currentThread().interrupt();
    }
    log("스레드 풀 종료 완료");
}
```

Spring Boot의 `@PreDestroy` 또는 JVM shutdown hook에서 이 패턴을 사용한다.

---

## 스레드 풀 크기 전략

**newFixedThreadPool(n) — 고정 크기**
```java
ExecutorService es = Executors.newFixedThreadPool(4);
// - 스레드 정확히 n개 유지
// - 작업이 없어도 스레드가 대기 (WAITING 상태)
// - 큐 무제한 (LinkedBlockingQueue)
// - 예측 가능한 자원 사용, 배치 작업에 적합
```

**newCachedThreadPool() — 동적 크기**
```java
ExecutorService es = Executors.newCachedThreadPool();
// - 필요할 때 스레드 생성 (최대 Integer.MAX_VALUE)
// - 60초 미사용 스레드는 제거
// - 큐 없음 (SynchronousQueue) → 바로 스레드에 전달
// - 짧은 작업 폭발적으로 처리할 때 적합
// - 큰 트래픽에는 위험 (스레드 무제한 생성)
```

**ThreadPoolExecutor — 세밀한 커스터마이징 (실무 권장)**
```java
ThreadPoolExecutor es = new ThreadPoolExecutor(
    2,                                      // corePoolSize: 항상 유지할 스레드 수
    10,                                     // maximumPoolSize: 최대 스레드 수
    60L, TimeUnit.SECONDS,                  // keepAliveTime: 초과 스레드 유지 시간
    new ArrayBlockingQueue<>(100),          // workQueue: 대기 큐 (최대 100개)
    new ThreadPoolExecutor.CallerRunsPolicy() // 거부 정책
);
```

**스레드 증가 순서:**
```
작업 제출 → corePoolSize 미만이면 새 스레드 생성
         → corePoolSize 이상이면 큐에 대기
         → 큐도 꽉 차면 maximumPoolSize까지 스레드 생성
         → maximumPoolSize도 초과하면 → 거부 정책 실행
```

---

## 거부 정책 (RejectedExecutionHandler)

큐도 꽉 차고 최대 스레드 수도 초과했을 때의 처리 방법:

| 정책 | 동작 | 적합한 상황 |
|---|---|---|
| `AbortPolicy` (기본) | RejectedExecutionException 발생 | 실패를 명시적으로 알아야 할 때 |
| `CallerRunsPolicy` | 제출한 스레드가 직접 실행 | 속도 조절 (back pressure) 필요 시 |
| `DiscardPolicy` | 조용히 버림 | 손실 허용 가능한 작업 |
| `DiscardOldestPolicy` | 큐에서 가장 오래된 것 버리고 재시도 | 최신 데이터가 중요할 때 |

```java
// CallerRunsPolicy: 제출자(main 스레드)가 직접 실행 → 자연스러운 백프레셔
new ThreadPoolExecutor.CallerRunsPolicy()
```

---

## CPU 바운드 vs IO 바운드에 따른 스레드 수

| 작업 유형 | 스레드 수 추천 | 이유 |
|---|---|---|
| CPU 바운드 (계산 위주) | CPU 코어 수 ≒ 스레드 수 | 스레드가 많아도 CPU가 동시에 처리 못함, 컨텍스트 스위칭만 증가 |
| IO 바운드 (DB, 네트워크) | CPU 코어 수 * (1 + 대기 시간/처리 시간) | IO 대기 중 CPU 놀기 때문에 스레드 더 많아도 됨 |

Spring MVC 기본 스레드 풀이 200개인 것도 이 때문: 대부분의 요청이 DB IO 대기 시간이 있기 때문.

---

## 면접 Q&A

**Q: shutdown()과 shutdownNow()의 차이는?**  
A: `shutdown()`은 새 작업 제출을 막고, 이미 제출된 작업(큐 포함)은 전부 완료될 때까지 기다린다. `shutdownNow()`는 실행 중인 스레드에 인터럽트를 보내고 큐에 남은 작업 목록을 반환한다. graceful shutdown 패턴은 shutdown() 후 awaitTermination()으로 기다리다 초과하면 shutdownNow()를 사용한다.

**Q: corePoolSize와 maximumPoolSize는 어떻게 동작하나?**  
A: 작업이 들어오면 먼저 corePoolSize까지 스레드를 생성한다. 그 이상이면 큐에 쌓는다. 큐도 꽉 차면 maximumPoolSize까지 스레드를 더 만든다. maximumPoolSize도 초과하면 거부 정책이 실행된다.

**Q: IO 바운드 작업에 스레드를 많이 두는 이유는?**  
A: IO 작업(DB 쿼리, HTTP 요청 등)은 대기 시간이 대부분이다. 이 대기 시간 동안 CPU는 놀기 때문에 스레드가 더 있어도 CPU 경쟁이 없다. 동시 처리 능력을 높이기 위해 스레드를 더 많이 둔다.

## stylink 실전 적용

```java
// Spring Boot application.yml에서 스레드 풀 설정
// server.tomcat.threads.max=200  (기본값, IO 바운드 작업)

// 비동기 처리가 필요한 경우
@Bean
public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
}
```
