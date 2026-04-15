package com.example.springaialibabademo.reviewadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("review_runs")
public class ReviewRunEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String repoKey;
    private String repositoryPath;
    private String commitSha;
    private String authorName;
    private String authorEmail;
    private String commitSubject;
    private String committedAt;
    private String reviewResult;
    private String reviewAdvice;
    private String riskLevel;
    private Integer immediateFix;
    private Integer findingCount;
    private Integer changedFileCount;
    private Integer relatedFileCount;
    private Integer standardCount;
    private String reviewSource;
    private String markdownReportPath;
    private String jsonReportPath;
    private String openclawResponsePath;
    private String reminderPath;
    private String rawResponse;
    private String responsePayload;
    private String sessionId;
    private String sessionFilePath;
    private String sessionsRoot;
    private String deliveryTarget;
    private String deliveryReceiveIdType;
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public String getCommitSubject() {
        return commitSubject;
    }

    public void setCommitSubject(String commitSubject) {
        this.commitSubject = commitSubject;
    }

    public String getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(String committedAt) {
        this.committedAt = committedAt;
    }

    public String getReviewResult() {
        return reviewResult;
    }

    public void setReviewResult(String reviewResult) {
        this.reviewResult = reviewResult;
    }

    public String getReviewAdvice() {
        return reviewAdvice;
    }

    public void setReviewAdvice(String reviewAdvice) {
        this.reviewAdvice = reviewAdvice;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getImmediateFix() {
        return immediateFix;
    }

    public void setImmediateFix(Integer immediateFix) {
        this.immediateFix = immediateFix;
    }

    public Integer getFindingCount() {
        return findingCount;
    }

    public void setFindingCount(Integer findingCount) {
        this.findingCount = findingCount;
    }

    public Integer getChangedFileCount() {
        return changedFileCount;
    }

    public void setChangedFileCount(Integer changedFileCount) {
        this.changedFileCount = changedFileCount;
    }

    public Integer getRelatedFileCount() {
        return relatedFileCount;
    }

    public void setRelatedFileCount(Integer relatedFileCount) {
        this.relatedFileCount = relatedFileCount;
    }

    public Integer getStandardCount() {
        return standardCount;
    }

    public void setStandardCount(Integer standardCount) {
        this.standardCount = standardCount;
    }

    public String getReviewSource() {
        return reviewSource;
    }

    public void setReviewSource(String reviewSource) {
        this.reviewSource = reviewSource;
    }

    public String getMarkdownReportPath() {
        return markdownReportPath;
    }

    public void setMarkdownReportPath(String markdownReportPath) {
        this.markdownReportPath = markdownReportPath;
    }

    public String getJsonReportPath() {
        return jsonReportPath;
    }

    public void setJsonReportPath(String jsonReportPath) {
        this.jsonReportPath = jsonReportPath;
    }

    public String getOpenclawResponsePath() {
        return openclawResponsePath;
    }

    public void setOpenclawResponsePath(String openclawResponsePath) {
        this.openclawResponsePath = openclawResponsePath;
    }

    public String getReminderPath() {
        return reminderPath;
    }

    public void setReminderPath(String reminderPath) {
        this.reminderPath = reminderPath;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionFilePath() {
        return sessionFilePath;
    }

    public void setSessionFilePath(String sessionFilePath) {
        this.sessionFilePath = sessionFilePath;
    }

    public String getSessionsRoot() {
        return sessionsRoot;
    }

    public void setSessionsRoot(String sessionsRoot) {
        this.sessionsRoot = sessionsRoot;
    }

    public String getDeliveryTarget() {
        return deliveryTarget;
    }

    public void setDeliveryTarget(String deliveryTarget) {
        this.deliveryTarget = deliveryTarget;
    }

    public String getDeliveryReceiveIdType() {
        return deliveryReceiveIdType;
    }

    public void setDeliveryReceiveIdType(String deliveryReceiveIdType) {
        this.deliveryReceiveIdType = deliveryReceiveIdType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
