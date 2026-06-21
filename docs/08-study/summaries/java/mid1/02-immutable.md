# 불변 객체

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
참조형 변수를 공유하면 사이드 이펙트 발생
  → a = b 하면 같은 객체를 참조 → b 변경 시 a도 변경됨

해결 방향1 — 의도적으로 공유하지 않음 → 번거로움 (매번 새 객체 생성)
해결 방향2 — 불변 객체: 공유해도 안전, 변경 자체를 막음

불변 객체 설계 방법:
  → 필드를 final로 선언
  → setter 제공 안 함
  → 값 변경이 필요하면 새 객체를 반환 (withXxx 패턴)

→ Java의 String, Integer 등이 불변인 이유
→ 멀티스레드 환경에서 동기화 없이 안전
```

---

## 공유 참조의 사이드 이펙트

```java
Address a = new Address("서울");
Address b = a;  // 같은 객체를 참조!

b.setValue("부산");  // b를 바꿨는데
System.out.println(a.getValue());  // "부산" 출력 — a도 바뀜 (사이드 이펙트!)
```

`b = a`는 참조값(주소)을 복사한다. 두 변수가 같은 객체를 가리키기 때문에 하나를 바꾸면 다른 것도 바뀐다.

---

## 불변 객체 설계

```java
public final class ImmutableAddress {
    private final String value;  // final — 한 번 설정 후 변경 불가

    public ImmutableAddress(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    // setter 없음 → 외부에서 값을 바꿀 수 없음
}
```

```java
ImmutableAddress a = new ImmutableAddress("서울");
ImmutableAddress b = a;  // 같은 객체 참조해도

// b.setValue("부산");  ← 컴파일 에러! 변경 불가
// b는 변경할 수 없으니 사이드 이펙트 없음
```

---

## 값 변경이 필요할 때 — 새 객체 반환

불변 객체에서 "값을 바꾸는" 메서드는 자신을 수정하는 게 아니라 **새 객체를 만들어 반환**한다.

```java
public final class ImmutableAddress {
    private final String value;

    public ImmutableAddress(String value) {
        this.value = value;
    }

    // 수정이 아닌 새 객체 반환
    public ImmutableAddress withValue(String newValue) {
        return new ImmutableAddress(newValue);
    }
}

// 사용
ImmutableAddress seoul = new ImmutableAddress("서울");
ImmutableAddress busan = seoul.withValue("부산");  // 새 객체

System.out.println(seoul.getValue());  // 서울 (원본 유지)
System.out.println(busan.getValue());  // 부산 (새 객체)
```

이 패턴이 `String.toUpperCase()`, `LocalDate.plusDays()` 등의 동작 방식이다.

---

## String이 불변인 이유

```java
String a = "hello";
String b = a;

a = a.toUpperCase();  // a = "HELLO" (새 String 객체 생성)

System.out.println(a);  // HELLO
System.out.println(b);  // hello (원본 유지, b는 영향받지 않음)
```

String이 불변이기 때문에 공유해도 안전하다. 여러 스레드가 같은 String을 참조해도 동기화 없이 안전하다.

---

## 불변 객체와 멀티스레드

불변 객체는 상태가 바뀌지 않으므로 여러 스레드가 동시에 읽어도 안전하다 — synchronized 없이도.

```java
// 불변 객체 — 멀티스레드 안전
private final ImmutableAddress serverAddress = new ImmutableAddress("https://api.example.com");

// 어떤 스레드에서 읽어도 항상 같은 값, 변경 불가 → 안전
```

---

## 불변 객체 적용 기준

**불변으로 만들면 좋은 것:**
- 값 객체 (VO): 금액, 주소, 날짜, 좌표
- 설정값, 상수
- 캐시에 저장되는 데이터

**불변이 맞지 않는 것:**
- 상태가 지속적으로 변하는 도메인 객체 (주문 상태, 재고 상수)
- 빌더 패턴의 중간 단계

---

## 면접 Q&A

**Q: 불변 객체를 만드는 방법은?**  
A: 필드를 `private final`로 선언하고, setter를 제공하지 않는다. 값 변경이 필요하면 새 객체를 생성해 반환하는 메서드(withXxx 등)를 제공한다. 클래스를 `final`로 선언해 상속을 막는 것도 고려한다.

**Q: String이 불변인 이유는?**  
A: 보안(외부에서 값 변경 불가), 멀티스레드 안전(동기화 없이 공유 가능), String 풀 최적화(같은 값을 여러 참조가 공유해도 안전) 등의 이유가 있다.

**Q: 불변 객체와 final 필드의 차이는?**  
A: `final` 필드는 참조값 자체가 바뀌지 않음을 보장하지만, 참조된 객체 내부가 변경될 수 있다. 불변 객체는 내부 상태 자체가 변경되지 않도록 설계된 것이다. `final List<String> list`는 list 참조는 바뀌지 않지만 `list.add()`는 가능하다.

## stylink 실전 적용

```java
// 금액 값 객체 — 불변
@Value
public class Money {
    private final int amount;
    private final String currency;

    public Money add(Money other) {
        return new Money(this.amount + other.amount, this.currency);
    }
}
```
