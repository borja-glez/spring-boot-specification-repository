package com.borjaglez.specrepository.http.spring;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

/**
 * Answers the client errors of the HTTP filter API with HTTP 400 and an RFC 9457 {@link
 * ProblemDetail}: {@link HttpFilterSyntaxException}, {@link HttpUnknownOperatorException}, {@link
 * DisallowedFieldException} (and its subclass {@link UndeclaredFieldListException}) and {@link
 * InvalidFilterException} (and its subclass {@code InvalidFilterValueException}).
 *
 * <p>Spring MVC also matches the cause of an exception, so the exceptions raised while the query
 * runs, which a repository proxy wraps in an {@code InvalidDataAccessApiUsageException}, are
 * handled too. No other exception is handled: a wrapper whose cause chain holds none of these types
 * is left to the other handlers.
 *
 * <p>The advice has the lowest precedence, so {@code @ExceptionHandler} methods of a controller and
 * the application's own advices win. It is registered by {@link HttpFilterAutoConfiguration} unless
 * {@code specrepository.http.problem-details.enabled} is {@code false}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class HttpFilterProblemDetailsExceptionHandler {

  /**
   * Builds the 400 response. The {@code detail} is the message of the library exception, which
   * never echoes an oversized value, and the {@code field} property names the field when the
   * exception carries one.
   *
   * @param exception the exception Spring MVC matched, either a library exception or an exception
   *     whose cause chain holds one
   * @return the Problem Details body, with status 400
   * @throws Exception {@code exception} itself if its cause chain holds no library exception
   */
  @ExceptionHandler({
    HttpFilterSyntaxException.class,
    HttpUnknownOperatorException.class,
    DisallowedFieldException.class,
    InvalidFilterException.class
  })
  public ProblemDetail handleClientFilterError(Exception exception) throws Exception {
    Throwable clientError = findClientError(exception);
    if (clientError == null) {
      throw exception;
    }
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, clientError.getMessage());
    String field = fieldOf(clientError);
    if (field != null) {
      problem.setProperty("field", field);
    }
    return problem;
  }

  private static Throwable findClientError(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (isClientError(current)) {
        return current;
      }
    }
    return null;
  }

  private static boolean isClientError(Throwable exception) {
    return exception instanceof HttpFilterSyntaxException
        || exception instanceof HttpUnknownOperatorException
        || exception instanceof DisallowedFieldException
        || exception instanceof InvalidFilterException;
  }

  private static String fieldOf(Throwable clientError) {
    if (clientError instanceof DisallowedFieldException disallowed) {
      return disallowed.field();
    }
    if (clientError instanceof InvalidFilterException invalid) {
      return invalid.field();
    }
    return null;
  }
}
