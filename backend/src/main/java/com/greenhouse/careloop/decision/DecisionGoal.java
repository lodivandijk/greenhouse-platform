package com.greenhouse.careloop.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "decision_goal")
public class DecisionGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "decision_id")
    private Long decisionId;

    @Column(name = "goal_id")
    private Long goalId;

    public DecisionGoal() {
    }

    public DecisionGoal(Long decisionId, Long goalId) {
        this.decisionId = decisionId;
        this.goalId = goalId;
    }

    public Long getId() { return id; }
    public Long getDecisionId() { return decisionId; }
    public Long getGoalId() { return goalId; }
}
