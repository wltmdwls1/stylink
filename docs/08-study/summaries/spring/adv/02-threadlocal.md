# 쓰레드 로컬 - ThreadLocal

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
V2 문제: TraceId를 파라미터로 전달 → 모든 메서드 시그니처 오염
  ↓
해결 시도: TraceId를 필드(traceIdHolder)에 저장
  → FieldLogTrace: 단일 스레드에서는 완벽히 동작
  → BUT 싱글톤 빈 + 멀티 스레드 = 필드 동시 접근 → 데이터 오염
  ↓
근본 해결: ThreadLocal — 스레드마다 별도 저장소
  → ThreadLocalLogTrace: FieldLogTrace 필드 타입만 ThreadLocal<TraceId>로 교체
  → 구현체만 바꾸면 됨 (LogTrace 인터페이스 + @Bean 수동 등록)
  ↓
주의: 스레드 풀 재사용 → remove() 안 하면 이전 스레드 데이터 노출 (보안 위험)
```

---

## 동시성 문제 발생 조건

| 조건 | 설명 |
|------|------|
| 공용 인스턴스 필드 | 싱글톤 빈, static 필드 |
| 쓰기 발생 | 읽기만 하면 문제 없음 |
| 멀티 스레드 | 단일 스레드는 문제 없음 |

```java
// 문제 시나리오 — FieldService
public class FieldService {
    private String nameStore;  // 공용 필드

    public String logic(String name) {
        nameStore = name;   // Thread-A: userA 저장
        sleep(1000);        // 이 사이에 Thread-B: userB로 덮어씀
        return nameStore;   // Thread-A가 읽으면 userB 반환! → 데이터 오염
    }
}
```

**지역 변수는 안전하다:** 지역 변수는 스레드마다 별도 스택 메모리에 존재

---

## LogTrace 인터페이스

```java
public interface LogTrace {
    TraceStatus begin(String message);
    void end(TraceStatus status);
    void exception(TraceStatus status, Exception e);
}
```

인터페이스로 추상화 + 수동 빈 등록 → 구현체 교체 시 비즈니스 코드 수정 없음

```java
@Configuration
public class LogTraceConfig {
    @Bean
    public LogTrace logTrace() {
        return new ThreadLocalLogTrace();  // 여기만 바꾸면 됨
    }
}
```

---

## ThreadLocal API

```java
ThreadLocal<String> nameStore = new ThreadLocal<>();

nameStore.set(name);   // 현재 스레드 전용 보관소에 저장
nameStore.get();       // 현재 스레드 전용 보관소에서 조회
nameStore.remove();    // 현재 스레드 전용 보관소 값 제거 — 반드시 호출!
```

같은 인스턴스의 ThreadLocal 필드더라도 스레드마다 독립적인 저장소를 사용

---

## ThreadLocalLogTrace 핵심 구조

FieldLogTrace와 거의 동일 — `traceIdHolder` 타입만 교체

```java
public class ThreadLocalLogTrace implements LogTrace {
    // 변경 전: private TraceId traceIdHolder;
    private ThreadLocal<TraceId> traceIdHolder = new ThreadLocal<>();

    private void syncTraceId() {
        TraceId traceId = traceIdHolder.get();
        if (traceId == null) {
            traceIdHolder.set(new TraceId());        // 최초 요청
        } else {
            traceIdHolder.set(traceId.createNextId()); // level +1
        }
    }

