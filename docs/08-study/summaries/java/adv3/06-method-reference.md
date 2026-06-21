# 메서드 참조

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
람다에서 특정 메서드를 단순히 호출만 하는 경우
  → 매개변수를 받아서 바로 넘기는 반복 코드가 생김
  → 메서드 참조(::)로 더 간결하게 표현

4가지 형태:
  1. 정적 메서드 참조:          클래스명::메서드명
  2. 특정 객체 인스턴스 메서드:  객체::메서드명
  3. 임의 객체 인스턴스 메서드:  클래스명::메서드명 (스트림 요소 자신이 주체)
  4. 생성자 참조:               클래스명::new
```

---

## 메서드 참조가 필요한 이유

```java
// 같은 로직을 여러 람다로 중복 작성
BinaryOperator<Integer> add1 = (x, y) -> add(x, y);
BinaryOperator<Integer> add2 = (x, y) -> add(x, y);  // 중복
```

핵심 `add` 메서드가 따로 있는데 람다가 단순히 "받아서 전달"하는 역할만 한다.  
→ 람다가 오직 특정 메서드를 호출하기 위한 껍데기일 때 메서드 참조로 대체 가능.

```java
// 메서드 참조
BinaryOperator<Integer> add1 = MethodRef::add;
BinaryOperator<Integer> add2 = MethodRef::add;
```

---

## 4가지 형태

### 1. 정적 메서드 참조 (클래스::정적메서드)

람다가 정적 메서드를 단순히 호출하는 경우.

```java
// 람다
Function<String, Integer> parse = s -> Integer.parseInt(s);

// 메서드 참조
Function<String, Integer> parse = Integer::parseInt;

// 스트림에서
List<Integer> numbers = List.of("1", "2", "3").stream()
    .map(Integer::parseInt)
    .toList();
```

### 2. 특정 객체의 인스턴스 메서드 참조 (인스턴스::메서드)

특정 **인스턴스**를 고정해서 그 메서드를 참조. 람다가 고정된 객체의 메서드를 호출하는 경우.

```java
System.out는 PrintStream의 특정 인스턴스
Consumer<String> printer = s -> System.out.println(s);

// 메서드 참조 (System.out이 고정됨)
Consumer<String> printer = System.out::println;

// 스트림에서
list.forEach(System.out::println);
```

```java
String prefix = "Hello: ";
UnaryOperator<String> addPrefix = s -> prefix.concat(s);

// 메서드 참조 (prefix 인스턴스가 고정)
UnaryOperator<String> addPrefix = prefix::concat;
```

### 3. 임의 객체의 인스턴스 메서드 참조 (클래스::인스턴스메서드)

스트림의 **각 요소 자신**이 메서드를 호출하는 주체가 되는 경우.

```java
// 람다: 스트림의 각 문자열(s)이 toUpperCase()를 호출
Function<String, String> upper = s -> s.toUpperCase();

// 메서드 참조: String의 toUpperCase를 각 요소가 호출
Function<String, String> upper = String::toUpperCase;

// 스트림에서 (list의 각 String 요소가 toUpperCase()를 호출)
list.stream().map(String::toUpperCase).toList();
```

**헷갈리는 부분**: `Integer::parseInt`와 `String::toUpperCase`가 같은 `클래스::메서드` 형태인데 다르다.
- `Integer::parseInt` → 정적 메서드 (parseInt는 static)
- `String::toUpperCase` → 임의 객체 인스턴스 메서드 (toUpperCase는 인스턴스 메서드)

구분 방법: static이면 정적, 아니면 임의 객체 인스턴스.

### 4. 생성자 참조 (클래스::new)

람다가 생성자를 호출해 객체를 만드는 경우.

```java
// 람다
Supplier<List<String>> factory = () -> new ArrayList<>();
Function<String, User> creator = name -> new User(name);

// 메서드 참조
Supplier<List<String>> factory = ArrayList::new;
Function<String, User> creator = User::new;

// 스트림에서 (객체로 매핑)
List<User> users = names.stream()
    .map(User::new)
    .toList();
```

---

## 요약 정리표

| 형태 | 예시 | 람다 동치 |
|---|---|---|
| 정적 메서드 | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| 특정 객체 | `System.out::println` | `s -> System.out.println(s)` |
| 임의 객체 | `String::toUpperCase` | `s -> s.toUpperCase()` |
| 생성자 | `User::new` | `name -> new User(name)` |

---

## 면접 Q&A

**Q: 메서드 참조를 쓰는 조건은?**  
A: 람다 본문이 단순히 특정 메서드를 호출하기만 할 때 사용한다. `s -> s.toUpperCase()`처럼 매개변수를 받아서 메서드에 그대로 넘기는 경우 `String::toUpperCase`로 대체할 수 있다. 가독성이 좋아진다.

**Q: `클래스::메서드`가 정적 메서드 참조인지 임의 객체 인스턴스 메서드 참조인지 어떻게 구분하나?**  
A: 해당 메서드가 static인지 확인하면 된다. `Integer::parseInt`는 `parseInt`가 static이므로 정적 메서드 참조. `String::toUpperCase`는 `toUpperCase`가 인스턴스 메서드이므로 스트림의 각 요소(String)가 호출 주체가 되는 임의 객체 인스턴스 메서드 참조.
