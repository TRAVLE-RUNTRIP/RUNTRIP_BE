# RUNTrip

## 로컬 실행

JDK 25와 PostGIS가 설치된 PostgreSQL이 필요합니다.

1. Windows는 PostgreSQL의 Stack Builder에서 PostGIS를 설치합니다.
2. DB를 만들고 확장을 켭니다. `CREATE EXTENSION postgis;`
3. `.env.example`을 `.env`로 복사해 값을 채웁니다.
4. 스키마와 시드를 빈 DB에 한 번만 적용합니다. SQL과 실행 순서는 [Notion](https://app.notion.com/p/40fdb762435d812ab6b4daeb337f7f70)에 있습니다.
5. 서버를 실행합니다.

```bash
./gradlew bootRun
```

## DB 스키마

스키마 기준은 Notion의 `RUNTrip_ERD.sql`입니다. `ddl-auto=validate`라서 서버는 테이블을 만들거나 바꾸지 않고, 기동할 때 entity와 맞는지만 검사합니다.

## 시간 필드

응답 DTO의 시간은 `OffsetDateTime`을 씁니다. `Instant`는 한국 시간(`+09:00`)이 아니라 UTC(`Z`)로 직렬화됩니다.
