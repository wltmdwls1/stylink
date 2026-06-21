# String 클래스

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
String은 불변 → 문자열 연산마다 새 객체 생성
  → 반복문에서 + 연산 → 객체 폭발 → 성능 저하

String 리터럴과 String 풀
  → "hello" == "hello" → 같은 객체 (풀에서 재사용)
  → new String("hello") → 항상 새 객체

StringBuilder — 가변 문자열 빌더
  → append(), delete() 등 내부 배열 직접 수정 → 새 객체 생성 없음
  → 대량 문자열 조작에 적합

String.format() vs "".formatted()
  → 가독성 좋은 문자열 포맷팅

→ 실무: + 연산은 소량 OK, 루프 안에서는 StringBuilder 사용
```

---

## String은 불변 클래스

```java
// String 내부 구조 (개념적)
public final class String {
    private final byte[] value;  // Java 9+
    // setter 없음 — 불변
}
```

문자열 연산은 항상 새 String을 만든다:
```java
String s = "hello";
s = s + " world";  // "hello world"라는 새 String 생성, s가 그것을 참조
                   // "hello"는 GC 대상
```

---

## 문자열 풀 (String Pool)

```java
String a = "hello";   // 풀에서 생성 or 조회
String b = "hello";   // 풀에서 같은 객체 반환

a == b       // true (같은 풀 객체)
a.equals(b)  // true

String c = new String("hello");  // 항상 힙에 새 객체 생성
a == c       // false
a.equals(c)  // true
```

리터럴 방식은 풀에서 재사용 → 메모리 효율적.  
**실무 주의**: 문자열 값 비교는 항상 `equals()`로 — `==`는 참조 비교.

---

## 반복문에서 + 연산의 문제

```java
// 문제: 반복마다 새 String 생성 (O(n²) 성능)
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i;  // 매번 새 String 객체 생성, 이전 것은 GC
}
```

10000번 반복하면 String 객체가 10000개 생성됐다 버려진다 → 심각한 성능 문제.

---

## StringBuilder — 가변 문자열

```java
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);  // 내부 배열에 직접 추가 — 새 객체 없음
}
String result = sb.toString();  // 완성 후 String으로 변환 (1회만)
```

`StringBuilder`는 내부적으로 가변 `byte[]`를 유지하며 직접 수정한다.

```java
StringBuilder sb = new StringBuilder("hello");
sb.append(" world");   // "hello world"
sb.insert(5, ",");     // "hello, world"
sb.delete(5, 6);       // "hello world"
sb.reverse();          // "dlrow olleh"
sb.replace(0, 5, "bye"); // "bye world"

String result = sb.toString();
```

**메서드 체이닝:**
```java
String result = new StringBuilder()
    .append("Hello")
    .append(", ")
    .append("World")
    .toString();
```

---

## 컴파일러 최적화

단순한 `+` 연산은 컴파일러가 자동으로 StringBuilder로 최적화한다:

```java
// 개발자 코드
String s = "Hello" + " " + "World";

// 컴파일러가 변환
String s = new StringBuilder().append("Hello").append(" ").append("World").toString();
```

하지만 **반복문 안에서는 자동 최적화가 안 된다** — 매번 새 StringBuilder 생성.

```java
// 루프 안 — 최적화 안 됨
for (String item : list) {
    result += item;  // 매번 new StringBuilder() → toString()
}

// 올바른 방법
StringBuilder sb = new StringBuilder();
for (String item : list) {
    sb.append(item);
}
String result = sb.toString();
```

---

## String 주요 메서드

```java
String s = "Hello World";
s.length()          // 11
s.charAt(0)         // 'H'
s.indexOf("World")  // 6
s.contains("World") // true
s.startsWith("Hello") // true
s.endsWith("World")   // true
s.toUpperCase()     // "HELLO WORLD"
s.toLowerCase()     // "hello world"
s.trim()            // 앞뒤 공백 제거
s.strip()           // 앞뒤 공백 제거 (유니코드 공백 포함, Java 11+)
s.replace("World", "Java") // "Hello Java"
s.split(" ")        // ["Hello", "World"]
s.substring(6)      // "World"
s.substring(0, 5)   // "Hello"
String.valueOf(42)  // "42"
Integer.parseInt("42") // 42
```

---

## 면접 Q&A

**Q: String이 불변인데 왜 문자열 연산이 가능한가?**  
A: 문자열 연산은 기존 String을 수정하는 게 아니라 새로운 String 객체를 만들어 반환한다. `s = s + "world"`는 "sworld" 내용의 새 String을 만들고 s가 그것을 가리키게 한다. 기존 "s"는 GC 대상이 된다.

**Q: 반복문에서 String +보다 StringBuilder를 써야 하는 이유는?**  
A: String +는 매번 새 String 객체를 생성하고 기존 것을 버린다. 10000번 반복이면 10000개 객체 생성 후 폐기 — O(n²) 메모리·시간 복잡도. StringBuilder는 내부 배열에 직접 추가하므로 O(n)으로 효율적이다.

**Q: `"hello" == "hello"`는 true인가?**  
A: 리터럴로 선언한 경우 자바가 String 풀을 사용해 같은 객체를 재사용하므로 true. 하지만 `new String("hello")`은 항상 새 객체를 생성하므로 다른 참조와 `==` 비교하면 false. 문자열 값 비교는 항상 `equals()`를 쓰는 것이 안전하다.
