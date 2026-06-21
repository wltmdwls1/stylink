# 스레드 생성과 실행

> 자바 고급1편 | LIGHT (레거시 방식 인식용)

---

## 전체 흐름 한눈에 보기

```
Thread 직접 생성 방식 (레거시)
  → Thread 클래스 상속
  → Runnable 인터페이스 구현 (더 권장됨)
  → 문제: 스레드를 매번 직접 만들고 관리해야 함 → 비효율 + 복잡

→ 실무에서는 Executor 프레임워크(챕터 12, 13)를 사용
```

> 이 챕터의 방식은 레거시 코드나 금융·공공 시스템에서 볼 수 있다.  
> 동작 원리를 이해하는 것이 목적 — 직접 쓸 일은 거의 없다.

---

## Thread 클래스 상속 (레거시 방식 1)

```java
public class HelloThread extends Thread {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + ": run()");
    }
}

HelloThread thread = new HelloThread();
thread.start();  // run()을 직접 호출하면 안 됨! start()로 새 스레드에서 실행
```

`start()`를 호출해야 JVM이 새 스레드를 생성하고 그 위에서 `run()`이 실행된다.  
`run()`을 직접 호출하면 현재 스레드(main)에서 실행되어 멀티스레드가 아니다.

---

## Runnable 인터페이스 구현 (레거시 방식 2 — 조금 더 낫다)

```java
public class HelloRunnable implements Runnable {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + ": run()");
    }
}

Thread thread = new Thread(new HelloRunnable());
thread.start();
```

**Thread 상속보다 Runnable 구현이 더 낫다:**
- 자바는 단일 상속만 허용 → Thread를 상속하면 다른 클래스를 상속할 수 없다
- 실행할 작업(Runnable)과 스레드(Thread)를 분리 → 역할 분리, 재사용성 향상

---

## 실제 레거시 코드에서 마주칠 상황

배치 서버나 레거시 금융 시스템에서 이런 패턴을 볼 수 있다:

```java
// 주문 처리를 별도 스레드에서
Thread orderThread = new Thread(new Runnable() {
    @Override
    public void run() {
        processOrder(orderId);  // 주문 처리 로직
    }
});
orderThread.start();
orderThread.join();  // 완료 대기
```

이 코드의 문제점:
1. 요청마다 새 스레드를 생성 → 스레드 생성 비용 큼 (1MB+ 메모리, OS 자원)
2. 스레드 수를 제한하는 장치 없음 → 급격한 트래픽 증가 시 서버 OOM
3. 결과값을 반환할 방법이 없음 (run()은 void)
4. checked exception을 던질 수 없음

**현대 대안**: `ExecutorService`를 사용하면 위 문제가 모두 해결된다 → 챕터 12, 13 참고

---

## 면접 Q&A

**Q: Thread 상속과 Runnable 구현의 차이는?**  
A: Thread를 상속하면 다른 클래스를 상속할 수 없어 유연성이 떨어진다. Runnable을 구현하면 Thread와 실행 작업이 분리되어 같은 Runnable을 여러 스레드에서 재사용할 수 있다. 실무에서는 대부분 Runnable을 사용하거나, 더 나아가 Executor 프레임워크를 사용한다.

**Q: run()과 start()의 차이는?**  
A: `run()`을 직접 호출하면 현재 스레드에서 순차적으로 실행된다. `start()`를 호출해야 JVM이 새 스레드를 생성하고 그 스레드 위에서 `run()`이 실행된다.

**Q: 실무에서 스레드를 직접 생성하지 않는 이유는?**  
A: 스레드 하나는 1MB 이상의 메모리를 사용하며 생성 비용이 크다. 요청마다 직접 생성하면 성능이 나쁘고, 스레드 수 제한이 없어 트래픽 급증 시 서버가 다운될 수 있다. Executor 프레임워크는 스레드 풀로 스레드를 재사용하고 최대 수를 제한해 이 문제를 해결한다.
