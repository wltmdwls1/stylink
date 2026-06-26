# 스프링 부트와 내장 톰캣

## 전체 흐름 한눈에 보기

```
WAR + 외장 WAS → Fat Jar → 스프링 부트 Executable Jar
                              java -jar 한 줄로 실행
```

WAR 배포 방식의 불편함 → 내장 톰캣(embed tomcat) 도입 → Fat Jar 한계 → 스프링 부트만의 특수 JAR 구조 설계로 완결.

---

## WAR 배포 방식의 단점

1. WAS(톰캣)를 서버에 별도 설치해야 함
2. IDE와 WAS 연동 설정이 복잡
3. 빌드(WAR) → WAS 전달 → 배포 단계가 번잡
4. 톰캣 버전 변경 시 WAS 재설치 필요

**해결 아이디어**: 톰캣을 라이브러리로 의존성 추가 → `main()` 실행만으로 WAS까지 실행

---

## 내장 톰캣 직접 사용 패턴

```java
public class EmbedTomcatSpringMain {
    public static void main(String[] args) throws LifecycleException {
        Tomcat tomcat = new Tomcat();
        Connector connector = new Connector();
        connector.setPort(8080);
        tomcat.setConnector(connector);

        AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
        appContext.register(HelloConfig.class);

        DispatcherServlet dispatcher = new DispatcherServlet(appContext);

        Context context = tomcat.addContext("", "/");
        tomcat.addServlet("", "dispatcher", dispatcher);
        context.addServletMappingDecoded("/", "dispatcher");

        tomcat.start();
    }
}
```

개발자가 `main()`에서 직접 순서 제어:
1. 톰캣 생성 + 포트 설정
2. 스프링 컨테이너 생성
3. 디스패처 서블릿 생성 + 연결
4. 톰캣 시작

---

## Fat Jar

### 등장 배경

일반 Jar로 빌드하면 라이브러리(스프링, 톰캣)가 포함되지 않아 실행 불가:
```
Caused by: java.lang.NoClassDefFoundError: org/springframework/web/context/WebApplicationContext
```

**원인**: JAR 스펙은 JAR 안에 JAR를 포함할 수 없음.

### Fat Jar 방식

라이브러리 jar를 모두 풀어서 `.class` 파일들을 하나의 jar에 직접 포함.

```groovy
task buildFatJar(type: Jar) {
    manifest { attributes 'Main-Class': 'hello.embed.EmbedTomcatSpringMain' }
    duplicatesStrategy = DuplicatesStrategy.WARN
    from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
    with jar
}
```

### Fat Jar 단점

| 단점 | 이유 |
|------|------|
| 라이브러리 추적 어려움 | 모두 `.class`로 풀려있어 어떤 라이브러리인지 확인 불가 |
| 파일명 중복 | 여러 라이브러리에 동일한 파일명 존재 시 하나만 살아남음 (예: `META-INF/services/jakarta.servlet.ServletContainerInitializer`) |

---

## 스프링 부트 실행 가능 Jar (Executable Jar)

### 핵심 아이디어

JAR 안에 JAR를 **그대로 포함** + `JarLauncher`가 내부 JAR를 직접 읽는 커스텀 클래스로더 사용.

### 스프링 부트 JAR 구조

```
boot-0.0.1-SNAPSHOT.jar
├── META-INF/
│   └── MANIFEST.MF
├── org/springframework/boot/loader/
│   └── JarLauncher.class        ← 진짜 진입점
└── BOOT-INF/
    ├── classes/                 ← 우리가 만든 클래스
    │   └── hello/boot/BootApplication.class
    └── lib/                     ← 라이브러리 jar 그대로
        ├── spring-webmvc-6.0.4.jar
        └── tomcat-embed-core-10.1.5.jar
```

### MANIFEST.MF 핵심

```
Main-Class: org.springframework.boot.loader.JarLauncher
Start-Class: hello.boot.BootApplication
Spring-Boot-Classes: BOOT-INF/classes/
Spring-Boot-Lib: BOOT-INF/lib/
```

- `Main-Class` = `JarLauncher` (우리가 만든 클래스가 아님)
- `Start-Class` = 우리가 만든 `BootApplication`

### 실행 흐름

```
java -jar xxx.jar
  → MANIFEST.MF 인식
  → JarLauncher.main()
    → BOOT-INF/classes 인식
    → BOOT-INF/lib/*.jar를 직접 읽는 클래스로더 생성
  → BootApplication.main() 호출
```

### Fat Jar 단점 해결

| Fat Jar 단점 | Executable Jar 해결 |
|---|---|
| 라이브러리 추적 어려움 | `BOOT-INF/lib/`에 jar 파일 그대로 → 확인 가능 |
| 파일명 중복 | jar 파일 그대로 포함 → 내부 경로 충돌 없음 |

---

## 스프링 부트 내부 실행 과정

```java
@SpringBootApplication
public class BootApplication {
    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
```

`SpringApplication.run()` 한 줄이 하는 일:
1. `AnnotationConfigServletWebServerApplicationContext` → 스프링 컨테이너 생성
2. `TomcatServletWebServerFactory` → 내장 톰캣 생성
3. 디스패처 서블릿 생성 + 연결

이것은 앞서 직접 구현한 `EmbedTomcatSpringMain`과 동일한 흐름. 스프링 부트가 이 과정을 자동화한 것.

---

## 강의 권장 방식 vs stylink 적용 포인트

**강의 권장 방식:**
직접 내장 톰캣 코드 → Fat Jar 한계 경험 → 스프링 부트 실행 가능 Jar 구조 이해

**stylink 적용 포인트: 강의 방식 그대로 (스프링 부트 자동화 사용)**
- `./gradlew bootJar` → Executable Jar 자동 생성
- `java -jar stylink.jar` 한 줄 실행
- 이 챕터 내용은 "스프링 부트가 내부에서 무슨 일을 하는지" 파악하는 배경 지식

**면접 포인트:**
- "Fat Jar와 스프링 부트 실행 가능 Jar 차이?" → JAR 안에 JAR 포함 가능 여부 + JarLauncher 구조
- "`java -jar`로 실행 시 어떤 클래스가 먼저 실행되나?" → `JarLauncher` (MANIFEST.MF의 Main-Class)
- "스프링 부트 내부에서 내장 톰캣은 어떻게 생성되나?" → `TomcatServletWebServerFactory`
