# AGENTS.md

## 1. Project Overview

이 프로젝트는 기존 선착순 굿즈 구매 서비스를 확장하는 3차 팀 프로젝트다.

현재 프로젝트의 핵심 목표는 다음과 같다.

1. 기존 백엔드 코드를 현재 프로젝트 구조로 마이그레이션한다.
2. Spring Boot 기반 **Modular Monolith** 구조로 도메인을 정리한다.
3. 기존 API와 핵심 비즈니스 동작을 최대한 유지한다.
4. 코드 변경 사항을 분석하여 테스트 필요 여부를 판단하는 자동 테스트 시스템을 구축한다.
5. 필요한 경우 기존 테스트를 수정하거나 새로운 테스트를 생성한다.
6. 생성/수정된 테스트를 실행하고 결과를 기록한다.
7. 핵심 기능 구현 이후 여유가 있다면 대기열 기능을 추가한다.

이 프로젝트에서는 MSA를 구현하지 않는다.

MSA를 전제로 한 다음 구조를 임의로 도입하지 않는다.

- API Gateway
- Service Discovery
- 서비스별 독립 DB
- 서비스 간 REST 통신 구조
- Kafka 기반 서비스 간 통신
- 분산 트랜잭션
- Saga
- Kubernetes
- Service Mesh

프로젝트 구조는 하나의 Spring Boot 애플리케이션 안에서 도메인 경계를 명확히 나누는 **Modular Monolith**를 기본으로 한다.

---

# 2. Tech Stack

현재 프로젝트의 기본 기술 스택은 다음을 기준으로 한다.

## Backend

- Spring Boot
- Kotlin
- Spring Data JPA
- Spring Security
- PostgreSQL
- Redis

## Frontend

- React

## Test

- JUnit 5
- MockK
- Spring Boot Test
- MockMvc
- Testcontainers 필요 시 사용
- k6 필요 시 사용
- Playwright는 E2E 테스트가 필요한 경우에만 사용

## Infrastructure / Automation

- Docker / Docker Compose
- GitHub Actions
- Git

새로운 기술을 추가하기 전에 기존 기술로 해결할 수 있는지 먼저 확인한다.

---

# 3. Core Principles

모든 작업은 다음 우선순위를 따른다.

1. 기존 기능 유지
2. 데이터 정합성
3. 기존 API 호환성
4. 테스트 가능성
5. 모듈 간 낮은 결합도
6. Kotlin / Spring 관례
7. 성능
8. 코드 간결성

항상 **최소 변경으로 목적을 달성하는 것**을 우선한다.

다음 작업은 특별한 이유가 없다면 하지 않는다.

- 요청과 관계없는 리팩터링
- 기존 API 임의 변경
- DB 스키마 불필요한 변경
- 과도한 디자인 패턴 도입
- 의미 없는 추상화
- 사용하지 않는 인터페이스 추가
- 불필요한 외부 라이브러리 추가
- 프로젝트 규모에 맞지 않는 인프라 도입
- MSA를 전제로 한 구조 도입

---

# 4. Existing Business Rules

기존 프로젝트의 핵심 도메인은 다음과 같다.

- User / Auth
- Goods
- Sale
- Inventory
- Purchase
- Token

마이그레이션 과정에서도 다음 비즈니스 규칙은 유지해야 한다.

## Purchase

- 판매 중인 상품만 구매할 수 있다.
- 판매 시작 전 구매할 수 없다.
- 판매 종료 후 구매할 수 없다.
- 재고가 없는 경우 구매할 수 없다.
- 동일 사용자의 중복 구매를 방지한다.
- 초과 판매가 발생하면 안 된다.

## Inventory

- 재고 감소는 원자적으로 처리해야 한다.
- 구매 실패 시 잘못된 재고 감소가 남아서는 안 된다.
- 구매 취소 시 재고가 정확히 복구되어야 한다.
- DB와 Redis의 재고 정합성을 고려한다.

## Sale

판매 상태 변경 및 검증 로직을 임의로 변경하지 않는다.

대표 상태 흐름은 기존 코드의 정책을 우선한다.

예:

```text
READY
  ↓
ON_SALE
  ↓
SOLD_OUT / CLOSED
```

