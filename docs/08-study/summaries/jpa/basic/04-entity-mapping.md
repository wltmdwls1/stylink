# 엔티티 매핑

> JPA 기본 | 객체-테이블 매핑, 스키마 자동 생성, 기본 키 전략

---

## 전체 흐름 한눈에 보기

```
@Entity + @Table  → 클래스 ↔ 테이블 매핑
@Id + @GeneratedValue → PK 매핑 (IDENTITY / SEQUENCE / TABLE / AUTO)
@Column / @Enumerated / @Temporal / @Lob / @Transient → 필드 ↔ 컬럼 매핑
hibernate.hbm2ddl.auto → 스키마 자동 생성 (개발 전용)

주의: 데이터 중심 설계(FK를 Long으로 들고 있는 것)는 객체 그래프 탐색 불가
→ 연관관계 매핑(다음 강의)으로 해결
```

---

## 객체와 테이블 매핑

### @Entity

```java
@Entity
public class Member { ... }
```

- JPA가 관리하는 클래스 → "엔티티"
- **기본 생성자 필수** (public 또는 protected)
- final 클래스, enum, interface, inner 클래스 사용 불가
- 저장할 필드에 final 사용 불가

`@Entity(name = "Member")` — JPA 내부에서 쓰는 이름 (기본값: 클래스명, 충돌 없으면 그대로 사용)

### @Table

```java
@Table(name = "MEMBER",
       uniqueConstraints = @UniqueConstraint(name = "NAME_AGE_UNIQUE",
                                             columnNames = {"NAME", "AGE"}))
```

| 속성 | 설명 |
|------|------|
| name | 매핑할 테이블 이름 (기본값: 엔티티 이름) |
| catalog | DB catalog 매핑 |
| schema | DB schema 매핑 |
| uniqueConstraints | DDL 생성 시 유니크 제약조건 추가 |

---

## 데이터베이스 스키마 자동 생성

`hibernate.hbm2ddl.auto` 설정값:

| 옵션 | 설명 | 사용 환경 |
|------|------|----------|
| `create` | 기존 테이블 DROP 후 CREATE | 개발 초기 |
| `create-drop` | create + 종료 시 DROP | 테스트 |
| `update` | 변경분만 반영 (컬럼 추가는 가능, 삭제 불가) | 개발/테스트 |
| `validate` | 매핑 정합성만 확인, 변경 없음 | 스테이징/운영 |
| `none` | 사용 안 함 | 운영 |

**운영 서버에 create / create-drop / update 절대 금지** — 데이터 소실 위험

---

## 필드와 컬럼 매핑 어노테이션

### @Column

```java
@Column(name = "name", nullable = false, length = 10)
private String username;
```

| 속성 | 설명 | 기본값 |
|------|------|--------|
| name | 컬럼명 | 필드명 |
| insertable / updatable | 등록/수정 가능 여부 | true |
| nullable(DDL) | NOT NULL 제약 | true(null 허용) |
| unique(DDL) | 유니크 제약 (이름 지정 불가 → @Table 권장) | |
| columnDefinition(DDL) | 직접 DDL 지정 (`varchar(100) default 'EMPTY'`) | |
| length(DDL) | 길이 제약 (String만) | 255 |
| precision / scale(DDL) | BigDecimal 전체 자릿수 / 소수 자릿수 | 19 / 2 |

### @Enumerated

```java
@Enumerated(EnumType.STRING)  // 필수! ORDINAL 사용 금지
private RoleType roleType;
```

- `ORDINAL` (기본값): 순서 숫자 저장 → Enum 순서 변경 시 **데이터 깨짐**
- `STRING`: 이름 문자열 저장 → 항상 STRING 사용

### @Temporal

```java
@Temporal(TemporalType.TIMESTAMP)
private Date createdDate;
```

- `DATE` / `TIME` / `TIMESTAMP`
- **LocalDate, LocalDateTime 사용 시 생략 가능** (최신 하이버네이트 지원) → 실무에서는 LocalDateTime 권장

### @Lob

```java
@Lob
private String description; // String → CLOB, byte[] → BLOB
```

- 지정 속성 없음, 필드 타입으로 자동 결정

### @Transient

```java
@Transient
private Integer temp; // DB 저장/조회 제외, 메모리에서만 사용
```

---

## 기본 키 매핑

### 전략 종류

