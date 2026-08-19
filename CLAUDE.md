# porest-desk-back — 작업 규칙

> **워크스페이스 공통 규칙**(Git 작업 격리 · 스테이징 범위 · 태그·릴리스)은
> 상위 `/home/lshdainty/study/CLAUDE.md` 에 있다. Claude Code 가 디렉토리 워크업으로
> 자동 로드하므로 여기에 복사하지 않는다 — 복사본은 원문이 바뀌어도 따라오지 않는다.

## 이 레포는

POREST Desk 의 Spring Boot 백엔드. 할 일·캘린더·가계부·자산·카드·구독·더치페이·토스증권 연동을
`/api/v1` REST API 로 제공하고, SSO(porest-sso-back) 토큰을 desk 토큰으로 교환해 인증한다.
Java 25 / **Spring Boot 4.0.4** / JPA(Hibernate 7) + QueryDSL 7.1 / MariaDB(운영)·H2(테스트) / Redis.
공통 라이브러리 `com.porest:porest-core:2.3.1` 을 **GitHub Packages(private)** 에서 받는다.

## 검증

```bash
export GITHUB_ACTOR=<github-user> GITHUB_TOKEN=<read:packages PAT>   # 없으면 401 로 죽는다
./gradlew test          # 테스트 + JaCoCo 리포트 + 커버리지 게이트가 한 번에 돈다
./gradlew compileJava   # 엔티티를 만들거나 필드를 고친 뒤 QueryDSL Q 클래스 재생성
```

- **자격증명이 없으면 컴파일 이전(의존성 해석)에서 죽는다.** 이 머신에는 `GITHUB_ACTOR`/`GITHUB_TOKEN` 도
  `gradle.properties` 의 `gpr.user`/`gpr.key` 도 캐시된 porest-core 사본도 없다 — 401 을 코드 문제로
  오진하지 말고 못 돌린다는 사실을 그대로 보고하라.
- Q 클래스는 `build/generated/querydsl` 로만 나오고 커밋되지 않는다. 엔티티를 바꾸고 컴파일을 안 돌리면
  QueryDsl 리포지토리에서 `QXxx` 를 못 찾는다. 커밋되는 생성 산출물은 이것 말고 없다.

## 이 레포에서만 통하는 것

- **`XxxJpaRepository` 를 고쳐도 아무 일도 안 일어난다.** 서비스는 인터페이스 `XxxRepository` 만 주입받고
  런타임에 붙는 것은 `@Primary` 가 달린 `XxxQueryDslRepository`(41개)다. JPQL 구현은 10개 모듈
  (todo/memo/memoFolder/user/calendarEvent/userCalendar/holiday/expenseBudget/expenseCategory/accessLog)에
  레거시로 남아 있다. 쿼리 버그를 JPA 쪽만 고치면 테스트는 통과하고 동작은 그대로다.
- **`@WithMockUser` 는 안 통한다.** `@LoginUser` ArgumentResolver 는 principal 이 `JwtUserPrincipal` 일 때만
  주입하므로 String principal 이면 인자가 null 이 된다. `com.porest.desk.support.security.WithLoginUser` 를
  쓰고 `@Import({WebConfig.class, LoginUserArgumentResolver.class})` 를 붙여라
  (예: `src/test/java/com/porest/desk/todo/controller/TodoApiControllerTest.java`).
- **Boot 3 테스트 임포트를 복붙하면 컴파일이 안 된다.** Boot 4 가 슬라이스 자동설정을 모듈별로 쪼갰다.
  `org.springframework.boot.webmvc.test.autoconfigure.{WebMvcTest,AutoConfigureMockMvc}`,
  `...boot.data.jpa.test.autoconfigure.DataJpaTest`, `...boot.jpa.test.autoconfigure.TestEntityManager`,
  `...boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` 만 존재한다.
- **DB 마이그레이션 도구가 없다.** 운영은 `ddl-auto: none` 이고 Flyway·Liquibase·`.sql` 이 하나도 없다.
  테스트는 H2 `create-drop` 이라 컬럼을 추가해도 초록불이 뜨고 dev/prod 는 Unknown column 으로 터진다.
  엔티티를 바꾸는 PR 은 필요한 DDL 을 반드시 함께 보고하라.
