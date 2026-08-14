package com.greenhouse.action;

public class ActionNotFoundException extends RuntimeException {

    public ActionNotFoundException(Long actionId) {
        super("Action not found: " + actionId);
    }
}
