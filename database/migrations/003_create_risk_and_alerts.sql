-- ==============================================================================
-- Migration: 003_create_risk_and_alerts.sql
-- Project: FinTrust – Financial Transaction and Risk Management System
-- ==============================================================================

-- Create risk_scores table
CREATE TABLE IF NOT EXISTS risk_scores (
    id SERIAL PRIMARY KEY,
    transaction_id INT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    organization_id INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    total_score NUMERIC(5, 2) NOT NULL CHECK (total_score >= 0 AND total_score <= 100),
    risk_tier VARCHAR(20) NOT NULL CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    factors_breakdown TEXT NOT NULL, -- Stored as JSON string for cross-database portability
    rule_matches TEXT NOT NULL,      -- Array of matched rules and assigned penalty weights
    evaluated_by VARCHAR(50) DEFAULT 'RULE_ENGINE_V1',
    assessed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create alerts table (Compliance & Risk alerts triggered by transactions)
CREATE TABLE IF NOT EXISTS alerts (
    id SERIAL PRIMARY KEY,
    alert_code VARCHAR(40) NOT NULL UNIQUE,
    transaction_id INT REFERENCES transactions(id) ON DELETE CASCADE,
    organization_id INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    resolution_notes TEXT,
    resolved_by INT REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for alerts and risk queries
CREATE INDEX IF NOT EXISTS idx_risk_tx ON risk_scores(transaction_id);
CREATE INDEX IF NOT EXISTS idx_risk_org ON risk_scores(organization_id);
CREATE INDEX IF NOT EXISTS idx_risk_tier ON risk_scores(risk_tier);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_sev ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_tx ON alerts(transaction_id);
CREATE INDEX IF NOT EXISTS idx_alerts_org ON alerts(organization_id);
