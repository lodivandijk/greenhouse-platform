package com.greenhouse.careloop;

// Who or what originated a care-loop record or event.
//
// HUMAN_VIA_AGENT is deliberately distinct from AGENT: it means a human
// explicitly confirmed the action in conversation and Claude relayed it,
// which is the only way an approval, acknowledgement or execution may be
// recorded in this version. AGENT would mean the agent acted on its own
// authority - not permitted today, but modelled so that progressive autonomy
// is a policy change rather than a schema change. See ADR-021.
public enum ActorType {
    DEVICE,
    DETERMINISTIC_ENGINE,
    AGENT,
    HUMAN_VIA_AGENT,
    HUMAN_DIRECT,
    SYSTEM_SCHEDULER
}
