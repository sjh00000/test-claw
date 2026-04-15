package com.example.springaialibabademo.reviewadmin.model;

public record ReviewRankingItem(
        String name,
        long totalCount,
        long immediateFixCount,
        long findingCount
) {
}