실제 상태와 전이 조건은 코드를 확인한 뒤 수정한다.

## Authentication

기존 인증 정책을 먼저 확인한다.

다음 요소를 임의로 제거하거나 변경하지 않는다.

- OAuth2
- JWT
- Access Token
- Refresh Token
- Spring Security
- 인증/인가 정책

---

# 5. Modular Monolith Architecture

현재 프로젝트는 MSA가 아니라 Modular Monolith이다.

하나의 애플리케이션으로 실행하고 배포한다.

예상 구조:

```text
src/main/kotlin/...

├── auth
├── user
├── goods
├── sale
├── inventory
├── purchase
├── queue
└── global
```

`queue`는 실제 기능을 구현하는 경우에만 추가한다.

각 도메인은 가능한 한 다음 구조를 기준으로 한다.

```text
purchase/
├── controller
├── service
├── repository
├── domain
├── dto
└── exception
```

프로젝트의 기존 구조가 다른 경우 기존 구조와 일관성을 우선한다.

---

# 6. Module Boundary

모듈 간 결합도를 최소화한다.

예를 들어 Purchase가 Goods 데이터가 필요하다고 해서 모든 Repository를 자유롭게 참조하는 구조를 만들지 않는다.

피한다:

```text
PurchaseService
 ├─ PurchaseRepository
 ├─ GoodsRepository
 ├─ SaleRepository
 ├─ UserRepository
 └─ InventoryRepository
```

가능하면 각 도메인의 역할을 담당하는 Service 또는 명확한 인터페이스를 통해 접근한다.

예:

```text
PurchaseService
   ↓
SaleService
   ↓
InventoryService
```

단, 단순히 계층을 만들기 위해 의미 없는 Service → Service 호출을 추가하지 않는다.

핵심은 다음 두 가지다.

- 책임의 위치가 명확해야 한다.
- 한 모듈이 다른 모듈의 내부 구현에 과도하게 의존하지 않아야 한다.

---

# 7. Dependency Direction

모듈 간 순환 의존성을 만들지 않는다.

피해야 하는 구조:

```text
Purchase
   ↓
Sale
   ↓
Purchase
```

순환 참조가 발견되면 공통 책임을 다시 분석한다.

다음 중 적절한 방법을 선택한다.

- 책임 이동
- 공통 Domain Service
- Interface 분리
- Domain Event

단순한 문제를 해결하기 위해 무조건 Event를 도입하지 않는다.

---

# 8. Migration Principle

기존 API를 새로운 구조로 옮길 때 가장 중요한 것은 **기능 보존**이다.

마이그레이션 과정에서 동시에 대규모 리팩터링을 하지 않는다.

권장 흐름:

```text
기존 코드 분석
    ↓
기존 동작 확인
    ↓
관련 테스트 확인
    ↓
새 구조로 이동
    ↓
테스트 실행
    ↓
기존 동작과 비교
```

---

# 9. Migration Order

가능하면 도메인 단위로 마이그레이션한다.

예:

```text
Auth
 ↓
Goods
 ↓
Sale
 ↓
Inventory
 ↓
Purchase
```

실제 의존관계를 분석한 뒤 순서를 변경할 수 있다.

하나의 도메인을 마이그레이션할 때 다음 흐름을 권장한다.

```text
Domain / Entity
      ↓
DTO
      ↓
Repository
      ↓
Service
      ↓
Controller
      ↓
Test
```

전체 프로젝트를 한 번에 변환하지 않는다.

---

# 10. Kotlin Rules

기존 Java 코드가 있다면 단순 문법 변환만 하지 않는다.

Kotlin 특성을 활용하되 기존 동작은 유지한다.

## 기본 규칙

- Null Safety를 활용한다.
- `!!` 사용을 최대한 피한다.
- nullable 여부를 실제 도메인 규칙에 맞게 정의한다.
- DTO에는 필요한 경우 `data class`를 사용한다.
- Entity에는 무조건 `data class`를 사용하지 않는다.
- 불필요한 getter/setter를 작성하지 않는다.
- `var`보다 `val`을 우선한다.
- 의미 없는 `lateinit` 사용을 피한다.

---

# 11. JPA + Kotlin

JPA Entity 수정 시 다음을 특히 주의한다.

