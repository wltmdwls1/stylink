# 웹 서버와 서블릿 컨테이너

## 전체 흐름 한눈에 보기

```
전통적 방식: 코드 → WAR 빌드 → 외장 WAS 설치 → WAR 배포 → 실행
스프링 부트: 코드 → JAR 빌드 → java -jar 실행 (내장 톰캣 포함)
```

스프링 부트가 왜 편한지를 이해하려면, 그 이전에 어떤 복잡한 과정이 있었는지 알아야 한다.
이 챕터는 그 배경 지식을 다룬다.

---

## JAR vs WAR

| 구분 | JAR | WAR |
|------|-----|-----|
| 실행 환경 | JVM | 외장 WAS(톰캣 등) |
| 실행 방법 | `java -jar app.jar` | WAS에 배포 |
| 스프링 부트 | JAR로 빌드 | 레거시 방식 |

**WAR 구조:**
```
WEB-INF/
  classes/   ← 컴파일된 클래스
  lib/       ← 라이브러리 jar들
web.xml      ← 배치 설정 (생략 가능)
index.html   ← 정적 리소스
```

---

## 서블릿 컨테이너 초기화 메커니즘

WAS 실행 시점에 스프링 컨테이너, 디스패처 서블릿, 필터 등을 등록해야 한다.

### 계층 구조

```
WAS 시작
  └─ ServletContainerInitializer.onStartup() 호출
       └─ @HandlesTypes 인터페이스 구현체를 찾아 실행
            └─ 스프링 컨테이너 생성 + 디스패처 서블릿 등록
```

### 1단계: ServletContainerInitializer 등록

```java
public class MyContainerInit implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        // WAS 시작 시 실행
    }
}
```

```
// resources/META-INF/services/jakarta.servlet.ServletContainerInitializer
hello.container.MyContainerInit
```

### 2단계: 애플리케이션 초기화 (@HandlesTypes)

`@HandlesTypes`로 인터페이스를 지정하면, 해당 인터페이스의 **모든 구현체를 자동으로 찾아** 실행한다.

```java
@HandlesTypes(AppInit.class)
public class MyContainerInit implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        for (Class<?> appInitClass : c) {
            AppInit appInit = (AppInit) appInitClass.getDeclaredConstructor().newInstance();
            appInit.onStartup(ctx);
        }
    }
}
```

---

## 스프링 컨테이너 + 디스패처 서블릿 등록 패턴

```java
public class AppInit implements WebApplicationInitializer {
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        // 1. 스프링 컨테이너 생성
        AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
        appContext.register(AppConfig.class);

        // 2. 디스패처 서블릿 생성 + 스프링 컨테이너 연결
        DispatcherServlet dispatcher = new DispatcherServlet(appContext);

        // 3. 서블릿 컨테이너에 디스패처 서블릿 등록
        ServletRegistration.Dynamic servlet = servletContext.addServlet("dispatcher", dispatcher);
        servlet.addMapping("/");
    }
}
```

---

## 스프링 MVC의 추상화: WebApplicationInitializer

스프링 MVC는 위 과정을 내부적으로 이미 구현해뒀다.
개발자는 `WebApplicationInitializer`만 구현하면 된다.

**내부 동작 원리:**
```
spring-web 라이브러리 내부
  META-INF/services/jakarta.servlet.ServletContainerInitializer
    → SpringServletContainerInitializer

@HandlesTypes(WebApplicationInitializer.class)
class SpringServletContainerInitializer implements ServletContainerInitializer {}
```

스프링이 `WebApplicationInitializer` 구현체를 자동으로 찾아서 실행해준다.

---

## 강의 권장 방식 vs stylink 적용 포인트

**강의 권장 방식:**
`WebApplicationInitializer` 구현 → 스프링 컨테이너 + 디스패처 서블릿 수동 등록

**stylink 적용 포인트: 강의 방식 그대로 적용 (스프링 부트가 자동화)**
- 스프링 부트가 내장 톰캣 + 초기화 과정 전부 자동화
- JAR로 빌드 → `java -jar`로 실행, WAS 설치/WAR 배포 불필요
- 이 챕터 내용은 "스프링 부트가 내부에서 무슨 일을 하는지" 이해하는 배경 지식

**면접 포인트:**
- "스프링 부트 없이 스프링 MVC를 외장 WAS에 배포하려면?" → `WebApplicationInitializer` 구현
- "DispatcherServlet은 어떻게 등록되나?" → `servletContext.addServlet()` + `addMapping("/")`
- "스프링 부트가 편한 이유?" → WAR/외장WAS 없이 JAR + 내장 톰캣으로 실행
