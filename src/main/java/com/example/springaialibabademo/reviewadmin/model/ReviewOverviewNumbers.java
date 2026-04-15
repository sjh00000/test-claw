package com.example.springaialibabademo.reviewadmin.model;

public record ReviewOverviewNumbers(
        long totalCount,
        long highRiskCount,
        long mediumRiskCount,
        long lowRiskCount,
        long noRiskCount,
        long ignoredCount,
        long pendingCount,
        long todayCount,
        long distinctRepoCount
) {
}
