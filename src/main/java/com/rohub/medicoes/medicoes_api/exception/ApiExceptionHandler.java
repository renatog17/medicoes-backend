package com.rohub.medicoes.medicoes_api.exception;

import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler({ConflitoException.class, ObjectOptimisticLockingFailureException.class,
            DataIntegrityViolationException.class})
    public ProblemDetail conflito(Exception ex) {
        return problema(HttpStatus.CONFLICT, ex instanceof ConflitoException ? ex.getMessage()
            : "Operacao conflita com um registro existente, vinculo ou alteracao concorrente");
    }
    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ProblemDetail invalida(RequisicaoInvalidaException ex) {
        return problema(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problema(HttpStatus.BAD_REQUEST, "Dados invalidos");
        body.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErroCampo(e.getField(), e.getDefaultMessage())).toList());
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problema(HttpStatus.BAD_REQUEST, "Parametros invalidos");
        body.setProperty("errors", ex.getParameterValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream().map(e -> new ErroCampo(
                e instanceof org.springframework.validation.FieldError fieldError
                    ? fieldError.getField() : r.getMethodParameter().getParameterName(),
                e.getDefaultMessage()))).toList());
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }
    private ProblemDetail problema(HttpStatus status, String detalhe) {
        return ProblemDetail.forStatusAndDetail(status, detalhe);
    }
    public record ErroCampo(String field, String message) {}
}
