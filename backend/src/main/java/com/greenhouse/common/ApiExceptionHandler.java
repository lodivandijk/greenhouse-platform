package com.greenhouse.common;

import com.greenhouse.action.ActionNotFoundException;
import com.greenhouse.crop.CropNotFoundException;
import com.greenhouse.crop.CropObservationNotFoundException;
import com.greenhouse.crop.HarvestNotFoundException;
import com.greenhouse.device.DeviceNotFoundException;
import com.greenhouse.goal.GoalNotFoundException;
import com.greenhouse.observation.ObservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(CropNotFoundException.class)
    ProblemDetail handleCropNotFound(CropNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Crop not found");
        return problem;
    }

    @ExceptionHandler(GoalNotFoundException.class)
    ProblemDetail handleGoalNotFound(GoalNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Goal not found");
        return problem;
    }

    @ExceptionHandler(HarvestNotFoundException.class)
    ProblemDetail handleHarvestNotFound(HarvestNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Harvest not found");
        return problem;
    }

    @ExceptionHandler(CropObservationNotFoundException.class)
    ProblemDetail handleCropObservationNotFound(CropObservationNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Crop observation not found");
        return problem;
    }

    @ExceptionHandler(ActionNotFoundException.class)
    ProblemDetail handleActionNotFound(ActionNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Action not found");
        return problem;
    }

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail handleDomainValidation(DomainValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
        problem.setTitle("Invalid request");
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + exception.getName() + "'."
        );
        problem.setTitle("Invalid request parameter");
        return problem;
    }
}
