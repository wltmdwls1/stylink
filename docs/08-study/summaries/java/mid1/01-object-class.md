# Object 클래스

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
모든 자바 클래스는 Object를 상속받는다 (명시 안 해도 자동)
  → Object의 핵심 메서드: toString(), equals(), hashCode()

toString()
  → 기본: 클래스명@해시코드 (주소) → 오버라이드해서 의미있는 정보 출력

equals() — 동등성 vs 동일성
  → 기본(Object): 참조값(주소) 비교 (동일성, ==과 같음)
  → 오버라이드: 값(내용) 비교 (동등성)

hashCode()
  → equals()를 오버라이드하면 hashCode()도 반드시 오버라이드
  → HashMap, HashSet 등 해시 기반 컬렉션에서 키 조회에 사용

Object의 다형성
  → Object 타입으로 모든 객체를 참조 가능
```

---

## toString() — 오버라이드 필수

```java
public class Order {
    private Long id;
    private String status;
}

Order order = new Order(1L, "PENDING");
System.out.println(order);  // Order@3764951d  (기본 — 주소값, 의미 없음)
```

오버라이드:
```java
@Override
public String toString() {
    return "Order{id=" + id + ", status=" + status + "}";
}

System.out.println(order);  // Order{id=1, status=PENDING}
```

IDE(IntelliJ)에서 Alt+Insert → toString() 자동 생성.

---

## equals() — 동일성 vs 동등성

```java
String a = new String("hello");
String b = new String("hello");

// 동일성(identity): 같은 객체인가? (참조값 ==)
a == b      // false (다른 객체)

// 동등성(equality): 같은 값인가? (equals)
a.equals(b) // true (String이 equals 오버라이드)
```

**equals()를 오버라이드하지 않으면 == 과 같이 동작한다** (Object의 기본 구현).

```java
// 주문 ID가 같으면 같은 주문으로 취급하고 싶을 때
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Order other)) return false;
    return Objects.equals(id, other.id);
}
```

---

## hashCode() — equals와 반드시 함께

`equals()`를 오버라이드했으면 `hashCode()`도 반드시 오버라이드해야 한다.

**이유**: HashMap/HashSet은 먼저 `hashCode()`로 버킷을 찾고, 그 안에서 `equals()`로 최종 비교한다. `equals()`는 같은데 `hashCode()`가 다르면 해시 컬렉션에서 다른 버킷으로 가서 찾지 못한다.

```java
@Override
public int hashCode() {
    return Objects.hash(id);  // equals에서 사용한 필드와 동일하게
}
```

**규칙:**
- `equals()`가 true면 반드시 `hashCode()`도 같아야 한다
- `hashCode()`가 같아도 `equals()`가 false일 수 있다 (해시 충돌)

---

## Object 다형성

모든 클래스가 Object를 상속하므로, Object 타입으로 어떤 객체든 참조할 수 있다.

```java
// Object 타입으로 뭐든 받을 수 있음
static void print(Object obj) {
    System.out.println(obj);  // toString() 자동 호출
}

print(new Order());
print("hello");
print(42);
```

---

## 면접 Q&A

**Q: equals()와 hashCode()를 함께 오버라이드해야 하는 이유는?**  
A: HashMap, HashSet 같은 해시 기반 컬렉션은 먼저 `hashCode()`로 버킷 위치를 계산하고, 같은 버킷 안에서 `equals()`로 최종 비교한다. equals()만 오버라이드하면 논리적으로 같은 객체가 해시 컬렉션에서 서로 다른 버킷에 들어가 조회가 안 된다.

**Q: 동일성(identity)과 동등성(equality)의 차이는?**  
A: 동일성은 두 참조가 정확히 같은 객체(메모리 주소)를 가리키는지(`==`). 동등성은 두 객체의 내용(값)이 논리적으로 같은지(`equals()`). String "hello"가 두 개 있을 때 동일성은 false지만 동등성은 true다.
