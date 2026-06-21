# 클래스와 데이터

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

클래스는 데이터(필드)와 기능(메서드)을 묶는 틀이다.  
객체는 클래스를 실체화한 것 — 같은 클래스에서 만들어진 객체들은 각각 독립적인 데이터를 가진다.

```java
class Student {
    String name;  // 필드 (인스턴스 변수)
    int age;
}

// 객체 생성: 힙(Heap)에 메모리 할당, 변수엔 참조값(주소) 저장
Student s1 = new Student();
Student s2 = new Student();
s1.name = "김철수";
s2.name = "이영희";
// s1과 s2는 독립적인 데이터를 가짐
```

---

## 참조와 null

```java
Student s = new Student();  // 참조 변수 s는 힙의 객체를 가리킴
s = null;                   // 참조 끊기 — 기존 객체는 GC 대상이 됨

Student a = new Student();
Student b = a;  // 같은 객체를 가리킴 (복사 아님!)
b.name = "변경";
System.out.println(a.name);  // "변경" (같은 객체)
```

---

## 배열로 객체 관리

```java
Student[] students = new Student[3];
students[0] = new Student();
students[0].name = "김철수";

// 배열 자체도 힙에 있고, 각 요소도 객체 참조
```

---

## 면접 Q&A

**Q: 자바에서 객체를 변수에 할당하면 어떻게 동작하나?**  
A: 객체는 힙(Heap)에 생성된다. 변수는 힙에 있는 객체의 참조값(메모리 주소)을 저장한다. 따라서 `a = b`를 하면 같은 객체를 가리키는 참조가 복사되는 것이지, 객체 자체가 복사되지 않는다.
