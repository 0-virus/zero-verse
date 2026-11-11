# admin DTO 경계

REQUIREMENTS §6.13-A의 allowlist만 반환한다. password/hash/token/birthDate/content와
last-access 수집값은 노출하지 않으며 `lastAccessAt`은 항상 nullable `null`이다. 시간은
기존 HTTP 계약과 같은 UTC `OffsetDateTime`이다.
