package com.evcharge.persistence;

public enum QueueKind {
    NONE,
    WAITING_FAST,
    WAITING_SLOW,
    REDISPATCH_FAST,
    REDISPATCH_SLOW,
    PRIORITY_FAST,
    PRIORITY_SLOW,
    PILE_QUEUE
}
