package com.calmara.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveDecision {
    private RetrieveAction action;
    private String query;
    private String thought;

    public enum RetrieveAction {
        RETRIEVE, ANSWER
    }

    public boolean shouldRetrieve() {
        return action == RetrieveAction.RETRIEVE;
    }
}