- Entity Proxy
- Lazy Loading
- 기본 생성자
- class final 문제
- equals / hashCode
- 연관관계
- N+1
- cascade
- orphanRemoval

Entity의 편의를 위해 무조건 `data class`를 사용하지 않는다.

연관관계 기본 전략은 특별한 이유가 없다면 Lazy Loading을 유지한다.

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
```

조회 성능 문제가 확인되는 경우 Query별로 Fetch Join, EntityGraph 등을 검토한다.

Lazy를 무조건 EAGER로 바꿔서 문제를 해결하지 않는다.

---

# 12. Transaction Rules

Transaction 범위를 명확히 유지한다.

기본적으로 Business Logic을 수행하는 Service 계층에서 Transaction을 관리한다.

```kotlin
@Transactional
fun purchase(...) {
    ...
}
```

다음 문제를 주의한다.

- 지나치게 긴 Transaction
- 외부 API 호출을 Transaction 내부에서 수행
- Redis와 DB 작업의 정합성
- Transaction 안에서 불필요한 조회
- Lazy Loading 때문에 Transaction 범위를 무작정 늘리는 것

---

# 13. Redis Rules

현재 프로젝트에서 Redis는 특히 재고 처리와 성능 관련 기능에 사용될 수 있다.

Redis를 사용할 때 다음을 고려한다.

- key naming
- TTL
- atomic operation
- race condition
- hot key
- DB 정합성
- 장애 시 처리

Redis를 단순 Cache로 보는 것이 아니라 현재 기능에서 어떤 역할을 담당하는지 먼저 확인한다.

기존 Lua Script가 존재하는 경우 직접 로직을 대체하기 전에 반드시 해당 Script의 원자성 보장 목적을 확인한다.

---

# 14. Automated Test System

자동 테스트 시스템은 다음 흐름을 기본으로 한다.

```text
Remote Branch Push / Pull Request
              ↓
          Git Diff
              ↓
      Changed File Detection
              ↓
        Change Analyzer
              ↓
        Test Required?
          /          \
        NO            YES
                       ↓
               Find Existing Tests
                  /          \
                YES           NO
                 ↓             ↓
              Modify        Generate
                  \          /
                   ↓        ↓
                    Run Tests
                        ↓
                  Save Result
                        ↓
                Commit / PR Comment
```

자동화 시스템이 모든 코드 변경에 대해 AI를 호출하는 구조를 만들지 않는다.

가능하면 정적 규칙으로 먼저 판단한다.

---

# 15. Change Analyzer

Change Analyzer는 변경된 코드가 테스트할 가치가 있는지를 판단한다.

## HIGH

테스트 검토가 거의 반드시 필요하다.

- Service 비즈니스 로직
- Controller API 동작
- Repository Query
- Transaction 처리
- Redis
- Lua Script
- Inventory
- Purchase
- Sale 상태 전이
- Authentication / Authorization
- JWT
- 예외 처리 조건
- Scheduler

## MEDIUM

변경 내용에 따라 테스트한다.

- DTO
- Entity
- Validation
- Mapper
- Configuration
- Serialization
- Security 설정 일부

## LOW

일반적으로 테스트 자동 생성을 하지 않는다.

- README
- docs
- 주석
- log 메시지
- formatting
- import
- 단순 변수명 변경
- whitespace

LOW 변경만 존재하면 AI 테스트 생성을 생략할 수 있다.

---

# 16. Git Diff Analysis

테스트 필요 여부를 판단할 때 파일 경로만 보지 않는다.

가능하면 다음 정보도 분석한다.

```text
변경된 파일
추가된 Method
삭제된 Method
Method Signature 변경
Annotation 변경
조건문 변경
Exception 변경
Repository Query 변경
Transaction 변경
Validation 변경
```

예:

```text
if (remainingStock <= 0)
```

가

```text
if (remainingStock < 0)
```

로 변경됐다면 파일 자체의 변경량은 작아도 중요한 Business Logic 변경이다.

---

# 17. AI Usage Rule

AI 호출은 비용이 발생할 수 있으므로 필요한 경우에만 사용한다.

권장 구조:

```text
Git Diff
   ↓
Rule Based Analyzer
   ↓
Test Needed?
   ↓
