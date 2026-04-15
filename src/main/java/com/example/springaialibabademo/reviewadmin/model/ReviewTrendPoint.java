package com.example.springaialibabademo.reviewadmin.model;

public record ReviewTrendPoint(
        String day,
        long totalCount,
        long highRiskCount,
        long mediumRiskCount,
        long lowRiskCount,
        long noRiskCount,
        long ignoredCount,
        long pendingCount
) {
}
