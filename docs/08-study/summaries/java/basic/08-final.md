# final

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

`final`은 "한 번 정해지면 변경 불가"를 보장하는 키워드.  
변수, 메서드, 클래스 각각에 다른 의미로 적용된다.

---

## final 변수

```java
// 기본형 final: 값 변경 불가
final int MAX = 100;
MAX = 200;  // 컴파일 에러!

// 참조형 final: 참조(주소) 변경 불가, 객체 내부는 변경 가능
final List<String> list = new ArrayList<>();
list = new ArrayList<>();  // 에러! 참조 변경 불가
list.add("A");             // OK! 내부 변경은 가능

// static final: 상수 관례 — 대문자 + 언더스코어
public static final int MAX_SIZE = 100;
public static final String DEFAULT_STATUS = "PENDING";
```

---

## final 필드와 생성자 초기화

```java
class Order {
    private final Long id;           // 반드시 생성자에서 초기화
    private final LocalDateTime createdAt = LocalDateTime.now();  // 선언 시 초기화

    Order(Long id) {
        this.id = id;  // OK
    }
    // id를 초기화 안 하면 컴파일 에러
}
```

---

## final 메서드와 클래스

```java
// final 메서드: 자식 클래스에서 오버라이드 불가
class BaseService {
    public final void audit() {
        // 감사 로직 — 자식이 바꾸면 안 됨
    }
}

// final 클래스: 상속 불가
public final class String { ... }  // 자바 String이 final
// new SpecialString extends String { }  // 컴파일 에러
```

---

## 불변 객체에서의 final

```java
// 모든 필드를 final로 선언 = 불변 객체
public final class Money {
    private final int amount;
    private final String currency;

    public Money(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    // setter 없음, 값 변경이 필요하면 새 객체 반환
    public Money add(Money other) {
        return new Money(this.amount + other.amount, this.currency);
    }
}
```

---

## 면접 Q&A

**Q: final 변수, final 메서드, final 클래스 각각의 의미는?**  
A: `final` 변수는 한 번 초기화 후 값 변경 불가. 기본형은 값 자체, 참조형은 참조(주소)가 고정된다. `final` 메서드는 자식 클래스에서 오버라이드 불가. `final` 클래스는 상속 불가. 자바의 `String`이 final 클래스인 이유는 불변성을 보장해 String 풀, 해시코드 캐싱 등의 최적화를 안전하게 하기 위해서다.