AI Analysis
```

다음 작업은 가능한 경우 AI 없이 처리한다.

- 변경 파일 분류
- docs 변경 판단
- import 변경 판단
- formatting 변경 판단
- 기존 테스트 파일 검색
- 테스트 실행
- 결과 수집

AI는 다음과 같은 판단에 집중한다.

- 변경된 비즈니스 로직 이해
- 테스트 필요성 판단
- 기존 테스트 영향 분석
- 테스트 Case 설계
- 테스트 코드 생성/수정

---

# 18. Existing Test Search

새로운 테스트를 작성하기 전에 반드시 기존 테스트를 먼저 확인한다.

예:

```text
PurchaseService.kt
```

가 수정되었다면:

```text
PurchaseServiceTest.kt
PurchaseControllerTest.kt
PurchaseIntegrationTest.kt
```

등을 검색한다.

기존 테스트를 수정해서 해결할 수 있다면 새 테스트를 추가하지 않는다.

---

# 19. Test Modification Rule

기존 코드가 변경되었다고 해서 기존 테스트를 무조건 변경하지 않는다.

먼저 실패 원인을 분석한다.

```text
Production Code Bug
Existing Test Bug
Generated Test Bug
Fixture Problem
Environment Problem
```

기존 Production Code가 잘못되었는데 테스트를 통과시키기 위해 Assertion을 바꾸면 안 된다.

---

# 20. Test Generation Rule

새 테스트가 필요한 경우 변경된 동작과 직접 관련된 테스트만 작성한다.

기본적으로 다음을 고려한다.

## Happy Path

정상적인 비즈니스 흐름

## Failure Path

대표 실패 조건

## Boundary

경계값 또는 상태 경계

예를 들어 Purchase 로직 변경 시 가능한 후보:

```text
정상 구매
재고 부족
중복 구매
판매 시작 전
판매 종료 후
잘못된 판매 상태
```

모든 테스트를 무조건 만들지 않는다.

변경 내용과 직접 관련된 Case를 선택한다.

---

# 21. Test Type

## Service

기본적으로 Unit Test를 우선한다.

- JUnit 5
- MockK

## Controller

- MockMvc
- WebMvcTest

## Repository

- DataJpaTest

실제 PostgreSQL 동작이 중요하면 Testcontainers를 고려한다.

## Integration

- SpringBootTest

## Performance / Concurrency

일반 자동 테스트와 분리한다.

- k6
- 별도 concurrency test

## E2E

실제 브라우저 사용자 흐름이 중요할 때만 Playwright를 사용한다.

---

# 22. Playwright Rule

Playwright를 Unit Test 대신 사용하지 않는다.

다음과 같은 실제 사용자 흐름을 검증하는 용도로 사용한다.

```text
Login
  ↓
Goods List
  ↓
Sale Page
  ↓
Purchase
  ↓
