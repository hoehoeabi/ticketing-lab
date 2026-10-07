package com.ticketing.ticketing_lab.domain.queue.interceptor;

import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueActiveCheckInterceptorTest {

    @InjectMocks
    private QueueActiveCheckInterceptor interceptor;

    @Mock
    private QueueService queueService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST가 아닌 메서드(GET 등)는 대기열 검증을 건너뛰고 통과한다")
    void preHandle_nonPostRequest_pass() {
        // given
        request.setMethod("GET");

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("경로 변수에 ticketId가 없으면 대기열 검증을 건너뛰고 통과한다")
    void preHandle_noTicketId_pass() {
        // given
        request.setMethod("POST");

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("인증되지 않은 유저가 접근 시 UNAUTHORIZED 예외가 발생한다")
    void preHandle_unauthenticated_throwsUnauthorized() {
        // given
        request.setMethod("POST");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("ticketId", "100"));

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("대기열 Active 상태가 아닌 유저 접근 시 QUEUE_NOT_ACTIVE(403) 예외가 발생한다")
    void preHandle_notActive_throwsQueueNotActive() {
        // given
        request.setMethod("POST");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("ticketId", "100"));

        User user = User.builder().email("test@test.com").password("pwd").role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        given(queueService.isActive(1L, 100L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUEUE_NOT_ACTIVE);
    }

    @Test
    @DisplayName("대기열 Active 상태인 유저는 정상 통과한다")
    void preHandle_activeUser_pass() {
        // given
        request.setMethod("POST");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("ticketId", "100"));

        User user = User.builder().email("test@test.com").password("pwd").role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        given(queueService.isActive(1L, 100L)).willReturn(true);

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }
}
