-- ==============================================================================
-- Migration: 001_create_organizations_and_users.sql
-- Project: FinTrust – Financial Transaction and Risk Management System
-- Database: PostgreSQL 14+ / ANSI SQL Compatible
-- ==============================================================================

-- Create organizations table
CREATE TABLE IF NOT EXISTS organizations (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    registration_number VARCHAR(80) NOT NULL UNIQUE,
    tax_id VARCHAR(80) NOT NULL UNIQUE,
    country VARCHAR(3) NOT NULL DEFAULT 'USA', -- ISO Alpha-3
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    risk_tier VARCHAR(20) NOT NULL DEFAULT 'LOW' CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_REVIEW', 'ARCHIVED')),
    compliance_contact VARCHAR(120),
    annual_turnover NUMERIC(18, 2) DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ANALYST' CHECK (role IN ('ADMIN', 'COMPLIANCE_OFFICER', 'AUDITOR', 'MANAGER', 'ANALYST')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
    phone VARCHAR(40),
    department VARCHAR(80),
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create employees table (Relationship: Users <-> Organizations with roles)
CREATE TABLE IF NOT EXISTS employees (
    id SERIAL PRIMARY KEY,
    organization_id INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    department VARCHAR(80) NOT NULL,
    designation VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ANALYST' CHECK (role IN ('ADMIN', 'COMPLIANCE_OFFICER', 'AUDITOR', 'MANAGER', 'ANALYST')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
    joined_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_org_user UNIQUE (organization_id, user_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_orgs_reg ON organizations(registration_number);
CREATE INDEX IF NOT EXISTS idx_employees_org ON employees(organization_id);
CREATE INDEX IF NOT EXISTS idx_employees_user ON employees(user_id);
