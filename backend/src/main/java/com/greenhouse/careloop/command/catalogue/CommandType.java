package com.greenhouse.careloop.command.catalogue;

// The allow-listed set of actions a decision may propose. Anything outside
// this list is rejected - there is no free-text command path.
public enum CommandType {
    INSPECT_CROP,
    WATER_CROP,
    VENTILATE_GREENHOUSE,
    MOVE_OR_SHADE_CROP,
    PRUNE_CROP,
    FEED_CROP
}
