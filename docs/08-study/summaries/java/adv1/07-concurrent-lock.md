# 고급 동기화 - concurrent.Lock

> 자바 고급1편 | 26페이지 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] V3 (synchronized 블럭)
  → 문제: 타임아웃·인터럽트·공정성 불가

LockSupport (원리 이해용 저수준 도구)
  → 스레드를 WAITING/RUNNABLE로 직접 제어
  → 문제: 너무 저수준, 직접 구현하면 복잡도 폭발

V4 (ReentrantLock 기본)
  → 해결: WAITING 상태로 대기 → 인터럽트 가능
  → 문제: 여전히 락 얻을 때까지 무한 대기

V5 (tryLock — 즉시 포기)
  → 해결: 락 없으면 기다리지 않고 즉시 false 반환
  → 문제: 아예 안 기다리는 게 아니라 잠깐은 기다리고 싶은 경우

V6 (tryLock(시간) — 타임아웃)
  → 해결: 지정한 시간만큼 대기 후 없으면 false 반환, 대기 중 인터럽트도 가능
```

---

## 왜 synchronized 만으론 부족한가

은행 앱에서 고객이 버튼을 눌렀는데 락을 기다리느라 응답이 10초 넘게 안 온다고 해보자.  
`synchronized`는 "락 얻을 때까지 무조건 기다려"만 있다. "3초 기다렸다 안 되면 포기"나 "중간에 취소"가 불가능하다.

또한 락이 반납됐을 때 대기 중인 스레드 중 누가 가져갈지 보장이 없다. 특정 스레드가 계속 후순위로 밀려 아주 오래 기다리는 기아(starvation) 현상도 생긴다.

자바 1.5에서 `java.util.concurrent.locks` 패키지가 추가되며 이 문제들을 해결한다.

---

## LockSupport — 원리 이해용 (직접 쓸 일 없음)

`ReentrantLock`의 내부에서 사용하는 저수준 클래스다. 이걸 먼저 보는 이유는 "왜 WAITING 상태를 쓰는지", "어떻게 인터럽트가 가능한지"를 이해하기 위해서다.

```java
LockSupport.park();                   // 현재 스레드 → WAITING (누군가 깨워야 풀림)
LockSupport.parkNanos(2_000_000_000); // 현재 스레드 → TIMED_WAITING, 2초 후 자동 복귀
LockSupport.unpark(thread);           // 지정한 스레드를 WAITING → RUNNABLE로 깨움
```

`park()`로 재운 스레드는 `unpark()`나 `interrupt()`로 깨울 수 있다.  
`interrupt()`로 깨우면 인터럽트 상태(`isInterrupted() == true`)가 남는다.

**BLOCKED vs WAITING — 핵심 차이**

| 구분 | BLOCKED | WAITING / TIMED_WAITING |
|---|---|---|
| **어디서 발생** | `synchronized` 락 대기 전용 | `park()`, `join()`, `wait()` 등 범용 |
| **인터럽트** | 걸어도 계속 대기 | 걸리면 즉시 RUNNABLE로 복귀 |
| **타임아웃** | 없음 | TIMED_WAITING은 자동 복귀 |
| **깨우는 방법** | 락 반납되면 자동 | `unpark()` 또는 `interrupt()` |

`ReentrantLock`은 내부에서 `LockSupport.park()`를 쓰기 때문에 락을 못 얻은 스레드가 **WAITING** 상태가 된다. 덕분에 인터럽트와 타임아웃이 가능해진다.

**LockSupport의 한계**: 너무 저수준이다. "10개의 스레드가 동시에 접근할 때 딱 1개만 실행되게 하라"를 직접 구현하려면, 어떤 스레드가 대기 중인지 추적하는 자료구조, 깨울 스레드 우선순위 결정 등 복잡도가 폭발한다. 그래서 `ReentrantLock`이 이걸 전부 래핑해서 제공한다.

---

## V4 — ReentrantLock 기본

```java
private final Lock lock = new ReentrantLock();

