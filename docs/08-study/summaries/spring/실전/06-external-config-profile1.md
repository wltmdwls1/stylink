# 외부설정과 프로필1

## 전체 흐름 한눈에 보기

```
문제: 환경(dev/prod)마다 DB URL 등 설정값이 다름
나쁜 방법: 환경별로 빌드를 따로 → 개발 검증 결과물 ≠ 운영 결과물

좋은 방법: 빌드는 한 번 → 실행 시점에 외부 설정 주입
원칙: 변하는 것(설정값)과 변하지 않는 것(코드)을 분리

외부 설정 4가지:
OS 환경변수 → 자바 시스템 속성 → 커맨드 라인 인수 → 설정 파일(application.properties)

스프링이 모두 Environment로 통합 → 코드는 env.getProperty(key) 한 가지만 사용
```

---

## 외부 설정 방법 4가지

| 방법 | 전달 방식 | 조회 | 범위 |
|---|---|---|---|
| OS 환경변수 | OS 설정 | `System.getenv(key)` | 모든 프로세스 |
| 자바 시스템 속성 | `-Dkey=value` | `System.getProperty(key)` | 해당 JVM |
| 커맨드 라인 인수 | args[] | 직접 파싱 필요 | main() |
| 커맨드 라인 옵션 인수 | `--key=value` | `ApplicationArguments` | main() |

---

## 커맨드 라인 옵션 인수 (스프링 표준)

일반 커맨드 라인 인수(`key=value`)는 단순 문자열 → 개발자가 직접 파싱해야 함.

스프링은 `--` 접두사를 붙이면 key=value로 자동 파싱:

```
java -jar app.jar --url=devdb --username=dev_user mode=on
```

```java
ApplicationArguments appArgs = new DefaultApplicationArguments(args);
appArgs.getOptionValues("url");    // List<String> ["devdb"]
appArgs.getNonOptionArgs();        // ["mode=on"] -- 없는 것
```

하나의 키에 여러 값도 가능: `--username=userA --username=userB`

스프링 부트는 `ApplicationArguments`를 빈으로 자동 등록 → 어디서든 주입받아 사용.

---

## 스프링 통합: Environment + PropertySource

OS 환경변수, 자바 시스템 속성, 커맨드 라인 옵션 인수는 읽는 방법이 모두 다름.
→ 스프링이 `PropertySource` 추상 클래스 + 구현체들로 통합.

```
클라이언트 → environment.getProperty(key) → Environment
                                            ├── CommandLinePropertySource
                                            ├── SystemEnvironmentPropertySource  
                                            └── 설정 데이터 PropertySource
```

결과: 소스가 어디든 `env.getProperty(key)` 한 줄로 조회. 소스를 바꿔도 코드 수정 불필요.

---

## 설정 데이터 진화 과정

### 1단계: 외부 파일

jar 실행 위치에 `application.properties` 배치 → 스프링이 자동 읽음.

문제: 서버 10대면 10개 파일 따로 관리, 변경 이력 추적 어려움.

### 2단계: 내부 파일 분리

```
src/main/resources/
├── application-dev.properties
└── application-prod.properties
```

`spring.profiles.active=dev` 로 프로필 지정 → 해당 파일 자동 읽음.

문제: 파일이 분리되어 전체가 한눈에 안 들어옴.

### 3단계: 내부 파일 합체 (권장)

하나의 `application.properties`에 `#---` 구분자로 논리 문서 분리:

```properties
# 기본값 (프로필 없이 항상 적용)
url=local.db.com
username=local_user
password=local_pw
#---
spring.config.activate.on-profile=dev
url=dev.db.com
username=dev_user
password=dev_pw
#---
spring.config.activate.on-profile=prod
url=prod.db.com
username=prod_user
password=prod_pw
```

구분자 규칙:
- `.properties`: `#---` 또는 `!---`
- `.yml`: `---`
- 구분자 바로 위/아래에 주석 금지

---

## 설정 파일 읽기 순서 (핵심)

스프링은 위에서 아래로 순서대로 읽으며 값을 설정. 기존 데이터는 덮어씀.

```
1. 기본값 논리 문서 읽기 (항상 적용)
   url=local.db.com / username=local_user / password=local_pw

2. dev 논리 문서 읽기 (dev 프로필 활성화 시 덮어씀)
   url=local.db.com → dev.db.com
   username=local_user → dev_user

3. prod 논리 문서 읽기 (prod 프로필 활성화 시 덮어씀)
   ...
```

부분 교체도 가능: dev 문서에 url만 있으면 url만 변경, 나머지는 기본값 유지.

---

## 우선순위 전체 (낮음 → 높음, 아래가 우선)

**자주 사용하는 우선순위:**
```
application.properties
< OS 환경변수
< 자바 시스템 속성 (-D)
< 커맨드 라인 옵션 인수 (--)
< @TestPropertySource (테스트)
```

**설정 데이터 내부 우선순위:**
```
jar 내부 application.properties
< jar 내부 application-{profile}.properties
< jar 외부 application.properties
< jar 외부 application-{profile}.properties
```

**우선순위 원칙:**
- 더 유연한 것 우선 (실행 시 지정 > 파일)
- 범위 좁은 것 우선 (커맨드 라인 > JVM 속성 > OS 환경변수)

---

## 강의 권장 방식 vs stylink 적용 포인트

**강의 권장 방식:**
- 기본값은 `application.properties` 맨 위 논리 문서에
- 환경별 설정은 `spring.config.activate.on-profile`로 분리
- 코드에서는 항상 `environment.getProperty(key)` 또는 `@Value`로 읽음

**stylink 적용 포인트: 강의 방식 그대로 + `application.yml` 사용**

현재 stylink의 `application.yml`:
- `spring.datasource.*` → DataSource 자동 구성이 읽어서 사용
- `${DB_PASSWORD}` → OS 환경변수 주입 (더 높은 우선순위)
- 로컬/개발/운영 분리 필요 시 `---` 구분자로 합체 파일 관리

**면접 포인트:**
- "스프링의 외부 설정 통합 방법은?" → `Environment` + `PropertySource` 추상화. `env.getProperty(key)` 한 줄로 OS 환경변수/시스템 속성/커맨드 라인/설정 파일 모두 동일하게 읽음
- "프로필이란?" → `spring.profiles.active` 값으로 환경 구분. 해당 값을 `spring.config.activate.on-profile`과 매칭해 설정 파일 논리 문서를 선택적 적용
- "외부 설정 우선순위는?" → 더 유연하고 범위 좁을수록 높음. 커맨드 라인 > 자바 시스템 속성 > OS 환경변수 > application.properties
- "기본값은 어떻게 설정하나?" → `spring.config.activate.on-profile` 없는 논리 문서를 파일 맨 위에 두면 프로필과 무관하게 항상 적용
