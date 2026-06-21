# 래퍼 클래스, Class 클래스

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
기본형(int, boolean...)의 한계
  → 객체 메서드 사용 불가
  → 컬렉션/제네릭에 사용 불가 (List<int> 안 됨)
  → null 표현 불가

래퍼 클래스: 기본형을 객체로 감쌈
  → Integer, Long, Boolean, Double 등
  → 박싱(int→Integer) / 언박싱(Integer→int)
  → 오토박싱으로 자동 변환

주요 유틸리티 메서드
  → 문자열 파싱, 비교, 최댓값/최솟값

Class 클래스 — 런타임 클래스 정보 조회
  → .class, getClass(), Class.forName()
  → 리플렉션의 진입점
```

---

## 기본형의 한계와 래퍼 클래스

```java
int value = 10;
// value.compareTo(5);  ← 컴파일 에러! int는 메서드 없음

// 래퍼 클래스 Integer
Integer boxed = new Integer(10);  // 구식 (deprecated)
Integer boxed = Integer.valueOf(10);  // 권장
boxed.compareTo(5);  // 가능

// List<int> ← 불가
List<Integer> list = new ArrayList<>();  // 래퍼 클래스 사용
```

---

## 오토박싱 / 언박싱

Java 5부터 컴파일러가 자동으로 변환해준다.

```java
// 오토박싱: int → Integer (자동)
Integer a = 10;  // 컴파일러가 Integer.valueOf(10)으로 변환

// 언박싱: Integer → int (자동)
int b = a;  // 컴파일러가 a.intValue()로 변환

// 연산에서도 자동
Integer x = 5;
Integer y = 3;
int sum = x + y;  // 언박싱 후 덧셈 후 결과는 int
```

**성능 주의**: 루프 안에서 대량 오토박싱은 성능 저하 요인.

```java
// 느림: 루프마다 Integer 박싱
Long total = 0L;
for (int i = 0; i < 1_000_000; i++) {
    total += i;  // 매번 Long 박싱/언박싱
}

// 빠름: 기본형 유지
long total = 0L;
for (int i = 0; i < 1_000_000; i++) {
    total += i;
}
```

---

## 래퍼 클래스 주요 메서드

```java
// 문자열 → 기본형 파싱
Integer.parseInt("123")    // 123 (int)
Long.parseLong("100")      // 100L
Double.parseDouble("3.14") // 3.14
Boolean.parseBoolean("true") // true

// 숫자 → 문자열
String.valueOf(42)  // "42"
Integer.toString(42) // "42"

// 비교
Integer.compare(5, 10)  // -1 (5 < 10)
Integer.max(5, 10)      // 10
Integer.min(5, 10)      // 5

// 상수
Integer.MAX_VALUE  // 2147483647
Integer.MIN_VALUE  // -2147483648
```

---

## null 표현

기본형은 null을 가질 수 없지만 래퍼 클래스는 가능하다.

```java
Integer score = null;  // "점수가 없음"을 표현

if (score == null) {
    System.out.println("점수 미입력");
} else {
    System.out.println("점수: " + score);
}
```

---

## Class 클래스 — 런타임 클래스 정보

```java
// Class 객체 얻는 3가지 방법
Class<String> c1 = String.class;          // 클래스 리터럴
Class<?> c2 = "hello".getClass();         // 인스턴스로부터
Class<?> c3 = Class.forName("java.lang.String");  // 이름 문자열로

// 클래스 정보 조회
c1.getName();         // "java.lang.String"
c1.getSimpleName();   // "String"
c1.getFields();       // public 필드 목록
c1.getMethods();      // public 메서드 목록
c1.getDeclaredFields(); // 모든 필드 (private 포함)
```

---

## 리플렉션 (실전 활용 포인트)

`Class` 클래스는 리플렉션의 진입점. 런타임에 동적으로 클래스 정보를 조회하고 메서드를 호출할 수 있다.

```java
// 리플렉션으로 private 필드 접근 (테스트에서 가끔 쓰임)
Class<?> clazz = Order.class;
Field field = clazz.getDeclaredField("status");
field.setAccessible(true);
Object value = field.get(orderInstance);
```

Spring의 DI, JPA 등이 내부적으로 리플렉션을 사용한다.

---

## 면접 Q&A

**Q: 래퍼 클래스가 필요한 이유는?**  
A: 기본형은 객체가 아니라 메서드를 가질 수 없고, null 표현이 불가하며, 컬렉션/제네릭에 사용할 수 없다. 래퍼 클래스는 기본형을 객체로 감싸 이 한계를 해결한다. 예를 들어 `List<int>`는 불가능하지만 `List<Integer>`는 가능하다.

**Q: 오토박싱 성능 주의점은?**  
A: 루프 안에서 대량 오토박싱이 일어나면 많은 래퍼 객체가 생성되고 GC 압력이 증가한다. 단순 연산이 많은 경우 기본형을 그대로 사용하는 것이 훨씬 빠르다.

**Q: `Integer == Integer` 비교 함정은?**  
A: -128~127 범위의 Integer는 내부 캐시(IntegerCache)를 사용해 같은 객체를 반환한다. 이 범위에서는 `==`가 true지만 범위를 벗어나면 false다. 래퍼 클래스는 값 비교에 항상 `equals()`나 `intValue()` 비교를 써야 한다.
