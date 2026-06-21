# 접근 제어자 (Access Modifier)

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

접근 제어자는 클래스, 필드, 메서드, 생성자의 접근 범위를 제한.  
캡슐화 구현의 핵심 도구.

---

## 4가지 접근 제어자

| 제어자 | 같은 클래스 | 같은 패키지 | 자식 클래스 | 전체 |
|---|:---:|:---:|:---:|:---:|
| `private` | O | X | X | X |
| `package-private` (기본) | O | O | X | X |
| `protected` | O | O | O | X |
| `public` | O | O | O | O |

```java
public class User {
    private Long id;          // 같은 클래스에서만
    String nickname;          // 같은 패키지에서 접근 (기본, package-private)
    protected String email;   // 자식 클래스까지
    public String name;       // 누구든지
}
```

---

## 실전 설계 원칙

**가장 제한적인 것부터 시작해서 필요할 때만 열어준다.**

```java
// 좋은 예: 필드는 private, 접근은 메서드로
public class Order {
    private Long id;
    private OrderStatus status;
    private int totalPrice;

    // 조회: getter
    public Long getId() { return id; }

    // 상태 변경: 비즈니스 메서드 (setter 대신)
    public void confirm() {
        if (status != OrderStatus.PENDING) throw new BusinessException(...);
        this.status = OrderStatus.CONFIRMED;
    }
}
```

---

## 클래스 접근 제어자

```java
public class PublicClass { }    // 파일명과 동일해야 함, 어디서든 접근 가능
class PackagePrivateClass { }   // 같은 패키지에서만
// private class는 최상위 클래스에 적용 불가 (중첩 클래스에는 가능)
```

---

## 면접 Q&A

**Q: private vs protected 차이는?**  
A: `private`은 선언된 클래스 내부에서만 접근 가능. `protected`는 같은 패키지 전체 + 다른 패키지의 자식 클래스에서도 접근 가능. 상속 관계에서 자식이 부모의 특정 필드/메서드를 사용해야 할 때 `protected`를 쓴다.

**Q: 왜 필드를 private으로 해야 하나?**  
A: 캡슐화를 위해서다. 외부에서 필드를 직접 변경하면 유효성 검사를 우회하거나 객체가 의도치 않은 상태가 될 수 있다. `private` 필드 + 공개 메서드 구조로 유효성 검사, 부가 로직을 메서드에 집중시킬 수 있다.
