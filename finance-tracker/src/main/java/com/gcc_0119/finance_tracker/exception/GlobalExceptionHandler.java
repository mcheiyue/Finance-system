package com.gcc_0119.finance_tracker.exception;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleRuntimeException(RuntimeException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleException(Exception e) {
        e.printStackTrace();
        return ApiResponse.error(500, "服务器内部错误");
    }
}