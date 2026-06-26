# 데이터 접근 기술 - MyBatis

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
MyBatis = SQL Mapper (JdbcTemplate 확장)

핵심 강점
  ① SQL을 XML에 작성 → 문자열 더하기 없음, 가독성 우수
  ② 동적 쿼리: <if>, <where>, <choose>, <foreach> 태그 지원
  ③ resultType="Item" → BeanPropertyRowMapper처럼 자동 매핑 + camelCase 변환

동작 구조
  @Mapper 인터페이스
    → MyBatis가 JDK 동적 프록시로 구현체 자동 생성
    → 스프링 빈 등록
    → 메서드 호출 → XML SQL 실행 → DataAccessException 변환

설정 (application.properties)
  mybatis.type-aliases-package: 패키지명 생략 가능
  mybatis.configuration.map-underscore-to-camel-case=true: snake_case → camelCase
```

---

## JdbcTemplate vs MyBatis 비교

```java
// JdbcTemplate — SQL 여러줄: 문자열 더하기 불편
String sql = "update item " +
        "set item_name=:itemName, price=:price, quantity=:quantity " +
        "where id=:id";
```

```xml
<!-- MyBatis — SQL 여러줄: 그냥 쓰면 됨 -->
<update id="update">
    update item
    set item_name=#{itemName},
        price=#{price},
        quantity=#{quantity}
    where id = #{id}
