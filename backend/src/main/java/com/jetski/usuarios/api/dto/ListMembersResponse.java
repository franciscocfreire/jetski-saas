package com.jetski.usuarios.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: List Members Response
 *
 * Response for listing tenant members.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListMembersResponse {
    private List<MemberSummaryDTO> members;
    private int totalCount;
    private int activeCount;
    private int inactiveCount;
    private PlanLimitInfo planLimit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanLimitInfo {
        private int maxUsuarios;
        private int currentActive;
        private int available;
        private boolean limitReached;
    }
}
