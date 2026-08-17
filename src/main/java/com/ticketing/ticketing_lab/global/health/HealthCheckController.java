package com.ticketing.ticketing_lab.global.health;

import com.ticketing.ticketing_lab.global.common.RsData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthCheckController {

    @GetMapping("/health")
    public RsData<String> healthCheck() {
        // 배포 후 해당 엔드포인트 호출 시 정상 응답을 확인합니다.
        return RsData.of("200", "서버가 정상적으로 동작 중입니다.", "OK");
    }
}
