# 액츄에이터

## 전체 흐름 한눈에 보기

```
문제: 운영 중인 서버가 잘 동작하는지 어떻게 알 수 있나?
     DB 연결은? 디스크는? 현재 로그 레벨은? 요청 처리는 정상인가?

해결: spring-boot-starter-actuator 의존성 추가
     → /actuator 경로에 수십 개의 엔드포인트 자동 제공

2단계 설정:
  1단계: 활성화 (대부분 기본 활성화, shutdown만 명시 필요)
  2단계: 노출 (management.endpoints.web.exposure.include: "*")

핵심 엔드포인트:
  health    → 서버 생존 여부 (DB, 디스크 포함)
  info      → 앱 정보 (java/os/build/git)
  loggers   → 로그 레벨 조회 + 실시간 변경
  metrics   → 메트릭 (다음 챕터에서 상세)
  beans     → 등록된 빈 목록
  conditions → @Conditional 평가 결과

보안: 반드시 내부망 전용 or 별도 포트 (management.server.port)
```

---

## 의존성 추가

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

## 모든 엔드포인트 노출

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

기본 접근: `GET /actuator` → 모든 엔드포인트 링크 목록 반환.

---

## 엔드포인트 설정 구조

| 설정 | 의미 | 예시 |
|------|------|------|
| 활성화 | 기능 on/off | `management.endpoint.shutdown.enabled: true` |
| 노출 | HTTP/JMX 중 어디에 | `management.endpoints.web.exposure.include: "*"` |

엔드포인트 활성화 + 노출 둘 다 있어야 접근 가능.

```yaml
# 모든 노출, env와 beans 제외
management:
  endpoints:
    web:
      exposure:
        include: "*"
        exclude: "env,beans"
```

---

## 주요 엔드포인트 목록

| 엔드포인트 | 설명 | 기본 활성화 |
|-----------|------|-----------|
| `health` | 헬스 상태 (DB, 디스크 등) | O |
| `info` | 앱/빌드/git 정보 | O |
| `loggers` | 로그 레벨 조회·변경 | O |
| `metrics` | 메트릭 (마이크로미터 연동) | O |
| `beans` | 스프링 빈 목록 | O |
| `conditions` | @Conditional 평가 결과 | O |
| `configprops` | @ConfigurationProperties | O |
| `env` | Environment 정보 | O |
| `mappings` | @RequestMapping 정보 | O |
| `threaddump` | 쓰레드 덤프 | O |
| `httpexchanges` | HTTP 요청/응답 기록 | O (빈 등록 필요) |
| `shutdown` | 서버 종료 | **X** |

---

## 헬스 정보 (health)

```
GET /actuator/health → {"status": "UP"}
```

**핵심 규칙: 하나라도 DOWN → 전체 status = DOWN**

```yaml
management:
  endpoint:
    health:
      show-details: always     # 상세 정보 (DB 버전, 디스크 용량 등)
      # show-components: always  # status만 (상세 없이)
```

show-details 응답:
```json
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP", "details": { "database": "MySQL", ... } },
    "diskSpace": { "status": "UP", "details": { "total": ..., "free": ... } },
    "ping":      { "status": "UP" }
  }
}
```

기본 제공 헬스 컴포넌트: `db`, `mongo`, `redis`, `diskspace`, `ping`.
직접 구현 필요 시 `HealthIndicator` 인터페이스 구현.

---

## 애플리케이션 정보 (info)

```
GET /actuator/info
```

### 각 항목 활성화 방법

```yaml
management:
  info:
    java: { enabled: true }   # JVM/런타임 정보
    os:   { enabled: true }   # OS 정보
    env:  { enabled: true }   # info.* 환경 변수

# env 활성화 후 커스텀 정보 추가
info:
  app:
    name: stylink
    version: 1.0.0
```

주의: `management.endpoint` 하위가 아님 — `management.info` 바로 하위.

### build 정보

```groovy
// build.gradle
springBoot { buildInfo() }
```

빌드 시 `META-INF/build-info.properties` 자동 생성 → 활성화 기본.

### git 정보

```groovy
// build.gradle
plugins {
    id "com.gorylenko.gradle-git-properties" version "2.4.1"
}
```