public boolean withdraw(int amount) {
    log("거래 시작");
    lock.lock();          // 락 획득 시도. 없으면 WAITING 상태로 대기
    try {
        if (balance < amount) return false;
        sleep(1000);
        balance = balance - amount;
        return true;
    } finally {
        lock.unlock();    // 반드시 finally에서 해제
    }
}
```

**왜 반드시 `finally`인가?**  
임계 영역 안에서 예외가 발생하거나 `return`으로 중간에 빠져나가도 `finally`는 무조건 실행된다. `unlock()`을 빠뜨리면 대기 중인 다른 스레드들이 영원히 WAITING 상태에 갇힌다.

**모니터 락과 혼동 주의**  
`lock.lock()`에서 사용하는 락은 객체 내부의 모니터 락이 아니다. `ReentrantLock`이 별도로 관리하는 자체 락이다. `synchronized`와 `ReentrantLock`은 서로 연동되지 않는다.

실행 결과에서 대기 스레드 상태가 `synchronized`의 BLOCKED와 달리 **WAITING**인 것을 확인할 수 있다.
```
t1 state: TIMED_WAITING  (sleep 중)
t2 state: WAITING        ← BLOCKED가 아님. 인터럽트 가능
```

**V4의 남은 문제**: `lock()`은 여전히 락을 얻을 때까지 무한 대기한다. 타임아웃이나 즉시 포기가 필요한 경우를 대응하지 못한다.

---

## V5 — tryLock(): 즉시 포기

같은 계좌에 두 번 동시에 출금 요청이 왔을 때, 두 번째 요청은 기다리는 대신 즉시 "이미 처리 중"이라고 알려주는 게 나을 수 있다.

```java
public boolean withdraw(int amount) {
    if (!lock.tryLock()) {              // 락 있으면 즉시 획득, 없으면 즉시 false
        log("[진입 실패] 이미 처리중인 작업이 있습니다.");
        return false;
    }
    try {
        // 임계 영역
    } finally {
        lock.unlock();
    }
    return true;
}
```

실행 결과:
```
t1] 거래 시작
t2] 거래 시작
t2] [진입 실패] 이미 처리중인 작업이 있습니다.   ← 즉시 종료
t2 state: TERMINATED     ← WAITING도 아님. 바로 끝남
t1] 거래 완료
```

t2는 WAITING 상태로 대기하지도 않고 바로 종료된다.

**V5의 남은 문제**: "아예 안 기다리는 것"과 "무한 대기" 사이의 중간이 없다. 잠깐은 기다리다가 너무 오래 걸리면 포기하고 싶은 경우가 있다.

---

## V6 — tryLock(시간): 타임아웃

```java
public boolean withdraw(int amount) {
    try {
        if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {  // 500ms만 대기
            log("[진입 실패] 너무 오래 기다렸습니다.");
            return false;
        }
    } catch (InterruptedException e) {       // 대기 중 인터럽트 발생 시
        throw new RuntimeException(e);
    }
    try {
        // 임계 영역
    } finally {
        lock.unlock();
    }
    return true;
}
```

실행 결과:
```
t1 state: TIMED_WAITING  (sleep(1000) 중)
t2 state: TIMED_WAITING  (tryLock(500ms) 대기 중)
t2] [진입 실패] 이미 처리중인 작업이 있습니다.  ← 0.5초 후 자동 포기
t1] 거래 완료
```

대기 중 스레드 상태가 TIMED_WAITING이고, 0.5초가 지나면 자동으로 RUNNABLE이 되어 `false` 반환.  
대기 중에 인터럽트가 걸려도 `InterruptedException`으로 즉시 빠져나올 수 있다.

---

## 공정 모드 vs 비공정 모드

```java
new ReentrantLock()       // 비공정 모드 (기본값)
new ReentrantLock(true)   // 공정 모드
```

- **비공정 (기본)**: 락이 반납되면 대기 중인 스레드 중 아무나 가져간다. 빠르지만 특정 스레드가 계속 밀릴 수 있다.
- **공정**: 대기 큐에 먼저 들어온 스레드가 먼저 락을 가져간다. 순서를 보장하지만 약간 느리다.

일반적으로 비공정 모드를 쓰고, 특정 스레드가 계속 밀리는 문제가 생길 때만 공정 모드를 고려한다.

---

## Lock 인터페이스 전체 메서드 정리

```java
public interface Lock {
    void lock();                                           // 무한 대기 (인터럽트 무시)
    void lockInterruptibly() throws InterruptedException;  // 무한 대기 + 인터럽트 가능
    boolean tryLock();                                     // 즉시 반환
    boolean tryLock(long time, TimeUnit unit);             // 시간 제한 대기
    void unlock();
    Condition newCondition();                              // 생산자-소비자 패턴에서 사용
}
```

맛집 비유:
- `lock()` — 먹을 때까지 무조건 줄 선다. 친구가 연락해도 안 간다.
- `lockInterruptibly()` — 줄 서지만, 친구가 연락하면 포기한다.
- `tryLock()` — 줄 있으면 즉시 포기.
- `tryLock(시간)` — N분만 기다리고 그래도 못 들어가면 포기. 친구 연락 와도 포기.

---

## 면접 Q&A

**Q: synchronized와 ReentrantLock의 차이는?**  
A: `synchronized`는 락 대기 중 타임아웃과 인터럽트가 불가능하고 공정성도 보장하지 않는다. `ReentrantLock`은 `tryLock(시간)`으로 타임아웃을 설정하거나, `lockInterruptibly()`로 인터럽트에 응답할 수 있고, 생성자에 `true`를 넣으면 공정 모드로 동작한다.

**Q: ReentrantLock에서 대기 중인 스레드의 상태는?**  
A: BLOCKED가 아닌 WAITING이다. 내부적으로 `LockSupport.park()`를 사용하기 때문이다. BLOCKED는 `synchronized`에서만 사용되는 특수한 대기 상태다.

**Q: tryLock()은 어떤 상황에 쓰나?**  
A: 락을 못 얻었을 때 기다리는 게 아니라 즉시 다른 응답을 줘야 할 때 쓴다. 예를 들어 같은 계좌에 동시에 두 번 출금 요청이 오면, 두 번째는 기다리지 않고 "이미 처리 중"이라는 응답을 바로 반환하는 게 UX상 낫다.

**Q: unlock()을 반드시 finally에 써야 하는 이유는?**  
A: 임계 영역 안에서 예외가 발생하거나 `return`으로 중간에 빠져나가더라도 `finally`는 반드시 실행된다. `unlock()`을 빠뜨리면 대기 중인 스레드들이 영원히 WAITING에 갇혀 데드락 상황이 발생한다.

**Q: 공정 모드를 항상 쓰지 않는 이유는?**  
A: 공정성을 보장하려면 대기 큐를 확인하고 순서를 관리해야 해서 오버헤드가 생긴다. 대부분의 경우 특정 스레드가 심하게 밀리는 일이 없어서 비공정 모드의 빠른 성능을 선택한다.

---

## stylink 실전 적용

### 재고 HOLD 타임아웃 처리
여러 고객이 동시에 같은 `InventoryItem`을 예약하려 할 때, 무한 대기 대신 일정 시간 후 포기하는 패턴.

```java
private final Lock lock = new ReentrantLock();

public boolean holdInventory(Long itemId) {
    try {
        if (!lock.tryLock(3, TimeUnit.SECONDS)) {
            throw new BusinessException(ErrorCode.INVENTORY_HOLD_TIMEOUT);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BusinessException(ErrorCode.INVENTORY_HOLD_INTERRUPTED);
    }
    try {
        // AVAILABLE 확인 → RESERVED로 변경
    } finally {
        lock.unlock();
    }
}
```

단, `ReentrantLock`은 **단일 JVM 내에서만 유효**하다. 서버가 여러 대로 늘어나는 환경에서는 DB의 `SELECT FOR UPDATE` 또는 Redis 기반 분산 락을 써야 한다.
