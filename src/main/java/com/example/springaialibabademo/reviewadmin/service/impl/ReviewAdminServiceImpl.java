package com.example.springaialibabademo.reviewadmin.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springaialibabademo.reviewadmin.entity.ReviewRunEntity;
import com.example.springaialibabademo.reviewadmin.mapper.ReviewRunMapper;
import com.example.springaialibabademo.reviewadmin.model.ReviewOverviewNumbers;
import com.example.springaialibabademo.reviewadmin.model.ReviewOverviewResponse;
import com.example.springaialibabademo.reviewadmin.model.ReviewRankingItem;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordDetail;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordPageResponse;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordSummary;
import com.example.springaialibabademo.reviewadmin.service.ReviewAdminService;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewAdminServiceImpl implements ReviewAdminService {

    private final ReviewRunMapper reviewRunMapper;

    public ReviewAdminServiceImpl(ReviewRunMapper reviewRunMapper) {
        this.reviewRunMapper = reviewRunMapper;
    }

    @Override
    public ReviewOverviewResponse getOverview() {
        ReviewOverviewNumbers numbers = reviewRunMapper.selectOverviewNumbers();
        if (numbers == null) {
            numbers = new ReviewOverviewNumbers(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return new ReviewOverviewResponse(
                numbers.totalCount(),
                numbers.highRiskCount(),
                numbers.mediumRiskCount(),
                numbers.lowRiskCount(),
                numbers.noRiskCount(),
                numbers.ignoredCount(),
                numbers.pendingCount(),
                numbers.todayCount(),
                numbers.distinctRepoCount(),
                reviewRunMapper.selectRecentTrend()
        );
    }

    @Override
    public ReviewRecordPageResponse getRecords(int page, int pageSize, String keyword, String reviewResult, String repoKey, String statKey) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);

        LambdaQueryWrapper<ReviewRunEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ReviewRunEntity::getCommitSha, keyword)
                    .or().like(ReviewRunEntity::getCommitSubject, keyword)
                    .or().like(ReviewRunEntity::getAuthorName, keyword)
                    .or().like(ReviewRunEntity::getAuthorEmail, keyword)
                    .or().like(ReviewRunEntity::getRepositoryPath, keyword));
        }
        if (StringUtils.hasText(reviewResult)) {
            wrapper.eq(ReviewRunEntity::getReviewResult, reviewResult.trim());
        }
        if (StringUtils.hasText(repoKey)) {
            wrapper.eq(ReviewRunEntity::getRepoKey, repoKey.trim());
        }
        applyStatFilter(wrapper, statKey);
        wrapper.orderByDesc(ReviewRunEntity::getCommittedAt)
                .orderByDesc(ReviewRunEntity::getCreatedAt)
                .orderByDesc(ReviewRunEntity::getId);

        IPage<ReviewRunEntity> pageResult = reviewRunMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<ReviewRecordSummary> records = pageResult.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return new ReviewRecordPageResponse(pageResult.getTotal(), safePage, safePageSize, records);
    }

    @Override
    public ReviewRecordDetail getRecordDetail(long id) {
        ReviewRunEntity entity = reviewRunMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("未找到对应的提交记录: " + id);
        }
        return toDetail(entity);
    }

    @Override
    public List<ReviewRankingItem> getUnqualifiedRepoRanking(int limit) {
        return reviewRunMapper.selectUnqualifiedRepoRanking(Math.max(limit, 1));
    }

    @Override
    public List<ReviewRankingItem> getUnqualifiedAuthorRanking(int limit) {
        return reviewRunMapper.selectUnqualifiedAuthorRanking(Math.max(limit, 1));
    }

    @Override
    public List<String> getRepoKeys() {
        return reviewRunMapper.selectRepoKeys();
    }

    private ReviewRecordSummary toSummary(ReviewRunEntity entity) {
        return new ReviewRecordSummary(
                value(entity.getId()),
                entity.getRepoKey(),
                entity.getRepositoryPath(),
                entity.getCommitSha(),
                entity.getAuthorName(),
                entity.getAuthorEmail(),
                entity.getCommitSubject(),
                entity.getCommittedAt(),
                entity.getReviewResult(),
                entity.getReviewAdvice(),
                entity.getRiskLevel(),
                bool(entity.getImmediateFix()),
                intValue(entity.getFindingCount()),
                intValue(entity.getChangedFileCount()),
                intValue(entity.getRelatedFileCount()),
                intValue(entity.getStandardCount()),
                entity.getReviewSource(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ReviewRecordDetail toDetail(ReviewRunEntity entity) {
        return new ReviewRecordDetail(
                value(entity.getId()),
                entity.getRepoKey(),
                entity.getRepositoryPath(),
                entity.getCommitSha(),
                entity.getAuthorName(),
                entity.getAuthorEmail(),
                entity.getCommitSubject(),
                entity.getCommittedAt(),
                entity.getReviewResult(),
                entity.getReviewAdvice(),
                entity.getRiskLevel(),
                bool(entity.getImmediateFix()),
                intValue(entity.getFindingCount()),
                intValue(entity.getChangedFileCount()),
                intValue(entity.getRelatedFileCount()),
                intValue(entity.getStandardCount()),
                entity.getReviewSource(),
                entity.getMarkdownReportPath(),
                entity.getJsonReportPath(),
                entity.getOpenclawResponsePath(),
                entity.getReminderPath(),
                entity.getRawResponse(),
                entity.getResponsePayload(),
                entity.getSessionId(),
                entity.getSessionFilePath(),
                entity.getSessionsRoot(),
                entity.getDeliveryTarget(),
                entity.getDeliveryReceiveIdType(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean bool(Integer value) {
        return value != null && value == 1;
    }

    private void applyStatFilter(LambdaQueryWrapper<ReviewRunEntity> wrapper, String statKey) {
        if (!StringUtils.hasText(statKey)) {
            return;
        }
        switch (statKey.trim()) {
            case "high" -> wrapper.eq(ReviewRunEntity::getReviewResult, "高风险");
            case "medium" -> wrapper.eq(ReviewRunEntity::getReviewResult, "中风险");
            case "low" -> wrapper.eq(ReviewRunEntity::getReviewResult, "低风险");
            case "safe" -> wrapper.eq(ReviewRunEntity::getReviewResult, "无风险");
            case "pending" -> wrapper.eq(ReviewRunEntity::getReviewResult, "待回传");
            case "today" -> wrapper.apply("DATE(created_at, 'localtime') = DATE('now', 'localtime')");
            default -> {
            }
        }
    }
}