빌드 시 `git.properties` 자동 생성 → 현재 브랜치, 커밋 ID/시간 노출.

**활용 포인트**: 배포 후 "이상한 동작" → `/actuator/info`로 어떤 커밋이 떠 있는지 즉시 확인.

```yaml
# git 전체 정보 (기본은 branch + commit만)
management:
  info:
    git:
      mode: "full"
```

---

## 로거 (loggers)

```
GET /actuator/loggers              → 전체 로거 목록
GET /actuator/loggers/{로거이름}   → 특정 로거
```

응답:
```json
{
  "ROOT":             { "configuredLevel": "INFO", "effectiveLevel": "INFO" },
  "hello.controller": { "configuredLevel": "DEBUG", "effectiveLevel": "DEBUG" }
}
```

- `configuredLevel`: 직접 설정한 레벨
- `effectiveLevel`: 실제 적용 레벨 (부모 상속 포함)

### 실시간 로그 레벨 변경 (핵심 기능)

**서버 재시작 없이** 운영 중 로그 레벨 변경 가능.

```
POST /actuator/loggers/hello.controller
Content-Type: application/json

{"configuredLevel": "TRACE"}
```

성공 시 204. 즉시 적용.

**언제 쓰나**: 운영 중 장애 발생 → 배포/재시작 없이 DEBUG 로그 즉시 켜서 원인 추적.

---

## HTTP 요청 응답 기록 (httpexchanges)

```java
@Bean
public InMemoryHttpExchangeRepository httpExchangeRepository() {
    return new InMemoryHttpExchangeRepository();
}
```

- 이 빈 없으면 httpexchanges 엔드포인트 비활성화
- 기본 최대 100개 (`setCapacity()`로 변경 가능)
- 개발 단계에서만 사용. 운영에는 Zipkin, 핀포인트 등 전용 도구 사용.

---

## 보안

### 별도 포트 분리 (권장)

```yaml
management:
  server:
    port: 9292
```

외부 인터넷 → 8080만 접근. 내부망에서만 9292 접근.

### 경로 인증

포트 분리가 어렵다면 `/actuator` 경로에 Spring Security 인증 추가.

### 경로 변경

```yaml
management:
  endpoints:
    web:
      base-path: "/manage"
```

`/actuator/{endpoint}` → `/manage/{endpoint}`

---

## stylink 적용 포인트

| 엔드포인트 | stylink에서의 활용 |
|-----------|-----------------|
| `health` | DB(MySQL) + diskSpace 헬스 자동 포함. `show-components: always`로 서비스 모니터링 |
| `info` | build/git 정보로 어떤 버전이 배포됐는지 추적 |
| `loggers` | 운영 중 특정 패키지 로그 레벨 실시간 변경 (재배포 없이) |
| `beans` | 자동 구성으로 어떤 빈이 등록됐는지 확인 (5장 연계) |
| `configprops` | application.yml 설정 값이 올바르게 바인딩됐는지 확인 |

보안 설정:
```yaml
management:
  server:
    port: 9090  # 운영 시 내부망 전용 포트
  endpoints:
    web:
      exposure:
        include: "health,info,loggers,metrics"  # 필요한 것만
```

---

## 면접 포인트

- **"스프링 부트 액츄에이터란?"**
  → 프로덕션 준비 기능(헬스체크, 메트릭, 로그 레벨 변경 등)을 `/actuator` 경로로 제공하는 라이브러리. 의존성 추가만으로 즉시 사용 가능

- **"엔드포인트 활성화와 노출의 차이는?"**
  → 활성화: 기능 자체 on/off. 노출: HTTP/JMX 중 어디에 공개할지. 대부분 기본 활성화, 노출만 설정하면 됨

- **"헬스체크가 DOWN이 되는 조건은?"**
  → 헬스 컴포넌트(DB, 디스크 등) 중 하나라도 DOWN이면 전체 status가 DOWN

- **"운영 중 로그 레벨을 바꾸는 방법은?"**
  → loggers 엔드포인트에 POST로 configuredLevel 변경. 서버 재시작 없이 즉시 적용

- **"액츄에이터 보안은 어떻게 처리하나?"**
  → management.server.port로 별도 포트 분리 후 내부망에서만 접근. 또는 Spring Security로 /actuator 경로 인증
