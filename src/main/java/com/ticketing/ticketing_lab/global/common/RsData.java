package com.ticketing.ticketing_lab.global.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RsData<T> {

    private String code;
    private String message;
    private T data;

    public static <T> RsData<T> of(String code, String message) {
        return new RsData<>(code, message, null);
    }

    public static <T> RsData<T> of(String code, String message, T data) {
        return new RsData<>(code, message, data);
    }

    // 성공 전용 편의 메서드
    public static <T> RsData<T> success(T data) {
        return of("200", "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> RsData<T> success(String message, T data) {
        return of("200", message, data);
    }

    @JsonIgnore
    public int getStatusCode() {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            // "S-1" 등 문자가 섞인 코드일 경우 기본 200 반환
            return 200;
        }
    }
}
