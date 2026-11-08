# DDL

스키마는 Flyway로 버전 관리합니다. 이 폴더에는 더 이상 SQL을 두지 않습니다.

- 위치: `domain/src/main/resources/db/migration/`
- 초기 스키마: `V1__init_schema.sql`
- 변경 시: 기존 파일은 수정하지 말고 `V{n}__{설명}.sql` 새 파일 추가
- 적용: fo-api / bo-api / batch 중 아무 앱이나 기동하면 미적용 버전이 자동 실행됨
