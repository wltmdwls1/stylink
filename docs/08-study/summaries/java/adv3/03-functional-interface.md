# 함수형 인터페이스

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
직접 만든 함수형 인터페이스 → 타입마다 하나씩 필요 → 폭발
  → 제네릭 도입: Function<T, R>으로 통합
  → 자바가 제공하는 표준 함수형 인터페이스 (java.util.function)
     Function, Consumer, Supplier, Predicate, BiFunction 등

타겟 타입: 람다를 어느 인터페이스에 대입하느냐에 따라 타입 결정
특화 함수형 인터페이스: 기본형(int, long, double) 처리 시 박싱/언박싱 최적화
```

---

## 자체 제작 함수형 인터페이스의 문제

```java
// String → String 용
interface StringFunction { String apply(String s); }

// Integer → Integer 용
interface NumberFunction { Integer apply(Integer n); }

// String → Integer 용도가 필요하면? 또 하나 만들어야 함...
```

타입마다 인터페이스를 만들면 조합이 폭발적으로 늘어난다.

**해결**: 제네릭으로 통합
```java
@FunctionalInterface
interface Function<T, R> {
    R apply(T t);  // T → R 변환
}

Function<String, String> toUpper = s -> s.toUpperCase();
Function<String, Integer> toLength = s -> s.length();
Function<Integer, String> toStr = n -> String.valueOf(n);
```

---

## 자바 표준 함수형 인터페이스 (java.util.function)

자바가 제공하는 것들을 직접 쓰면 된다.

| 인터페이스 | 시그니처 | 용도 | 예시 |
|---|---|---|---|
| `Function<T, R>` | `R apply(T t)` | 변환 | `s -> s.length()` |
| `Consumer<T>` | `void accept(T t)` | 소비 (반환값 없음) | `s -> System.out.println(s)` |
| `Supplier<T>` | `T get()` | 공급 (인자 없음) | `() -> new Order()` |
| `Predicate<T>` | `boolean test(T t)` | 조건 판단 | `n -> n > 0` |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | 두 인자 변환 | `(a, b) -> a + b` |
| `UnaryOperator<T>` | `T apply(T t)` | 같은 타입 변환 | `s -> s.toUpperCase()` |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` | 같은 타입 둘 → 하나 | `(a, b) -> a + b` |

```java
// 실제 사용 예시
Function<String, Integer> lengthOf = String::length;
Consumer<String> printer = System.out::println;
Supplier<List<String>> listFactory = ArrayList::new;
Predicate<String> isLong = s -> s.length() > 5;
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
```

---

## 타겟 타입 (Target Type)

람다 자체는 타입이 없다. 대입하는 컨텍스트(타겟 타입)에 따라 타입이 결정된다.

```java
// 같은 람다지만 다른 타입에 대입 가능
Runnable r = () -> System.out.println("hello");     // 타겟: Runnable
Callable<Void> c = () -> { System.out.println("hello"); return null; };  // 타겟: Callable
```

타겟 타입이 추론 가능하면 됨. 타입이 명확하지 않으면 컴파일 에러:
```java
var x = () -> System.out.println("hello");  // 컴파일 에러: 타겟 타입 불명확
```

---

## 기본형 특화 함수형 인터페이스

`Function<Integer, Integer>`를 쓰면 자동으로 `int ↔ Integer` 박싱/언박싱이 발생한다. 대용량 데이터에서 성능 문제가 생긴다.

이를 위해 기본형 특화 버전이 있다:

```java
// 박싱 발생 (느림)
Function<Integer, Integer> square = n -> n * n;

// 기본형 특화 (빠름, 박싱 없음)
IntUnaryOperator square = n -> n * n;        // int → int
ToIntFunction<String> length = String::length; // T → int
IntFunction<String> toString = n -> "num" + n; // int → T
```

스트림에서 자주 나오는 `mapToInt()`, `mapToLong()`, `mapToDouble()`이 이 특화 타입을 활용한다.

---

## 함수 합성 (Function Composition)

Function 인터페이스는 함수를 이어붙이는 메서드를 제공한다.

```java
Function<Integer, Integer> times2 = x -> x * 2;
Function<Integer, Integer> plus3  = x -> x + 3;

// andThen: times2 실행 후 plus3
Function<Integer, Integer> times2ThenPlus3 = times2.andThen(plus3);
times2ThenPlus3.apply(5);  // (5*2)+3 = 13

// compose: plus3 먼저 실행 후 times2 (순서 반대)
Function<Integer, Integer> plus3ThenTimes2 = times2.compose(plus3);
plus3ThenTimes2.apply(5);  // (5+3)*2 = 16
```

```java
// Predicate 조합
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> isEven = n -> n % 2 == 0;

isPositive.and(isEven)   // 양수이면서 짝수
isPositive.or(isEven)    // 양수이거나 짝수
isPositive.negate()      // 양수가 아님
```

---

## 면접 Q&A

**Q: Java의 대표 함수형 인터페이스 4개는?**  
A: `Function<T,R>` (T→R 변환), `Consumer<T>` (T를 소비, void), `Supplier<T>` (인자 없이 T를 공급), `Predicate<T>` (T를 받아 boolean 반환). 이 4개가 거의 모든 함수형 패턴의 기반이다.

**Q: Predicate vs Function<T, Boolean>의 차이는?**  
A: 기능은 비슷하지만 `Predicate<T>`는 `test()` 메서드를 가지며 `and()`, `or()`, `negate()` 같은 조합 메서드를 제공한다. 조건 판단에는 Predicate를 쓰는 것이 의미도 명확하고 조합도 편하다.

**Q: 기본형 특화 함수형 인터페이스가 왜 필요한가?**  
A: `Function<Integer, Integer>`는 int를 Integer로 박싱하고 다시 언박싱하는 오버헤드가 있다. 대용량 데이터 처리 시 이 비용이 누적된다. `IntUnaryOperator`, `ToIntFunction` 등 기본형 특화 인터페이스는 박싱 없이 기본형을 직접 처리해 성능이 좋다.
