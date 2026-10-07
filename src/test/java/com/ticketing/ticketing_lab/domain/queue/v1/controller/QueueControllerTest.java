package com.ticketing.ticketing_lab.domain.queue.v1.controller;

import com.ticketing.ticketing_lab.domain.queue.enums.QueueStatus;
import com.ticketing.ticketing_lab.domain.queue.v1.dto.QueueResponseDto;
import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QueueControllerTest {

    @InjectMocks
    private QueueController queueController;

    @Mock
    private QueueService queueService;

    private MockMvc mockMvc;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("test@test.com")
                .password("pwd")
                .role(Role.ROLE_USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        userDetails = new CustomUserDetails(user);

        // @AuthenticationPrincipal 처리를 위한 ArgumentResolver 설정
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return userDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(queueController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("대기열 진입 API - 정상 호출 시 200 OK와 순번 정보가 반환된다")
    void enterQueue_success() throws Exception {
        // given
        Long ticketId = 100L;
        QueueResponseDto responseDto = QueueResponseDto.ofWaiting(1L, ticketId, 3L, 1L);
        given(queueService.enterQueue(1L, ticketId)).willReturn(responseDto);

        // when & then
        mockMvc.perform(post("/api/v1/queue/tickets/{ticketId}/enter", ticketId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.message").value("대기열 진입에 성공했습니다."))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.rank").value(3))
                .andExpect(jsonPath("$.data.estimatedWaitTimeSec").value(1));

        verify(queueService).enterQueue(1L, ticketId);
    }

    @Test
    @DisplayName("대기열 상태 폴링 API - 정상 호출 시 200 OK와 현재 상태가 반환된다")
    void getQueueStatus_success() throws Exception {
        // given
        Long ticketId = 100L;
        QueueResponseDto responseDto = QueueResponseDto.ofActive(1L, ticketId);
        given(queueService.getQueueStatus(1L, ticketId)).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/v1/queue/tickets/{ticketId}/status", ticketId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.message").value("대기열 상태 조회가 완료되었습니다."))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.rank").value(0));

        verify(queueService).getQueueStatus(1L, ticketId);
    }

    @Test
    @DisplayName("대기열 이탈 API - 정상 호출 시 200 OK가 반환된다")
    void leaveQueue_success() throws Exception {
        // given
        Long ticketId = 100L;

        // when & then
        mockMvc.perform(delete("/api/v1/queue/tickets/{ticketId}/leave", ticketId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.message").value("대기열에서 정상적으로 이탈했습니다."));

        verify(queueService).leaveQueue(1L, ticketId);
    }
}
