from locust import HttpUser, task, between

# 전역 토큰 캐시 (모든 유저가 토큰을 공유하여 중복 로그인 방지)
CACHED_TOKEN = None


class TicketingUser(HttpUser):
    # 가상 유저 간 대기 시간 (0.1 ~ 0.5초)
    wait_time = between(0.1, 0.5)

    def on_start(self):
        """
        가상 유저 생성 시:
        1. base_url 끝의 슬래시(/)를 안전하게 제거하여 더블 슬래시(//api/v1) 방지
        2. JWT 토큰을 획득하여 세션 헤더에 주입
        """
        global CACHED_TOKEN
        if self.client.base_url:
            self.client.base_url = self.client.base_url.rstrip("/")

        if not CACHED_TOKEN:
            res = self.client.post(
                "/api/v1/users/login",
                json={"email": "admin@test.com", "password": "password123!"},
                name="[Setup] JWT 토큰 발급 로그인"
            )
            if res.status_code == 200:
                CACHED_TOKEN = res.json().get("data", {}).get("accessToken")
                print(f"[Locust] JWT 인증 토큰 발급 완료: {CACHED_TOKEN[:25]}...")
            elif res.status_code == 400 or res.status_code == 404:
                # 계정이 없는 경우 회원가입 후 재로그인
                self.client.post(
                    "/api/v1/users/signup",
                    json={"email": "admin@test.com", "password": "password123!", "provider": "LOCAL"},
                    name="[Setup] 회원가입"
                )
                res2 = self.client.post(
                    "/api/v1/users/login",
                    json={"email": "admin@test.com", "password": "password123!"},
                    name="[Setup] 재로그인"
                )
                if res2.status_code == 200:
                    CACHED_TOKEN = res2.json().get("data", {}).get("accessToken")

        if CACHED_TOKEN:
            self.client.headers["Authorization"] = f"Bearer {CACHED_TOKEN}"

    @task(3)
    def reserve_ticket(self):
        """
        [최적화] 선착순 티켓 예매 (Redisson 분산 락 적용)
        """
        ticket_id = 148
        with self.client.post(
            f"/api/v1/orders/{ticket_id}",
            catch_response=True,
            name="/api/v1/orders/[ticketId] (분산 락 적용)"
        ) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code == 400:
                res_json = response.json()
                msg = res_json.get("message", "")
                if "매진" in msg or "SOLD_OUT" in msg or "락" in msg:
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
        ticket_id = 148
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
        ticket_id = 148
        self.client.get(
            f"/api/v1/tickets/{ticket_id}",
            name="/api/v1/tickets/[ticketId] (티켓 상세 조회)"
        )
