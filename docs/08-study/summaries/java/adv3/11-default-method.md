# 디폴트 메서드

> 자바 고급3편 | LIGHT (배경 이해 목적)

---

## 전체 흐름 한눈에 보기

```
문제: Java 8 이전에는 인터페이스에 메서드를 추가하면 구현 클래스 전부 컴파일 에러
  → List, Collection에 stream()을 추가하려면 전 세계 모든 구현 클래스를 수정해야 함?

해결: default 메서드 (Java 8)
  → 인터페이스에 구현 본문을 가진 메서드 추가 가능
  → 기존 구현 클래스는 오버라이드 안 해도 자동으로 상속

→ Stream API, Comparator.comparing(), Predicate.and() 등이 모두 이 덕분에 추가됨
```

---

## 디폴트 메서드 탄생 배경

```java
// Java 8에서 Collection에 forEach를 추가하고 싶다
public interface Collection<E> {
    // 기존 추상 메서드들...

    // 새로 추가하고 싶은 메서드
    void forEach(Consumer<? super E> action);  // 이걸 추가하면?
}
```

전 세계 `Collection`을 구현하는 모든 클래스(`ArrayList`, `LinkedList`, 사용자 정의 등)가 `forEach`를 구현하지 않았으므로 **전부 컴파일 에러**.

**해결 — default 메서드:**
```java
public interface Collection<E> {
    default void forEach(Consumer<? super E> action) {
        for (E e : this) {
            action.accept(e);
        }
    }
}
```

기존 구현 클래스들은 오버라이드 안 해도 되고, 필요하면 오버라이드할 수 있다.

---

## 실제 예시 — Notifier 인터페이스에 기능 추가

```java
// 기존 인터페이스
public interface Notifier {
    void notify(String message);  // 기존 추상 메서드
}

// 이미 3개의 구현 클래스가 있는 상태에서 "반복 발송" 기능 추가
public interface Notifier {
    void notify(String message);

    // default 메서드 — 구현 클래스들은 수정 안 해도 됨
    default void notifyRepeat(String message, int count) {
        for (int i = 0; i < count; i++) {
            notify(message);  // 추상 메서드 호출 (각 구현체의 것이 실행됨)
        }
    }
}
```

`EmailNotifier`, `SMSNotifier`, `AppPushNotifier`는 모두 수정 없이 `notifyRepeat()`을 사용할 수 있다.

---

## default 메서드의 올바른 사용

**용도 1 — 기존 인터페이스에 새 기능 추가 (하위 호환성)**: 라이브러리 개발자가 기존 구현 클래스를 깨지 않고 새 기능을 추가할 때.

**용도 2 — 공통 구현 제공**: 모든 구현 클래스에서 동일하게 쓸 구현을 인터페이스에서 제공.

**피해야 하는 상황:**
- 비즈니스 로직을 default 메서드에 담는 것 → 클래스에 담아야 할 책임을 인터페이스가 가짐
- 상태(인스턴스 변수)가 필요한 로직 → default 메서드는 상태를 가질 수 없음

---

## 다중 인터페이스 충돌

여러 인터페이스의 default 메서드 이름이 충돌할 때:

```java
interface A {
    default void hello() { System.out.println("A"); }
}
interface B {
    default void hello() { System.out.println("B"); }
}

// 컴파일 에러 — 어느 쪽을 써야 할지 모름
class C implements A, B {
    @Override
    public void hello() {  // 반드시 오버라이드해야 함
        A.super.hello();   // 명시적으로 A의 것을 선택
    }
}
```

---

## 면접 Q&A

**Q: 디폴트 메서드가 왜 도입됐나?**  
A: Java 8에서 Stream API를 도입하면서 `List.stream()`, `Iterable.forEach()` 등을 기존 인터페이스에 추가해야 했다. 추상 메서드로 추가하면 전 세계 구현 클래스가 모두 컴파일 에러가 나므로, 기존 코드를 깨지 않고 인터페이스에 기능을 추가하기 위해 default 메서드가 도입됐다.

**Q: 추상 클래스와 디폴트 메서드의 차이는?**  
A: 추상 클래스는 상태(필드), 생성자, 접근 제어자 제한 없는 메서드를 가질 수 있다. 인터페이스의 default 메서드는 상태가 없고 `public`이며 다중 구현이 가능하다는 차이가 있다. 목적도 다르다 — 추상 클래스는 공통 구현 상속, default 메서드는 기존 인터페이스의 하위 호환 확장이 주 용도.
