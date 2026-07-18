package com.greenhouse.common;

import com.greenhouse.device.DeviceNotFoundException;
import com.greenhouse.observation.ObservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    ProblemDetail handleDeviceNotFound(DeviceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Device not found");
        return problem;
    }

    @ExceptionHandler(ObservationNotFoundException.class)
    ProblemDetail handleObservationNotFound(ObservationNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Observation not found");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request payload is invalid."
        );
        problem.setTitle("Validation failed");
        return problem;
    }
}
