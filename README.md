<p align="center">
  <img src="https://img.shields.io/badge/POREST_DESK-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="POREST Desk" />
</p>

<h1 align="center">POREST Desk Backend</h1>

<p align="center">
  <strong>개인 생산성/라이프 로그 관리를 위한 Desk 백엔드 서비스</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/JWT-000000?logo=jsonwebtokens&logoColor=white" alt="JWT" />
</p>

---

## 소개

**POREST Desk Backend**는 [POREST](https://github.com/lshdainty/POREST) 서비스의 Desk 백엔드입니다.

할 일, 캘린더, 메모, 지출, 자산, 타이머, 그룹 등 개인 생산성 기능을 API로 제공합니다.

> 현재 출시 전(Pre-release) 단계이며 `project/1.0.0` 브랜치 기준으로 개발 중입니다.

---

## 기술 스택

| Category | Technology |
|----------|------------|
| **Language** | ![Java](https://img.shields.io/badge/Java_25-007396?style=flat-square&logo=openjdk&logoColor=white) |
| **Framework** | ![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.0.4-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) |
| **ORM** | ![JPA](https://img.shields.io/badge/JPA-59666C?style=flat-square&logo=hibernate&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL_7.1-0769AD?style=flat-square) |
| **Database** | ![MariaDB](https://img.shields.io/badge/MariaDB_3.5.1-003545?style=flat-square&logo=mariadb&logoColor=white) |
| **Cache** | ![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white) |
| **Authentication** | ![JWT](https://img.shields.io/badge/JJWT_0.12.6-000000?style=flat-square&logo=jsonwebtokens&logoColor=white) |
| **Monitoring** | ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Loki](https://img.shields.io/badge/Loki-F46800?style=flat-square&logo=grafana&logoColor=white) |
| **공통 라이브러리** | ![porest-core](https://img.shields.io/badge/porest--core_2.0.2-6DB33F?style=flat-square) |
| **API Documentation** | ![Swagger](https://img.shields.io/badge/SpringDoc_OpenAPI_3.0.0-85EA2D?style=flat-square&logo=swagger&logoColor=black) |
| **Testing** | ![JUnit5](https://img.shields.io/badge/JUnit5-25A162?style=flat-square&logo=junit5&logoColor=white) ![JaCoCo](https://img.shields.io/badge/JaCoCo_0.8.13-C5D9C8?style=flat-square) |
| **Build** | ![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white) |

---

## 도메인 모듈

```
src/main/java/com/porest/desk/
├── album/                 # 앨범/사진
├── asset/                 # 자산/이체
├── calculator/            # 계산기 히스토리
├── calendar/              # 일정/라벨
├── dashboard/             # 대시보드 집계
├── dutchpay/              # 더치페이
├── expense/               # 지출/예산/반복지출/템플릿
├── file/                  # 첨부파일
├── group/                 # 그룹/멤버/댓글
├── memo/                  # 메모/폴더
├── notification/          # 알림
├── timer/                 # 타이머 세션
├── todo/                  # 할 일/프로젝트/태그
├── user/                  # 사용자
├── security/              # 보안/JWT
├── client/                # SSO 연동
└── common/                # 공통 설정/예외/메시지
```

---

## 시작하기

### 요구사항

- **Java**: 25 (toolchain 자동 관리)
- **Gradle**: 8.x+
- **MariaDB**: 10.x+
- **Redis**: 6.x+
- **GitHub Packages 접근**: `GITHUB_ACTOR`, `GITHUB_TOKEN` 환경변수 필요 (porest-core 의존성)

### 환경 변수

앱은 아래 파일을 자동 로드합니다.

- `.env.local.properties` (기본)
- `.env.{profile}.properties`

핵심 키:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `JWT_SECRET`, `JWT_SSO_SECRET`
- `SSO_API_URL`, `CORS_ORIGINS`

### 빌드 및 실행

```bash
# 빌드 (테스트 포함)
./gradlew clean build

# 빌드 (테스트 제외)
./gradlew clean build -x test

# 테스트 + JaCoCo 커버리지 리포트 생성
./gradlew test

# 로컬 실행
./gradlew bootRun -Dspring.profiles.active=local
```

기본 포트: `8002`

---

## API 문서

- Swagger UI: `http://localhost:8002/swagger-ui/index.html`
- OpenAPI: `http://localhost:8002/api-docs`
- Actuator: `http://localhost:8002/actuator`

---

## 대표 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/exchange` | SSO 토큰 교환 |
| GET | `/api/v1/dashboard/summary` | 대시보드 요약 |
| POST | `/api/v1/todo` | 할 일 생성 |
| POST | `/api/v1/calendar/event` | 일정 생성 |
| POST | `/api/v1/expense/category` | 지출 카테고리 생성 |
| POST | `/api/v1/memo` | 메모 생성 |
| POST | `/api/v1/timer/session` | 타이머 세션 저장 |
| POST | `/api/v1/group` | 그룹 생성 |

---

## 관련 저장소

| Repository | Description |
|------------|-------------|
| [POREST](https://github.com/lshdainty/POREST) | 통합 레포지토리 (서비스 소개) |
| [porest-desk-front](https://github.com/lshdainty/porest-desk-front) | Desk 프론트엔드 |
| [porest-core](https://github.com/lshdainty/porest-core) | 공통 라이브러리 |
| [porest-sso-back](https://github.com/lshdainty/porest-sso-back) | SSO 백엔드 |
| [porest-sso-front](https://github.com/lshdainty/porest-sso-front) | SSO 프론트엔드 |

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/lshdainty">lshdainty</a>
</p>
