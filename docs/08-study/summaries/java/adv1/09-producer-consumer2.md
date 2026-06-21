# 생산자 소비자 문제2

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] V3 Object.wait()/notify()
  → 문제: notify()가 생산자를 깨울지 소비자를 깨울지 구분 불가

V4 — ReentrantLock + 단일 Condition
  → notify → Condition.signal()
  → 여전히 어떤 스레드를 깨울지 모름

V5 — 생산자/소비자 전용 Condition 분리
  → 생산자 전용 Condition, 소비자 전용 Condition
  → 생산자는 소비자를 깨우고, 소비자는 생산자를 깨움 → 비효율 해결

V6 — BlockingQueue (자바 표준 라이브러리)
  → V5와 동일한 원리를 자바가 이미 구현해둠 → 실무에서 이걸 쓰면 됨
```

---

## V4 — ReentrantLock + 단일 Condition

```java
private final Lock lock = new ReentrantLock();
private final Condition condition = lock.newCondition();

public void put(String data) {
    lock.lock();
    try {
        while (queue.size() == max) {
            condition.await();  // wait() 역할, 락 해제 + WAITING
        }
        queue.offer(data);
        condition.signal();  // notify() 역할
    } finally {
        lock.unlock();
    }
}
```

`synchronized`/`wait()`에서 `ReentrantLock`/`Condition`으로 바꿨다.  
기능은 같지만 코드 구조가 명시적이고 타임아웃(`awaitNanos()`) 등 추가 기능이 있다.

**V4의 남은 문제**: `condition.signal()`이 어떤 스레드를 깨울지 아직 알 수 없다. 생산자 가득 찬 큐 → 생산자를 깨우는 문제가 여전히 존재한다.

---

## V5 — 생산자/소비자 Condition 분리 (핵심)

핵심 아이디어: 생산자를 위한 Condition, 소비자를 위한 Condition을 **따로** 만들면, 정확히 필요한 쪽을 깨울 수 있다.

```java
private final Lock lock = new ReentrantLock();
private final Condition producerCond = lock.newCondition();  // 생산자가 여기서 기다림
private final Condition consumerCond = lock.newCondition();  // 소비자가 여기서 기다림

public void put(String data) {
    lock.lock();
    try {
        while (queue.size() == max) {
            producerCond.await();  // 생산자가 여기서 대기
        }
        queue.offer(data);
        consumerCond.signal();     // 소비자를 깨움 (생산자가 아닌)
    } finally {
        lock.unlock();
    }
}

public String take() {
    lock.lock();
    try {
        while (queue.isEmpty()) {
            consumerCond.await();  // 소비자가 여기서 대기
        }
        String result = queue.poll();
        producerCond.signal();     // 생산자를 깨움 (소비자가 아닌)
        return result;
    } finally {
        lock.unlock();
    }
}
```

이제 정확히 필요한 스레드를 깨운다. 생산자가 아무리 많아도 서로 깨우지 않는다.

---

## V6 — BlockingQueue (자바 표준, 실무에서 이걸 쓴다)

자바가 V5와 동일한 구현을 이미 제공한다.

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);

// 생산자
queue.put(data);    // 꽉 차면 자동으로 대기 (V5의 put과 동일)

// 소비자
String data = queue.take();  // 비어있으면 자동으로 대기 (V5의 take와 동일)
```

직접 구현 대신 `BlockingQueue`를 쓰면 된다.

**BlockingQueue 구현체:**
| 클래스 | 특징 |
|---|---|
| `ArrayBlockingQueue(n)` | 고정 크기 배열 기반, 공정/비공정 선택 가능 |
| `LinkedBlockingQueue` | 연결 리스트 기반, 크기 무제한(Integer.MAX_VALUE) 또는 지정 |
| `PriorityBlockingQueue` | 우선순위 큐 기반, 정렬 순서대로 꺼냄 |
| `SynchronousQueue` | 크기 0 — 생산자/소비자가 동시에 만나야만 전달 |

---

## 실무 활용 사례

스프링 `@Async`, `ThreadPoolTaskExecutor`의 내부 작업 큐도 `BlockingQueue`를 사용한다.

```java
// Executor 프레임워크의 내부 구조 (개념적으로)
BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(100);
// 워커 스레드들이 workQueue.take()로 작업을 꺼내 실행
```

stylink에서 주문 이벤트 처리에 비동기 큐를 도입한다면 이 구조와 같다.

---

## 면접 Q&A

**Q: notify() 대신 Condition을 분리하는 이유는?**  
A: `Object.notify()`와 단일 `Condition.signal()`은 대기 중인 스레드 중 누구를 깨울지 지정할 수 없다. 생산자 전용 Condition, 소비자 전용 Condition을 만들면, 큐에 공간이 생겼을 때 정확히 소비자를 깨우고, 데이터가 들어왔을 때 정확히 생산자를 깨울 수 있어 불필요한 깨우기를 방지한다.

**Q: BlockingQueue의 put()과 offer()의 차이는?**  
A: `put()`은 큐가 꽉 차면 공간이 생길 때까지 WAITING으로 무한 대기한다. `offer()`는 즉시 실패(false 반환) 또는 타임아웃을 지정할 수 있다. `offer(data, 1, TimeUnit.SECONDS)`처럼 타임아웃을 주면 실용적이다.

**Q: 실무에서 생산자-소비자 패턴 어디서 보이나?**  
A: 메시지 큐(Kafka, RabbitMQ), 스프링의 ThreadPoolTaskExecutor 내부 workQueue, 비동기 이벤트 처리, 배치 파이프라인 등에서 쓰인다. 자바 레벨에서는 BlockingQueue가 기본 빌딩 블록이다.
