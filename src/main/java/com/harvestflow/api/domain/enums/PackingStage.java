package com.harvestflow.api.domain.enums;

/** Packing material issue (Godown → packing team) and return/reconciliation
 * share an identical shape (line items + handled-by + remarks), so one
 * entity type is used for both, tagged with this stage. */
public enum PackingStage {
    ISSUE,
    RETURN
}
