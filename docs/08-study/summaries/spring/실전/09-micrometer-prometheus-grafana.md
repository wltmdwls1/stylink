# 마이크로미터, 프로메테우스, 그라파나

## 전체 흐름 한눈에 보기

```
문제: 모니터링 툴마다 측정 코드가 달라 → 툴 변경 시 코드 전체 재작성

해결: Micrometer = 메트릭 파사드 (SLF4J와 동일한 패턴)
  애플리케이션 코드는 Micrometer 표준 방식으로 측정
  구현체(프로메테우스, JMX 등)만 교체 → 앱 코드 불변

구조:
  스프링 부트 앱
    └─ 액츄에이터 + Micrometer → /actuator/prometheus 메트릭 노출
  프로메테우스 (TSDB)
    └─ 주기적 수집 (pull 방식) → PromQL로 조회
  그라파나
    └─ 프로메테우스를 데이터소스로 연결 → 대시보드 시각화

핵심: 프로메테우스 = 저장소, 그라파나 = 시각화 (그라파나가 DB가 아님)
```

---

## Micrometer 추상화

```
로그: SLF4J 추상화 → Logback/Log4j2 구현체
메트릭: Micrometer 추상화 → Prometheus/JMX/Datadog 구현체
```

스프링 부트 액츄에이터는 Micrometer를 기본 내장.

지원 툴 (19종): AppOptics, Atlas, CloudWatch, Datadog, Dynatrace, Elastic, Ganglia, Graphite, Humio, Influx, JMX, KairosDB, New Relic, **Prometheus**, SignalFx, StatsD, Wavefront 등

---

## 메트릭 확인 (/actuator/metrics)

```
GET /actuator/metrics
→ 전체 메트릭 이름 목록

GET /actuator/metrics/{name}
→ 특정 메트릭 상세
예: /actuator/metrics/jvm.memory.used
```

응답:
```json
{
  "name": "jvm.memory.used",
  "baseUnit": "bytes",
  "measurements": [{"statistic": "VALUE", "value": 131172848}],
  "availableTags": [
    {"tag": "area", "values": ["heap", "nonheap"]},
    {"tag": "id", "values": ["G1 Old Gen", ...]}
  ]
}
```

Tag 필터: `?tag=KEY:VALUE`
```
/actuator/metrics/jvm.memory.used?tag=area:heap
/actuator/metrics/http.server.requests?tag=uri:/log&tag=status:200
```

---

## 자동 등록 메트릭 분류

| 분류 | 메트릭 예시 | 특이 설정 |
|------|-----------|---------|
| JVM | `jvm.memory.used`, `jvm.gc.*`, `jvm.threads.*` | 없음 |
| 시스템 | `system.cpu.usage`, `process.cpu.usage`, `disk.free` | 없음 |
| 앱 시작 | `application.started.time`, `application.ready.time` | 없음 |
| MVC | `http.server.requests` (tag: uri/method/status/exception/outcome) | 없음 |
| 데이터소스 | `jdbc.connections.*`, `hikaricp.*` | 없음 |
| 로그 | `logback.events` (level별) | 없음 |
| 톰캣 | `tomcat.*` | `server.tomcat.mbeanregistry.enabled: true` |

MVC outcome 값: 1xx=INFORMATIONAL / 2xx=SUCCESS / 3xx=REDIRECTION / 4xx=CLIENT_ERROR / 5xx=SERVER_ERROR

---

## 프로메테우스 연동 설정

### 1. 의존성 추가

```groovy
implementation 'io.micrometer:micrometer-registry-prometheus'
```

→ `/actuator/prometheus` 엔드포인트 자동 노출 (프로메테우스 텍스트 포맷)

### 2. 포맷 변환 규칙

| 액츄에이터 형식 | 프로메테우스 형식 | 변환 이유 |
|--------------|--------------|---------|
| `jvm.info` | `jvm_info` | `.` → `_` |
| `logback.events` | `logback_events_total` | 카운터 → `_total` 접미사 추가 |
| `http.server.requests` | `http_server_requests_seconds_count`<br>`http_server_requests_seconds_sum`<br>`http_server_requests_seconds_max` | 내부 측정값 분리 |

### 3. prometheus.yml 수집 설정

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "prometheus"
    static_configs:
      - targets: ["localhost:9090"]

  # 스프링 앱 추가
  - job_name: "spring-actuator"
    metrics_path: '/actuator/prometheus'
    scrape_interval: 1s          # 운영 환경: 10s~1m 권장
    static_configs:
      - targets: ['localhost:8080']
```

연동 확인:
- `http://localhost:9090/config` → 설정 반영 확인
- `http://localhost:9090/targets` → 수집 대상 UP/DOWN 확인

---

## PromQL 문법

### 레이블 필터

```promql
# = 정확히 일치
http_server_requests_seconds_count{uri="/log", method="GET"}

# != 불일치
http_server_requests_seconds_count{uri!="/actuator/prometheus"}

# =~ 정규식 일치
http_server_requests_seconds_count{method=~"GET|POST"}

# !~ 정규식 불일치
http_server_requests_seconds_count{uri!~"/actuator.*"}
```

### 집계 함수

```promql
sum(http_server_requests_seconds_count)
sum by(method, status)(http_server_requests_seconds_count)   # GROUP BY
count(http_server_requests_seconds_count)
topk(3, http_server_requests_seconds_count)                  # 상위 3개
```

### 시간 관련

