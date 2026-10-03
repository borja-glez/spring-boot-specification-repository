package com.borjaglez.specrepository.examples.boot3.secure;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

/**
 * Answers the client errors of the HTTP filter API with 400. Without it, every exception below
 * except {@code UndeclaredFieldListException} reaches the servlet container and becomes a 500.
 *
 * <p>The exceptions raised while the query runs ({@link InvalidFilterException}, and a {@link
 * DisallowedFieldException} for the {@code Pageable} sort) can arrive wrapped in an {@code
 * InvalidDataAccessApiUsageException}. Spring MVC matches the cause, as long as no handler here
 * matches the wrapper, so this advice declares no handler for {@code Exception} or {@code
 * RuntimeException}.
 *
 * <p>It is limited to {@link SecureProductController} so that the other demo endpoints keep their
 * behaviour; an application would usually apply it to every controller.
 */
@RestControllerAdvice(assignableTypes = SecureProductController.class)
public class FilterErrorHandler {

  @ExceptionHandler({
    HttpFilterSyntaxException.class,
    HttpUnknownOperatorException.class,
    DisallowedFieldException.class,
    InvalidFilterException.class
  })
  public ProblemDetail invalidFilter(IllegalArgumentException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
