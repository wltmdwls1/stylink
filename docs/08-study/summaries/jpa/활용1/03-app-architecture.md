# 03. 애플리케이션 구현 준비

## 전체 흐름 한눈에 보기

```
Controller → Service → Repository → DB
                ↓
             Domain (모든 계층에서 사용)
```

---

## 계층형 아키텍처

| 계층 | 역할 |
|------|------|
| controller / web | 웹 계층, HTTP 요청/응답 처리 |
| service | 비즈니스 로직, 트랜잭션 처리 |
| repository | JPA 직접 사용, EntityManager 사용 |
| domain | 엔티티 모음, 모든 계층에서 참조 |

---

## 개발 순서

**Service + Repository → 테스트 케이스 검증 → 웹 계층 적용**

핵심 비즈니스 로직을 먼저 완성하고 테스트로 검증한 뒤, 마지막에 웹(Controller)을 붙인다.

---

## stylink 적용 포인트

**강의 권장 방식**: 계층형 구조 + Service/Repository 먼저 개발 + 테스트 검증 + 마지막 웹 적용.

**stylink 적용**:
- stylink 멀티모듈 구조도 동일한 계층 분리 적용
  - `domain` 모듈 → 엔티티 + 핵심 비즈니스 로직
  - `fo-api` / `bo-api` → Controller (웹 계층)
  - Service / Repository → domain 모듈 내부
- 개발 순서: Service → Repository → 단위 테스트 → Controller (강의와 동일)
