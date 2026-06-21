# 생산자 소비자 문제1

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
생산자-소비자 패턴 — 큐(Buffer)를 사이에 두고 독립적으로 동작
  → V1: 큐 꽉 차면 데이터 버림, 비어있으면 null 반환 (단순)
  → V2: while 루프로 재시도 (Busy-Waiting — CPU 낭비)
  → V3: Object.wait() / notify()로 대기 → CPU 낭비 해결

V3의 남은 문제: 생산자가 생산자를 깨우는 상황 → 비효율
→ 다음 챕터(09)에서 Lock+Condition으로 해결
```

---

## 생산자-소비자 패턴이란

웹 서버에서 요청을 받는 스레드(생산자)와 처리하는 스레드(소비자)가 직접 연결되면 속도 차이 때문에 한쪽이 항상 다른 쪽을 기다려야 한다. 둘 사이에 **큐(버퍼)**를 두면 속도가 달라도 독립적으로 동작할 수 있다.

```
생산자 스레드 → [큐 (Buffer)] → 소비자 스레드
```

문제는 큐가 꽉 찼을 때 생산자가 어떻게 해야 하고, 비었을 때 소비자가 어떻게 해야 하는지다.

---

## V1 — BoundedQueueV1: 버리기 (단순)

```java
public synchronized void put(String data) {
    if (queue.size() == max) {
        log("큐가 꽉 참, 버림: " + data);
        return;  // 그냥 버림
    }
    queue.offer(data);
}

public synchronized String take() {
    if (queue.isEmpty()) {
        log("큐가 비어있음, null 반환");
        return null;  // 그냥 null
    }
    return queue.poll();
}
```

가장 단순하지만 데이터 손실이 발생한다. 실제 시스템에서는 데이터를 버릴 수 없는 경우가 대부분이다.

**V1의 남은 문제**: 데이터 손실. 큐가 찰 때 생산자가, 큐가 빌 때 소비자가 기다려야 한다.

---

## V2 — BoundedQueueV2: Busy-Waiting (CPU 낭비)

```java
public synchronized void put(String data) {
    while (queue.size() == max) {
        log("큐가 꽉 참, 1ms 대기");
        sleep(1); // TIMED_WAITING → 1ms 후 다시 확인
    }
    queue.offer(data);
}
```

큐가 꽉 차면 1ms마다 다시 확인한다. 데이터는 안 버리지만:

**문제**: `synchronized` 메서드 안에서 `sleep()`을 하기 때문에 **락을 잡은 채로 잠든다.** 그래서 소비자 스레드가 아무리 take()를 하려 해도 락이 잠겨있어 큐에서 뺄 수가 없다. → **교착 상태처럼 동작**

락을 쥐고 잠드는 건 항상 문제가 된다.

**V2의 남은 문제**: 락을 쥔 채 sleep → 소비자 접근 불가. CPU도 계속 낭비.

---

## V3 — BoundedQueueV3: wait() / notify()

`Object.wait()`는 **락을 해제하면서 WAITING 상태**로 기다린다. 깨어나면 락을 다시 획득하고 재개한다.  
`Object.notify()`는 wait()로 대기 중인 스레드 하나를 깨운다.

```java
public synchronized void put(String data) {
    while (queue.size() == max) {
        log("큐가 꽉 참, wait 진입");
        wait();  // 락 해제 + WAITING 상태 진입
    }
    queue.offer(data);
    notify();  // 소비자(또는 생산자) 스레드 중 하나를 깨움
}

public synchronized String take() {
    while (queue.isEmpty()) {
        log("큐 비어있음, wait 진입");
        wait();  // 락 해제 + WAITING 상태 진입
    }
    String result = queue.poll();
    notify();  // 생산자(또는 소비자) 스레드 중 하나를 깨움
    return result;
}
```

**왜 if가 아니라 while인가?**  
`notify()`가 깨우는 스레드가 꼭 필요한 스레드가 아닐 수 있다 (소비자를 원했는데 생산자를 깨울 수 있음). 깨어난 후 다시 조건을 확인해야 하므로 `while`이 필수다.

**V3의 남은 문제**: `notify()`는 대기 중인 스레드 하나를 **무작위로** 깨운다. 생산자가 꽉 찬 큐를 발견해서 wait()했는데, 다른 생산자를 깨워서 그 생산자도 wait()해버리는 상황이 발생할 수 있다. → **생산자가 생산자를 깨우는 비효율**

---

## wait()/notify()의 동작 원리

```
synchronized 진입 → 모니터 락 획득
   |
  wait() 호출
   → 락 해제 + WAITING 대기 집합에 들어감
   |
  (다른 스레드가 작업 후 notify() 호출)
   |
  WAITING → BLOCKED (락 다시 경쟁)
   → 락 획득 후 wait() 다음 줄부터 재개
```

---

## 면접 Q&A

**Q: 생산자-소비자 패턴이란?**  
A: 데이터를 만드는 생산자 스레드와 데이터를 사용하는 소비자 스레드 사이에 큐(버퍼)를 두어 두 스레드가 독립적으로 동작하도록 하는 패턴. 처리 속도가 다를 때 한쪽이 다른 쪽을 직접 기다리는 것보다 유연하다.

**Q: synchronized 안에서 sleep()하면 안 되는 이유는?**  
A: sleep()은 락을 해제하지 않고 그대로 잠든다. 락을 쥔 채 잠들면 다른 스레드가 같은 synchronized 메서드에 접근할 수 없어, 결과적으로 전체 시스템이 멈추는 현상이 생긴다.

**Q: wait()와 sleep()의 차이는?**  
A: sleep()은 TIMED_WAITING으로 전환하면서 락을 유지한다. wait()는 WAITING으로 전환하면서 **락을 해제**한다. 그래서 다른 스레드가 진입해 notify()를 호출할 수 있고, 깨어나면 다시 락 경쟁 후 재개한다.

**Q: wait()/notify() 대신 while을 써야 하는 이유는?**  
A: notify()가 깨운 스레드가 실제 필요한 스레드(생산자 vs 소비자)와 다를 수 있다. 깨어난 후 다시 조건을 확인해야 하므로 if 대신 while로 감싸야 한다. 이를 'spurious wakeup' 방어라고도 한다.