```promql
http_server_requests_seconds_count offset 10m      # 10분 전 값
http_server_requests_seconds_count[1m]             # 지난 1분간 모든 샘플 (범위 벡터)
```

범위 벡터 `[1m]`는 차트에 직접 출력 불가 → `increase()`, `rate()`로 가공 필요

---

## 게이지 vs 카운터

### 게이지(Gauge)

- 오르내리는 값: CPU 사용량, 메모리, 커넥션 수
- **현재 값 그대로** 그래프에 표현 가능

### 카운터(Counter)

- 단조 증가하는 누적 값: HTTP 요청 수, 로그 발생 수
- 문제: 계속 증가해 시간대별 변화 파악 어려움 → 함수로 변환 필요

| 함수 | 의미 | 용도 |
|------|------|------|
| `increase(metric[1m])` | 지정 시간 내 총 증가량 (절대 개수) | 분당 요청 수 |
| `rate(metric[1m])` | 초당 평균 증가율 (총 증가 / 60) | 초당 RPS |
| `irate(metric[1m])` | 초당 순간 증가율 | 급격한 스파이크 확인 |

```promql
increase(http_server_requests_seconds_count{uri="/log"}[1m])
rate(http_server_requests_seconds_count{uri="/log"}[1m])
irate(http_server_requests_seconds_count{uri="/log"}[1m])
```

---

## 그라파나 연동

### 데이터소스 연결

```
http://localhost:3000
Configuration → Data sources → Add data source → Prometheus
URL: http://localhost:9090
```

### 대시보드 패널 생성

패널 추가 → Edit → Code 모드 → PromQL 입력 → Run queries

예시:
```promql
# CPU
system_cpu_usage
process_cpu_usage

# 디스크 사용량
disk_total_bytes - disk_free_bytes
```

Unit 설정: bytes(SI) → 패널에 GB/TB 단위로 자동 표시

### 공유 대시보드 (Import)

Dashboards → New → Import → ID 입력 → Load → 데이터소스 선택

| 대시보드 | ID | 설명 |
|---------|-----|------|
| 스프링 부트 시스템 모니터 | `11378` | JVM, MVC, DB 커넥션 등 종합 |
| JVM Micrometer | `4701` | JVM 상세 메트릭 |

주의: 일부 대시보드는 Jetty 기준 → Tomcat 메트릭명으로 수정 필요
- `jetty_threads_config_max` → `tomcat_threads_config_max_threads`
- `jetty_threads_current` → `tomcat_threads_current_threads`

---

## 실무 문제 확인 4패턴

| 문제 | 관찰 메트릭 | 징후 |
|------|-----------|------|
| **CPU 초과** | `system_cpu_usage`, `process_cpu_usage` | 1.0(100%) 근접, 지속 고점 |
| **JVM OOM** | `jvm_memory_used_bytes{area="heap"}` | 힙 계속 상승 → 메트릭 수집 끊김 → `OutOfMemoryError: Java heap space` |
| **커넥션 풀 고갈** | `hikaricp_connections_active` | Active ≥ Max(10) → 요청 대기 → 30초 후 타임아웃 에러 |
| **에러 로그 급증** | `logback_events_total{level="error"}` | error 카운트 급격히 증가 |

OOM 로그: `java.lang.OutOfMemoryError: Java heap space`
커넥션 타임아웃 로그: `Connection is not available, request timed out after 30004ms`

메트릭은 대략적인 값과 **추세(trend)** 파악이 목적 — 정확한 수치가 아님

---

## stylink 적용 포인트

| 상황 | 적용 방법 |
|------|---------|
| 프로메테우스 연동 | `micrometer-registry-prometheus` 의존성 + actuator 노출 설정 |
| 재고 상태 변화 모니터링 | 커스텀 게이지로 AVAILABLE/RESERVED/IN_TRANSIT/SOLD 수량 추적 |
| 주문/결제 성공률 | `http.server.requests` tag=uri:/order, status별 비율 모니터링 |
| 커넥션 풀 | `hikaricp_connections_active` 알람 설정 (Active > 8개 경고) |
| OOM 조기 감지 | JVM heap 사용률 80% 초과 시 알람 |

---

## 면접 포인트

- **"Micrometer란?"**
  → 메트릭 측정 추상화 라이브러리. SLF4J처럼 인터페이스만 의존하고 구현체(프로메테우스, JMX 등)를 교체 가능. 스프링 부트 액츄에이터에 기본 내장

- **"프로메테우스와 그라파나의 역할 차이는?"**
  → 프로메테우스: 메트릭 수집·저장 TSDB (PromQL로 조회). 그라파나: 프로메테우스를 데이터소스로 연결해 대시보드 시각화. 그라파나 자체는 DB가 아님

- **"카운터를 그래프로 보려면 왜 increase()/rate()가 필요한가?"**
  → 카운터는 단조 증가하는 누적값이므로 시간대별 변화량을 파악하기 어려움. increase()는 구간 증가량, rate()는 초당 평균 증가율로 변환

- **"게이지와 카운터의 차이는?"**
  → 게이지: 오르내리는 현재 상태값 (CPU 사용량, 커넥션 수). 카운터: 단조 증가하는 누적값 (요청 수, 로그 발생 수)

- **"운영 중 커넥션 풀 고갈을 메트릭으로 어떻게 감지하나?"**
  → `hikaricp_connections_active`가 최대 풀 크기에 도달하면 신규 요청이 대기 상태가 됨. 이를 그라파나 알람으로 설정해 사전 감지 가능
