# 데이터 접근 기술 - 스프링 JdbcTemplate

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
JdbcTemplate 발전 단계

V1 — 기본 JdbcTemplate
  순서 기반 파라미터 바인딩 (?)
  KeyHolder로 auto increment PK 조회
  RowMapper로 ResultSet → 객체 변환

V2 — NamedParameterJdbcTemplate
  이름 기반 파라미터 바인딩 (:itemName)
  파라미터 소스: Map / MapSqlParameterSource / BeanPropertySqlParameterSource
  BeanPropertyRowMapper: camelCase ↔ snake_case 자동 변환

V3 — SimpleJdbcInsert 추가
  INSERT SQL 작성 불필요
  withTableName + usingGeneratedKeyColumns
  executeAndReturnKey()로 PK 자동 조회

장점: 설정 없이 바로 사용, JDBC 반복 코드 제거
단점: 동적 SQL 어려움 → MyBatis로 해결
```

---

## 설정

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
runtimeOnly 'com.h2database:h2'
```

```properties
# application.properties (JdbcTemplate SQL 로그)
logging.level.org.springframework.jdbc=debug
```

> `spring-boot-starter-jdbc` 하나만 추가하면 `spring-jdbc`(JdbcTemplate 포함) 자동 포함. 별도 설정 없음.

---

## V1 — JdbcTemplate 기본

```java
@Repository
public class JdbcTemplateItemRepositoryV1 implements ItemRepository {
    private final JdbcTemplate template;

    public JdbcTemplateItemRepositoryV1(DataSource dataSource) {
        this.template = new JdbcTemplate(dataSource);  // 관례: 생성자에서 생성
    }

    @Override
    public Item save(Item item) {
        String sql = "insert into item (item_name, price, quantity) values (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, item.getItemName());
            ps.setInt(2, item.getPrice());
            ps.setInt(3, item.getQuantity());
            return ps;
        }, keyHolder);
        long key = keyHolder.getKey().longValue();
        item.setId(key);
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        String sql = "update item set item_name=?, price=?, quantity=? where id=?";
        template.update(sql,
                updateParam.getItemName(),
                updateParam.getPrice(),
                updateParam.getQuantity(),
                itemId);
    }

    @Override
    public Optional<Item> findById(Long id) {
        String sql = "select id, item_name, price, quantity from item where id = ?";
        try {
            Item item = template.queryForObject(sql, itemRowMapper(), id);
            return Optional.of(item);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();  // 결과 없으면 예외 → Optional.empty()
        }
    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {
        String sql = "select id, item_name, price, quantity from item";
        // 동적 쿼리 — 조건에 따라 where/and 직접 조합
        if (StringUtils.hasText(itemName) || maxPrice != null) {
            sql += " where";
        }
        boolean andFlag = false;
        List<Object> param = new ArrayList<>();
        if (StringUtils.hasText(itemName)) {
            sql += " item_name like concat('%',?,'%')";
            param.add(itemName);
            andFlag = true;
        }
        if (maxPrice != null) {
            if (andFlag) sql += " and";
            sql += " price <= ?";
            param.add(maxPrice);
        }
        return template.query(sql, itemRowMapper(), param.toArray());
    }

    private RowMapper<Item> itemRowMapper() {
        return (rs, rowNum) -> {
            Item item = new Item();
            item.setId(rs.getLong("id"));
            item.setItemName(rs.getString("item_name"));
            item.setPrice(rs.getInt("price"));
            item.setQuantity(rs.getInt("quantity"));
            return item;
        };
    }
}
```

### 핵심 메서드 정리

| 메서드 | 용도 | 반환 |
|--------|------|------|
| `template.update()` | INSERT / UPDATE / DELETE | 영향 받은 row 수 (`int`) |
| `template.queryForObject()` | 단건 조회 | 객체 1개 (없으면 `EmptyResultDataAccessException`) |
| `template.query()` | 다건 조회 | `List<T>` (없으면 빈 컬렉션) |

---

## 동적 쿼리 문제

```
검색 조건 조합 4가지:
  ① 조건 없음         → select id, item_name, price, quantity from item
  ② itemName만        → ... where item_name like concat('%',?,'%')
  ③ maxPrice만        → ... where price <= ?
  ④ 둘 다             → ... where item_name like ... and price <= ?
```

> where/and 붙이는 위치를 코드로 계산해야 해서 복잡. 파라미터도 별도 관리 필요.  
> **이 문제의 해결책 = MyBatis** (동적 쿼리 강점).

---

## V2 — NamedParameterJdbcTemplate (권장)

