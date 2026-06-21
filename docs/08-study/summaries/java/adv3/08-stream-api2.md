# 스트림 API2 - 기능

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] 스트림 기본 구조: 생성 → 중간 연산 → 최종 연산

더 다양한 스트림 생성 방법
  → 무한 스트림: iterate(), generate()

핵심 중간 연산 심화
  → flatMap: 중첩 컬렉션을 하나로 펼치기
  → peek: 중간 결과 디버깅용 확인

핵심 최종 연산 심화
  → reduce: 요소를 하나로 합치기
  → collect: Collectors로 원하는 형태로 모으기

기본형 특화 스트림
  → IntStream, LongStream, DoubleStream
  → 박싱 없이 기본형 처리, 합계/평균/범위 편의 메서드 제공
```

---

## 다양한 스트림 생성

```java
// 컬렉션
list.stream()
Set.stream()

// 배열
Arrays.stream(arr)
Arrays.stream(arr, 1, 4)  // 인덱스 1~3만

// 직접 나열
Stream.of("a", "b", "c")

// 무한 스트림 — 반드시 limit() 또는 findFirst() 등으로 종료
Stream.iterate(0, n -> n + 2)   // 0, 2, 4, 6, ... (무한)
    .limit(5)                   // 0, 2, 4, 6, 8

// iterate(초기값, 조건, 다음값 생성) — Java 9+
Stream.iterate(1, n -> n <= 100, n -> n + 1)  // 1~100

Stream.generate(Math::random)  // 랜덤값 무한 생성
    .limit(3)
```

---

## flatMap — 중첩 컬렉션 펼치기

리스트 속 리스트를 하나의 평평한 스트림으로 만들 때 사용.

**상황**: 각 주문에는 여러 주문 항목이 있다. 모든 주문의 모든 항목 목록을 한 번에 처리하고 싶다.

```java
List<Order> orders = getOrders();
// orders: [주문1([항목A, 항목B]), 주문2([항목C, 항목D])]

// map: Stream<List<Item>> 반환 (중첩 스트림)
orders.stream()
    .map(Order::getItems);  // Stream<List<Item>> — 원하는 구조가 아님

// flatMap: Stream<Item> 반환 (모든 항목이 하나의 스트림으로)
orders.stream()
    .flatMap(order -> order.getItems().stream())  // Stream<Item>
    .filter(item -> item.getStatus() != CANCELLED)
    .mapToInt(Item::getPrice)
    .sum();
```

```java
// 문자열 분리 예시
List<String> sentences = List.of("hello world", "java stream");

List<String> words = sentences.stream()
    .flatMap(s -> Arrays.stream(s.split(" ")))
    // ["hello", "world", "java", "stream"]
    .toList();
```

---

## reduce — 요소를 하나로 합치기

```java
// 1+2+3+...+10
int sum = IntStream.rangeClosed(1, 10)
    .reduce(0, (a, b) -> a + b);  // 초기값 0, 두 값을 합치는 함수

// 최댓값
Optional<Integer> max = list.stream()
    .reduce(Integer::max);  // 초기값 없음 → Optional (비어있을 수 있음)
```

`reduce(identity, accumulator)` 구조:
- identity: 초기값 (합산이면 0, 곱셈이면 1)
- accumulator: 현재 누적값과 다음 요소를 합치는 방법

실제로는 대부분 `sum()`, `count()`, `max()` 같은 전용 최종 연산을 쓰는 게 더 읽기 쉽다.

---

## peek — 중간 결과 디버깅

```java
List<Integer> result = numbers.stream()
    .filter(n -> n > 3)
    .peek(n -> System.out.println("filter 통과: " + n))  // 중간 확인
    .map(n -> n * 10)
    .peek(n -> System.out.println("map 결과: " + n))      // 중간 확인
    .toList();
```

`peek()`는 중간 연산이라 소비하지 않고 그대로 통과시킨다. 디버깅용으로만 사용하고 운영 코드에는 남기지 않는다.

---

## 기본형 특화 스트림 (IntStream, LongStream, DoubleStream)

`Stream<Integer>`는 박싱 오버헤드가 있다. 숫자 처리에는 기본형 특화 스트림이 효율적이다.

```java
// 생성
IntStream.of(1, 2, 3)
IntStream.range(1, 10)       // 1~9 (끝 미포함)
IntStream.rangeClosed(1, 10) // 1~10 (끝 포함)

// 유용한 편의 메서드 (Stream<Integer>에는 없는 것들)
IntStream.rangeClosed(1, 100).sum()     // 5050
IntStream.rangeClosed(1, 100).average() // OptionalDouble
IntStream.of(3, 1, 4, 1, 5).max()      // OptionalInt
IntStream.of(3, 1, 4, 1, 5).min()      // OptionalInt
```

```java
// 일반 스트림과 변환
Stream<String> names = Stream.of("Alice", "Bob", "Charlie");

// String → int 변환 시 mapToInt 사용 (IntStream 반환)
int totalLength = names
    .mapToInt(String::length)  // IntStream
    .sum();

// 기본형 → 박싱 스트림으로 돌아오기
IntStream.range(1, 10)
    .boxed()           // Stream<Integer>
    .collect(Collectors.toList());
```

---

## Optional 간단 개요 (상세는 챕터 10 참고)

스트림 최종 연산 중 `findFirst()`, `max()`, `min()`, `reduce()` 등은 `Optional<T>`를 반환한다.

```java
Optional<String> first = names.stream()
    .filter(s -> s.startsWith("A"))
    .findFirst();

// 값이 있으면 처리, 없으면 기본값
String result = first.orElse("없음");
first.ifPresent(s -> System.out.println("찾음: " + s));
```

---

## 면접 Q&A

**Q: flatMap이 map과 다른 점은?**  
A: `map`은 요소 하나를 다른 요소 하나로 변환한다. `flatMap`은 요소 하나를 여러 요소(스트림)로 변환한 후 결과를 하나의 평평한 스트림으로 합친다. 리스트 안의 리스트를 처리하거나, 문자열을 단어로 분리해 처리할 때 쓴다.

**Q: IntStream과 Stream<Integer>의 차이는?**  
A: `Stream<Integer>`는 각 요소가 Integer 객체로 박싱되어 오버헤드가 있다. `IntStream`은 기본형 int를 직접 다뤄 박싱/언박싱이 없다. 또한 `sum()`, `average()`, `range()` 같은 숫자 전용 편의 메서드를 제공한다.

**Q: 무한 스트림은 어떻게 만들고 언제 종료되나?**  
A: `Stream.iterate()` 또는 `Stream.generate()`로 만든다. 무한 스트림은 지연 연산 덕분에 실제로 모든 요소를 생성하지 않고, `limit()`, `findFirst()`, `anyMatch()` 같은 연산이 종료 조건을 만족하면 중단된다.
