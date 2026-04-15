package com.example.springaialibabademo.reviewadmin.controller;

import java.util.List;

import com.example.springaialibabademo.reviewadmin.model.ReviewOverviewResponse;
import com.example.springaialibabademo.reviewadmin.model.ReviewRankingItem;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordDetail;
import com.example.springaialibabademo.reviewadmin.model.ReviewRecordPageResponse;
import com.example.springaialibabademo.reviewadmin.service.ReviewAdminService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-admin")
public class ReviewAdminController {

    private final ReviewAdminService reviewAdminService;

    public ReviewAdminController(ReviewAdminService reviewAdminService) {
        this.reviewAdminService = reviewAdminService;
    }

    @GetMapping("/overview")
    public ReviewOverviewResponse overview() {
        return reviewAdminService.getOverview();
    }

    @GetMapping("/records")
    public ReviewRecordPageResponse records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String reviewResult,
            @RequestParam(required = false) String repoKey,
            @RequestParam(required = false) String statKey
    ) {
        return reviewAdminService.getRecords(page, pageSize, keyword, reviewResult, repoKey, statKey);
    }

    @GetMapping("/records/{id}")
    public ReviewRecordDetail recordDetail(@PathVariable long id) {
        return reviewAdminService.getRecordDetail(id);
    }

    @GetMapping("/rankings/unqualified-repos")
    public List<ReviewRankingItem> unqualifiedRepoRanking(@RequestParam(defaultValue = "10") int limit) {
        return reviewAdminService.getUnqualifiedRepoRanking(limit);
    }

    @GetMapping("/rankings/unqualified-authors")
    public List<ReviewRankingItem> unqualifiedAuthorRanking(@RequestParam(defaultValue = "10") int limit) {
        return reviewAdminService.getUnqualifiedAuthorRanking(limit);
    }

    @GetMapping("/repo-keys")
    public List<String> repoKeys() {
        return reviewAdminService.getRepoKeys();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
