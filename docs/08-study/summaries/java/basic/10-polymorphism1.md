# 다형성 1 (Polymorphism)

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

다형성 = "하나의 참조 타입으로 여러 형태의 객체를 다룰 수 있다"  
자바에서 다형성의 핵심: **업캐스팅 + 메서드 오버라이딩**

---

## 업캐스팅과 다운캐스팅

```java
class Animal { void sound() { System.out.println("..."); } }
class Dog extends Animal { void sound() { System.out.println("멍멍"); } }
class Cat extends Animal { void sound() { System.out.println("야옹"); } }

// 업캐스팅: 자식 → 부모 타입 (자동)
Animal a = new Dog();   // Dog 객체를 Animal 타입으로
a.sound();              // "멍멍" (실제 타입인 Dog의 메서드 실행 — 동적 디스패치)

// 부모 타입으로는 자식 전용 메서드 호출 불가
// a.fetch();  // 컴파일 에러! Animal에 없는 메서드

// 다운캐스팅: 부모 → 자식 타입 (명시적 캐스팅)
Dog dog = (Dog) a;  // 실제로 Dog 객체이므로 안전
dog.fetch();        // 이제 Dog 전용 메서드 사용 가능

// 잘못된 다운캐스팅 → ClassCastException (런타임 에러)
Animal b = new Cat();
Dog wrong = (Dog) b;  // Cat인데 Dog로 캐스팅 → 런타임 에러!
```

---

## instanceof로 안전하게

```java
Animal a = new Dog();

// 전통적 방법
if (a instanceof Dog) {
    Dog dog = (Dog) a;  // 안전
    dog.fetch();
}

// Java 16+ Pattern Matching (더 간결)
if (a instanceof Dog dog) {
    dog.fetch();  // 자동으로 Dog 타입으로 바인딩
}
```

---

## 다형성의 진가 — 배열과 반복문

```java
// 다형성 없이
Dog dog = new Dog();
Cat cat = new Cat();
dog.sound();
cat.sound();

// 다형성 활용 — 여러 종류를 하나의 배열로
Animal[] animals = { new Dog(), new Cat(), new Dog() };
for (Animal a : animals) {
    a.sound();  // 각 객체의 sound() 호출 — 타입별로 다르게 동작
}
// 새 동물 클래스 추가 시 이 코드는 변경 필요 없음!
```

---

## 컴파일 타임 타입 vs 런타임 타입

```java
Animal a = new Dog();
//  ↑                ↑
// 컴파일 타임 타입   런타임 타입 (실제 객체)

// 컴파일 타임 타입 기준: 어떤 메서드를 '호출할 수 있는지' 결정
// 런타임 타입 기준:      어떤 메서드가 '실제로 실행되는지' 결정
```

---

## 면접 Q&A

**Q: 다형성이란 무엇이고 어떻게 구현되나?**  
A: 다형성은 하나의 참조 변수로 다양한 타입의 객체를 가리킬 수 있는 것이다. 자바에서는 업캐스팅(자식 객체를 부모 타입으로)과 메서드 오버라이딩(자식이 부모의 메서드를 재정의)으로 구현된다. 참조 변수 타입이 Animal이어도, 실제 객체가 Dog라면 Dog의 `sound()`가 실행된다. 이를 동적 디스패치라 한다.

**Q: ClassCastException을 방지하는 방법은?**  
A: 다운캐스팅 전에 `instanceof`로 실제 타입을 확인한 후 캐스팅한다. Java 16+에서는 패턴 매칭 `instanceof`로 확인과 캐스팅을 한 번에 할 수 있다.
