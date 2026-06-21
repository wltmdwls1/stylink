# 날짜와 시간

> 자바 중급1편 | LIGHT (레거시 API 인식 + 현대 API 사용법)

---

## 전체 흐름 한눈에 보기

```
레거시 API (Java 8 이전) — 레거시 코드에서 마주칠 수 있음
  → Date: 대부분 deprecated, 월이 0부터 시작 등 설계 결함
  → Calendar: mutable, 스레드 안전 아님, 사용 불편

현대 API (Java 8+, java.time 패키지) — 실무 표준
  → LocalDate, LocalTime, LocalDateTime (시간대 없음)
  → ZonedDateTime (시간대 포함)
  → Duration (시간 간격), Period (날짜 간격)
  → 불변 객체 → 스레드 안전, 직관적 API
```

> 실무에서는 java.time 패키지를 쓰면 된다.  
> 레거시 API는 코드 리딩 목적으로만 이해.

---

## 레거시 API (레거시 코드 읽기용)

```java
// Date — 레거시 코드에서 볼 수 있음
Date date = new Date();  // 현재 시간
date.getYear();  // 1900 기준! 2024라면 124 반환 → 버그 원인
date.getMonth(); // 0부터 시작! 1월은 0 → 버그 원인

// Calendar — Date보다 낫지만 여전히 불편
Calendar cal = Calendar.getInstance();
cal.set(Calendar.YEAR, 2024);
cal.set(Calendar.MONTH, 0);  // 1월 = 0
cal.set(Calendar.DAY_OF_MONTH, 15);
cal.add(Calendar.DAY_OF_MONTH, 10);  // 10일 뒤
Date result = cal.getTime();
```

**레거시 API 문제점:**
- 가변(mutable) → 멀티스레드에서 위험
- 월이 0부터 시작 → 직관적이지 않음
- API 설계가 일관성 없음

---

## 현대 API (java.time) — 실무 표준

**핵심 클래스:**

```java
LocalDate today = LocalDate.now();       // 날짜만 (2024-01-15)
LocalTime now = LocalTime.now();         // 시간만 (14:30:00)
LocalDateTime dt = LocalDateTime.now();  // 날짜+시간 (시간대 없음)
ZonedDateTime zdt = ZonedDateTime.now(); // 날짜+시간+시간대 (2024-01-15T14:30+09:00[Asia/Seoul])
```

**생성:**
```java
LocalDate date = LocalDate.of(2024, 1, 15);  // 2024-01-15 (1월=1!)
LocalTime time = LocalTime.of(14, 30, 0);    // 14:30:00
LocalDateTime dateTime = LocalDateTime.of(date, time);
```

**조작 — 불변이라 항상 새 객체 반환:**
```java
LocalDate date = LocalDate.of(2024, 1, 15);
date.plusDays(10)    // 2024-01-25 (새 객체, 원본 유지)
date.plusMonths(1)   // 2024-02-15
date.minusYears(1)   // 2023-01-15
date.withYear(2025)  // 2025-01-15 (특정 필드 교체)
```

**비교:**
```java
date1.isBefore(date2)  // date1 < date2
date1.isAfter(date2)   // date1 > date2
date1.isEqual(date2)   // date1 == date2
```

---

## Duration vs Period

```java
// Duration: 시간 단위 간격 (초, 나노초)
Duration duration = Duration.between(startTime, endTime);
duration.toMinutes();  // 몇 분?
duration.toHours();    // 몇 시간?

// Period: 날짜 단위 간격 (년, 월, 일)
Period period = Period.between(startDate, endDate);
period.getDays();   // 며칠 차이?
period.getMonths(); // 몇 달 차이?
```

---

## 포맷팅 / 파싱

```java
// 포맷팅 (날짜 → 문자열)
LocalDate date = LocalDate.of(2024, 1, 15);
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
String str = date.format(fmt);  // "2024-01-15"

// 파싱 (문자열 → 날짜)
LocalDate parsed = LocalDate.parse("2024-01-15", fmt);

// 표준 ISO 형식은 formatter 없이
LocalDate.parse("2024-01-15");  // ISO 8601 기본 지원
```

---

## JPA에서 날짜 처리

```java
@Entity
public class Order {
    @Column
    private LocalDateTime orderedAt;  // java.time 타입 직접 사용 가능 (JPA 2.2+)

    @Column
    private LocalDate deliveryDate;
}
```

---

## 면접 Q&A

**Q: java.time이 Calendar보다 나은 이유는?**  
A: java.time 클래스들은 불변이라 멀티스레드에 안전하고, 월이 1부터 시작해 직관적이며, 메서드 이름이 일관성 있고 명확하다. Calendar는 가변 객체라 공유 시 위험하고, 설계가 직관적이지 않았다.

**Q: LocalDateTime과 ZonedDateTime의 차이는?**  
A: `LocalDateTime`은 시간대 정보가 없다. "2024-01-15 14:30"이 어느 나라 시간인지 모른다. `ZonedDateTime`은 시간대가 포함돼 "서울 기준 2024-01-15 14:30"처럼 정확한 시점을 나타낸다. 글로벌 서비스나 시간대가 중요한 기능은 ZonedDateTime을 써야 한다.
