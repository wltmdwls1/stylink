# CAS - 동기화와 원자적 연산

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
문제: 카운터를 여러 스레드가 동시에 증가시키면 값이 손실됨

V1 BasicInteger: count++ → 원자적이지 않음 → 데이터 손실
V2 VolatileInteger: volatile count++ → 가시성 있지만 원자성 없음 → 여전히 손실
V3 SyncInteger: synchronized count++ → 정확하지만 락 오버헤드
V4 MyAtomicInteger: CAS(Compare-And-Swap)로 직접 구현
→ AtomicInteger: 자바가 제공하는 완성형 (하드웨어 CAS 명령 활용)

CAS = 락 없이 원자성을 보장하는 하드웨어 레벨 지원
```

---

## V1 — BasicInteger: 데이터 손실

```java
private int count = 0;
public void increment() { count++; }  // 읽기 → 더하기 → 쓰기 (3단계, 원자적 아님)
```

스레드A가 `count = 100`을 읽고 더하는 사이에 스레드B도 `count = 100`을 읽으면, 둘 다 `count = 101`을 쓴다. 기대값 102가 아닌 101.

**V1의 남은 문제**: `count++`는 원자적이지 않아 데이터 손실 발생.

---

## V2 — VolatileInteger: 가시성 있지만 원자성 없음

```java
private volatile int count = 0;
public void increment() { count++; }  // volatile이어도 3단계 연산은 여전히 비원자적
```

`volatile`로 메모리 가시성은 확보했지만, 읽기-수정-쓰기의 원자성은 여전히 없다.

**V2의 남은 문제**: 원자성 부재. 동일한 데이터 손실 발생.

---

## V3 — SyncInteger: 정확하지만 느림

```java
private int count = 0;
public synchronized void increment() { count++; }
```

`synchronized`로 한 번에 하나의 스레드만 진입 → 정확하다. 하지만 락 획득/해제 오버헤드, 나머지 스레드 BLOCKED 대기로 성능이 떨어진다. 경쟁이 심할수록 병목이 커진다.

**V3의 남은 문제**: 동시성 낮음. 락 기반 방식의 본질적 한계.

---

## V4 — MyAtomicInteger: CAS 원리 이해

CAS(Compare-And-Swap) = "현재 값이 내가 예상하는 값과 같으면 새 값으로 바꾸고, 다르면 아무것도 안 한다"

```java
// CAS 원리 (의사코드)
public boolean compareAndSet(int expected, int newValue) {
    // 이 전체가 하드웨어 레벨에서 원자적으로 실행
    if (this.value == expected) {
        this.value = newValue;
        return true;
    }
    return false;
}
```

CAS 기반 increment 구현:
```java
public int incrementAndGet() {
    int expected, newValue;
    do {
        expected = value;          // 현재 값 읽기
        newValue = expected + 1;   // 새 값 계산
    } while (!compareAndSet(expected, newValue));
    // CAS 실패 = 다른 스레드가 먼저 바꿈 → 다시 시도 (스핀)
    return newValue;
}
```

스레드A가 `count = 100`을 읽고 101을 넣으려는 순간, 다른 스레드가 이미 101로 바꿨다면 CAS가 실패하고 다시 현재값(101)을 읽어 102로 시도한다.

---

## AtomicInteger — 자바 표준 (실무에서 쓰는 것)

```java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();  // CAS 기반, 락 없이 원자적 증가
count.get();              // 현재 값 읽기
count.compareAndSet(100, 101);  // 100이면 101로 바꿈, 아니면 false
```

**AtomicInteger가 빠른 이유**: JVM이 CAS를 하드웨어의 단일 CPU 명령(`lock cmpxchg`)으로 변환한다. 락과 달리 스레드를 재우지 않고 루프로 재시도하는 **낙관적 동기화(Optimistic Locking)** 방식.

경쟁이 낮을 때: CAS >> synchronized (락 오버헤드 없음)  
경쟁이 매우 높을 때: CAS 스핀이 증가 → synchronized와 비슷해짐

---

## CAS vs synchronized 선택 기준

| 상황 | 추천 |
|---|---|
| 단순 카운터, 플래그 | AtomicInteger / AtomicBoolean |
| 복잡한 복합 연산 (읽기+조건+여러 필드 수정) | synchronized |
| 성능 최우선, 낮은 경쟁 | AtomicXxx (CAS) |
| 안전성 우선, 코드 단순성 | synchronized |

---

## 면접 Q&A

**Q: CAS가 무엇인가?**  
A: Compare-And-Swap. "현재 값이 기대한 값과 같으면 새 값으로 교체한다"는 단일 원자 연산이다. 하드웨어 CPU 명령으로 구현되어 락 없이 원자적 연산을 수행할 수 있다. Java의 `AtomicInteger`, `AtomicReference` 등이 내부적으로 이를 사용한다.

**Q: volatile과 AtomicInteger의 차이는?**  
A: `volatile`은 메모리 가시성(캐시 우회)만 보장하고 원자성은 없다. `AtomicInteger`는 CAS를 통해 원자적 읽기-수정-쓰기를 보장한다. `count++`처럼 복합 연산이 필요할 때는 AtomicInteger를 써야 한다.

**Q: CAS의 ABA 문제란?**  
A: 값이 A → B → A로 바뀌었을 때 CAS는 A로 같으니 성공으로 간주한다. 실제로는 중간에 변경이 있었지만 감지 못하는 문제. `AtomicStampedReference`로 버전 번호를 함께 비교해 해결한다.

## stylink 실전 적용

```java
// 재고 HOLD 카운터 — 여러 스레드가 동시에 접근
private final AtomicInteger holdCount = new AtomicInteger(0);

public boolean tryHold() {
    int current;
    do {
        current = holdCount.get();
        if (current >= maxHold) return false;  // 초과 시 실패
    } while (!holdCount.compareAndSet(current, current + 1));
    return true;
}
```
