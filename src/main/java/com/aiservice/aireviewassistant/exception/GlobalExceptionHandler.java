package com.aiservice.aireviewassistant.exception;

import com.aiservice.aireviewassistant.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 统一捕获 Controller 层抛出的各类异常，按异常类型映射为对应的 HTTP 状态码与业务提示，
 * 最终包装为 {@link ApiResponse} 返回，避免直接将堆栈信息暴露给客户端。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 {@code @Valid} / {@code @RequestBody} 参数校验失败。
     * <p>
     * 提取第一个字段错误作为提示，返回 400 Bad Request。
     * </p>
     *
     * @param e {@link MethodArgumentNotValidException}
     * @return 包含字段错误信息的失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("请求参数校验失败");
        log.warn("参数校验失败: {}", message);
        return ApiResponse.error(400, message);
    }

    /**
     * 处理 {@code @Validated} 方法参数校验失败（如 {@code @RequestParam} / {@code @PathVariable}）。
     *
     * @param e {@link ConstraintViolationException}
     * @return 包含所有校验错误信息的失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.error(400, message);
    }

    /**
     * 处理 GET 请求参数绑定失败（如类型转换错误）。
     *
     * @param e {@link BindException}
     * @return 包含字段错误信息的失败响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("请求参数绑定失败");
        log.warn("参数绑定失败: {}", message);
        return ApiResponse.error(400, message);
    }

    /**
     * 处理业务异常。
     *
     * @param e {@link BusinessException}
     * @return 使用业务异常自身 code 与 message 的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理权限不足异常。
     *
     * @param e {@link AccessDeniedException}
     * @return 403 无权访问响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        return ApiResponse.error(403, "无权访问，请先登录");
    }

    /**
     * 处理限流异常。
     *
     * @param e {@link TooManyRequestsException}
     * @return 429 请求过于频繁的响应
     */
    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleTooManyRequests(TooManyRequestsException e) {
        return ApiResponse.error(429, e.getMessage());
    }

    /**
     * 处理静态资源 / 页面未找到异常。
     * <p>
     * Spring Boot 3.2 将静态资源缺失包装为 {@link NoResourceFoundException}，
     * 此处单独捕获避免返回 500。
     * </p>
     *
     * @param e {@link NoResourceFoundException}
     * @return 404 资源不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源未找到: {}", e.getMessage());
        return ApiResponse.error(404, "请求的资源不存在");
    }

    /**
     * 处理上传文件超出大小限制。
     * <p>
     * 该异常在 Multipart 解析阶段抛出（早于 Controller 执行），若不单独处理会被兜底为
     * “系统繁忙，请稍后再试”，用户无法得知真实原因。此处返回可操作的提示。
     * </p>
     *
     * @param e {@link MaxUploadSizeExceededException}
     * @return 400 提示文件过大
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超出大小限制: {}", e.getMessage());
        return ApiResponse.error(400, "文件过大，单个文件不能超过 10MB");
    }

    /**
     * 处理运行时异常。
     * <p>
     * 兜底捕获非受检异常，仅记录服务端日志，向前端返回通用提示，防止泄露内部细节。
     * </p>
     *
     * @param e {@link RuntimeException}
     * @return 500 系统繁忙响应
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return ApiResponse.error(500, "系统繁忙，请稍后再试");
    }

    /**
     * 最终兜底异常处理。
     * <p>
     * 捕获所有未被上述处理器处理的受检异常，返回通用 500 提示。
     * </p>
     *
     * @param e {@link Exception}
     * @return 500 系统繁忙响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ApiResponse.error(500, "系统繁忙，请稍后再试");
    }
}
