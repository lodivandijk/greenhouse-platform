package com.greenhouse.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// One row per care-loop MCP write request. A retry with the same key returns
// the stored result instead of re-running the action, so a repeated approval
// or watering confirmation cannot create a second command or execution
// (ADR-021).
@Entity
@Table(name = "idempotent_request")
public class IdempotentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "request_fingerprint")
    private String requestFingerprint;

    @Column(name = "status")
    private String status;

    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public IdempotentRequest() {
    }

    public IdempotentRequest(String idempotencyKey, String toolName, String requestFingerprint,
                             String status, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.toolName = toolName;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getToolName() { return toolName; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
