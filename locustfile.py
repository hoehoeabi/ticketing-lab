from locust import HttpUser, task, between

# 전역 캐시 (모든 가상 유저가 토큰 및 테스트 티켓 ID를 공유하여 불필요한 초기 비용 방지)
CACHED_TOKEN = None
CACHED_TICKET_ID = None


class TicketingUser(HttpUser):
    # 가상 유저 간 대기 시간 (0.1 ~ 0.5초)
    wait_time = between(0.1, 0.5)

    def on_start(self):
        """
        가상 유저 생성 시:
        1. base_url 끝의 슬래시(/)를 안전하게 제거하여 더블 슬래시(//api/v1) 방지
        2. TestDataInitializer가 생성한 테스트 계정(user1@test.com)으로 JWT 토큰 획득
        3. DB에 존재하는 실제 티켓 ID를 동적으로 조회(없으면 생성)하여 캐싱
        """
        global CACHED_TOKEN, CACHED_TICKET_ID
        if self.client.base_url:
            self.client.base_url = self.client.base_url.rstrip("/")

        if not CACHED_TOKEN:
            res = self.client.post(
                "/api/v1/users/login",
                json={"email": "user1@test.com", "password": "password123!"},
                name="[Setup] JWT 토큰 발급 로그인"
            )
            if res.status_code == 200:
                CACHED_TOKEN = res.json().get("data", {}).get("accessToken")
                print(f"[Locust] JWT 인증 토큰 발급 완료: {CACHED_TOKEN[:25]}...")

        if CACHED_TOKEN:
            self.client.headers["Authorization"] = f"Bearer {CACHED_TOKEN}"

        if not CACHED_TICKET_ID and CACHED_TOKEN:
            ticket_res = self.client.get(
                "/api/v1/tickets?size=10",
                name="[Setup] 티켓 목록 조회"
            )
            if ticket_res.status_code == 200:
                content = ticket_res.json().get("data", {}).get("content", [])
                if content:
                    CACHED_TICKET_ID = content[0].get("id")
                    print(f"[Locust] 대상 티켓 ID 설정 완료: {CACHED_TICKET_ID}")

            if not CACHED_TICKET_ID:
                create_res = self.client.post(
                    "/api/v1/tickets",
                    json={
                        "title": "Locust 부하테스트 콘서트",
                        "totalQuantity": 10000,
                        "openAt": "2026-10-17T00:00:00"
                    },
                    name="[Setup] 테스트 티켓 생성"
                )
                if create_res.status_code in (200, 201):
                    CACHED_TICKET_ID = create_res.json().get("data", {}).get("id")
                    print(f"[Locust] 신규 테스트 티켓 생성 및 ID 설정 완료: {CACHED_TICKET_ID}")

    @task(3)
    def reserve_ticket(self):
        """
        [최적화] 선착순 티켓 예매 (대기열 진입 + Redisson 분산 락 적용)
        """
        if not CACHED_TICKET_ID:
            return

        ticket_id = CACHED_TICKET_ID

        # 대기열 진입 (Fast-Path 활성화 시 대기열이 비어있으면 즉시 ACTIVE 부여)
        self.client.post(
            f"/api/v1/queue/tickets/{ticket_id}/enter",
            name="/api/v1/queue/tickets/[ticketId]/enter (대기열 진입)"
        )

        with self.client.post(
            f"/api/v1/orders/{ticket_id}",
            catch_response=True,
            name="/api/v1/orders/[ticketId] (분산 락 적용)"
        ) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code in (400, 403, 409):
                res_json = response.json()
                msg = res_json.get("message", "")
                if "매진" in msg or "SOLD_OUT" in msg or "지연" in msg or "대기열" in msg:
                    response.success()
                else:
                    response.failure(f"비즈니스 에러: {msg}")
            elif response.status_code == 401:
                response.failure("인증 실패 (401)")
            else:
                response.failure(f"서버 에러: {response.status_code}")

    @task(2)
    def reserve_ticket_without_lock(self):
        """
        [대조군] 선착순 티켓 예매 (분산 락 미적용 - 동시성 충돌 유발)
        """
        if not CACHED_TICKET_ID:
            return

        ticket_id = CACHED_TICKET_ID
        with self.client.post(
            f"/api/v1/orders/{ticket_id}/no-lock",
            catch_response=True,
            name="/api/v1/orders/[ticketId]/no-lock (락 미적용)"
        ) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code == 400:
                res_json = response.json()
                msg = res_json.get("message", "")
                if "매진" in msg or "SOLD_OUT" in msg:
                    response.success()
                else:
                    response.failure(f"동시성 충돌/비즈니스 에러: {msg}")
            elif response.status_code == 401:
                response.failure("인증 실패 (401)")
            else:
                response.failure(f"서버 에러 (충돌): {response.status_code}")

    @task(1)
    def get_orders_no_offset(self):
        """
        [최적화] 100만 건 대용량 주문 목록 No-Offset 커서 페이징 조회 (O(1) Seek 속도 검증)
        """
        self.client.get(
            "/api/v1/orders?size=20",
            name="/api/v1/orders (No-Offset 커서 페이징)"
        )

    @task(1)
    def get_orders_offset(self):
        """
        [대조군] 100만 건 대용량 주문 목록 Offset 페이징 조회 (뒤쪽 4만 번째 페이지 Full Scan 지연 비교용)
        """
        self.client.get(
            "/api/v1/orders/offset?page=40000&size=20",
            name="/api/v1/orders/offset (Offset 페이징 - page 40000)"
        )

    @task(2)
    def get_ticket_detail(self):
        """
        [공통] 티켓 단건 상세 조회
        """
        if not CACHED_TICKET_ID:
            return

        ticket_id = CACHED_TICKET_ID
        self.client.get(
            f"/api/v1/tickets/{ticket_id}",
            name="/api/v1/tickets/[ticketId] (티켓 상세 조회)"
        )

