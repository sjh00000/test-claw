package com.example.springaialibabademo.reviewadmin.model;

import java.util.List;

public record ReviewOverviewResponse(
        long totalCount,
        long highRiskCount,
        long mediumRiskCount,
        long lowRiskCount,
        long noRiskCount,
        long ignoredCount,
        long pendingCount,
        long todayCount,
        long distinctRepoCount,
        List<ReviewTrendPoint> recentTrend
) {
}
