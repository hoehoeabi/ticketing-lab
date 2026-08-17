package com.ticketing.ticketing_lab.global.error;

import com.ticketing.ticketing_lab.global.common.RsData;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 커스텀 예외 처리
     * - HTTP Status: ErrorCode에 정의된 상태 코드 동적 적용 (400, 401, 403, 404, 409 등)
     */
    @ExceptionHandler(BusinessException.class)
    public RsData<Void> handleBusinessException(BusinessException e, HttpServletResponse response) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[Business Exception] code: {}, message: {}", errorCode.getCode(), e.getMessage());

        response.setStatus(errorCode.getStatus().value());
        return RsData.of(errorCode.getCode(), e.getMessage(), null);
    }

    /**
     * @Valid DTO 유효성 검증 실패 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RsData<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletResponse response) {
        BindingResult bindingResult = e.getBindingResult();
        String errorMessage = bindingResult.getFieldErrors().isEmpty()
                ? ErrorCode.INVALID_INPUT_VALUE.getMessage()
                : bindingResult.getFieldErrors().get(0).getDefaultMessage();

        log.warn("[Validation Exception] field: {}, message: {}",
                bindingResult.getFieldErrors().get(0).getField(), errorMessage);

        response.setStatus(ErrorCode.INVALID_INPUT_VALUE.getStatus().value());
        return RsData.of(ErrorCode.INVALID_INPUT_VALUE.getCode(), errorMessage, null);
    }

    /**
     * 필수 요청 헤더 누락 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public RsData<Void> handleMissingRequestHeaderException(MissingRequestHeaderException e, HttpServletResponse response) {
        log.warn("[Missing Header Exception] header: {}", e.getHeaderName());

        response.setStatus(ErrorCode.INVALID_INPUT_VALUE.getStatus().value());

        String errorMessage = "필수 요청 헤더 [" + e.getHeaderName() + "]가 누락되었습니다.";
        return RsData.of(ErrorCode.INVALID_INPUT_VALUE.getCode(), errorMessage, null);
    }

    /**
     * 지원하지 않는 HTTP Method 호출 예외 처리 (405 Method Not Allowed)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public RsData<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletResponse response) {
        log.warn("[Method Not Allowed Exception] {}", e.getMessage());

        response.setStatus(ErrorCode.METHOD_NOT_ALLOWED.getStatus().value());
        return RsData.of(ErrorCode.METHOD_NOT_ALLOWED.getCode(), ErrorCode.METHOD_NOT_ALLOWED.getMessage(), null);
    }

    /**
     * 기타 미처리 서버 내부 예외 처리 (500 Internal Server Error)
     * 위에서 캐치하지 못한 모든 Exception이 이곳으로 들어옴
     */
    @ExceptionHandler(Exception.class)
    public RsData<Void> handleException(Exception e, HttpServletResponse response) {
        log.error("[Unhandled Exception] 서버 에러 발생: ", e);

        response.setStatus(ErrorCode.INTERNAL_SERVER_ERROR.getStatus().value());
        return RsData.of(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), null);
    }
}