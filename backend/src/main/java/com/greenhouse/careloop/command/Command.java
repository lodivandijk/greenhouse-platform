package com.greenhouse.careloop.command;

import com.greenhouse.careloop.command.catalogue.CommandType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

// A concrete instruction generated from an APPROVED decision, targeting a
// human. Immutable - a changed instruction is a replacement command with
// supersedesCommandId set (ADR-021).
@Entity
@Table(name = "command")
public class Command {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Column(name = "decision_id")
    private Long decisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type")
    private CommandType commandType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private CommandTargetType targetType;

    @Column(name = "target_id")
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json")
    private Map<String, Object> parameters;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "supersedes_command_id")
    private Long supersedesCommandId;

    public Command() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCareLoopId() { return careLoopId; }
    public void setCareLoopId(Long careLoopId) { this.careLoopId = careLoopId; }
    public Long getDecisionId() { return decisionId; }
    public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
    public CommandType getCommandType() { return commandType; }
    public void setCommandType(CommandType commandType) { this.commandType = commandType; }
    public CommandTargetType getTargetType() { return targetType; }
    public void setTargetType(CommandTargetType targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Long getSupersedesCommandId() { return supersedesCommandId; }
    public void setSupersedesCommandId(Long supersedesCommandId) { this.supersedesCommandId = supersedesCommandId; }
}
