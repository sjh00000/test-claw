package com.example.springaialibabademo.reviewadmin.model;

import java.util.List;

public record ReviewRecordPageResponse(
        long total,
        int page,
        int pageSize,
        List<ReviewRecordSummary> records
) {
}
