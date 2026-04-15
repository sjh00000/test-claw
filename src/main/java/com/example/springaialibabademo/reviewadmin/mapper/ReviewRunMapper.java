package com.example.springaialibabademo.reviewadmin.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springaialibabademo.reviewadmin.entity.ReviewRunEntity;
import com.example.springaialibabademo.reviewadmin.model.ReviewOverviewNumbers;
import com.example.springaialibabademo.reviewadmin.model.ReviewRankingItem;
import com.example.springaialibabademo.reviewadmin.model.ReviewTrendPoint;

import org.apache.ibatis.annotations.Param;

public interface ReviewRunMapper extends BaseMapper<ReviewRunEntity> {

    ReviewOverviewNumbers selectOverviewNumbers();

    List<ReviewTrendPoint> selectRecentTrend();

    List<ReviewRankingItem> selectUnqualifiedRepoRanking(@Param("limit") int limit);

    List<ReviewRankingItem> selectUnqualifiedAuthorRanking(@Param("limit") int limit);

    List<String> selectRepoKeys();
}