### 이름 지정 파라미터를 써야 하는 이유

```java
// 순서 기반 — 실수 위험 (price↔quantity 순서 바뀌면 데이터 오염)
String sql = "update item set item_name=?, price=?, quantity=? where id=?";
template.update(sql, itemName, price, quantity, itemId);

// 이름 기반 — 순서 무관, 안전
String sql = "update item set item_name=:itemName, price=:price, quantity=:quantity where id=:id";
```

```java
@Repository
public class JdbcTemplateItemRepositoryV2 implements ItemRepository {
    private final NamedParameterJdbcTemplate template;

    public JdbcTemplateItemRepositoryV2(DataSource dataSource) {
        this.template = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public Item save(Item item) {
        String sql = "insert into item (item_name, price, quantity) values (:itemName, :price, :quantity)";
        SqlParameterSource param = new BeanPropertySqlParameterSource(item);  // getItemName() → itemName
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(sql, param, keyHolder);
        item.setId(keyHolder.getKey().longValue());
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        String sql = "update item set item_name=:itemName, price=:price, quantity=:quantity where id=:id";
        SqlParameterSource param = new MapSqlParameterSource()  // 메서드 체인
                .addValue("itemName", updateParam.getItemName())
                .addValue("price", updateParam.getPrice())
                .addValue("quantity", updateParam.getQuantity())
                .addValue("id", itemId);  // ItemUpdateDto에 id 없으므로 직접 추가
        template.update(sql, param);
    }

    @Override
    public Optional<Item> findById(Long id) {
        String sql = "select id, item_name, price, quantity from item where id = :id";
        try {
            Map<String, Object> param = Map.of("id", id);  // 단순 Map
            Item item = template.queryForObject(sql, param, itemRowMapper());
            return Optional.of(item);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private RowMapper<Item> itemRowMapper() {
        return BeanPropertyRowMapper.newInstance(Item.class);  // camelCase 자동 변환
    }
}
```

### 파라미터 소스 3가지

| 종류 | 특징 | 사용 위치 |
|------|------|---------|
| `Map` | 단순, 코드 간결 | `findById()` 처럼 파라미터 1~2개 |
| `MapSqlParameterSource` | 메서드 체인, SQL 타입 지정 가능 | `update()` 처럼 DTO에 없는 값 추가 필요 시 |
| `BeanPropertySqlParameterSource` | getXxx() → 자동 매핑 | `save()`, `findAll()` 처럼 객체 바로 사용 시 |

> `BeanPropertySqlParameterSource`가 편하지만 `update()`처럼 DTO에 `id`가 없는 경우엔 사용 불가 → `MapSqlParameterSource`로 대체.

### BeanPropertyRowMapper — camelCase 자동 변환

```java
// V1: 직접 매핑
private RowMapper<Item> itemRowMapper() {
    return (rs, rowNum) -> {
        item.setItemName(rs.getString("item_name"));  // snake_case 직접 처리
        ...
    };
}

// V2: BeanPropertyRowMapper (snake_case → camelCase 자동)
private RowMapper<Item> itemRowMapper() {
    return BeanPropertyRowMapper.newInstance(Item.class);
    // item_name → setItemName() 자동 호출
}
```

> `select item_name`으로 조회해도 `setItemName()` 자동 매핑.  
> 컬럼명과 필드명이 완전히 다른 경우: `select member_name as username` 별칭으로 해결.

---

## V3 — SimpleJdbcInsert

```java
@Repository
public class JdbcTemplateItemRepositoryV3 implements ItemRepository {
    private final NamedParameterJdbcTemplate template;
    private final SimpleJdbcInsert jdbcInsert;

    public JdbcTemplateItemRepositoryV3(DataSource dataSource) {
        this.template = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcInsert = new SimpleJdbcInsert(dataSource)
                .withTableName("item")
                .usingGeneratedKeyColumns("id");
                // .usingColumns("item_name", "price", "quantity");  // 생략 가능 (메타데이터 자동 조회)
    }

    @Override
    public Item save(Item item) {
        SqlParameterSource param = new BeanPropertySqlParameterSource(item);
        Number key = jdbcInsert.executeAndReturnKey(param);  // INSERT + PK 반환
        item.setId(key.longValue());
        return item;
    }
    // update/findById/findAll 은 V2와 동일
}
```

> `SimpleJdbcInsert`는 생성 시점에 DB 테이블 메타데이터를 조회해 INSERT SQL 자동 생성.  
> 로그: `INSERT INTO item (ITEM_NAME, PRICE, QUANTITY) VALUES(?, ?, ?)` 자동 생성 확인 가능.

