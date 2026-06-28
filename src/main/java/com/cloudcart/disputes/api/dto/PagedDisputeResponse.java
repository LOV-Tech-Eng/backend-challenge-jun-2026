package com.cloudcart.disputes.api.dto;

import java.util.List;

public record PagedDisputeResponse(
    List<DisputeResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