</update>
```

```java
// JdbcTemplate — 동적 쿼리: 자바 코드로 직접 조합
if (StringUtils.hasText(itemName) || maxPrice != null) { sql += " where"; }
boolean andFlag = false;
if (StringUtils.hasText(itemName)) { sql += " item_name like ..."; andFlag = true; }
if (maxPrice != null) { if (andFlag) sql += " and"; sql += " price <= :maxPrice"; }
```

```xml
<!-- MyBatis — 동적 쿼리: 태그로 간결하게 -->
<where>
    <if test="itemName != null and itemName != ''">
        and item_name like concat('%',#{itemName},'%')
    </if>
    <if test="maxPrice != null">
        and price &lt;= #{maxPrice}
    </if>
</where>
```

---

## 설정

```groovy
// build.gradle
implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.2.0'  // Boot 2.x
implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3'  // Boot 3.x
implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1'  // Boot 4.x
```

```properties
# application.properties (main + test 둘 다 추가)
mybatis.type-aliases-package=hello.itemservice.domain
mybatis.configuration.map-underscore-to-camel-case=true
logging.level.hello.itemservice.repository.mybatis=trace
```

---

## @Mapper 인터페이스

```java
@Mapper
public interface ItemMapper {
    void save(Item item);
    void update(@Param("id") Long id, @Param("updateParam") ItemUpdateDto updateParam);
    // 파라미터 2개 이상: @Param으로 이름 지정 필수
    Optional<Item> findById(Long id);
    List<Item> findAll(ItemSearchCond itemSearch);
}
```

---

## ItemMapper.xml

```
경로: src/main/resources/hello/itemservice/repository/mybatis/ItemMapper.xml
     (인터페이스 패키지와 동일한 경로에 위치)
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="hello.itemservice.repository.mybatis.ItemMapper">

    <insert id="save" useGeneratedKeys="true" keyProperty="id">
        insert into item (item_name, price, quantity)
        values (#{itemName}, #{price}, #{quantity})
        <!-- useGeneratedKeys=true: identity 전략, keyProperty="id": 생성된 PK → item.id에 자동 세팅 -->
    </insert>

    <update id="update">
        update item
        set item_name=#{updateParam.itemName},
            price=#{updateParam.price},
            quantity=#{updateParam.quantity}
        where id = #{id}
        <!-- @Param("updateParam") → #{updateParam.xxx} 으로 접근 -->
    </update>

    <select id="findById" resultType="Item">
        select id, item_name, price, quantity
        from item
        where id = #{id}
        <!-- resultType="Item": type-aliases-package 덕분에 패키지명 생략 가능 -->
    </select>

    <select id="findAll" resultType="Item">
        select id, item_name, price, quantity
        from item
        <where>
            <if test="itemName != null and itemName != ''">
                and item_name like concat('%',#{itemName},'%')
            </if>
            <if test="maxPrice != null">
                and price &lt;= #{maxPrice}
                <!-- XML 특수문자: < → &lt;, > → &gt;, & → &amp; -->
                <!-- 또는 CDATA 사용: <![CDATA[ and price <= #{maxPrice} ]]> -->
            </if>
        </where>
    </select>

</mapper>
```

---

## MyBatisItemRepository — 위임 패턴

```java
@Repository
@RequiredArgsConstructor
public class MyBatisItemRepository implements ItemRepository {
    private final ItemMapper itemMapper;  // 동적 프록시 구현체 주입

    @Override
    public Item save(Item item) {
        itemMapper.save(item);
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        itemMapper.update(itemId, updateParam);
    }

    @Override
    public Optional<Item> findById(Long id) {
        return itemMapper.findById(id);
    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {
        return itemMapper.findAll(cond);
    }
}
```

---

## 동작 원리 — 동적 프록시

```
애플리케이션 로딩 시점:
  mybatis-spring 모듈이 @Mapper 인터페이스 스캔
    → JDK 동적 프록시로 ItemMapper 구현체 자동 생성
    → 스프링 빈으로 등록

메서드 호출 시:
  itemMapper.save(item)
    → 프록시가 ItemMapper.xml에서 id="save" 찾아서 SQL 실행
    → 예외 발생 시 DataAccessException으로 자동 변환

로그 확인:
  itemMapper class=class com.sun.proxy.$Proxy66  ← JDK 동적 프록시
```

> 구현 클래스 없이 인터페이스만으로 DB 접근 가능.  
> 예외 변환 + 트랜잭션 동기화도 자동 처리.

---

## 동적 쿼리

### if

```xml
<select id="findActiveBlogWithTitleLike" resultType="Blog">
  SELECT * FROM BLOG WHERE state = 'ACTIVE'
  <if test="title != null">
    AND title like #{title}
  </if>
</select>
```

### choose / when / otherwise (switch-case)

```xml
<choose>
    <when test="title != null">AND title like #{title}</when>
    <when test="author != null">AND author_name like #{author.name}</when>
    <otherwise>AND featured = 1</otherwise>
</choose>
```

### where / set

```xml
<!-- <where>: 내부 조건 있으면 WHERE 추가, 첫 AND/OR 자동 제거 -->
<where>
    <if test="state != null">state = #{state}</if>
    <if test="title != null">AND title like #{title}</if>
</where>

<!-- <set>: UPDATE에서 사용, 마지막 쉼표 자동 제거 -->
<set>
    <if test="username != null">username=#{username},</if>
    <if test="password != null">password=#{password},</if>
</set>

<!-- trim으로 직접 정의 가능 -->
<trim prefix="WHERE" prefixOverrides="AND |OR "> ... </trim>
```

### foreach

```xml
<!-- WHERE id IN (1,2,3) 자동 생성 -->
<where>
    <foreach item="item" index="index" collection="list"
        open="ID in (" separator="," close=")" nullable="true">
          #{item}
    </foreach>
</where>
```

---

## 기타 기능

### 어노테이션으로 SQL 작성 (단순 쿼리 전용)

```java
@Select("select id, item_name, price, quantity from item where id=#{id}")
Optional<Item> findById(Long id);
// @Insert, @Update, @Delete, @Select 지원
// 동적 SQL 불가 → 단순 쿼리에만 사용
```

### ${}  — SQL 인젝션 주의

```java
// #{}: PreparedStatement → 안전
// ${}: 문자열 그대로 치환 → SQL 인젝션 위험 (ORDER BY 컬럼명 등 불가피한 경우에만)
@Select("select * from user where ${column} = #{value}")
User findByColumn(@Param("column") String column, @Param("value") String value);
```

### SQL 재사용 — `<sql>` / `<include>`

```xml
<sql id="userColumns"> id, username, password </sql>

<select id="selectUsers" resultType="User">
    select <include refid="userColumns"/>
    from some_table
</select>
```

### resultMap — 컬럼명 ↔ 프로퍼티명 매핑

```xml
<!-- 별칭 방식 -->
<select id="selectUsers" resultType="User">
    select user_id as "id", user_name as "userName" from some_table
</select>

<!-- resultMap 방식 (재사용 가능) -->
<resultMap id="userResultMap" type="User">
    <id property="id" column="user_id"/>
    <result property="username" column="user_name"/>
</resultMap>
<select id="selectUsers" resultMap="userResultMap"> ... </select>
```

---

## 정리

| 항목 | MyBatis | JdbcTemplate |
|------|---------|--------------|
| 동적 쿼리 | `<if>/<where>/<foreach>` 태그로 간결 | 자바 코드로 직접 조합 |
| SQL 위치 | XML 파일 분리 | 자바 코드 안 |
| 설정 | 약간 필요 (starter 의존성) | 스프링 내장 |
| 예외 변환 | DataAccessException 자동 변환 | 동일 |

> 동적 쿼리가 많다면 MyBatis, 단순 쿼리가 많다면 JdbcTemplate. 둘 혼용도 가능.

---

## 면접 Q&A

**Q: MyBatis에서 @Mapper 인터페이스의 구현체는 어떻게 만들어지는가?**
A: `mybatis-spring` 라이브러리가 애플리케이션 로딩 시점에 `@Mapper` 어노테이션이 붙은 인터페이스를 스캔한다. JDK 동적 프록시 기술을 사용해 런타임에 구현체를 자동 생성하고 스프링 빈으로 등록한다. 메서드가 호출되면 프록시가 XML에서 같은 `id`의 SQL을 찾아 실행한다. MyBatis에서 발생한 예외는 스프링의 `DataAccessException`으로 자동 변환되며, 트랜잭션 동기화도 함께 처리된다.

**Q: MyBatis에서 파라미터가 2개 이상일 때 @Param을 사용하는 이유는?**
A: 파라미터가 1개이면 MyBatis가 해당 객체를 바로 `#{}` 에 매핑할 수 있다. 파라미터가 2개 이상이면 어느 값이 어느 이름에 해당하는지 구분할 수 없으므로 `@Param`으로 이름을 명시해야 한다. 예를 들어 `void update(@Param("id") Long id, @Param("updateParam") ItemUpdateDto updateParam)`처럼 선언하면 XML에서 `#{id}`, `#{updateParam.itemName}`처럼 접근할 수 있다.

**Q: MyBatis XML에서 특수문자(<, >)를 사용하는 방법 2가지는?**
A: 첫 번째는 HTML 엔티티를 사용하는 방법이다 — `<`는 `&lt;`, `>`는 `&gt;`, `&`는 `&amp;`로 대체한다. 두 번째는 CDATA 구문을 사용하는 방법이다 — `<![CDATA[ and price <= #{maxPrice} ]]>` 안에서는 특수문자를 그대로 쓸 수 있다. 단, CDATA 안에서는 MyBatis XML 태그(`<if>`, `<where>` 등)가 동작하지 않으므로, 동적 쿼리와 특수문자를 함께 써야 할 때는 해당 부분만 CDATA로 감싸야 한다.
