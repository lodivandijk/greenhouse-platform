package com.greenhouse.careloop.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "decision_assessment")
public class DecisionAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "decision_id")
    private Long decisionId;

    @Column(name = "assessment_id")
    private Long assessmentId;

    public DecisionAssessment() {
    }

    public DecisionAssessment(Long decisionId, Long assessmentId) {
        this.decisionId = decisionId;
        this.assessmentId = assessmentId;
    }

    public Long getId() { return id; }
    public Long getDecisionId() { return decisionId; }
    public Long getAssessmentId() { return assessmentId; }
}
