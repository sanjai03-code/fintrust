package com.fintrust.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class RiskScore {
    private int id;
    private int transactionId;
    private int organizationId;
    private BigDecimal totalScore;
    private String riskTier;
    private String factorsBreakdown;
    private String ruleMatches;
    private Timestamp assessedAt;

    public RiskScore() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getOrganizationId() { return organizationId; }
    public void setOrganizationId(int organizationId) { this.organizationId = organizationId; }

    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }

    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }

    public String getFactorsBreakdown() { return factorsBreakdown; }
    public void setFactorsBreakdown(String factorsBreakdown) { this.factorsBreakdown = factorsBreakdown; }

    public String getRuleMatches() { return ruleMatches; }
    public void setRuleMatches(String ruleMatches) { this.ruleMatches = ruleMatches; }

    public Timestamp getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Timestamp assessedAt) { this.assessedAt = assessedAt; }
}