Purchase Result
```

UI 변경이 없거나 서버 내부 로직만 변경된 경우 Playwright 테스트를 생성하지 않는다.

---

# 23. Concurrency Rules

선착순 구매 시스템에서는 동시성이 핵심 Business Requirement다.

다음 코드가 변경되면 동시성 영향을 확인한다.

```text
Inventory
Purchase
Sale
Redis
Lua Script
Lock
Transaction
Cancellation
```

필요한 경우 다음 Scenario를 검토한다.

- oversell
- duplicate purchase
- stock consistency
- cancellation consistency
- simultaneous purchase

단순 코드 변경마다 1,000 VU 부하 테스트를 실행하지 않는다.

단위/통합 테스트와 성능 테스트를 분리한다.

---

# 24. Performance Test Rule

성능 테스트는 기능 테스트와 별도로 관리한다.

성능 테스트에서 확인할 대표 항목:

```text
success rate
failure rate
p95
p99
throughput
connection errors
DB connection pool
Redis latency
CPU
Memory
```

성능 결과를 개선하기 위해 비즈니스 정합성을 희생하지 않는다.

특히 다음은 절대 허용하지 않는다.

```text
빠르지만 oversell 발생
빠르지만 중복 구매 발생
빠르지만 재고 불일치 발생
```

---

# 25. Queue Feature

대기열 기능은 필수 MVP가 아니다.

핵심 기능과 자동 테스트 시스템 구현 이후 시간이 있을 경우 추가한다.

대기열을 구현할 경우 목적은 명확해야 한다.

- 순간적인 대량 트래픽 완화
- Backend 보호
- 구매 요청 진입량 제어
- 중복 요청 방지
- 사용자에게 대기 상태 제공

단순히 "대기열을 사용했다"는 이유만으로 복잡한 Queue Infrastructure를 추가하지 않는다.

---

# 26. Queue Design Rule

대기열 구현을 결정하면 다음 방법을 먼저 검토한다.

```text
Redis
```

필요에 따라 다음 자료구조를 비교한다.

```text
ZSET
SET
HASH
Stream
```

사용자의 정확한 순번이 반드시 필요한지 먼저 판단한다.

정확한 전역 순번이 필요하지 않다면 대량 트래픽에서 모든 요청을 하나의 ZSET 순위로 관리하는 방식이 정말 필요한지 검토한다.

다음 대안도 고려한다.

```text
시간 구간 단위 Batch
Randomized admission
Token based admission
입장 허용 수 제한
```

Queue 설계는 구현 전에 다음 사항을 문서화한다.

- 입장 기준
- 순서 보장 수준
- 중복 사용자 처리
- 이탈 사용자 처리
- TTL
- 허용 처리량
- Redis 장애 시 정책

---

# 27. Security Rules

Security 관련 코드는 특별히 신중하게 수정한다.

변경 시 최소한 다음을 확인한다.

- 로그인
- 인증되지 않은 사용자
- 권한 없는 사용자
- Access Token
- Refresh Token
- Token 만료
- Reissue
- Logout
- OAuth callback

보안 코드는 단순 리팩터링 목적으로 대규모 변경하지 않는다.

---

# 28. API Compatibility

기존 API를 마이그레이션할 때 Request / Response 계약을 유지하는 것을 기본으로 한다.

변경 전:

```http
POST /api/sales/{saleId}/purchases
```

이라면 특별한 이유 없이 URL을 변경하지 않는다.

다음이 변경되면 API 변경으로 취급한다.

- URL
- HTTP Method
- Request Body
- Response Body
- HTTP Status
- Error Code
- Validation

API 변경이 필요한 경우 변경 이유를 명확히 기록한다.

---

# 29. Error Handling

기존 공통 예외 처리 구조가 있다면 이를 유지한다.

예:

```text
ErrorCode
BusinessException
GlobalExceptionHandler
```

새로운 기능을 만들 때 별도의 예외 처리 방식을 임의로 만들지 않는다.

동일한 유형의 오류는 기존 ErrorCode 체계를 먼저 확인한다.

---

# 30. Logging

로그는 문제 분석에 필요한 정보만 기록한다.

피한다:

```text
모든 메서드 진입 로그
모든 변수 출력
Access Token 출력
Refresh Token 출력
개인정보 출력
```

필요한 경우 다음과 같은 식별자를 활용한다.

```text
purchaseId
saleId
userId
requestId
```

민감한 인증 정보는 로그에 출력하지 않는다.

---

# 31. Git Safety Rules

작업 시작 전에 현재 변경 상태를 확인한다.

```bash
git status
git diff
```

사용자가 작업한 코드를 임의로 제거하지 않는다.

다음 명령은 명시적인 요청 없이 사용하지 않는다.

```bash
git reset --hard
git clean -fd
git checkout -- .
```

이미 존재하는 사용자 변경사항과 AI 변경사항을 구분한다.

---

# 32. GitHub Actions

자동 테스트 시스템은 가능하면 GitHub Actions를 중심으로 구성한다.

기본 흐름:

```text
push / pull_request
        ↓
changed files
        ↓
change analyzer
        ↓
test planner
        ↓
generate / modify tests
        ↓
run tests
        ↓
save result
```

GitHub Actions Workflow 자체를 불필요하게 복잡하게 만들지 않는다.

초기 버전은 동작 가능한 최소 파이프라인을 우선한다.

---

# 33. Automated Commit Safety

AI가 생성한 테스트를 자동 Commit하는 기능을 구현할 경우 main 브랜치에 직접 반영하지 않는다.

권장:

```text
Feature Branch
      ↓
AI Generated Test
      ↓
Test Execution
      ↓
Commit
      ↓
