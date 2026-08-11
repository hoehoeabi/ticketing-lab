# ticketing-lab

## 📁 프로젝트 패키지 구조 (Project Structure)

본 프로젝트는 확장성과 가독성을 위해 **Domain-driven Package Structure**를 채택하고 있습니다.  
비즈니스 변화가 잦은 표현 계층(Controller, Service, DTO)은 API 버전별(`v1`, `v2`, `v3`)로 분리하여 동시성 제어 방식 및 성능 실험을 독립적으로 수행할 수 있도록 설계했습니다.

```text
com.ticketing.lab
├── global                                 # [전역 공통 모듈] 시스템 전반에서 사용되는 공통 설정 및 유틸리티
│   ├── config                             # 외부 라이브러리 및 Framework 설정 (Security, Redis, QueryDSL 등)
│   ├── security                           # 인증/인가 핵심 로직 (JwtProvider, Custom Filter, UserDetails)
│   ├── exception                          # 예외 처리 공통 모듈 (GlobalExceptionHandler, CustomErrorCode, CustomException)
│   └── common                             # 시스템 공통 객체 (ApiResponse DTO, BaseEntity, PageRequest/Response DTO)
│
└── domain                                 # [비즈니스 도메인 모듈] 각 도메인별 핵심 비즈니스 객체 및 로직
    ├── user                               # [유저 도메인] 사용자 회원가입, 로그인, 권한 관리
    │   ├── entity                         # User.java (사용자 DB 테이블 매핑 엔티티)
    │   ├── enums                          # Role.java (ROLE_USER, ROLE_ADMIN 권한 상수)
    │   ├── repository                     # UserRepository.java (JPA DB 접근 인터페이스)
    │   └── v1                             # Version 1 유저 관련 API 및 로직
    │       ├── controller                 # UserController.java (회원가입, 로그인 엔드포인트)
    │       ├── dto                        # UserSignUpReqDto, UserLoginReqDto, UserResponseDto
    │       └── service                    # UserService.java (비밀번호 암호화, JWT 발급 요청 로직)
    │
    ├── ticket                             # [티켓/공연 도메인] 공연 티켓 정보 및 핵심 재고 관리 (동시성 제어 실험)
    │   ├── entity                         # Ticket.java (티켓 상품 엔티티, @Version 낙관적 락 포함)
    │   ├── repository                     # TicketRepository.java (티켓 조회 및 비관적 락 쿼리)
    │   ├── v1                             # V1: RDB/JPA 기반 동시성 제어 (Optimistic/Pessimistic Lock)
    │   │   ├── controller                 # TicketV1Controller.java
    │   │   ├── dto                        # TicketCreateReqDto, TicketResponseDto
    │   │   └── service                    # TicketV1Service.java (JPA DB 락을 활용한 재고 차감)
    │   ├── v2                             # V2: Redis 인메모리 기반 고성능 동시성 제어
    │   │   ├── controller                 # TicketV2Controller.java
    │   │   ├── dto                        # TicketReserveV2ReqDto
    │   │   └── service                    # TicketV2Service.java (Redisson 분산락 / Lua Script 제어)
    │   └── v3                             # V3: Message Queue 기반 비동기 대기열 및 고성능 처리
    │       ├── controller                 # TicketV3Controller.java
    │       ├── dto                        # TicketReserveV3ReqDto
    │       └── service                    # TicketV3Service.java (Kafka/RabbitMQ 비동기 예매 처리)
    │
    ├── order                              # [주문/예매 도메인] 사용자의 티켓 구매 및 예매 내역 관리
    │   ├── entity                         # TicketOrder.java (주문 엔티티, No-Offset 복합인덱스 포함)
    │   ├── enums                          # OrderStatus.java (SUCCESS, FAILED, CANCELLED 주문 상태)
    │   ├── repository                     # TicketOrderRepository.java (QueryDSL 커스텀 조회 포함)
    │   └── v1                             # Version 1 주문 관련 API 및 로직
    │       ├── controller                 # OrderController.java (예매 생성 및 내역 조회 API)
    │       ├── dto                        # OrderCreateReqDto, OrderResponseDto
    │       └── service                    # OrderService.java (티켓 재고 차감 연동 및 주문 생성)
    │
    └── notification                       # [알림 도메인] 예매 성공/실패 시 비동기 메시지/이메일 발송 Log 관리
        ├── entity                         # OrderNotification.java (알림 발송 이력 엔티티)
        ├── enums                          # NotificationStatus.java (PENDING, SENT, FAILED 발송 상태)
        ├── repository                     # OrderNotificationRepository.java (알림 조회 인터페이스)
        └── v1                             # Version 1 알림 처리 로직
            ├── controller                 # NotificationController.java (알림 상태 확인 API)
            ├── dto                        # NotificationResponseDto
            └── service                    # NotificationService.java (이메일/알림톡 발송 모듈 연동)
```