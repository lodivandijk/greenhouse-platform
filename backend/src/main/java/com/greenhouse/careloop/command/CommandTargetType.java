package com.greenhouse.careloop.command;

// HUMAN only in this version - enforced by a database CHECK constraint as well
// as by CommandService. The enum exists so a future actuator target is a new
// value rather than a schema migration, but no such target may be issued today
// (ADR-021).
public enum CommandTargetType {
    HUMAN
}
