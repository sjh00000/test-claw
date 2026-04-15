package com.example.springaialibabademo.reviewadmin.model;

public record ReviewRecordSummary(
        long id,
        String repoKey,
        String repositoryPath,
        String commitSha,
        String authorName,
        String authorEmail,
        String commitSubject,
        String committedAt,
        String reviewResult,
        String reviewAdvice,
        String riskLevel,
        boolean immediateFix,
        int findingCount,
        int changedFileCount,
        int relatedFileCount,
        int standardCount,
        String reviewSource,
        String createdAt,
        String updatedAt
) {
}
