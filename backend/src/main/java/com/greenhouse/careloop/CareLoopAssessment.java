package com.greenhouse.careloop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// Links an assessment to a loop. A loop may link several assessments - one
// greenhouse ventilation loop can cover every crop's temperature assessment,
// producing a single command rather than one per crop.
@Entity
@Table(name = "care_loop_assessment")
public class CareLoopAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "linked_at")
    private Instant linkedAt;

    public CareLoopAssessment() {
    }

    public CareLoopAssessment(Long careLoopId, Long assessmentId, Instant linkedAt) {
        this.careLoopId = careLoopId;
        this.assessmentId = assessmentId;
        this.linkedAt = linkedAt;
    }

    public Long getId() { return id; }
    public Long getCareLoopId() { return careLoopId; }
    public Long getAssessmentId() { return assessmentId; }
    public Instant getLinkedAt() { return linkedAt; }
}
