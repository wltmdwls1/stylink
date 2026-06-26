# 빈 생명주기 콜백

> 스프링 기본편 | BASIC

---

## 전체 흐름 한눈에 보기

```
왜 생명주기 콜백이 필요한가?
  → DB 커넥션 풀, 네트워크 소켓 등
  → 앱 시작 시 연결, 종료 시 해제 필요
  → 그런데 스프링 빈은 생성 → DI 순서
  → 생성 시점에는 DI가 안 끝남 → 초기화 불가!

스프링 빈 이벤트 라이프사이클
  컨테이너 생성 → 빈 생성 → DI → 초기화 콜백 → 사용 → 소멸전 콜백 → 종료

3가지 콜백 방법
  1. InitializingBean/DisposableBean 인터페이스 (구식, 거의 사용 안 함)
  2. @Bean(initMethod/destroyMethod) (외부 라이브러리에 사용)
  3. @PostConstruct/@PreDestroy (권장!)

결론: @PostConstruct, @PreDestroy 사용
     외부 라이브러리는 @Bean(initMethod/destroyMethod)
```

---

## 왜 생성자에서 초기화하면 안 되나?

```
생성자 호출 → url = null
→ connect(null) 호출됨 → 의미 없음
→ 이후 setUrl("http://hello-spring.dev") 호출
→ 이미 늦음!
```

스프링 빈은 **객체 생성 → DI 완료 후**에야 초기화할 수 있는 준비가 된다.

> **원칙**: 객체 생성(가벼운 작업)과 초기화(무거운 작업)를 분리하라.

---

## 방법 1: 인터페이스 (거의 사용 안 함)

```java
public class NetworkClient implements InitializingBean, DisposableBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        connect(); // DI 완료 후 호출
    }

    @Override
    public void destroy() throws Exception {
        disConnect(); // 소멸 직전 호출
    }
}
```

단점: 스프링 전용 인터페이스 의존, 메서드명 고정, 외부 라이브러리 적용 불가.

---

## 방법 2: @Bean 설정 정보 (외부 라이브러리에 활용)

```java
@Bean(initMethod = "init", destroyMethod = "close")
public NetworkClient networkClient() {
    NetworkClient networkClient = new NetworkClient();
    networkClient.setUrl("http://hello-spring.dev");
    return networkClient;
}
```

장점: 메서드명 자유, 스프링 코드 의존 없음, **외부 라이브러리에도 적용 가능**.

**destroyMethod 추론 기능**:
- 기본값: `(inferred)` → `close`, `shutdown` 이름의 메서드 자동 호출
- 직접 스프링 빈 등록 시 `destroyMethod` 생략해도 됨
- 끄려면: `destroyMethod = ""`

---

## 방법 3: @PostConstruct, @PreDestroy (권장)

```java
public class NetworkClient {
    @PostConstruct
    public void init() {
        connect(); // DI 완료 후 자동 호출
    }

    @PreDestroy
    public void close() {
        disConnect(); // 소멸 직전 자동 호출
    }
}
```

| 특징 | 설명 |
|------|------|
| 스프링 표준 | 최신 스프링이 가장 권장 |
| 간결함 | 애노테이션 하나만 추가 |
| 자바 표준 | `javax.annotation.PostConstruct` = JSR-250, 다른 컨테이너에서도 동작 |
| 컴포넌트 스캔 | 잘 어울림 |
| 단점 | **외부 라이브러리에는 적용 불가** → `@Bean(initMethod/destroyMethod)` 사용 |

---

## 정리

```
기본: @PostConstruct + @PreDestroy
외부 라이브러리: @Bean(initMethod = "...", destroyMethod = "...")
```

---

## 면접 Q&A

**Q: 스프링 빈의 생명주기는?**
A: 스프링 컨테이너 생성 → 스프링 빈 생성 → 의존관계 주입 → 초기화 콜백(@PostConstruct) → 사용 → 소멸전 콜백(@PreDestroy) → 스프링 컨테이너 종료 순서다.

**Q: 왜 생성자에서 초기화하면 안 되나?**
A: 스프링 빈은 생성 이후에 의존관계가 주입된다. 생성자 호출 시점에는 의존관계 주입이 완료되지 않아 필요한 데이터(url 등)가 아직 없다. 또한 객체 생성(가벼운 작업)과 초기화(무거운 외부 연결 등)를 분리하는 것이 유지보수 관점에서 좋다.

**Q: @PostConstruct와 @Bean(initMethod)의 차이는?**
A: `@PostConstruct`는 자바 표준(JSR-250)으로, 내가 작성한 클래스에 바로 붙일 수 있어 간결하다. 외부 라이브러리처럼 소스 코드를 수정할 수 없는 경우에는 `@Bean(initMethod = "메서드명")`으로 설정 정보에서 지정해야 한다.
