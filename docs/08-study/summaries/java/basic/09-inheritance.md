# 상속 (Inheritance)

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

상속은 부모 클래스의 필드와 메서드를 자식 클래스가 재사용하는 메커니즘.  
코드 중복을 줄이고 계층 구조를 표현.

```java
class Animal {
    String name;
    void sound() { System.out.println("..."); }
}

class Dog extends Animal {  // Animal 상속
    void sound() {          // 오버라이드
        System.out.println("멍멍");
    }
    void fetch() { System.out.println("가져와!"); }  // Dog만의 메서드
}

Dog dog = new Dog();
dog.name = "바둑이";   // 부모 필드 사용 가능
dog.sound();           // "멍멍" (오버라이드된 메서드)
dog.fetch();           // Dog 전용
```

---

## super 키워드

```java
class Animal {
    String name;
    Animal(String name) { this.name = name; }
    String describe() { return "동물: " + name; }
}

class Dog extends Animal {
    String breed;

    Dog(String name, String breed) {
        super(name);        // 부모 생성자 호출 (첫 줄 필수)
        this.breed = breed;
    }

    @Override
    String describe() {
        return super.describe() + ", 품종: " + breed;  // 부모 메서드 활용
    }
}
```

---

## 메서드 오버라이딩 규칙

```java
class Parent {
    public String method() { return "parent"; }
}

class Child extends Parent {
    @Override
    public String method() { return "child"; }
    // 오버라이드 규칙:
    // - 메서드 이름, 매개변수, 반환 타입 동일 (공변 반환 타입은 예외)
    // - 접근 제어자는 같거나 더 넓게만 (public → public/protected → protected)
    // - @Override 어노테이션: 컴파일러가 오버라이드 여부 검증 (강력 권장)
}
```

오버라이딩 vs 오버로딩:
- **오버라이딩**: 부모의 메서드를 자식이 **재정의** (런타임 다형성)
- **오버로딩**: 같은 클래스에서 매개변수가 다른 **같은 이름** 메서드 (컴파일 타임 결정)

---

## 상속의 장단점

```java
// 장점: 코드 재사용, 다형성 기반
// 단점: 강한 결합 (부모 변경이 자식에 영향)

// 현대적 관점: "상속보다 조합(Composition)을 선호"
// 잘못된 상속 예시:
class Stack<T> extends Vector<T> {  // 자바 표준 라이브러리의 실수
    // Vector의 add(), remove() 등이 노출되어 Stack 의미 깨짐
}

// 조합으로 대체:
class Stack<T> {
    private Deque<T> deque = new ArrayDeque<>();  // 내부에 소유
    public void push(T item) { deque.push(item); }
    public T pop() { return deque.pop(); }
}
```

---

## 면접 Q&A

**Q: 자바에서 다중 상속이 안 되는 이유와 대안은?**  
A: 다이아몬드 문제 때문이다. A와 B가 같은 메서드를 가지고 C가 둘 다 상속하면 어느 쪽 메서드를 써야 할지 모호해진다. 자바는 클래스 단일 상속 + 인터페이스 다중 구현으로 해결한다. 인터페이스는 default 메서드 충돌 시 구현 클래스에서 직접 오버라이드해야 한다.

**Q: 메서드 오버라이딩과 오버로딩의 차이는?**  
A: 오버라이딩은 부모의 메서드를 자식이 재정의하는 것으로, 런타임에 실제 객체 타입에 따라 호출 메서드가 결정된다(동적 디스패치). 오버로딩은 같은 클래스에서 매개변수 목록이 다른 동일 이름 메서드를 여러 개 정의하는 것으로, 컴파일 타임에 결정된다.
