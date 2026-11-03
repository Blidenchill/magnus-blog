package com.hubc.magnusblog.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps exceptions to status-coded error pages; upload API gets JSON errors. Never leaks stack traces. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException e, HttpServletRequest request) {
        return errorView(request, HttpStatus.NOT_FOUND, "内容不存在或已被移除", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView noResource(NoResourceFoundException e, HttpServletRequest request) {
        return errorView(request, HttpStatus.NOT_FOUND, "页面不存在", null);
    }

    @ExceptionHandler(ConflictException.class)
    public Object conflict(ConflictException e, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return json(HttpStatus.CONFLICT, e.getMessage());
        }
        return errorView(request, HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(InvalidInputException.class)
    public Object invalid(InvalidInputException e, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return json(HttpStatus.UNPROCESSABLE_CONTENT, firstMessage(e));
        }
        return errorView(request, HttpStatus.UNPROCESSABLE_CONTENT, "提交的内容未通过校验", e.getFieldErrors());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public Object tooMany(TooManyRequestsException e, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return json(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
        return errorView(request, HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return json(HttpStatus.CONTENT_TOO_LARGE, "文件超过大小限制（单张最大 10 MB）");
        }
        return errorView(request, HttpStatus.CONTENT_TOO_LARGE, "文件超过大小限制（单张最大 10 MB）", null);
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public Object optimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException e,
                                 HttpServletRequest request) {
        log.info("optimistic lock failure [{}]: {}", request.getAttribute(RequestIdFilter.ATTRIBUTE), e.getMessage());
        if (isJsonRequest(request)) {
            return json(HttpStatus.CONFLICT, "内容已被其他窗口修改，请刷新后重试");
        }
        return errorView(request, HttpStatus.CONFLICT, "内容已被其他窗口修改，请刷新后重试", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView badRequest(IllegalArgumentException e, HttpServletRequest request) {
        return errorView(request, HttpStatus.BAD_REQUEST, "请求参数不正确", null);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView internal(Exception e, HttpServletRequest request) {
        log.error("unhandled error [{}]", request.getAttribute(RequestIdFilter.ATTRIBUTE), e);
        return errorView(request, HttpStatus.INTERNAL_SERVER_ERROR, "服务器开小差了，请稍后再试", null);
    }

    private static boolean isJsonRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/admin/media/upload");
    }

    private static String firstMessage(InvalidInputException e) {
        return e.getFieldErrors().values().stream().findFirst().orElse(e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> json(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    private ModelAndView errorView(HttpServletRequest request, HttpStatus status,
                                   String message, Map<String, String> fieldErrors) {
        ModelAndView mav = new ModelAndView("errors/error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        mav.addObject("fieldErrors", fieldErrors);
        mav.addObject("requestId", request.getAttribute(RequestIdFilter.ATTRIBUTE));
        return mav;
    }
}
