package com.greenhouse.careloop.command;

public enum CommandLifecycleEventType {
    ISSUED,
    ACKNOWLEDGED,
    DEFERRED,
    DECLINED,
    CANCELLED,
    EXPIRED
}
