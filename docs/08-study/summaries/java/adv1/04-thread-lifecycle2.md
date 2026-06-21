# 스레드 제어와 생명 주기2

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] join()으로 종료 대기

인터럽트 — 대기 중인 스레드를 깨우거나 중단시키기
  → 문제: 스레드가 sleep 중일 때 외부에서 멈출 수 없음
  → interrupt()로 InterruptedException 발생 → 스레드가 스스로 중단 처리

인터럽트 플래그
  → isInterrupted() / interrupted()로 폴링
  → sleep 없는 루프에서 중단 조건 체크

yield() — CPU 양보
  → 현재 스레드가 CPU를 잠시 다른 스레드에 양보
```

---

## 인터럽트 (Interrupt)

스레드가 `sleep()` 중이거나 `WAITING` 상태일 때 외부에서 강제로 깨울 수 있다.

**문제 상황**: 파일을 다운로드하는 스레드가 `sleep(1000)` 중인데, 사용자가 취소를 눌렀다.
```java
// 다운로드 스레드
while (!done) {
    downloadChunk();
    Thread.sleep(1000); // ← 여기서 멈춰있으면 외부에서 어떻게 취소?
}
```

**해결: interrupt()**
```java
downloadThread.interrupt(); // 다운로드 스레드에 인터럽트 신호 전송
```

`interrupt()`를 호출하면:
1. 대상 스레드가 `sleep()`/`WAITING` 중이면 → 즉시 깨어나면서 `InterruptedException` 발생
2. 대상 스레드가 `RUNNABLE`이면 → 인터럽트 플래그만 `true`로 설정됨

```java
// 다운로드 스레드 내부
while (!done) {
    downloadChunk();
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        log("인터럽트 발생, 다운로드 중단");
        return; // 스레드 종료
    }
}
```

---

## 인터럽트 플래그로 폴링

`sleep()` 없이 CPU를 계속 사용하는 루프에서는 인터럽트 플래그를 직접 확인한다.

```java
while (!Thread.currentThread().isInterrupted()) {
    // 작업 처리
    doWork();
}
// 인터럽트 발생 시 루프 탈출
log("작업 중단됨");
```

**isInterrupted() vs interrupted()**
- `isInterrupted()`: 플래그 상태만 조회, 플래그 변경 없음
- `Thread.interrupted()`: 플래그 조회 후 **플래그를 false로 초기화** (static 메서드)

주의: `InterruptedException`이 발생하면 인터럽트 플래그가 자동으로 `false`로 초기화된다.  
catch 후 인터럽트 상태를 유지하고 싶다면 `Thread.currentThread().interrupt()`로 다시 설정해야 한다.

---

## 인터럽트를 무시하면 안 되는 이유

```java
// 잘못된 처리 — 인터럽트를 삼킴
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // 아무것도 안 함 ← 인터럽트 신호가 사라짐
}
```

인터럽트를 무시하면 상위 레이어에서 스레드를 종료하려고 해도 신호가 전달되지 않는다.  
처리할 수 없으면 `Thread.currentThread().interrupt()`로 플래그를 복원하거나, 체크 예외로 다시 던져야 한다.

---

## yield() — CPU 양보

`Thread.yield()`를 호출하면 현재 스레드가 RUNNABLE 상태를 유지하면서 CPU를 다른 RUNNABLE 스레드에 양보한다.

`sleep(0)`과 비슷하지만 TIMED_WAITING 상태로 전환되지 않는다는 차이가 있다.  
다른 스레드에 기회를 주고 싶지만 대기 상태로 빠지고 싶지 않을 때 사용.

단, OS 스케줄러에 힌트를 주는 것일 뿐 — 반드시 양보가 보장되진 않는다.

---

## 면접 Q&A

**Q: interrupt()는 어떻게 동작하나?**  
A: 대상 스레드에 인터럽트 신호를 보낸다. 스레드가 `sleep()`이나 `WAITING` 상태이면 즉시 깨어나며 `InterruptedException`이 발생한다. 스레드가 RUNNABLE 상태이면 인터럽트 플래그만 `true`로 설정되고, 스레드가 직접 플래그를 확인해 처리해야 한다.

**Q: InterruptedException catch 시 주의할 점은?**  
A: `InterruptedException`이 발생하면 인터럽트 플래그가 자동으로 `false`로 초기화된다. 상위 코드에 인터럽트 사실을 전달하려면 catch 블럭 안에서 `Thread.currentThread().interrupt()`로 플래그를 다시 설정해야 한다.

**Q: isInterrupted()와 interrupted()의 차이는?**  
A: `isInterrupted()`는 인터럽트 플래그 상태만 읽고 변경하지 않는다. `Thread.interrupted()`는 플래그를 읽은 후 `false`로 초기화한다.
