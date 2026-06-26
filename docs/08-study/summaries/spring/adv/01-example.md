# 예제 만들기 (로그 추적기 도입 배경)

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
V0: 순수 기능 (Controller → Service → Repository)
  ↓ 로그 추적기 요구사항 발생
V1: HelloTraceV1 도입 → 로그 남기기 가능
     but 트랜잭션ID 다름, level 항상 0
  ↓
V2: beginSync()로 TraceId 파라미터 전달
     트랜잭션ID 동기화 + level 표현 가능
     but 모든 메서드 시그니처 변경 필요
  ↓
다음: ThreadLocal로 파라미터 없이 TraceId 공유
```

---

## 로그 추적기 요구사항

| 요구사항 | 설명 |
|---------|------|
| 모든 public 메서드 로그 | 호출/응답 정보 출력 |
| 실행 시간 | 각 메서드 수행 시간(ms) |
| 호출 깊이 표현 | `\|-->`, `\|<--` 들여쓰기 |
| HTTP 요청 구분 | 같은 요청 = 같은 트랜잭션ID |
| 예외 구분 | 정상: `<--`, 예외: `<X-` |
| 비즈니스 로직 불간섭 | 로그 때문에 예외 삼키면 안 됨 |

```
[796bccd9] OrderController.request()
[796bccd9] |-->OrderService.orderItem()
[796bccd9] | |-->OrderRepository.save()
[796bccd9] | |<--OrderRepository.save() time=1004ms
[796bccd9] |<--OrderService.orderItem() time=1014ms
[796bccd9] OrderController.request() time=1016ms
```

---

## 핵심 클래스 구조

### TraceId — 트랜잭션ID + 호출 깊이

```java
public class TraceId {
    private String id;    // UUID 앞 8자리 (트랜잭션ID)
    private int level;    // 호출 깊이 (0부터 시작)

    public TraceId createNextId() {
        return new TraceId(id, level + 1); // id 유지, depth +1
    }
}
```

### TraceStatus — 로그 시작 시점 상태 보관

```java
public class TraceStatus {
    private TraceId traceId;
    private Long startTimeMs; // 종료 시 경과시간 계산용
    private String message;
}
```

### HelloTraceV1 — 로그 추적기 본체

```java
@Slf4j
@Component
public class HelloTraceV1 {
    public TraceStatus begin(String message) { ... }
    public void end(TraceStatus status) { complete(status, null); }
    public void exception(TraceStatus status, Exception e) { complete(status, e); }
}
```

---

## 적용 패턴 (V1/V2 공통)

```java
TraceStatus status = null;
try {
    status = trace.begin("OrderController.request()");
    // 비즈니스 로직
    trace.end(status);
    return "ok";
} catch (Exception e) {
    trace.exception(status, e);
    throw e; // 반드시 다시 던질 것 — 로그가 예외를 삼키면 안 됨
}
```

**핵심 포인트:** `throw e` 없으면 예외가 사라져서 정상 흐름으로 동작함 → 치명적 버그

---

## V1 vs V2 비교

| | V1 | V2 |
|---|---|---|
| 트랜잭션ID | 레이어마다 새로 생성 | 첫 ID를 파라미터로 전달 |
| 호출 깊이 | 항상 0 | 올바르게 표현 |
| 문제점 | ID, depth 동기화 불가 | 모든 메서드 시그니처 변경 필요 |

### V2 핵심 — beginSync()

```java
// 기존 TraceId를 받아 id 유지 + level +1
public TraceStatus beginSync(TraceId beforeTraceId, String message) {
    TraceId nextId = beforeTraceId.createNextId();
    ...
}

// Controller → Service → Repository로 TraceId 넘기기
orderService.orderItem(status.getTraceId(), itemId);
```

---

## V2의 한계와 다음 방향

**문제:** TraceId를 파라미터로 넘기는 방식

- 모든 메서드 시그니처에 `TraceId` 추가 → 인터페이스까지 변경
- Service를 Controller 없이 직접 호출하면 TraceId가 없음
- 비즈니스 로직과 로그 인프라가 강결합

**해결:** `ThreadLocal` — HTTP 요청 스레드마다 별도 저장소에 TraceId 보관, 파라미터 전달 불필요

---

## stylink 적용 포인트

**강의 권장 방식:** 개발용 로그 추적기 직접 구현 → 원리 이해용

**stylink 적용 포인트:** 실무에서는 직접 구현 대신 MDC(Mapped Diagnostic Context) + Logback 조합 사용
- `MDC.put("traceId", UUID)` → Logback 패턴에서 `%X{traceId}` 로 자동 출력
- 강의의 TraceId 파라미터 전달 문제 → MDC는 ThreadLocal 기반이라 파라미터 불필요
- stylink에서는 Logback 이미 구성되어 있으므로 MDC 적용이 자연스러운 선택

---

## 면접 Q&A

**Q: 로그를 남기다가 예외가 발생하면 어떻게 처리해야 하나?**
A: 반드시 `throw e`로 예외를 다시 던져야 한다. 로그는 애플리케이션 흐름에 영향을 줘선 안 되는 부가 기능이다. 로그 처리에서 예외를 삼키면 예외가 사라지고 정상 흐름으로 동작해 버그가 숨겨진다.

**Q: TraceId를 파라미터로 전달하는 방식의 문제점은?**
A: 세 가지다. ① 모든 메서드 시그니처가 변경되어 인터페이스까지 수정해야 한다. ② Controller를 거치지 않고 Service를 직접 호출하면 TraceId가 없다. ③ 비즈니스 로직 메서드에 인프라 관심사(TraceId)가 침투한다. 이를 해결하는 것이 ThreadLocal이다.

**Q: ThreadLocal이 이 문제를 어떻게 해결하나?**
A: ThreadLocal은 스레드마다 별도 저장소를 제공한다. HTTP 요청이 하나의 스레드에서 처리되므로, 요청 시작 시 TraceId를 ThreadLocal에 저장해두면 Controller → Service → Repository 어디서든 파라미터 없이 꺼내 쓸 수 있다.
