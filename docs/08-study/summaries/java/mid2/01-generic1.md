# 제네릭 - Generic1

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
제네릭 없이 타입마다 클래스를 따로 만드는 문제
  → V1: IntBox, StringBox 각각 → 타입마다 클래스 폭발
  → V2: ObjectBox → 모든 타입 가능하지만 타입 안전성 없음, 캐스팅 필요
  → V3: GenericBox<T> → 타입 안전성 + 재사용성 동시에

제네릭 기초
  → 타입 파라미터: T, E, K, V 관례
  → 제네릭 메서드
  → 타입 추론: new ArrayList<>() (다이아몬드 연산자)

제네릭 타입 제한 (extends)
  → <T extends Animal> → T는 Animal의 하위 타입만 허용
  → 와일드카드: <?>, <? extends T>, <? super T>
```

---

## 제네릭이 필요한 이유

```java
// V1 — 타입마다 클래스 필요
class IntBox { private int value; }
class StringBox { private String value; }
class UserBox { private User value; }
// 새 타입이 필요할 때마다 클래스 추가...

// V2 — Object로 통합
class ObjectBox {
    private Object value;
    public void set(Object value) { this.value = value; }
    public Object get() { return value; }
}
ObjectBox box = new ObjectBox();
box.set("hello");
String s = (String) box.get();  // 캐스팅 필요 — ClassCastException 위험
box.set(42);
String wrong = (String) box.get();  // 런타임 에러!
```

**V2의 남은 문제**: 타입 안전성 없음. 캐스팅 오류는 런타임에야 발견됨.

---

## V3 — 제네릭 클래스

```java
class Box<T> {  // T: 타입 파라미터
    private T value;
    public void set(T value) { this.value = value; }
    public T get() { return value; }
}

Box<String> strBox = new Box<>();  // T = String으로 확정
strBox.set("hello");
String s = strBox.get();  // 캐스팅 불필요, 타입 안전

strBox.set(42);  // 컴파일 에러! String만 허용
```

타입 안전성 + 재사용성 동시에 달성.

---

## 타입 파라미터 관례

```
T   — Type (일반적인 타입)
E   — Element (컬렉션 요소)
K   — Key (맵의 키)
V   — Value (맵의 값)
N   — Number
R   — Result (반환값)
```

---

## 제네릭 메서드

클래스 전체가 아닌 메서드 단위로 제네릭을 적용:

```java
// 제네릭 메서드: 반환타입 앞에 <T> 선언
public <T> T getFirst(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
}

// 사용 — 타입 추론
String first = getFirst(List.of("a", "b", "c"));
Integer firstNum = getFirst(List.of(1, 2, 3));
```

---

## 타입 제한 (Bounded Type Parameter)

```java
// T extends Number → T는 Number의 하위 타입만 허용
class NumberBox<T extends Number> {
    private T value;
    public double doubleValue() {
        return value.doubleValue();  // Number 메서드 사용 가능
    }
}

NumberBox<Integer> intBox = new NumberBox<>();  // 가능
NumberBox<String> strBox = new NumberBox<>();   // 컴파일 에러!
```

```java
// 인터페이스도 extends로 제한 (implements가 아님!)
<T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

---

## 와일드카드

```java
// <?> 비제한: 어떤 타입이든 허용 (읽기만 가능)
void printList(List<?> list) {
    for (Object o : list) System.out.println(o);
}

// <? extends T> 상한: T의 하위 타입 (읽기에 안전, 쓰기 불가)
void sumNumbers(List<? extends Number> list) {
    double sum = 0;
    for (Number n : list) sum += n.doubleValue();
}

// <? super T> 하한: T의 상위 타입 (쓰기에 안전, 읽기 불편)
void addNumbers(List<? super Integer> list) {
    list.add(1);
    list.add(2);
}
```

**PECS 원칙**: Producer Extends, Consumer Super
- 값을 꺼내 쓸 때(생산자) → `extends`
- 값을 넣을 때(소비자) → `super`

---

## 타입 소거 (Type Erasure)

제네릭 타입 정보는 컴파일 시에만 존재. 런타임에는 소거된다.

```java
List<String> strList = new ArrayList<>();
List<Integer> intList = new ArrayList<>();
strList.getClass() == intList.getClass();  // true! 런타임에는 같은 ArrayList
```

따라서 제네릭 타입으로 `instanceof` 체크나 배열 생성이 불가:
```java
if (list instanceof List<String>)  // 컴파일 에러 (소거되어 알 수 없음)
T[] arr = new T[10];               // 컴파일 에러 (소거됨)
```

---

## 면접 Q&A

**Q: 제네릭이 필요한 이유는?**  
A: Object를 사용하면 타입 안전성이 없어 잘못된 타입을 넣어도 컴파일 에러가 없고 런타임에 ClassCastException이 발생한다. 제네릭을 쓰면 타입 파라미터로 사용할 타입을 명시하여 컴파일 시 타입 안전성을 보장하면서 하나의 클래스로 다양한 타입을 처리할 수 있다.

**Q: `<? extends T>`와 `<? super T>`의 차이는?**  
A: `extends`는 T의 하위 타입만 허용. 꺼낼 때는 T 타입으로 안전하게 읽을 수 있지만 추가가 제한된다. `super`는 T의 상위 타입만 허용. T 타입 값을 안전하게 추가할 수 있지만 읽을 때는 Object로만 가능하다. PECS 원칙: 읽기(생산)엔 extends, 쓰기(소비)엔 super.

**Q: 제네릭 타입 소거란?**  
A: 자바 컴파일러는 제네릭 타입을 검증한 후 컴파일된 바이트코드에서 타입 정보를 제거한다. 이를 타입 소거라 한다. 런타임에 `List<String>`과 `List<Integer>`는 둘 다 그냥 `List`다. 하위 호환성을 위해 설계된 방식으로, 런타임에 제네릭 타입 정보를 알 수 없는 한계가 있다.
