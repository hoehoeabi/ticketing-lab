package com.ticketing.ticketing_lab.domain.queue.interceptor;

import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueActiveCheckInterceptor implements HandlerInterceptor {

    private final QueueService queueService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // POST 요청만 대기열 Active 상태 검증
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 경로 변수에서 ticketId 추출
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        );

        if (pathVariables == null || !pathVariables.containsKey("ticketId")) {
            return true;
        }

        Long ticketId;
        try {
            ticketId = Long.parseLong(pathVariables.get("ticketId"));
        } catch (NumberFormatException e) {
            return true;
        }

        // 인증된 사용자 식별자 추출
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = userDetails.getUserId();

        // Redis 대기열 Active 상태 검증
        if (!queueService.isActive(userId, ticketId)) {
            log.warn("[대기열 미통과 접근 차단] userId: {}, ticketId: {}", userId, ticketId);
            throw new BusinessException(ErrorCode.QUEUE_NOT_ACTIVE);
        }

        log.info("[대기열 검증 통과] userId: {}, ticketId: {}", userId, ticketId);
        return true;
    }
}