---

## JdbcTemplate 기능 정리

### 조회

```java
// 단건 — 숫자
int rowCount = jdbcTemplate.queryForObject("select count(*) from t_actor", Integer.class);

// 단건 — 파라미터 바인딩
int count = jdbcTemplate.queryForObject("select count(*) from t_actor where first_name = ?",
        Integer.class, "Joe");

// 단건 — 객체
Actor actor = jdbcTemplate.queryForObject(
        "select first_name, last_name from t_actor where id = ?",
        (rs, rowNum) -> {
            Actor a = new Actor();
            a.setFirstName(rs.getString("first_name"));
            return a;
        }, 1212L);

// 다건
List<Actor> actors = jdbcTemplate.query(
        "select first_name, last_name from t_actor",
        actorRowMapper);  // RowMapper 재사용 가능
```

### 변경 (INSERT / UPDATE / DELETE)

```java
// 등록
jdbcTemplate.update("insert into t_actor (first_name, last_name) values (?, ?)", "Leonor", "Watling");

// 수정
jdbcTemplate.update("update t_actor set last_name = ? where id = ?", "Banjo", 5276L);

// 삭제
jdbcTemplate.update("delete from t_actor where id = ?", actorId);
```

### 기타

```java
// DDL
jdbcTemplate.execute("create table mytable (id integer, name varchar(100))");

// 스토어드 프로시저
jdbcTemplate.update("call SUPPORT.REFRESH_ACTORS_SUMMARY(?)", unionId);
```

### 주요 클래스 정리

| 클래스 | 용도 |
|--------|------|
| `JdbcTemplate` | 순서 기반 파라미터 바인딩 |
| `NamedParameterJdbcTemplate` | 이름 기반 파라미터 바인딩 (권장) |
| `SimpleJdbcInsert` | INSERT SQL 자동 생성 |
| `SimpleJdbcCall` | 스토어드 프로시저 호출 |

---

## 정리

| 항목 | 내용 |
|------|------|
| **장점** | `spring-boot-starter-jdbc` 하나로 설정 끝, 반복 코드(커넥션/예외변환/리소스종료) 자동 처리 |
| **단점** | 동적 SQL 작성 어려움, SQL을 자바 코드로 작성해야 함 (+ 연결) |
| **동적 쿼리 해결** | → **MyBatis** (다음 챕터) |

> JPA 같은 ORM 기술과 함께 일부 SQL을 직접 써야 할 때도 JdbcTemplate을 병용.

---

## 면접 Q&A

**Q: JdbcTemplate의 장단점은?**
A: 장점은 두 가지다. 첫째, `spring-boot-starter-jdbc` 의존성 하나만 추가하면 별도 설정 없이 바로 사용할 수 있다. 둘째, 커넥션 조회/동기화, 예외 변환(`SQLExceptionTranslator`), 리소스 정리(`finally`) 같은 JDBC 반복 코드를 템플릿 콜백 패턴으로 제거해준다. 단점은 동적 SQL을 만들기 어렵다는 점이다 — `where`/`and`를 코드로 직접 조합해야 해서 조건이 많아질수록 복잡해진다. 이 문제는 MyBatis로 해결할 수 있다.

**Q: NamedParameterJdbcTemplate을 사용하는 이유는?**
A: 기본 `JdbcTemplate`은 `?` 순서대로 파라미터를 바인딩한다. SQL 컬럼 순서가 바뀌면 파라미터도 같이 바뀌어야 하는데, 이를 놓치면 `price`와 `quantity`가 뒤바뀌는 식의 데이터 오염 버그가 발생한다. 운영 중 DB에 잘못된 데이터가 들어가면 코드 수정 외에 데이터 복구까지 필요해 영향이 크다. `NamedParameterJdbcTemplate`은 `:itemName`처럼 이름으로 바인딩하므로 SQL 순서 변경에 안전하다.

**Q: BeanPropertySqlParameterSource와 MapSqlParameterSource의 차이와 선택 기준은?**
A: `BeanPropertySqlParameterSource`는 자바빈 `getXxx()` 메서드를 리플렉션으로 읽어 자동으로 파라미터를 생성한다. 코드가 간결하지만 파라미터로 전달할 값이 객체에 없을 때는 사용할 수 없다. 예를 들어 `update(itemId, updateParam)` 에서 `:id`를 바인딩해야 하는데 `ItemUpdateDto`에는 `id`가 없으므로 `BeanPropertySqlParameterSource`가 불가능하다. 이 경우 `MapSqlParameterSource`로 메서드 체인을 통해 직접 추가한다.
