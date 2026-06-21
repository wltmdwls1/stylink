# Optional

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
문제: null을 반환하면 NPE 위험 + 반환값에 null 가능성이 있는지 시그니처만으로 모름

Optional 등장 (Java 8)
  → "값이 있을 수도 없을 수도 있다"를 타입으로 표현
  → 호출자에게 null 처리를 강제로 인식시킴

Optional 생성 → 값 꺼내기 → 없을 때 처리
  → orElse vs orElseGet (즉시 평가 vs 지연 평가)

Best Practice
  → 반환 타입에만 사용, 필드/매개변수에는 사용 금지
  → isPresent()+get() 대신 orElse/orElseGet/ifPresent 사용
```

---

## Optional이 해결하는 문제

```java
// null 반환 방식 — 문제
String name = findNameById(3L);
name.toUpperCase();  // NPE! (name이 null일 수 있음)

// null 체크를 해야 하는데... 깜빡하기 쉽다
if (name != null) {
    name.toUpperCase();
}

// Optional 방식 — "없을 수도 있다"가 타입에 드러남
Optional<String> name = findNameById(3L);
name.toUpperCase();  // 컴파일 에러! Optional에서 바로 toUpperCase 못 씀
                     // → 개발자에게 null 처리를 강제로 인식시킴
```

---

## Optional 생성

```java
Optional<String> present = Optional.of("hello");      // 값 있음 (null이면 NPE)
Optional<String> maybe = Optional.ofNullable(value);  // null이면 empty
Optional<String> empty  = Optional.empty();            // 빈 Optional
```

---

## 값 꺼내기 / 없을 때 처리

```java
Optional<String> opt = findName();

// 기본값 지정
String name1 = opt.orElse("익명");             // 없으면 "익명" 반환
String name2 = opt.orElseGet(() -> "익명");    // 없으면 람다 실행해서 반환
String name3 = opt.orElseThrow();              // 없으면 NoSuchElementException
String name4 = opt.orElseThrow(() ->           // 없으면 커스텀 예외
    new BusinessException(ErrorCode.USER_NOT_FOUND));

// 있을 때만 실행
opt.ifPresent(name -> System.out.println("이름: " + name));

// 있으면 변환, 없으면 빈 Optional
Optional<Integer> length = opt.map(String::length);

// 중간 체이닝
opt
    .filter(s -> s.length() > 3)   // 조건 충족할 때만 통과
    .map(String::toUpperCase)       // 변환
    .ifPresent(System.out::println); // 있으면 출력
```

---

## orElse vs orElseGet — 즉시 평가 vs 지연 평가

가장 실수하기 쉬운 부분.

```java
// orElse: 값이 있든 없든 "익명"이라는 객체를 항상 생성
String name = opt.orElse("익명");  // 상수라면 괜찮음

// orElse를 메서드 호출과 함께 쓰면?
User user = opt.orElse(createDefaultUser());  // opt에 값이 있어도 createDefaultUser() 항상 실행
```

```java
// orElseGet: 없을 때만 람다 실행 (지연 평가)
User user = opt.orElseGet(() -> createDefaultUser());  // 비어있을 때만 생성

// 비용이 큰 연산 (DB 조회, 객체 생성 등)은 orElseGet을 써야 함
String result = opt.orElseGet(() -> fetchFromDatabase());
```

---

## 실전 예시 — 주소 체인 처리

null 체크가 중첩된 코드를 Optional로 리팩토링:

```java
// null 체크 지옥
String city = null;
User user = findUser(id);
if (user != null) {
    Address address = user.getAddress();
    if (address != null) {
        city = address.getCity();
    }
}

// Optional 체이닝
String city = findOptionalUser(id)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("알 수 없음");
```

---

## Optional 베스트 프랙티스

**써도 되는 곳:**
- 메서드 반환 타입 — 값이 없을 수도 있음을 명시

**쓰면 안 되는 곳:**
- **필드**: `Optional`은 직렬화를 지원하지 않음, 메모리 낭비
- **메서드 매개변수**: null 대신 Optional을 넘기는 건 의미 없음 (그냥 null 체크하면 됨)
- **컬렉션 요소**: 빈 컬렉션 또는 Optional.empty()를 쓰는 게 맞음

**안티패턴:**
```java
// 안티패턴: isPresent() + get() → orElseThrow()나 ifPresent()를 써야 함
if (opt.isPresent()) {
    return opt.get();  // 이렇게 쓰지 말 것
}

// 좋은 패턴
return opt.orElseThrow(() -> new NotFoundException());
```

---

## 면접 Q&A

**Q: Optional을 왜 쓰나?**  
A: null을 직접 반환하면 호출자가 null 가능성을 모르고 NPE를 만날 수 있다. Optional<T>를 반환하면 "없을 수도 있다"는 사실을 타입으로 강제하여 호출자가 반드시 null 가능성을 처리하도록 유도한다.

**Q: orElse와 orElseGet의 차이는?**  
A: `orElse(value)`는 Optional의 상태와 무관하게 value 표현식이 항상 즉시 평가된다. `orElseGet(() -> value)`는 Optional이 비어있을 때만 람다를 실행한다. 비용이 큰 연산(DB 조회 등)은 반드시 `orElseGet`을 써야 한다.

**Q: Optional을 필드로 쓰면 안 되는 이유는?**  
A: Optional은 직렬화(Serializable)를 구현하지 않아 JPA 엔티티, 세션 저장, 네트워크 전송 등에서 문제가 생긴다. 또한 매번 Optional 객체를 감싸는 메모리 낭비도 있다.

## stylink 실전 적용

```java
// 사용자 조회 — 없을 수 있음을 반환 타입으로 명시
public Optional<User> findUserById(Long id) {
    return userRepository.findById(id);
}

// 서비스에서 사용
User user = findUserById(userId)
    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

// 예약 조회 시 스타일리스트 정보 (null일 수 있음)
String stylistName = reservation
    .getStylist()
    .map(Stylist::getName)
    .orElse("담당 스타일리스트 미정");
```
