package com.example.gameflix.exception;

// The shape of the JSON error sent back: a status number and a message
import com.example.gameflix.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Exception thrown by AuthService when login fails
import org.springframework.security.authentication.BadCredentialsException;
// Exception Spring throws when a request body breaks a validation rule (@NotBlank, @Email, @Size...)
import org.springframework.web.bind.MethodArgumentNotValidException;
// Annotations that mark a method as the handler for a particular exception
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// @RestControllerAdvice: watches every controller; when one throws an exception listed below,
// the matching method here runs and its return value becomes the JSON response.
// That keeps the controllers and service free of error-response code.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400: bean validation failed on a request body
    // Runs when @Valid in the controller finds a broken rule (bad email, short password, blank field...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Same short message every time; it doesn't echo back what the user typed
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Invalid input"));
    }

    // 409: email already registered (thrown by AuthService when the DB constraint fires)
    // 409 Conflict = the request can't be done because it clashes with existing data
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "Email already registered"));
    }

    // 401: ONE generic message for every login failure (unknown email, wrong password, ...)
    // The message is written here, not taken from the exception, so every BadCredentialsException
    // gets exactly the same response and nothing leaks about why it failed
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "Invalid email or password"));
    }
}