Pull Request
      ↓
Human Review
```

자동 생성 코드에 대한 사람의 검토 단계를 유지한다.

---

# 34. Test Execution Order

가능한 경우 가장 좁은 범위부터 테스트한다.

```text
변경된 Class Test
      ↓
Domain Tests
      ↓
Integration Tests
      ↓
전체 Tests
```

예:

```bash
./gradlew test --tests "*PurchaseServiceTest"
```

필요한 경우:

```bash
./gradlew test
```

전체 테스트부터 실행해서 불필요한 시간을 사용하지 않는다.

---

# 35. Forbidden Tests

다음 테스트는 작성하지 않는다.

- getter 테스트
- setter 테스트
- Kotlin data class 자체 테스트
- Framework 자체 기능 테스트
- 단순 객체 생성 테스트
- 무의미한 null 테스트
- 기존 테스트와 동일한 테스트
- Coverage 수치만 올리기 위한 테스트

테스트 개수보다 **비즈니스 위험을 검증하는 테스트**를 우선한다.

---

# 36. Work Procedure

AI가 코드 수정 작업을 수행할 때 기본적으로 다음 순서를 따른다.

```text
1. 요청 이해

2. 관련 코드 검색

3. 기존 구조 파악

4. git status / git diff 확인

5. 영향 범위 확인

6. 기존 테스트 검색

7. 최소 범위 구현

8. 기존 테스트 수정 또는 신규 테스트 작성

9. 관련 테스트 실행

10. 실패 원인 분석

11. 필요 시 수정

12. 테스트 재실행

13. 변경 사항 보고
```

관련 코드를 읽지 않은 상태에서 바로 코드를 수정하지 않는다.

---

# 37. Completion Report

작업 완료 후 결과를 다음 형식으로 정리한다.

## 변경 사항

- 수정 파일
- 추가 파일
- 삭제 파일

## 변경 이유

각 변경이 필요한 이유를 간단히 설명한다.

## 테스트

실행한 테스트와 결과를 기록한다.

예:

```text
PurchaseServiceTest: PASS
PurchaseControllerTest: PASS

42 tests completed
42 passed
0 failed
```

## 확인 필요

사람이 확인해야 하는 부분이 있으면 기록한다.

예:

```text
- Redis 장애 상황은 아직 테스트하지 않음
- 실제 동시 요청 환경에서 추가 검증 필요
```

---

# 38. Documentation

설계에 영향을 주는 변경이 발생하면 필요한 경우 `docs/` 문서를 갱신한다.

권장 구조:

```text
docs/

├── architecture.md
├── migration.md
├── automated-test-system.md
├── performance-test.md
└── queue.md
```

모든 코드 수정마다 문서를 변경하지 않는다.

설계 또는 사용 방법이 달라질 때만 관련 문서를 수정한다.

---

# 39. Project Priority

현재 프로젝트 우선순위는 다음과 같다.

```text
기존 기능 파악
      ↓
기존 API 마이그레이션
      ↓
Modular Monolith 구조 정리
      ↓
핵심 기능 안정화
      ↓
자동 테스트 시스템
      ↓
GitHub Actions 연동
      ↓
테스트 결과 정리
      ↓
대기열 기능 (Optional)
```

핵심 기능이 완성되지 않은 상태에서 대기열과 같은 추가 기능을 우선하지 않는다.

---

# 40. Final Rules

판단이 애매하면 다음 원칙을 따른다.

> 기존 기능을 깨뜨리지 않는 것이 새로운 기술을 추가하는 것보다 중요하다.

> 이 프로젝트는 MSA가 아니다.

> 하나의 애플리케이션 안에서도 도메인 책임과 경계는 명확하게 유지한다.

> 모든 변경에 AI를 사용하는 것이 자동화의 목표가 아니다.

> 정적 분석으로 해결할 수 있는 작업은 정적 분석으로 처리한다.

> 새 테스트를 만들기 전에 기존 테스트를 먼저 확인한다.

> Coverage보다 Business Risk를 우선한다.

> 성능보다 데이터 정합성을 우선한다.

> 요청과 관계없는 코드는 수정하지 않는다.

> 구현이 복잡해질수록 현재 프로젝트에서 정말 필요한지 다시 판단한다.