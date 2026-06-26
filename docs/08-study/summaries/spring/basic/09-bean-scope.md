# 빈 스코프

> 스프링 기본편 | DEEP

---

## 전체 흐름 한눈에 보기

```
스코프 = 빈이 존재할 수 있는 범위

싱글톤 (기본)
  → 컨테이너 시작~종료까지 유지

프로토타입
  → 요청마다 새 인스턴스 생성 후 반환
  → 컨테이너는 생성+DI+초기화까지만 → 종료 메서드 호출 안 됨

싱글톤 + 프로토타입 함께 사용 시 문제
  → 싱글톤 빈에 프로토타입 주입 시 생성 시점에만 새로 생성
  → 이후 계속 같은 인스턴스 사용됨 (의도 아님)

해결: ObjectProvider (DL - Dependency Lookup)
  → getObject() 호출 시점에 새로 생성

웹 스코프 (request)
  → HTTP 요청마다 별도 인스턴스
  → 싱글톤 빈이 주입받으려면 시작 시 아직 없음 → 오류
  
해결: Provider 지연 조회 or 프록시(@Scope proxyMode)
  → 핵심: 진짜 객체 조회를 꼭 필요한 시점까지 지연처리
```

---

## 스코프 종류

| 스코프 | 생존 범위 | 관리 주체 |
|--------|-----------|----------|
| 싱글톤 | 컨테이너 시작~종료 | 스프링 |
| 프로토타입 | 생성+DI+초기화 후 클라이언트에 위임 | 클라이언트 |
| request | HTTP 요청 하나 | 스프링 |
| session | HTTP 세션 | 스프링 |
| application | 서블릿 컨텍스트 | 스프링 |

---

## 프로토타입 스코프

```java
@Scope("prototype")
@Component
public class PrototypeBean {}

// 조회할 때마다 새 인스턴스
PrototypeBean b1 = ac.getBean(PrototypeBean.class);
PrototypeBean b2 = ac.getBean(PrototypeBean.class);
assertThat(b1).isNotSameAs(b2); // 다른 인스턴스
```

**핵심 차이**: 싱글톤은 `@PreDestroy` 호출됨. 프로토타입은 **호출 안 됨** — 클라이언트가 직접 관리해야 함.

---

## 싱글톤 + 프로토타입 함께 사용 시 문제

```java
static class ClientBean {
    private final PrototypeBean prototypeBean; // 생성 시점에 1번만 주입

    @Autowired
    public ClientBean(PrototypeBean prototypeBean) {
        this.prototypeBean = prototypeBean;
    }

    public int logic() {
        prototypeBean.addCount(); // 항상 같은 인스턴스의 count 증가
        return prototypeBean.getCount();
    }
}
```

```
clientBean1.logic() → count = 1
clientBean2.logic() → count = 2  ← 프로토타입인데 싱글톤처럼 동작!
```

싱글톤 빈은 생성 시점에만 DI를 받으므로, 프로토타입 빈이 한 번 주입된 후 계속 유지된다.

---

## 해결: ObjectProvider (권장)

```java
@Autowired
private ObjectProvider<PrototypeBean> prototypeBeanProvider;

public int logic() {
    PrototypeBean prototypeBean = prototypeBeanProvider.getObject(); // 매번 새로 조회 (DL)
    prototypeBean.addCount();
    return prototypeBean.getCount();
}
```

```
clientBean1.logic() → count = 1
clientBean2.logic() → count = 1  ← 매번 새 인스턴스
```

- `ObjectProvider.getObject()` = 스프링 컨테이너에서 해당 빈을 찾아서 반환 (Dependency Lookup)
- 기능이 단순해서 단위 테스트, mock 만들기 쉬움
- 스프링이 제공, 별도 라이브러리 불필요

---

## 웹 스코프 (request)

HTTP 요청마다 별도 빈 인스턴스 → UUID로 요청 구분, 로그 추적에 유용