- **JaCoCo 게이트가 `test` 를 깬다.** 측정 스코프(= 사실상 service·repository. controller/domain/dto/
  config/security/type/exception 제외)의 BUNDLE LINE 이 0.50 미만이면 실패. 실측 ~56%, 여유 6%p 뿐이다.
- **에러 메시지는 `src/main/resources/message/` 의 `messages`·`messages_en`·`messages_ko` 세 개 모두**에
  넣어라. 기본 번들에 키가 없으면 ko 아닌 Locale 요청에서 키가 그대로 새거나 예외가 난다. 지금도 ko 에만
  있는 키가 기본 대비 19개, en 대비 17개다 — 반복되던 사고다.
- **"오늘/지금" 은 `LocalDate.now()` 가 아니라 core `UserClock`**(배치는 `ServiceClock`)으로 판단한다.
  사용자 타임존 → 없으면 Asia/Seoul 폴백이다. 테스트 JVM 에 `-Duser.timezone=Asia/Seoul` 을 못 박아 둔
  이유도 이것 — 대시보드·별빛·별자리처럼 날짜가 축인 로직에서 하루가 어긋난다.
- **사용자 식별은 `loginUser.getRowId()`(Long, DB PK)** 다. `getUserId()` 는 SSO 문자열 식별자로 별개이고
  도메인 FK 는 전부 `userRowId` 다. 헷갈려 쓰면 타입은 맞는데 남의 데이터를 긁는다.
- **응답은 항상 `com.porest.core.controller.ApiResponse<T>` + `ApiResponse.success(...)`** — `ResponseEntity`
  직접 사용 금지. 새 에러는 `DeskErrorCode` 에 (코드, 메시지키, HttpStatus) 를 추가하고 예외는 core 의
  `EntityNotFoundException`/`ForbiddenException`/`InvalidValueException`/`ExternalServiceException` 을 쓴다.
  임의 예외 타입은 핸들러가 없어 500 으로 샌다.
- **모듈 레이아웃은 18개 모듈이 예외 없이 같다**: `com.porest.desk.<모듈>/{controller, controller/dto, domain,
  repository, service, service/dto, type}`, DTO 는 2단(`XxxApiDto` ↔ `XxxServiceDto`, 컨트롤러가 손으로 변환).
  JaCoCo 제외 목록이 이 이름들에 물려 있어 로직을 엉뚱한 패키지에 두면 커버리지 측정이 통째로 어긋난다.
- **`APP_ENCRYPTION_KEY` 가 비면 에러 없이 조용히 꺼진다** — 로그인은 되는데 SSO 세션이 저장되지 않아 무음
  재인증만 죽는다. 인증 쪽을 고칠 때 "로그인 되니까 괜찮다" 로 판단하지 마라(이 함정으로 커밋이 두 번 났다).

## 머지·태그가 촉발하는 것

- **main 머지 = dev 배포.** `ci-main.yml` 의 `trigger-deploy` 가 push 시 Jenkins 웹훅을 쏜다.
- **태그 `vX.Y.Z` = 릴리스 + 운영 배포 후보.** 미는 즉시 `release.yml` 이 GitHub Release 를 자동 생성하고,
  Jenkins Validate 는 운영 배포에서 이 형식 태그만 받는다(딸지 말지는 루트 규칙대로 사용자가 정한다).
- 커밋 메시지는 한국어 + Conventional Commits 스코프(`feat(sms): ...`), 브랜치는 `type/kebab-case-영문`.

## 짝 레포 — 동기화 장치가 없다

`porest-core`(ApiResponse·예외·UserClock·YNType 출처) · `porest-desk-front` · `porest-desk-app`(Flutter) ·
`porest-sso-back`. 코드젠도 공유 타입도 없어 컨트롤러 DTO 를 바꿔도 이 레포 안에서는 경고가 안 뜬다.
API 스키마를 바꾸면 front/app 을, core 응답 포맷을 바꾸면 버전 범프를 손으로 챙겨라.
