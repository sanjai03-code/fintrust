-- ==============================================================================
-- Migration: 004_create_audit_and_documents.sql
-- Project: FinTrust – Financial Transaction and Risk Management System
-- ==============================================================================

-- Create audit_logs table (Immutable trail of financial events and operations)
CREATE TABLE IF NOT EXISTS audit_logs (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    user_email VARCHAR(180),
    organization_id INT REFERENCES organizations(id) ON DELETE CASCADE,
    action VARCHAR(60) NOT NULL, -- e.g. TRANSACTION_CREATED, LEDGER_VERIFIED, ALERT_RESOLVED, ORG_UPDATED
    entity_type VARCHAR(40) NOT NULL, -- e.g. TRANSACTION, LEDGER, ALERT, USER, ORGANIZATION, DOCUMENT
    entity_id VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50) DEFAULT '127.0.0.1',
    user_agent VARCHAR(255),
    old_values TEXT, -- JSON string representation
    new_values TEXT, -- JSON string representation
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create documents table (S3-compatible storage architecture for invoices, receipts, proof of funds)
CREATE TABLE IF NOT EXISTS documents (
    id SERIAL PRIMARY KEY,
    organization_id INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    transaction_id INT REFERENCES transactions(id) ON DELETE SET NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    s3_bucket VARCHAR(100) NOT NULL DEFAULT 'fintrust-vault',
    s3_key VARCHAR(300) NOT NULL UNIQUE,
    checksum VARCHAR(64) NOT NULL, -- SHA-256 for integrity verification
    document_category VARCHAR(50) DEFAULT 'INVOICE' CHECK (document_category IN ('INVOICE', 'PROOF_OF_FUNDS', 'CONTRACT', 'AUDIT_REPORT', 'TAX_FILING')),
    status VARCHAR(20) NOT NULL DEFAULT 'STORED' CHECK (status IN ('STORED', 'VERIFIED', 'FLAGGED', 'ARCHIVED')),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit queries and compliance reports
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_docs_tx ON documents(transaction_id);
CREATE INDEX IF NOT EXISTS idx_docs_org ON documents(organization_id);
