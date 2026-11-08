# 개발 환경 셋업 가이드

새 컴퓨터(Windows)에서 stylink 개발 환경을 구성하는 절차입니다.
DB는 컴퓨터마다 로컬 MySQL을 두고, 스키마는 Flyway 마이그레이션 파일로 Git을 통해 동기화합니다.

---

## 1. 프로그램 설치

| 프로그램 | 버전 | 비고 |
|---|---|---|
| JDK | 17 | Zulu 17 사용 중 (17이면 배포판 무관) |
| MySQL | 8.4 | 설치 시 지정한 **root 비밀번호** 기억 |
| Git | 최신 | |
| IntelliJ | 최신 | |
| Claude Code | 최신 | 선택 |

---

## 2. 코드 받기

```powershell
mkdir C:\dev
cd C:\dev
git clone https://github.com/wltmdwls1/stylink.git
```

- 경로는 `C:\dev\stylink`로 통일 (Claude 메모리 폴더명이 프로젝트 경로 기준이기 때문)

Git 사용자 정보:
```powershell
git config --global user.name "<이름>"
git config --global user.email "<이메일>"
```

---

## 3. 환경변수 설정

PowerShell에서 실행:
```powershell
[Environment]::SetEnvironmentVariable('DB_PASSWORD', '<이 컴퓨터 MySQL root 비밀번호>', 'User')
[Environment]::SetEnvironmentVariable('JWT_SECRET', '<32자 이상 임의 문자열>', 'User')
```

| 변수 | 필수 | 설명 |
|---|---|---|
| `DB_PASSWORD` | O | 이 컴퓨터 MySQL 비밀번호 (컴퓨터마다 달라도 됨) |
| `JWT_SECRET` | O | 32자 이상. DB가 컴퓨터별로 분리되어 있으므로 다른 컴퓨터와 같을 필요 없음 |
| `DB_URL` | X | 기본값 `localhost:3306/stylink` |
| `DB_USERNAME` | X | 기본값 `root` |

> 설정 후 **IntelliJ와 터미널을 완전히 재시작**해야 적용됩니다.

---

## 4. 첫 실행

1. MySQL 서비스 실행 확인 (설치 시 보통 자동 시작)
2. IntelliJ로 `C:\dev\stylink` 열기 → Gradle 동기화 완료 대기
3. `FoApiApplication` 실행

터미널 실행:
```powershell
.\gradlew.bat :fo-api:bootRun
```

---

## 5. 정상 동작 확인

로그에 아래가 순서대로 나오면 성공:
```
Creating Schema History table `stylink`.`flyway_schema_history`
Migrating schema `stylink` to version "1 - init schema"
Successfully applied 1 migration
Started FoApiApplication
```

- `stylink` DB가 없으면 자동 생성 (`createDatabaseIfNotExist=true`)
- 테이블 22개 자동 생성 (Flyway `V1__init_schema.sql`)
- Swagger: http://localhost:8080/swagger-ui/index.html
- bo-api(8090), batch는 필요 시 실행

---

## 6. Claude Code 설정 (선택)

Claude 메모리는 Git에 포함되지 않습니다. 이어서 쓰려면 기존 컴퓨터의 아래 폴더를 새 컴퓨터 같은 위치로 복사합니다.
```
C:\Users\<사용자명>\.claude\projects\C--dev-stylink\memory\
```

- `CLAUDE.md`는 레포에 있으므로 별도 작업 불필요

---

## 7. 작업 흐름

```
작업 시작:  git pull
작업 중:    테이블 변경 시 새 마이그레이션 파일 추가 (V2__xxx.sql, V3__xxx.sql ...)
작업 종료:  git add → commit → push
```

다른 컴퓨터에서는 `git pull` 후 서버 기동만 하면 새 마이그레이션이 자동 적용됩니다.

### 마이그레이션 규칙
- 위치: `domain/src/main/resources/db/migration/`
- 파일명: `V{버전}__{설명}.sql` (언더스코어 2개)
- **이미 적용된 파일은 절대 수정 금지** → 변경은 항상 새 버전 파일로
- `ddl-auto: validate` 유지 → 테이블은 Flyway가 만들고 JPA는 검증만

---

## 8. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| `Access denied for user 'root'` | `DB_PASSWORD` 불일치 또는 미적용 → 값 확인 후 IntelliJ 재시작 |
| `Could not resolve placeholder 'JWT_SECRET'` | 환경변수 없음 → 3번 확인 후 재시작 |
| `Communications link failure` | MySQL 서비스 꺼짐 → `services.msc`에서 MySQL 시작 |
| `Migration checksum mismatch` | 적용된 마이그레이션 파일이 수정됨 → 원래대로 되돌리기 |
| `Schema-validation: missing column/table` | 엔티티 변경에 맞는 마이그레이션 누락 → 새 V 파일 추가 |
| `unmappable character for encoding` | 루트 `build.gradle`의 UTF-8 인코딩 설정 누락 여부 확인 |
| `Public Key Retrieval is not allowed` | JDBC URL의 `allowPublicKeyRetrieval=true` 누락 여부 확인 |
| push 인증 실패 | GitHub 로그인 창에서 로그인 또는 Personal Access Token 사용 |
