package com.example.springaialibabademo.reviewadmin.service;

import java.util.List;

import com.example.springaialibabademo.reviewadmin.model.ReviewOverviewResponse;
import com.example.springaialibabademo.reviewadmin.model.ReviewRankingItem;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordDetail;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordPageResponse;

public interface ReviewAdminService {

    ReviewOverviewResponse getOverview();

    ReviewRecordPageResponse getRecords(int page, int pageSize, String keyword, String reviewResult, String repoKey, String statKey);

    ReviewRecordDetail getRecordDetail(long id);

    List<ReviewRankingItem> getUnqualifiedRepoRanking(int limit);

    List<ReviewRankingItem> getUnqualifiedAuthorRanking(int limit);

    List<String> getRepoKeys();
}
