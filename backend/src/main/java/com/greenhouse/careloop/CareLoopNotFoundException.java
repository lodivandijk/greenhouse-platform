package com.greenhouse.careloop;

public class CareLoopNotFoundException extends RuntimeException {
    public CareLoopNotFoundException(Long careLoopId) {
        super("Care loop not found: " + careLoopId);
    }
}
