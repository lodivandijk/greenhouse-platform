package com.greenhouse.careloop;

// A requested transition is not permitted from the record's current state -
// approving an already-superseded decision, executing a declined command,
// and so on. Distinct from a validation error so MCP can explain the actual
// state rather than just refusing.
public class InvalidLoopTransitionException extends RuntimeException {
    public InvalidLoopTransitionException(String message) {
        super(message);
    }
}
