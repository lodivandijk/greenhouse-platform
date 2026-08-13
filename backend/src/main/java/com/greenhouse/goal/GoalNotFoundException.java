package com.greenhouse.goal;

public class GoalNotFoundException extends RuntimeException {

    public GoalNotFoundException(Long goalId) {
        super("Goal not found: " + goalId);
    }
}
