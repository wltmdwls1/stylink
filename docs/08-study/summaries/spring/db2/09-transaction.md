# 스프링 트랜잭션 이해

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
스프링 트랜잭션 핵심 주제

① 추상화: PlatformTransactionManager
   JDBC: DataSourceTransactionManager / JdbcTransactionManager
   JPA:  JpaTransactionManager
   → @Transactional 하나로 어떤 기술이든 동일하게 사용

② AOP 프록시: @Transactional → CGLIB 프록시 생성
   BasicService$$EnhancerBySpringCGLIB...

③ 적용 위치 우선순위 (더 구체적인 것이 높음)
   메서드 > 클래스 > 인터페이스 메서드 > 인터페이스 타입

④ 주의 - 프록시 내부 호출
   external() → this.internal() → 프록시 안 거침 → 트랜잭션 미적용
   해결: internal()을 별도 클래스로 분리

⑤ 예외와 커밋/롤백 정책
   RuntimeException / Error → 롤백 (복구 불가)
   체크 예외 (Exception)   → 커밋 (비즈니스 예외)
   rollbackFor로 변경 가능
```

---

## 트랜잭션 추상화

```java
// PlatformTransactionManager 인터페이스
public interface PlatformTransactionManager extends TransactionManager {
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition);
    void commit(TransactionStatus status);
    void rollback(TransactionStatus status);
}
```

| 기술 | 트랜잭션 매니저 |
|------|--------------|
| JDBC / JdbcTemplate / MyBatis | `DataSourceTransactionManager` (스프링 5.3+: `JdbcTransactionManager`) |
| JPA / 스프링 데이터 JPA / Querydsl | `JpaTransactionManager` |

> 스프링 부트: 클래스패스의 기술 자동 감지 → 적절한 트랜잭션 매니저 자동 등록.

---

## 선언적 vs 프로그래밍 방식

```java
// 프로그래밍 방식 (직접 코드 작성)
TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
try {
    bizLogic();
    transactionManager.commit(status);
} catch (Exception e) {
    transactionManager.rollback(status);
}

// 선언적 방식 (실무 표준)
@Transactional
public void bizLogic() {
    // 순수 비즈니스 로직만
}
```

---

## 트랜잭션 적용 확인

```java
// 프록시 확인
assertThat(AopUtils.isAopProxy(basicService)).isTrue();
// basicService.getClass() → BasicService$$EnhancerBySpringCGLIB$xxx

// 트랜잭션 활성 여부 확인 (내부에서)
boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

// 로그 추가 (트랜잭션 시작/종료 추적)
// application.properties
// logging.level.org.springframework.transaction.interceptor=TRACE
```

---

## 트랜잭션 적용 위치 우선순위

```java
@Transactional(readOnly = true)  // 클래스: 기본값
static class LevelService {

    @Transactional(readOnly = false)  // 메서드: 더 구체적 → 메서드가 우선
    public void write() { ... }

    public void read() { ... }  // @Transactional 없음 → 클래스의 readOnly=true 적용
}
```

**우선순위 순서**:
1. 클래스의 메서드 (가장 높음)
2. 클래스의 타입
3. 인터페이스의 메서드
4. 인터페이스의 타입 (가장 낮음)

> 인터페이스에 `@Transactional` 사용은 공식 메뉴얼에서 권장하지 않음. 구체 클래스에 붙일 것.

---

## 프록시 내부 호출 문제

```java
// 문제 상황
static class CallService {
    public void external() {  // @Transactional 없음
        internal();  // this.internal() → 프록시 아닌 실제 객체 직접 호출!
    }

    @Transactional
    public void internal() { ... }  // 트랜잭션이 적용 안 됨!!
}
```

```
원인:
  external() 호출 → 프록시 거침 (external에 @Transactional 없으므로 트랜잭션 시작 안 함)
  → 실제 객체의 external() 실행
  → internal() 호출 시 this.internal() = 실제 객체 직접 호출
  → 프록시를 거치지 않으므로 트랜잭션 적용 안 됨
```

```java
// 해결: internal()을 별도 클래스로 분리
static class CallService {
    private final InternalService internalService;  // 주입받은 프록시

    public void external() {
        internalService.internal();  // 프록시를 거쳐서 호출 → 트랜잭션 적용
    }
}

static class InternalService {
    @Transactional
    public void internal() { ... }  // 정상 동작
}
```

> **핵심 원칙**: 트랜잭션을 적용하려면 반드시 프록시를 통해서 대상 객체를 호출해야 한다.  
> `public` 메서드에만 트랜잭션 적용 (스프링 부트 3.0+은 `protected`도 가능).

---

## 초기화 시점 주의

```java
// @PostConstruct + @Transactional → 트랜잭션 미적용!
// 이유: @PostConstruct는 AOP 적용 전에 호출됨
@PostConstruct
@Transactional
public void initV1() {
    boolean isActive = TransactionSynchronizationManager.isActualTransactionActive();
    log.info("tx active={}", isActive);  // false!
}

// 해결: ApplicationReadyEvent 사용
@EventListener(value = ApplicationReadyEvent.class)
@Transactional
public void init2() {
    boolean isActive = TransactionSynchronizationManager.isActualTransactionActive();
    log.info("tx active={}", isActive);  // true
}
// ApplicationReadyEvent: 스프링 컨테이너(AOP 포함) 완전 초기화 후 호출
```

---

## 트랜잭션 옵션

```java
public @interface Transactional {
    String value() default "";                               // 트랜잭션 매니저 빈 이름
    String transactionManager() default "";
    Class<? extends Throwable>[] rollbackFor() default {};   // 추가 롤백 대상 지정
    Class<? extends Throwable>[] noRollbackFor() default {}; // 롤백 제외
    Propagation propagation() default Propagation.REQUIRED;  // 전파 옵션
    Isolation isolation() default Isolation.DEFAULT;         // 격리 수준
    int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;  // 타임아웃(초)
    boolean readOnly() default false;                        // 읽기 전용
}
```

### value / transactionManager

```java
// 트랜잭션 매니저 2개 이상일 때 이름으로 지정
@Transactional("memberTxManager")
public void member() {...}

