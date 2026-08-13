package com.greenhouse.goal;

public record CreateGoalRequest(
        GoalType goalType,
        String description,
        String sourceInstruction,
        Integer priority
) {
}
