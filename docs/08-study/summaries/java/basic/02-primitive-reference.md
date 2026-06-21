# 기본형과 참조형

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

자바의 변수는 두 종류: 기본형(Primitive)과 참조형(Reference).

**기본형**: 값 자체를 저장. 스택(Stack)에 저장.
```
byte, short, int, long, float, double, char, boolean
```

**참조형**: 힙(Heap)의 객체 주소를 저장. 배열, 클래스, 인터페이스 모두 해당.
```
String, int[], Student, List<Integer> ...
```

---

## 기본형 vs 참조형 비교

```java
// 기본형 — 값 복사
int a = 10;
int b = a;  // 값 복사
b = 20;
System.out.println(a);  // 10 (영향 없음)

// 참조형 — 주소 복사 (같은 객체를 가리킴)
int[] arr1 = {1, 2, 3};
int[] arr2 = arr1;  // 주소 복사
arr2[0] = 99;
System.out.println(arr1[0]);  // 99 (같은 배열!)
```

---

## 메서드 호출과 변수 전달

```java
// 기본형 → 값 전달 (call by value)
void add(int x) { x += 10; }
int n = 5;
add(n);
System.out.println(n);  // 5 (변경 안 됨)

// 참조형 → 주소 전달 (객체 내부 변경 가능)
void change(Student s) { s.name = "변경됨"; }
Student student = new Student();
student.name = "원본";
change(student);
System.out.println(student.name);  // "변경됨"
```

---

## null과 NullPointerException

```java
String s = null;       // 참조형만 null 가능
int n = null;          // 컴파일 에러! 기본형은 null 불가

s.length();            // NullPointerException!
// null인 참조변수의 메서드/필드에 접근하면 NPE 발생
```

---

## 면접 Q&A

**Q: 자바는 call by value인가 call by reference인가?**  
A: 자바는 항상 call by value다. 기본형은 값 자체를 복사해 전달한다. 참조형은 참조값(주소)을 복사해 전달한다. 메서드 안에서 참조 변수가 가리키는 객체의 내부를 변경하면 원본에 반영되지만, 참조 변수 자체(어떤 객체를 가리키는지)를 바꿔도 호출자에게 영향을 주지 않는다.
