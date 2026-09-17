# Swagger 문서 점검

기준: PR #18 (`5978694`), 2026-09-17. Springdoc이 생성하는 `/v3/api-docs`와 컨트롤러, 인증 설정, DTO, 예외 코드를 비교했다. Swagger UI를 사용하므로 REST Docs 생성 체계는 추가하지 않았다.

## 수정한 불일치

| 항목 | 수정 내용 |
| --- | --- |
| 사용자·구매·결제 인증 | 미정의 `accessCookie` 참조를 등록된 `cookieAuth`로 통일 |
| 상품 조회 | 전체 상품 API에 적용되는 ADMIN 권한과 쿠키 인증, 조회의 401/403 응답 안내 |
| 내부 결제 조회 | `X-INTERNAL-KEY` 인증 스키마와 401 응답 추가 |
| 고객 문의 | 접수 경로, 인증, 메시지 제한, 성공·실패 응답 설명 추가 |
| 구매 취소 | 판매 종료 후 1일의 취소 기한과 `PURCHASE_005` 추가 |
| 오류 응답 | 정상 DTO로 잘못 추론되던 오류 응답을 `ErrorResponse`로 명시. 403은 업무 오류 DTO를 보장하지 않으므로 해당 스키마를 지정하지 않음 |
| 날짜 | `JsonFormat`이 적용된 상품·판매 DTO에 `yyyy-MM-dd HH:mm:ss` 형식과 예시 추가 |
| 로그인 안내 | 카카오 로그인 경로, 필수 이메일 제공, HttpOnly 쿠키 사용 방법 안내 |

## 사용 및 검증

- 브라우저에서 `/oauth2/authorization/kakao`로 로그인 후 같은 사이트의 `/swagger-ui/index.html`에 접근한다. 보호 API 호출에는 발급된 쿠키가 사용된다.
- `/api/auth/reissue`는 access_token 대신 refresh_token 쿠키가 필요하다.
- 내부 API는 Swagger UI의 `internalApiKey` 항목에 운영 환경에서 제공받은 키를 입력한다. 문서에 실제 키를 기록하지 않는다.
- `SwaggerDocumentationTest`는 실제 애플리케이션의 `/v3/api-docs`를 조회해 인증 참조, 주요 응답, 날짜 형식과 Swagger UI 제공을 확인한다. 테스트에서 Redis 소비자와 초기화만 대체하며 컨트롤러·Springdoc·인증 설정은 실제 빈을 사용한다.

## 별도 확인 사항

현재 SecurityConfig에서 판매 수정(PUT)과 재고 Warm-up(POST)은 ADMIN 전용 규칙이 아닌 USER/ADMIN 공통 규칙을 적용받는다. 문서 변경 과정에서 접근 제어 정책을 변경하지 않았다. 관리자 전용이 의도라면 별도 보안 변경으로 검토해야 한다.
