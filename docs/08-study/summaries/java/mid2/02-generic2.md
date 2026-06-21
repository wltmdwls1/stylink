# 제네릭 - Generic2

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] 제네릭 기초 — Box<T>, 타입 제한

제네릭 활용 패턴
  → 제네릭 인터페이스: Comparable<T>, Iterable<T>
  → 제네릭 상속: 타입 파라미터 전달/고정
  → 복합 타입 제한: <T extends Comparable<T> & Serializable>

실전 패턴
  → 페이징 응답 래퍼: Page<T>
  → API 응답 래퍼: ApiResponse<T>
  → 제네릭 리포지토리 인터페이스
```

---

## 제네릭 인터페이스

인터페이스에도 타입 파라미터 적용 가능:

```java
public interface Comparable<T> {  // 자바 표준 라이브러리
    int compareTo(T other);
}

// 구현
public class Product implements Comparable<Product> {
    private int price;

    @Override
    public int compareTo(Product other) {
        return Integer.compare(this.price, other.price);
    }
}
```

---

## 제네릭 상속

자식 클래스가 부모의 타입 파라미터를 처리하는 2가지 방법:

```java
// 방법1: 타입 파라미터 전달 (그대로 유지)
class Box<T> { T value; }
class SpecialBox<T> extends Box<T> { }  // T를 유지

// 방법2: 타입 파라미터 고정
class StringBox extends Box<String> { }  // T를 String으로 고정

// JPA의 CrudRepository 패턴도 이 방식
public interface UserRepository extends JpaRepository<User, Long> { }
//                                             타입 고정 ↑     ↑
```

---

## 복합 타입 제한

```java
// T는 Comparable도 구현하고 Serializable도 구현해야 함
// & 로 여러 조건 지정 (여러 개면 클래스 먼저, 인터페이스 나중에)
<T extends Comparable<T> & Serializable>

// 활용 예시: 정렬 가능하고 직렬화 가능한 요소만 다루는 컨테이너
class SortableContainer<T extends Comparable<T>> {
    private List<T> items = new ArrayList<>();

    public T min() {
        return items.stream().min(Comparator.naturalOrder()).orElseThrow();
    }
}
```

---

## 실전 패턴: API 응답 래퍼

```java
// 제네릭으로 범용 응답 래퍼
@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}

// 사용
ApiResponse<UserResponse> response = ApiResponse.of(userResponse);
ApiResponse<List<OrderResponse>> listResponse = ApiResponse.of(orders);
```

---

## 실전 패턴: 페이징 래퍼

```java
public class Page<T> {
    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int currentPage;

    // 다른 타입으로 변환
    public <R> Page<R> map(Function<T, R> mapper) {
        List<R> converted = content.stream().map(mapper).toList();
        return new Page<>(converted, totalElements, totalPages, currentPage);
    }
}

// 사용
Page<User> userPage = userRepository.findAll(pageable);
Page<UserResponse> responsePage = userPage.map(UserResponse::from);
```

---

## 실전 패턴: 제네릭 Repository 인터페이스

```java
// 공통 CRUD 인터페이스
public interface Repository<T, ID> {
    Optional<T> findById(ID id);
    T save(T entity);
    void delete(ID id);
    List<T> findAll();
}

// 각 도메인별 구현
public class UserRepository implements Repository<User, Long> {
    @Override
    public Optional<User> findById(Long id) { ... }
    // ...
}
```

---

## 면접 Q&A

**Q: 제네릭 인터페이스 구현 시 타입 전달과 타입 고정의 차이는?**  
A: 타입 전달(`class Box<T> extends Container<T>`)은 자식도 제네릭으로 남아 사용 시 타입을 지정한다. 타입 고정(`class StringBox extends Container<String>`)은 자식 클래스를 생성할 때 이미 타입이 결정된다. JPA의 `UserRepository extends JpaRepository<User, Long>`이 타입 고정의 예시다.

**Q: 와일드카드와 제네릭 타입 파라미터를 언제 구분해서 쓰나?**  
A: 타입 파라미터(`<T>`)는 여러 곳에서 같은 타입을 쓰거나, 반환 타입과 매개변수 타입의 관계를 표현할 때 사용한다. 와일드카드(`<?>`)는 타입을 제어할 필요는 없고 단순히 "어떤 타입의 컬렉션이든 받겠다"는 유연성이 필요할 때 사용한다.
