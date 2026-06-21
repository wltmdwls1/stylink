# 객체지향 프로그래밍 (OOP)

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

객체지향의 핵심은 **데이터(필드)와 기능(메서드)을 하나의 객체로 묶는 것**.  
절차지향은 데이터와 기능이 분리되어 데이터가 여기저기 전달됨.

---

## 절차지향 vs 객체지향

```java
// 절차지향: 데이터를 기능에 전달
static int area(int width, int height) {
    return width * height;
}
int w = 10, h = 5;
int a = area(w, h);  // 데이터를 함수에 넘김

// 객체지향: 데이터와 기능을 묶음
class Rectangle {
    int width, height;
    int area() { return width * height; }  // 자신의 데이터를 직접 사용
}
Rectangle r = new Rectangle();
r.width = 10; r.height = 5;
r.area();  // 객체가 스스로 계산
```

---

## 캡슐화 (Encapsulation)

데이터를 외부에서 직접 변경하지 못하도록 숨기고, 메서드로만 접근:

```java
class BankAccount {
    private int balance;  // 외부에서 직접 접근 불가

    public void deposit(int amount) {
        if (amount > 0) balance += amount;  // 검증 로직 포함
    }

    public int getBalance() { return balance; }
}
```

외부에서 `account.balance = -1000`으로 잘못된 값을 설정하는 것을 방지.

---

## this 키워드

```java
class User {
    String name;

    // 필드와 매개변수 이름이 같을 때 구분
    void setName(String name) {
        this.name = name;  // this.name = 필드, name = 매개변수
    }

    // 생성자 체이닝
    User() {
        this("기본이름");  // 다른 생성자 호출
    }
    User(String name) {
        this.name = name;
    }
}
```

---

## 면접 Q&A

**Q: 객체지향의 주요 특성 4가지는?**  
A: 캡슐화(Encapsulation) — 데이터와 기능을 묶고 내부 구현을 숨김. 상속(Inheritance) — 부모의 코드를 재사용. 다형성(Polymorphism) — 같은 인터페이스로 다른 동작. 추상화(Abstraction) — 공통 특성을 뽑아 인터페이스/추상클래스로 정의.
