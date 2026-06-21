# 함수형 프로그래밍

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
프로그래밍 패러다임
  → 명령형(How): 절차지향, 객체지향
  → 선언형(What): 함수형

함수형 프로그래밍의 3가지 핵심
  1. 순수 함수: 같은 입력 → 항상 같은 출력, 부수 효과 없음
  2. 불변성: 데이터를 변경하지 않고 새 데이터를 만듦
  3. 함수 합성: 작은 함수들을 조합해 복잡한 로직 구성

자바에서의 함수형
  → 완전한 함수형 언어는 아님 (상태, 부수 효과 있음)
  → 람다 + 스트림 + Optional로 함수형 스타일 적용 가능
  → "멀티스레드 안전성"과 "테스트 용이성"이 실용적 이유
```

---

## 명령형 vs 선언형

**명령형**: "어떻게(How)" 할 것인지 단계별로 지시
```java
// 100 이상의 짝수 합 — 명령형
List<Integer> numbers = List.of(50, 100, 150, 200, 250);
int sum = 0;
for (Integer n : numbers) {
    if (n >= 100 && n % 2 == 0) {
        sum += n;
    }
}
```

**선언형**: "무엇(What)"을 원하는지 표현
```java
// 100 이상의 짝수 합 — 선언형 (스트림)
int sum = numbers.stream()
    .filter(n -> n >= 100)
    .filter(n -> n % 2 == 0)
    .mapToInt(Integer::intValue)
    .sum();
```

---

## 핵심1 — 순수 함수 (Pure Function)

같은 입력에 대해 항상 같은 출력을 반환하고, **외부 상태를 변경하지 않는** 함수.

```java
// 순수 함수 — 외부 의존 없음, 부수 효과 없음
public int add(int a, int b) {
    return a + b;  // 언제 호출해도 항상 같은 결과
}

// 순수하지 않은 함수 — 외부 상태에 의존하거나 변경함
private int count = 0;
public int addAndCount(int a, int b) {
    count++;  // 부수 효과 (외부 상태 변경)
    return a + b;
}

public int random() {
    return Math.random() > 0.5 ? 1 : 0;  // 같은 입력에 다른 출력
}
```

순수 함수의 장점:
- 테스트하기 쉽다 — 입력/출력만 검증하면 됨
- 멀티스레드 안전 — 공유 상태를 수정하지 않음
- 추론하기 쉽다 — 결과를 예측할 수 있음

---

## 핵심2 — 불변성 (Immutability)

데이터를 변경하지 않고, 수정이 필요하면 **새 객체를 만들어 반환**.

```java
// 명령형 — 기존 리스트 변경
List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3));
numbers.add(4);  // 기존 리스트를 변경

// 함수형 스타일 — 새 리스트 생성
List<Integer> original = List.of(1, 2, 3);
List<Integer> added = Stream.concat(
    original.stream(),
    Stream.of(4)
).toList();  // 원본은 그대로, 새 리스트를 만듦
```

Java의 `String`, `Integer` 등이 불변인 이유와 같다 — 공유해도 안전하다.

---

## 핵심3 — 함수 합성

작은 함수들을 조합해 더 복잡한 로직을 만든다.

```java
Function<String, String> trim    = String::trim;
Function<String, String> lower   = String::toLowerCase;
Function<String, Integer> length = String::length;

// andThen으로 파이프라인 구성
Function<String, Integer> pipeline = trim.andThen(lower).andThen(length);
int result = pipeline.apply("  Hello World  ");  // 11
```

스트림의 `.filter().map().collect()`가 바로 함수 합성 패턴이다.

---

## 자바에서의 함수형 프로그래밍 현실

자바는 순수 함수형 언어가 아니다 — 상태도 있고 부수 효과도 있다.  
하지만 **함수형 스타일을 선택적으로 적용**하면 실질적인 이점이 있다.

```
함수형 스타일을 써야 할 곳:
  → 데이터 변환/집계 파이프라인 (Stream)
  → 없을 수 있는 값 처리 (Optional)
  → 비즈니스 규칙 표현 (Predicate 조합)

명령형을 써야 할 곳:
  → 상태 변경이 필요한 로직
  → 성능이 매우 중요한 부분
  → 복잡한 제어 흐름 (break/continue/인덱스)
```

---

## 면접 Q&A

**Q: 순수 함수란 무엇이고 왜 중요한가?**  
A: 같은 입력에 항상 같은 출력을 반환하고 외부 상태를 변경하지 않는 함수. 테스트가 쉽고(입력/출력만 확인), 멀티스레드 환경에서 공유 상태를 수정하지 않으므로 동시성 문제가 없으며, 코드 추론이 쉽다.

**Q: 함수형 프로그래밍과 객체지향 프로그래밍은 대립되나?**  
A: 대립이 아니라 보완 관계다. 자바처럼 대부분의 현대 언어는 두 패러다임을 혼합해 쓴다. 객체지향으로 도메인 구조를 설계하고, 데이터 처리 파이프라인은 함수형 스타일(스트림)을 쓰는 식이다.

**Q: 불변 객체가 멀티스레드에서 안전한 이유는?**  
A: 불변 객체는 생성 후 상태가 변하지 않는다. 여러 스레드가 동시에 읽어도 값이 바뀔 가능성이 없으므로 동기화가 필요 없다. 자바의 String이 불변인 것도 이 이유다 — 어디서 참조해도 안전하다.

## stylink 실전 적용

```java
// 순수 함수 스타일 — OrderService
// 상태를 변경하지 않고 새 객체 반환
public Order calculateTotal(Order order) {
    int total = order.getItems().stream()
        .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
        .mapToInt(item -> item.getPrice() * item.getQuantity())
        .sum();
    return order.withTotal(total);  // 새 Order 반환 (불변 스타일)
}

// 함수 합성으로 비즈니스 규칙 표현
Predicate<Reservation> isConfirmed = r -> r.getStatus() == CONFIRMED;
Predicate<Reservation> isToday = r -> r.getDate().equals(LocalDate.now());
Predicate<Reservation> isTodayConfirmed = isConfirmed.and(isToday);

List<Reservation> todaySchedule = reservations.stream()
    .filter(isTodayConfirmed)
    .toList();
```
