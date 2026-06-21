# 메모리 가시성

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
CPU 캐시 문제
  → 스레드가 메인 메모리 대신 CPU 캐시에서 값을 읽음
  → 다른 스레드가 쓴 값이 캐시에 아직 반영 안 됨 → 가시성 문제

volatile
  → 변수를 CPU 캐시 없이 메인 메모리에서 직접 읽고 씀 → 가시성 해결
  → 하지만 원자성(Atomicity)은 보장 안 됨

synchronized / AtomicInteger
  → 원자성까지 필요할 때
  → volatile만으론 부족한 이유: 읽기-쓰기가 두 단계인 경우 여전히 경쟁 조건 발생

→ 다음 챕터: synchronized로 원자성까지 해결
```

---

## CPU 캐시와 메모리 가시성 문제

현대 CPU는 메인 메모리(RAM) 접근이 느리기 때문에, 자주 쓰는 값을 빠른 **CPU 캐시(L1/L2)**에 보관한다.

**문제**: 스레드A가 캐시에 있는 `running = true`를 읽고 있는 사이, 스레드B가 메인 메모리의 `running = false`를 썼지만, 스레드A는 자기 캐시를 계속 보기 때문에 변경된 값을 모른다.

```
메인 메모리: running = false  ← 스레드B가 이미 바꿨음
CPU 캐시 A:  running = true   ← 스레드A는 이걸 보고 있음 → 무한 루프
CPU 캐시 B:  running = false
```

```java
// 스레드A (실행 중)
while (running) {  // CPU 캐시에서 running을 읽음 → 계속 true
    doWork();
}

// 스레드B
running = false;  // 메인 메모리에 썼지만, 스레드A의 캐시엔 미반영
```

결과: 스레드B가 `running = false`를 해도 스레드A가 영원히 멈추지 않는 버그.

---

## volatile — 가시성 보장

`volatile` 키워드를 붙이면 해당 변수는 항상 메인 메모리에서 직접 읽고 쓴다. CPU 캐시를 우회한다.

```java
private volatile boolean running = true;

// 스레드A
while (running) {   // 메인 메모리에서 직접 읽음 → 변경 즉시 반영
    doWork();
}

// 스레드B
running = false;    // 메인 메모리에 직접 씀 → 스레드A 즉시 감지
```

---

## volatile의 한계 — 원자성 미보장

`volatile`은 **가시성(Visibility)**은 해결하지만 **원자성(Atomicity)**은 보장하지 않는다.

```java
private volatile int count = 0;

// 스레드A, 스레드B 동시에:
count++;  // 이건 사실 3단계: 읽기 → 더하기 → 쓰기
```

스레드A가 `count = 0`을 읽는 사이 스레드B도 `count = 0`을 읽으면, 둘 다 `count = 1`을 쓴다. 기대는 2지만 결과는 1.

```
volatile이 해결하는 것:   캐시와 메인 메모리 간 불일치 (가시성)
volatile이 해결 못 하는 것: 읽기-수정-쓰기의 원자적 실행 (원자성)
```

원자성이 필요하면 `synchronized` 또는 `AtomicInteger`를 써야 한다 → 챕터 6, 10 참고

---

## volatile은 언제 쓰나?

단 하나의 스레드만 쓰고(write), 나머지는 읽기(read)만 하는 플래그에 적합하다.

```java
// 서버 종료 플래그 — main 스레드만 바꾸고, 워커 스레드들은 읽기만 함
private volatile boolean shutdown = false;

// 워커 스레드
while (!shutdown) {
    processRequest();
}
```

이처럼 **플래그 용도**에서는 volatile로 충분하다.  
여러 스레드가 동시에 값을 수정하는 카운터에는 부족하다.

---

## Java 메모리 모델 (JMM) 핵심

Java Memory Model은 멀티스레드 환경에서 메모리 가시성을 정의한 스펙이다.

핵심 규칙:
- `volatile` 쓰기는 동일 변수의 이후 `volatile` 읽기 전에 **happens-before** 관계를 형성한다
- `synchronized` 블록 안의 코드는 동일 모니터의 이후 `synchronized` 블록 코드보다 먼저 실행된 것으로 보장된다

"happens-before": A happens-before B = A의 결과가 B에 반드시 보인다는 JMM 보장.

---

## 면접 Q&A

**Q: volatile이 뭔가?**  
A: volatile 변수는 CPU 캐시 대신 메인 메모리에서 직접 읽고 쓰도록 강제하는 키워드다. 한 스레드가 값을 바꿔도 다른 스레드에서 즉시 반영된다는 가시성(visibility)을 보장한다.

**Q: volatile이 있으면 synchronized가 필요 없나?**  
A: 아니다. volatile은 가시성만 보장하고 원자성은 보장하지 않는다. `count++`처럼 읽기-수정-쓰기가 복합적인 연산은 volatile만으로는 경쟁 조건이 발생한다. 이때는 synchronized 또는 AtomicInteger가 필요하다.

**Q: 언제 volatile을 쓰면 좋은가?**  
A: 한 스레드만 값을 변경하고 나머지는 읽기만 하는 공유 플래그에 적합하다. 대표 예: 서버 종료 플래그, 캐시 유효성 플래그. 여러 스레드가 동시에 값을 수정하는 카운터에는 부족하다.
