# 스레드 제어와 생명 주기1

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
스레드의 6가지 상태
  → NEW / RUNNABLE / BLOCKED / WAITING / TIMED_WAITING / TERMINATED

join() — 다른 스레드가 끝날 때까지 기다리기
  → 문제1 (join 없음): main이 t1 결과를 아직 못 받았는데 먼저 끝남
  → 문제2 (sleep 사용): 몇 ms 기다릴지 하드코딩 → 불안정
  → V3 (join 사용): t1이 종료될 때까지 정확히 대기

→ 다음 챕터: 스레드 중간에 멈추는 방법 (interrupt)
```

---

## 스레드의 6가지 상태

```
NEW → RUNNABLE → TERMINATED
              ↓         ↑
          BLOCKED ──────┤
          WAITING ──────┤
      TIMED_WAITING ────┘
```

| 상태 | 설명 |
|---|---|
| **NEW** | 스레드 객체 생성됨. `start()` 호출 전 |
| **RUNNABLE** | 실행 중이거나 실행 대기 중 (OS 스케줄러에 의해 언제든 실행될 수 있음) |
| **BLOCKED** | `synchronized` 락을 기다리는 중 |
| **WAITING** | 누군가 깨워줄 때까지 무한 대기 (`join()`, `park()`, `wait()`) |
| **TIMED_WAITING** | 지정한 시간만큼 대기 (`sleep(ms)`, `join(ms)`, `parkNanos()`) |
| **TERMINATED** | `run()` 메서드가 완료되어 스레드 종료. 재시작 불가 |

RUNNABLE 상태라도 항상 CPU를 점유하는 건 아니다. OS 스케줄러가 허용할 때만 실제 실행된다.

---

## join() — 스레드 종료 대기

**문제 상황**: 스레드에서 1+2+...+100을 계산하고 결과를 main에서 쓰고 싶다.

**V1 — join 없음: 결과 못 받음**
```java
SumTask task = new SumTask(1, 100);
Thread t1 = new Thread(task);
t1.start();
// 여기서 t1이 아직 계산 중인데 main이 result를 읽어버림
System.out.println(task.result); // 0 출력 (아직 완료 안 됨)
```
t1은 RUNNABLE이고 main도 RUNNABLE이라 실행 순서가 보장되지 않는다.

**V2 — sleep으로 때우기: 불안정**
```java
t1.start();
Thread.sleep(3000); // 3초면 되겠지...
System.out.println(task.result);
```
실행 환경에 따라 3초가 부족할 수도, 너무 길 수도 있다. 근본적 해결책이 아니다.

**V3 — join(): 정확히 대기**
```java
t1.start();
t1.join(); // t1이 TERMINATED 상태가 될 때까지 main이 WAITING으로 대기
System.out.println(task.result); // t1 완료 후 정확히 읽힘
```
`join()`을 호출한 스레드(main)는 WAITING 상태가 되고, t1이 끝나면 자동으로 RUNNABLE로 돌아온다.

**join(ms) — 최대 대기 시간 지정**
```java
t1.join(3000); // 최대 3초만 기다림. 3초 후에도 안 끝나면 그냥 진행
```

---

## 체크 예외와 스레드

`run()` 메서드는 체크 예외(checked exception)를 던질 수 없다.  
`Runnable`의 `run()` 시그니처가 `throws` 없이 선언되어 있기 때문.

```java
@Override
public void run() {
    try {
        Thread.sleep(1000); // InterruptedException: checked exception
    } catch (InterruptedException e) {
        // run() 안에서 처리해야 함
    }
}
```

스레드 안에서 발생한 예외를 바깥에서 받으려면 별도 메커니즘(`Future`, `UncaughtExceptionHandler` 등)이 필요하다. → Executor 프레임워크(챕터 12)에서 해결

---

## 면접 Q&A

**Q: 스레드의 상태 6가지는?**  
A: NEW(생성), RUNNABLE(실행 중/대기), BLOCKED(synchronized 락 대기), WAITING(무한 대기), TIMED_WAITING(시간 제한 대기), TERMINATED(종료). BLOCKED는 synchronized에서만 사용하고, WAITING/TIMED_WAITING은 더 범용적이다.

**Q: join()은 무엇이고 왜 쓰나?**  
A: 다른 스레드가 종료될 때까지 현재 스레드를 WAITING 상태로 대기시키는 메서드. 예를 들어 스레드에서 계산한 결과를 main에서 사용해야 할 때, join() 없이는 계산이 끝나기 전에 main이 결과를 읽어버릴 수 있다.

**Q: RUNNABLE 상태인 스레드가 항상 CPU를 점유하나?**  
A: 아니다. RUNNABLE은 "실행 중이거나 실행될 준비가 된" 상태다. 실제 CPU를 점유하는 건 OS 스케줄러가 해당 스레드에 CPU를 할당할 때뿐이다.