| 전략 | 설명 | 주 사용 DB |
|------|------|-----------|
| 직접 할당 | `@Id`만 사용, 앱에서 직접 값 세팅 | - |
| IDENTITY | DB에 위임 (AUTO_INCREMENT) | MySQL, PostgreSQL |
| SEQUENCE | DB 시퀀스 오브젝트 사용 | Oracle, H2 |
| TABLE | 키 생성 전용 테이블 사용 | 모든 DB (성능 낮음) |
| AUTO | 방언에 따라 자동 선택 | 기본값 |

### IDENTITY 전략 — 주의사항

```java
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

- `em.persist()` 시점에 **즉시 INSERT SQL 실행** → DB에서 ID 조회
- 쓰기 지연 불가 (INSERT 즉시 나감)
- MySQL 사용 시 주로 선택

### SEQUENCE 전략 — 주의사항

```java
@SequenceGenerator(name = "MEMBER_SEQ_GENERATOR",
                   sequenceName = "MEMBER_SEQ",
                   initialValue = 1, allocationSize = 1)
```

- `allocationSize` 기본값 **50** → DB 시퀀스 1씩 증가하면 반드시 1로 설정
- `allocationSize = 50`이면 시퀀스 호출 1번에 50개 미리 확보 → 성능 최적화
- `em.persist()` 시 시퀀스 조회 후 1차 캐시에 저장, INSERT는 커밋 시 실행

### 권장 식별자 전략

> **Long + 대체키(surrogate key) + 키 생성전략**

- 기본 키 조건: NOT NULL, UNIQUE, **불변**
- 자연키(주민번호, 이메일 등)는 미래 정책 변경 시 문제 → 사용 금지
- `Long`형 사용 이유: int(약 21억)는 대규모 서비스에서 부족

---

## 데이터 중심 설계의 문제점

```java
// 나쁜 예: FK를 Long으로 그대로 들고 있는 경우
public class Order {
    private Long memberId;  // FK만 있음
}

// 객체 그래프 탐색 불가
order.getMember(); // 불가능
```

객체는 **참조**로 연관 객체를 탐색해야 한다. FK를 Long으로 들고 있으면 매번 별도 조회가 필요 → 연관관계 매핑으로 해결 (05강).

---

## 면접 Q&A

**Q: @Enumerated ORDINAL 사용 시 왜 문제가 생기나?**  
A: ORDINAL은 Enum 선언 순서의 숫자(0, 1, 2...)를 DB에 저장한다. 나중에 Enum 항목이 추가/순서 변경되면 기존 데이터의 숫자가 다른 의미가 되어 데이터가 깨진다. STRING으로 이름을 저장하면 순서가 바뀌어도 안전하다.

**Q: hibernate.hbm2ddl.auto를 운영에서 사용하면 안 되는 이유는?**  
A: create나 update는 운영 중 실수로 테이블을 DROP하거나 의도치 않은 스키마 변경을 일으킬 수 있다. 운영 DDL 변경은 DBA가 검토한 마이그레이션 스크립트로 적용해야 한다. 운영에서는 validate 또는 none을 사용한다.

**Q: IDENTITY 전략에서 쓰기 지연이 동작하지 않는 이유는?**  
A: 영속성 컨텍스트는 엔티티를 관리하려면 PK(@Id) 값이 필요하다. IDENTITY는 INSERT SQL을 실행해야 DB가 PK를 생성하므로, em.persist() 시점에 즉시 INSERT SQL이 나간다. 따라서 IDENTITY 전략에서는 쓰기 지연 배치가 동작하지 않는다.

**Q: SEQUENCE 전략의 allocationSize 최적화 원리는?**  
A: allocationSize=50으로 설정하면 DB 시퀀스를 1번 호출해 50만큼 증가시키고, 애플리케이션 메모리에 50개의 ID를 미리 확보한다. 이후 50번의 INSERT는 시퀀스를 추가 조회 없이 메모리에서 ID를 채번하므로 DB 왕복 횟수를 줄인다. DB 시퀀스 increment가 1이라면 allocationSize도 1로 맞춰야 한다.

**Q: 왜 자연키보다 대체키를 사용하나?**  
A: 자연키(주민번호, 이메일 등)는 비즈니스 정책에 따라 변할 수 있다. 주민번호를 PK로 쓰다가 법이 바뀌어 외부 노출이 금지되면 모든 테이블의 PK와 FK를 변경해야 한다. 대체키(AUTO_INCREMENT, 시퀀스)는 비즈니스와 무관하게 안정적이다.
