# 생성자 (Constructor)

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

생성자는 객체 생성 시 초기화를 담당하는 특수 메서드.  
반환 타입이 없고, 클래스 이름과 동일.

---

## 기본 생성자 vs 매개변수 생성자

```java
class User {
    String name;
    int age;

    // 기본 생성자 — 명시하지 않으면 컴파일러가 자동 추가
    User() { }

    // 매개변수 생성자
    User(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

User u1 = new User();            // 기본 생성자
User u2 = new User("김철수", 25);  // 매개변수 생성자
```

**주의**: 매개변수 생성자를 하나라도 정의하면 기본 생성자는 자동 추가되지 않는다.

---

## 생성자 오버로딩

```java
class Order {
    Long id;
    String itemName;
    int quantity;

    Order(String itemName) {                    // 수량 기본값 1
        this(itemName, 1);
    }

    Order(String itemName, int quantity) {
        this.itemName = itemName;
        this.quantity = quantity;
    }
}
```

`this()` 로 같은 클래스의 다른 생성자를 호출. 항상 생성자 첫 줄에서만 호출 가능.

---

## 면접 Q&A

**Q: 생성자가 없으면 어떻게 되나?**  
A: 컴파일러가 매개변수 없는 기본 생성자를 자동으로 추가한다. 단, 개발자가 매개변수 있는 생성자를 하나라도 직접 정의하면 기본 생성자는 자동 추가되지 않는다. 이 상태에서 `new Foo()`를 호출하면 컴파일 에러가 발생한다.

**Q: `super()`를 생성자에서 호출하는 이유는?**  
A: 자바에서 자식 클래스 생성자가 실행될 때 반드시 부모 생성자가 먼저 실행되어야 한다. 명시적으로 `super()`를 쓰지 않으면 컴파일러가 자동으로 `super()` (부모 기본 생성자 호출)을 첫 줄에 삽입한다. 부모에 매개변수 있는 생성자만 있다면 자식에서 `super(args)`를 명시해야 한다.
