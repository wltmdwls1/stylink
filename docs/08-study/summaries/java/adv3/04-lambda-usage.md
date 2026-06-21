# 람다 활용

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
람다를 실전에서 어떻게 쓰는지 패턴으로 익히기

패턴1 — filter: Predicate로 조건을 파라미터화
패턴2 — map: Function으로 변환을 파라미터화
패턴3 — filter + map 조합: 파이프라인 구성
패턴4 — 스트림 만들기: 컬렉션 → 스트림 → 필터/맵 → 수집

→ 이 패턴들이 자바 Stream API의 기반이 됨
```

---

## 패턴1 — filter: Predicate로 조건 전달

```java
// 중복 없는 하나의 filter 메서드
static List<Integer> filter(List<Integer> numbers, Predicate<Integer> condition) {
    List<Integer> result = new ArrayList<>();
    for (Integer n : numbers) {
        if (condition.test(n)) result.add(n);
    }
    return result;
}

// 사용: 조건만 람다로 전달
List<Integer> evens = filter(numbers, n -> n % 2 == 0);
List<Integer> bigs  = filter(numbers, n -> n > 5);
List<Integer> both  = filter(numbers, n -> n % 2 == 0 && n > 5);
```

중복 로직 없이 조건만 바꿔 재사용 가능.

---

## 패턴2 — map: Function으로 변환 전달

```java
// 변환을 파라미터로 받는 map
static <T, R> List<R> map(List<T> list, Function<T, R> mapper) {
    List<R> result = new ArrayList<>();
    for (T item : list) {
        result.add(mapper.apply(item));
    }
    return result;
}

// 사용: 어떻게 변환할지 람다로 전달
List<String> names   = map(users, user -> user.getName());
List<Integer> scores = map(users, user -> user.getScore());
List<String> upper   = map(names, String::toUpperCase);
```

---

## 패턴3 — filter + map 조합

```java
// 80점 이상 학생의 이름을 대문자로
List<String> result = map(
    filter(students, s -> s.getScore() >= 80),  // 먼저 필터
    s -> s.getName().toUpperCase()              // 그 다음 변환
);
```

이 구조가 정확히 Stream의 `.filter().map()`과 같은 아이디어다. 자바 스트림은 이걸 더 편하게 쓸 수 있게 해준다.

---

## 패턴4 — 자체 스트림 만들기 (Stream API의 원리)

여러 연산을 체이닝할 수 있는 스트림을 직접 구현해본다.

```java
// MyStream: filter/map을 지연 없이 단순히 연결
MyStream<Integer> stream = MyStream.of(numbers)
    .filter(n -> n > 3)
    .map(n -> n * 10);
List<Integer> result = stream.toList();
```

이 패턴이 자바 Stream API의 구조다:
- `of()` → 스트림 생성
- `filter()` → 조건 필터 (중간 연산)
- `map()` → 변환 (중간 연산)
- `toList()` → 수집 (최종 연산)

---

## 람다 조합 실전 예시

```java
// 주문 목록에서 총 금액 계산
List<Order> orders = getOrders();

int total = orders.stream()
    .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
    .mapToInt(o -> o.getAmount())
    .sum();

// 사용자 이름 목록을 알파벳순 정렬 후 대문자로
List<String> sortedNames = users.stream()
    .map(User::getName)
    .sorted()
    .map(String::toUpperCase)
    .toList();
```

---

## Comparator — 람다로 정렬 기준 전달

```java
// 이름 순서로 정렬
List<User> sorted = users.stream()
    .sorted((u1, u2) -> u1.getName().compareTo(u2.getName()))
    .toList();

// 더 간결하게: Comparator.comparing 활용
List<User> sorted = users.stream()
    .sorted(Comparator.comparing(User::getName))
    .toList();

// 역순 + 2차 정렬
List<User> sorted = users.stream()
    .sorted(Comparator.comparing(User::getScore).reversed()
        .thenComparing(User::getName))
    .toList();
```

---

## 면접 Q&A

**Q: 람다를 실무에서 가장 자주 쓰는 패턴은?**  
A: 컬렉션 처리에서 가장 많이 쓴다. `stream().filter().map().collect()` 파이프라인, Comparator로 정렬 기준 전달, `Optional.orElseGet()`에 대안 값 생산 람다 전달 등이 대표적이다.

**Q: Comparator.comparing()은 왜 편한가?**  
A: 정렬 기준 필드를 메서드 참조로 간단하게 지정할 수 있고, `.reversed()`, `.thenComparing()`으로 다단 정렬 조건을 선언적으로 표현할 수 있다. 직접 `(a, b) -> ...`를 쓰는 것보다 의도가 명확하게 드러난다.