@Transactional("orderTxManager")
public void order() {...}
```

### rollbackFor

```java
// 체크 예외도 롤백하고 싶을 때
@Transactional(rollbackFor = Exception.class)
```

### readOnly

```
readOnly=true 효과:
  프레임워크: JPA 플러시 생략, 변경 감지 스냅샷 미생성 → 성능 최적화
  JDBC 드라이버: 읽기 전용 변경 시 예외, 슬레이브 DB 커넥션 사용 가능
  DB 자체: 읽기 전용 최적화
```

---

## 예외와 트랜잭션 커밋/롤백

```
스프링 기본 정책:
  RuntimeException / Error (언체크) → 롤백  (복구 불가능한 예외)
  Exception (체크 예외)              → 커밋  (비즈니스 예외)
  정상 리턴                          → 커밋
```

```java
@Transactional
public void runtimeException() {
    throw new RuntimeException();  // → 롤백
}

@Transactional
public void checkedException() throws MyException {
    throw new MyException();  // Exception 상속 → 커밋
}

@Transactional(rollbackFor = MyException.class)
public void rollbackFor() throws MyException {
    throw new MyException();  // rollbackFor 지정 → 롤백
}
```

---

## 비즈니스 예외 활용

```
시나리오: 주문 결제
  정상 → payStatus=완료, 커밋
  시스템 예외 → RuntimeException → 롤백 (데이터 날아감)
  잔고 부족 → NotEnoughMoneyException (체크예외) → 커밋
    → payStatus=대기로 저장 (Order 데이터 유지, 고객에게 입금 안내)
```

```java
@Service
@Transactional
public class OrderService {
    public void order(Order order) throws NotEnoughMoneyException {
        orderRepository.save(order);

        if (order.getUsername().equals("예외")) {
            throw new RuntimeException("시스템 예외");  // 롤백 → Order 사라짐
        } else if (order.getUsername().equals("잔고부족")) {
            order.setPayStatus("대기");
            throw new NotEnoughMoneyException("잔고가 부족합니다");  // 커밋 → Order 유지
        } else {
            order.setPayStatus("완료");
        }
    }
}

// NotEnoughMoneyException — 비즈니스 예외 (체크 예외)
public class NotEnoughMoneyException extends Exception {
    public NotEnoughMoneyException(String message) { super(message); }
}
```

```java
// 테스트
@Test
void bizException() {
    Order order = new Order();
    order.setUsername("잔고부족");
    try {
        orderService.order(order);
    } catch (NotEnoughMoneyException e) {
        log.info("고객에게 잔고 부족을 알리고 입금 안내");
    }
    Order findOrder = orderRepository.findById(order.getId()).get();
    assertThat(findOrder.getPayStatus()).isEqualTo("대기");  // 커밋됨!
}
```

> **비즈니스 예외**: 예외가 리턴값처럼 사용됨 → 체크 예외로 정의 → 커밋.  
> 체크 예외도 롤백하고 싶으면 `rollbackFor` 사용.

---

## 면접 Q&A

**Q: 스프링 트랜잭션 AOP에서 프록시 내부 호출 문제란 무엇이고 해결 방법은?**
A: `@Transactional`이 붙은 메서드를 같은 클래스의 다른 메서드에서 `this.internal()`로 호출하면 트랜잭션이 적용되지 않는 문제다. 스프링 트랜잭션은 CGLIB 프록시로 동작하는데, `this.`를 통한 내부 호출은 프록시를 거치지 않고 실제 객체를 직접 호출하기 때문이다. 해결책은 `internal()` 메서드를 별도의 클래스로 분리하고 스프링 빈으로 주입받아 외부 호출로 변경하는 것이다. 그러면 주입받은 빈이 프록시이므로 정상적으로 트랜잭션이 적용된다.

**Q: 스프링에서 체크 예외는 커밋하고 런타임 예외는 롤백하는 이유는?**
A: 스프링의 기본 정책은 `RuntimeException`/`Error`는 복구 불가능한 시스템 예외로 보아 롤백하고, 체크 예외(`Exception`)는 비즈니스 의미가 있는 예외로 보아 커밋한다. 예를 들어 잔고 부족(비즈니스 예외)은 `NotEnoughMoneyException`(체크 예외)으로 정의하면, 주문 데이터를 `대기` 상태로 저장하고 커밋할 수 있다. 롤백하면 주문 데이터 자체가 사라지므로 고객에게 안내할 방법이 없다. 체크 예외를 롤백하고 싶으면 `@Transactional(rollbackFor = ...)` 옵션을 사용하면 된다.

**Q: @PostConstruct와 @Transactional을 함께 사용하면 왜 트랜잭션이 적용되지 않는가?**
A: `@PostConstruct`는 스프링 빈이 생성된 직후, AOP 적용 이전에 호출된다. 즉, 트랜잭션 AOP 프록시가 아직 만들어지기 전에 메서드가 실행되므로 트랜잭션을 적용할 수 없다. 해결책은 `@EventListener(ApplicationReadyEvent.class)`를 사용하는 것이다. 이 이벤트는 스프링 컨테이너가 AOP를 포함해서 완전히 초기화된 후에 호출되므로 트랜잭션이 정상 적용된다.