    private void releaseTraceId() {
        TraceId traceId = traceIdHolder.get();
        if (traceId.isFirstLevel()) {
            traceIdHolder.remove();  // level==0 → 완전 제거 (필수!)
        } else {
            traceIdHolder.set(traceId.createPreviousId()); // level -1
        }
    }
}
```

---

## ThreadLocal 주의사항 — remove() 필수

**스레드 풀 재사용 시 데이터 유출 시나리오:**

```
1. 사용자A 요청 → thread-A 할당 → ThreadLocal에 사용자A 정보 저장
2. 응답 완료 → thread-A를 풀에 반환 (ThreadLocal 값은 그대로 남아있음)
3. 사용자B 요청 → 풀에서 thread-A 재사용
4. 사용자B가 ThreadLocal 조회 → 사용자A 데이터 반환! (개인정보 유출)
```

**해결:** 요청 처리 완료 시점(level==0)에 `traceIdHolder.remove()` 호출  
→ ThreadLocalLogTrace의 `releaseTraceId()`에서 이미 처리

---

## FieldLogTrace vs ThreadLocalLogTrace 비교

| | FieldLogTrace | ThreadLocalLogTrace |
|---|---|---|
| 필드 타입 | `TraceId traceIdHolder` | `ThreadLocal<TraceId> traceIdHolder` |
| 단일 스레드 | 정상 동작 | 정상 동작 |
| 멀티 스레드 | **데이터 오염** | 정상 동작 (스레드 격리) |
| remove() | 불필요 | **필수** |

---

## stylink 적용 포인트

**강의 권장 방식:** ThreadLocalLogTrace 직접 구현 → ThreadLocal 원리 체득

**stylink 적용 포인트:** MDC(Mapped Diagnostic Context) 사용
- MDC는 내부적으로 ThreadLocal 기반 → 동일한 스레드 격리 보장
- Logback과 기본 통합 → 별도 log.info() 파라미터 없이 패턴에서 `%X{traceId}` 자동 출력
- 요청 시작 시 `MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8))`
- 요청 종료 시 `MDC.clear()` → ThreadLocal.remove()와 동일한 역할
- stylink는 이미 Logback 구성 완료 → MDC 적용이 가장 자연스러운 선택

```java
// Filter 또는 Interceptor에서
MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
try {
    chain.doFilter(request, response);
} finally {
    MDC.clear();  // 스레드 풀 반환 전 반드시 제거
}
```

---

## 면접 Q&A

**Q: 싱글톤 빈에서 동시성 문제가 발생하는 이유는?**
A: 스프링 싱글톤 빈은 인스턴스 1개를 여러 스레드가 공유한다. 인스턴스 필드에 상태를 저장하면 여러 스레드가 동시에 같은 필드를 읽고 쓰게 되어 데이터가 오염된다. 읽기만 하는 필드는 안전하고, 쓰기가 있을 때 문제가 생긴다.

**Q: ThreadLocal이란 무엇이고 어떻게 동시성 문제를 해결하나?**
A: ThreadLocal은 스레드마다 별도의 저장소를 제공하는 Java 내장 기능이다. 같은 ThreadLocal 인스턴스에 여러 스레드가 접근해도, 각 스레드는 자신만의 저장소에서 데이터를 읽고 쓴다. 덕분에 싱글톤 빈의 필드를 ThreadLocal로 선언하면 멀티 스레드 환경에서도 데이터 격리가 보장된다.

**Q: ThreadLocal 사용 시 반드시 remove()를 호출해야 하는 이유는?**
A: WAS는 스레드 풀로 스레드를 재사용하기 때문이다. 요청 처리 후 remove()를 호출하지 않으면 ThreadLocal 값이 남아있고, 다음 요청에서 같은 스레드를 재사용할 때 이전 사용자의 데이터가 노출된다. 이는 개인정보 유출로 이어지는 심각한 보안 문제다.

**Q: MDC와 ThreadLocal의 관계는?**
A: MDC(Mapped Diagnostic Context)는 내부적으로 ThreadLocal을 사용한다. Logback이 제공하는 MDC는 ThreadLocal 기반으로 요청별 컨텍스트(traceId, userId 등)를 저장하고, 로그 패턴에서 `%X{key}`로 자동 출력한다. 직접 ThreadLocal을 다루는 것보다 MDC를 쓰면 Logback 연동이 자동으로 되어 실무에서 더 편리하다.