```java
@Component
@Scope(value = "request")
public class MyLogger {
    private String uuid;
    private String requestURL;

    @PostConstruct
    public void init() {
        uuid = UUID.randomUUID().toString(); // HTTP 요청마다 새 UUID
    }

    @PreDestroy
    public void close() {
        System.out.println("[" + uuid + "] request scope bean close");
    }
}
```

**문제**: 애플리케이션 시작 시 싱글톤 빈 생성 → `MyLogger` 주입 시도 → 아직 HTTP 요청 없음 → **오류!**

---

## 해결 방법 1: ObjectProvider로 지연 조회

```java
@Controller
@RequiredArgsConstructor
public class LogDemoController {
    private final ObjectProvider<MyLogger> myLoggerProvider;

    @RequestMapping("log-demo")
    @ResponseBody
    public String logDemo(HttpServletRequest request) {
        MyLogger myLogger = myLoggerProvider.getObject(); // HTTP 요청 중에 조회 → OK
        myLogger.setRequestURL(request.getRequestURL().toString());
        myLogger.log("controller test");
        return "OK";
    }
}
```

---

## 해결 방법 2: 스코프 프록시 (더 편리)

```java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS) // 클래스면 TARGET_CLASS
public class MyLogger {}
// 인터페이스면 ScopedProxyMode.INTERFACES
```

```java
// Controller/Service는 Provider 사용 전과 동일하게 작성
@Controller
@RequiredArgsConstructor
public class LogDemoController {
    private final MyLogger myLogger; // 가짜 프록시 주입됨
    ...
}
```

**동작 원리**:
1. CGLIB으로 `MyLogger`를 상속받은 **가짜 프록시 객체** 생성
2. 가짜 프록시를 싱글톤 빈에 미리 주입 (HTTP 요청 없어도 OK)
3. `myLogger.log()` 호출 → 가짜 프록시가 진짜 request scope 빈을 찾아서 위임

```
myLogger.getClass() = MyLogger$$EnhancerBySpringCGLIB$$...  ← 프록시!
```

**핵심 아이디어**: Provider와 프록시 모두 **진짜 객체 조회를 꼭 필요한 시점까지 지연**처리.

---

## 면접 Q&A

**Q: 프로토타입 스코프와 싱글톤 스코프의 차이는?**
A: 싱글톤은 컨테이너 전체에서 하나의 인스턴스만 생성되어 공유된다. 프로토타입은 컨테이너에 요청할 때마다 새로운 인스턴스를 생성해서 반환한다. 또한 싱글톤은 컨테이너가 종료될 때 소멸 콜백이 호출되지만, 프로토타입은 반환 이후 컨테이너가 관리하지 않아 소멸 콜백이 호출되지 않는다.

**Q: 싱글톤 빈에서 프로토타입 빈을 매번 새로 받으려면?**
A: `ObjectProvider<PrototypeBean>`을 주입받아 `getObject()` 메서드를 호출한다. 이 방법은 DI가 아닌 DL(Dependency Lookup)로, 컨테이너에서 직접 빈을 찾아온다. 매번 새로운 프로토타입 빈을 생성해서 반환한다.

**Q: request scope 빈을 싱글톤 빈에 주입할 때 발생하는 문제와 해결책은?**
A: 싱글톤 빈은 애플리케이션 시작 시 생성되는데, 이 시점에는 HTTP 요청이 없어 request scope 빈을 생성할 수 없어 오류가 발생한다. 해결책은 두 가지다. 첫 번째는 `ObjectProvider`로 실제 요청 처리 시점까지 빈 조회를 지연하는 것. 두 번째는 `proxyMode = ScopedProxyMode.TARGET_CLASS`를 설정하여 CGLIB 프록시를 통해 실제 요청 시점까지 진짜 빈 조회를 지연하는 것이다. 두 방법 모두 핵심 아이디어는 "지연 처리"다.
