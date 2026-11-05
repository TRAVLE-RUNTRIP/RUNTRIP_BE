# RUNTrip

## 로컬 실행

JDK 25와 PostGIS가 설치된 PostgreSQL이 필요합니다.

1. Windows는 PostgreSQL의 Stack Builder에서 PostGIS를 설치합니다.
2. DB를 만들고 확장을 켭니다. `CREATE EXTENSION postgis;`
3. `.env.example`을 `.env`로 복사해 값을 채웁니다.
4. 서버를 실행합니다.

```bash
./gradlew bootRun
```

## 시간 필드

응답 DTO의 시간은 `OffsetDateTime`을 씁니다. `Instant`는 한국 시간(`+09:00`)이 아니라 UTC(`Z`)로 직렬화됩니다.
