-- ==============================================================================
-- Migration: 002_create_transactions_and_ledger.sql
-- Project: FinTrust – Financial Transaction and Risk Management System
-- ==============================================================================

-- Create transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    transaction_ref VARCHAR(40) NOT NULL UNIQUE,
    organization_id INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('TRANSFER', 'PAYMENT', 'WITHDRAWAL', 'DEPOSIT', 'PAYROLL', 'FX_EXCHANGE')),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    source_account VARCHAR(40) NOT NULL,
    destination_account VARCHAR(40) NOT NULL,
    destination_country VARCHAR(3) DEFAULT 'USA',
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FLAGGED', 'REJECTED', 'UNDER_REVIEW')),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW' CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    risk_score_cache NUMERIC(5, 2) DEFAULT 0.00,
    settlement_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create ledger_entries table (Double-Entry Bookkeeping Ledger)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id SERIAL PRIMARY KEY,
    transaction_id INT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    account_number VARCHAR(40) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    balance_after NUMERIC(18, 2) DEFAULT 0.00,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    verified_by INT REFERENCES users(id) ON DELETE SET NULL,
    verification_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for lightning fast lookups & verification queries
CREATE INDEX IF NOT EXISTS idx_trans_ref ON transactions(transaction_ref);
CREATE INDEX IF NOT EXISTS idx_trans_org ON transactions(organization_id);
CREATE INDEX IF NOT EXISTS idx_trans_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_trans_created ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_trans_src_dst ON transactions(source_account, destination_account, amount);
CREATE INDEX IF NOT EXISTS idx_ledger_tx ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_acc ON ledger_entries(account_number);
CREATE INDEX IF NOT EXISTS idx_ledger_verified ON ledger_entries(is_verified);
