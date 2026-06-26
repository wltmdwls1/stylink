# 스프링 부트 스타터와 라이브러리 관리

## 전체 흐름 한눈에 보기

```
문제: 라이브러리 직접 선택 + 버전 호환성 확인 → 프로젝트 초기 세팅에 과도한 시간 소비

해결 1: dependency-management 플러그인 → 버전 생략 (BOM 기반 자동 관리)
해결 2: 스프링 부트 스타터 → 라이브러리 선택 자체를 생략
```

---

## 라이브러리 직접 관리의 문제

웹 프로젝트 하나에 필요한 라이브러리:
- spring-webmvc, tomcat-embed-core, jackson-databind
- spring-boot, spring-boot-autoconfigure
- logback-classic, log4j-to-slf4j, jul-to-slf4j
- snakeyaml

각각 버전도 지정해야 하고, 라이브러리 간 호환 버전까지 확인해야 함 → 세팅에만 상당한 시간 소비.

---

## 해결 1: 버전 관리 (dependency-management 플러그인)

```groovy
plugins {
    id 'org.springframework.boot' version '3.0.2'
    id 'io.spring.dependency-management' version '1.1.0'
    id 'java'
}
```

플러그인 추가 후 의존성에서 버전 생략 가능:
```groovy
implementation 'org.springframework:spring-webmvc'          // 버전 없음
implementation 'org.apache.tomcat.embed:tomcat-embed-core'  // 버전 없음
```

### 동작 원리: BOM

`spring-boot-dependencies`의 BOM(Bill of Materials) 참조:
- 스프링 부트 버전 하나를 지정하면 → 각 라이브러리의 검증된 버전을 자동 결정
- 스프링 부트 버전 변경 → 모든 라이브러리 버전 연동 변경

스프링 부트가 관리하지 않는 라이브러리는 여전히 버전 직접 명시 필요.

---

## 해결 2: 스프링 부트 스타터

버전 문제는 해결됐지만 "어떤 라이브러리를 골라야 하나" 문제는 여전히 존재.

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

`spring-boot-starter-web` 하나로 웹 개발에 필요한 모든 라이브러리 자동 포함:
- spring-webmvc, 내장 톰캣, jackson, logback, yaml 등

### 자주 사용하는 스타터

| 스타터 | 용도 |
|--------|------|
| `spring-boot-starter-web` | RESTful API, 내장 톰캣 |
| `spring-boot-starter-data-jpa` | JPA + 하이버네이트 |
| `spring-boot-starter-jdbc` | JDBC + HikariCP |
| `spring-boot-starter-validation` | Bean Validation |
| `spring-boot-starter-batch` | 스프링 배치 |

공식 스타터 패턴: `spring-boot-starter-*`
서드파티 패턴: `xxx-spring-boot-starter` (예: `mybatis-spring-boot-starter`)

---

## 강의 권장 방식 vs stylink 적용 포인트

**강의 권장 방식:**
1. 스타터 사용 (라이브러리 직접 선택 불필요)
2. dependency-management 플러그인으로 버전 관리

**stylink 적용 포인트: 강의 방식 그대로 적용**

stylink의 `build.gradle`이 이미 이 방식:
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa` 등 스타터 사용
- `io.spring.dependency-management` 플러그인 포함
- 버전 명시 없이 스프링 부트 버전 하나만 관리

**면접 포인트:**
- "스프링 부트 스타터란?" → 관련 라이브러리를 모아둔 의존성 세트; 한 줄로 웹 개발 환경 구성
- "dependency-management 플러그인은 어떻게 동작하나?" → `spring-boot-dependencies`의 BOM 참조, 부트 버전 기준으로 라이브러리 버전 자동 결정
- "BOM이 뭔가?" → Bill of Materials; 호환되는 라이브러리 버전 목록을 명세한 메타데이터
