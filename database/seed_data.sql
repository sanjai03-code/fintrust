-- ==============================================================================
-- Seed Data: seed_data.sql
-- Project: FinTrust – Financial Transaction and Risk Management System
-- Default Password for all seed users: Admin@123
-- ==============================================================================

-- 1. Insert Organizations
INSERT INTO organizations (id, name, registration_number, tax_id, country, currency, risk_tier, status, compliance_contact, annual_turnover)
VALUES 
(1, 'Apex Global Holdings Inc.', 'CORP-US-98214', 'TAX-883-9912', 'USA', 'USD', 'LOW', 'ACTIVE', 'compliance@apexholdings.com', 45000000.00),
(2, 'Meridian FinTech Europe Ltd.', 'CORP-GB-44109', 'TAX-GB-3391', 'GBR', 'EUR', 'MEDIUM', 'ACTIVE', 'officer@meridianpay.co.uk', 18500000.00),
(3, 'Pacific Maritime Logistics Ltd.', 'CORP-SG-77123', 'TAX-SG-5501', 'SGP', 'USD', 'HIGH', 'ACTIVE', 'audit@pacificmaritime.sg', 8200000.00)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence if needed for postgres
SELECT setval('organizations_id_seq', (SELECT MAX(id) FROM organizations));

-- 2. Insert Users (Password: Admin@123)
-- Hash: $2y$10$2YJqwiKAVaYbDjzOiox6QO0yVNfcvRMdF8HqUDsfkKUXIP.G6mkR2
INSERT INTO users (id, email, password_hash, full_name, role, status, phone, department)
VALUES
(1, 'admin@fintrust.io', '$2y$10$2YJqwiKAVaYbDjzOiox6QO0yVNfcvRMdF8HqUDsfkKUXIP.G6mkR2', 'Alexander Wright', 'ADMIN', 'ACTIVE', '+1-202-555-0143', 'Executive Management'),
(2, 'compliance@fintrust.io', '$2y$10$2YJqwiKAVaYbDjzOiox6QO0yVNfcvRMdF8HqUDsfkKUXIP.G6mkR2', 'Elena Rostova', 'COMPLIANCE_OFFICER', 'ACTIVE', '+1-202-555-0178', 'Regulatory & Compliance'),
(3, 'auditor@fintrust.io', '$2y$10$2YJqwiKAVaYbDjzOiox6QO0yVNfcvRMdF8HqUDsfkKUXIP.G6mkR2', 'Marcus Vance', 'AUDITOR', 'ACTIVE', '+1-202-555-0199', 'Internal Audit'),
(4, 'manager@fintrust.io', '$2y$10$2YJqwiKAVaYbDjzOiox6QO0yVNfcvRMdF8HqUDsfkKUXIP.G6mkR2', 'Sarah Jenkins', 'MANAGER', 'ACTIVE', '+1-202-555-0164', 'Treasury & Operations'),
(5, 'analyst@fintrust.io', '$2y$10$2YJqwiKAVaYbDjzOiox6QO0yVNfcvRMdF8HqUDsfkKUXIP.G6mkR2', 'David Kim', 'ANALYST', 'ACTIVE', '+1-202-555-0122', 'Transaction Surveillance')
ON CONFLICT (id) DO NOTHING;

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- 3. Insert Employees
INSERT INTO employees (id, organization_id, user_id, department, designation, role, status, joined_date)
VALUES
(1, 1, 1, 'Executive', 'Chief Risk Officer', 'ADMIN', 'ACTIVE', '2023-01-15'),
(2, 1, 2, 'Compliance', 'Senior Compliance Officer', 'COMPLIANCE_OFFICER', 'ACTIVE', '2023-03-01'),
(3, 1, 5, 'Surveillance', 'Risk Analyst', 'ANALYST', 'ACTIVE', '2023-06-20'),
(4, 2, 4, 'Treasury', 'Treasury Operations Director', 'MANAGER', 'ACTIVE', '2023-04-10'),
(5, 3, 3, 'Audit', 'Lead Financial Auditor', 'AUDITOR', 'ACTIVE', '2023-05-18')
ON CONFLICT (id) DO NOTHING;

SELECT setval('employees_id_seq', (SELECT MAX(id) FROM employees));

-- 4. Insert Transactions
INSERT INTO transactions (id, transaction_ref, organization_id, user_id, type, amount, currency, source_account, destination_account, destination_country, description, status, risk_level, risk_score_cache, created_at)
VALUES
(1, 'TX-2026-8801', 1, 1, 'TRANSFER', 125000.00, 'USD', 'ACC-US-992011', 'ACC-CH-440192', 'CHE', 'Quarterly Liquidity Transfer to Geneva Subsidiary', 'COMPLETED', 'LOW', 12.50, '2026-10-25 09:15:00+00'),
(2, 'TX-2026-8802', 1, 4, 'PAYMENT', 45000.00, 'USD', 'ACC-US-992011', 'ACC-DE-883100', 'DEU', 'Enterprise Cloud Infrastructure Annual License', 'COMPLETED', 'LOW', 8.00, '2026-10-26 11:30:00+00'),
(3, 'TX-2026-8803', 2, 4, 'TRANSFER', 98500.00, 'EUR', 'ACC-GB-102938', 'ACC-KY-772910', 'CYM', 'Offshore Custody Account Settlement', 'FLAGGED', 'HIGH', 78.40, '2026-10-27 14:45:00+00'),
(4, 'TX-2026-8804', 3, 5, 'WITHDRAWAL', 9950.00, 'USD', 'ACC-SG-551029', 'ACC-MY-339102', 'MYS', 'Smurfing / Structured threshold transfer', 'UNDER_REVIEW', 'HIGH', 72.00, '2026-10-28 16:10:00+00'),
(5, 'TX-2026-8805', 1, 1, 'TRANSFER', 250000.00, 'USD', 'ACC-US-992011', 'ACC-US-110022', 'USA', 'Syndicated Facility Principal Repayment', 'COMPLETED', 'LOW', 15.00, '2026-10-29 10:00:00+00'),
(6, 'TX-2026-8806', 2, 4, 'TRANSFER', 180000.00, 'EUR', 'ACC-GB-102938', 'ACC-RU-992201', 'RUS', 'Sanctioned jurisdiction transfer attempt', 'REJECTED', 'CRITICAL', 94.50, '2026-10-30 08:20:00+00'),
(7, 'TX-2026-8807', 1, 5, 'PAYMENT', 15750.00, 'USD', 'ACC-US-992011', 'ACC-US-339911', 'USA', 'Legal Advisory Retainer - Q4 Audit Prep', 'COMPLETED', 'LOW', 5.00, '2026-10-31 13:00:00+00'),
(8, 'TX-2026-8808', 3, 5, 'TRANSFER', 32000.00, 'USD', 'ACC-SG-551029', 'ACC-SG-882210', 'SGP', 'Unbalanced Ledger Test - Missing matching credit', 'PENDING', 'MEDIUM', 45.00, '2026-11-01 04:30:00+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval('transactions_id_seq', (SELECT MAX(id) FROM transactions));

-- 5. Insert Ledger Entries (Double-Entry Bookkeeping)
INSERT INTO ledger_entries (id, transaction_id, entry_type, account_number, amount, currency, balance_after, is_verified, verified_at, verified_by, verification_notes)
VALUES
-- TX 1 (Balanced)
(1, 1, 'DEBIT', 'ACC-US-992011', 125000.00, 'USD', 4875000.00, TRUE, '2026-10-25 09:20:00+00', 3, 'Reconciled against SWIFT MT103 acknowledgment'),
(2, 1, 'CREDIT', 'ACC-CH-440192', 125000.00, 'USD', 125000.00, TRUE, '2026-10-25 09:20:00+00', 3, 'Credited in Geneva nostro account'),

-- TX 2 (Balanced)
(3, 2, 'DEBIT', 'ACC-US-992011', 45000.00, 'USD', 4830000.00, TRUE, '2026-10-26 11:35:00+00', 3, 'Invoice #INV-2026-994 matched'),
(4, 2, 'CREDIT', 'ACC-DE-883100', 45000.00, 'USD', 45000.00, TRUE, '2026-10-26 11:35:00+00', 3, 'Vendor settlement confirmed'),

-- TX 3 (Balanced)
(5, 3, 'DEBIT', 'ACC-GB-102938', 98500.00, 'EUR', 1201500.00, FALSE, NULL, NULL, 'Pending Enhanced Due Diligence verification'),
(6, 3, 'CREDIT', 'ACC-KY-772910', 98500.00, 'EUR', 98500.00, FALSE, NULL, NULL, 'Pending destination KYC pack'),

-- TX 4 (Balanced)
(7, 4, 'DEBIT', 'ACC-SG-551029', 9950.00, 'USD', 810050.00, FALSE, NULL, NULL, 'Flagged by Structuring Rule Engine'),
(8, 4, 'CREDIT', 'ACC-MY-339102', 9950.00, 'USD', 9950.00, FALSE, NULL, NULL, 'Recipient identity unverified'),

-- TX 5 (Balanced)
(9, 5, 'DEBIT', 'ACC-US-992011', 250000.00, 'USD', 4580000.00, TRUE, '2026-10-29 10:05:00+00', 3, 'Credit facility balance updated'),
(10, 5, 'CREDIT', 'ACC-US-110022', 250000.00, 'USD', 2500000.00, TRUE, '2026-10-29 10:05:00+00', 3, 'Principal repayment credited'),

-- TX 7 (Balanced)
(11, 7, 'DEBIT', 'ACC-US-992011', 15750.00, 'USD', 4564250.00, TRUE, '2026-10-31 13:05:00+00', 3, 'Law firm retainer invoice attached'),
(12, 7, 'CREDIT', 'ACC-US-339911', 15750.00, 'USD', 15750.00, TRUE, '2026-10-31 13:05:00+00', 3, 'Vendor ledger confirmed'),

-- TX 8 (INTENTIONALLY UNBALANCED for Ledger Verification demonstration!)
(13, 8, 'DEBIT', 'ACC-SG-551029', 32000.00, 'USD', 778050.00, FALSE, NULL, NULL, 'Debit posted without credit pairing (Simulated accounting anomaly)')
ON CONFLICT (id) DO NOTHING;

SELECT setval('ledger_entries_id_seq', (SELECT MAX(id) FROM ledger_entries));

-- 6. Insert Risk Scores
INSERT INTO risk_scores (id, transaction_id, organization_id, total_score, risk_tier, factors_breakdown, rule_matches, assessed_at)
VALUES
(1, 1, 1, 12.50, 'LOW', '{"amount_risk": 5.0, "velocity_risk": 2.5, "destination_risk": 5.0, "history_risk": 0.0}', '["Whitelisted Corporate Beneficiary"]', '2026-10-25 09:15:05+00'),
(2, 2, 1, 8.00, 'LOW', '{"amount_risk": 2.0, "velocity_risk": 1.0, "destination_risk": 5.0, "history_risk": 0.0}', '["Routine SaaS Vendor"]', '2026-10-26 11:30:04+00'),
(3, 3, 2, 78.40, 'HIGH', '{"amount_risk": 25.0, "velocity_risk": 18.4, "destination_risk": 35.0, "history_risk": 0.0}', '["Offshore Low-Tax Jurisdiction (KY)", "Large Value Outflow", "Deviation From 30-Day Mean (+240%)"]', '2026-10-27 14:45:10+00'),
(4, 4, 3, 72.00, 'HIGH', '{"amount_risk": 15.0, "velocity_risk": 22.0, "destination_risk": 15.0, "structuring_risk": 20.0}', '["Structuring Pattern: Amount $9,950 just below $10k CTR reporting threshold", "Cross-Border Southeast Asia Corridor"]', '2026-10-28 16:10:08+00'),
(5, 5, 1, 15.00, 'LOW', '{"amount_risk": 10.0, "velocity_risk": 0.0, "destination_risk": 0.0, "history_risk": 5.0}', '["Pre-Approved Lending Partner"]', '2026-10-29 10:00:02+00'),
(6, 6, 2, 94.50, 'CRITICAL', '{"amount_risk": 30.0, "velocity_risk": 14.5, "destination_risk": 50.0, "sanction_risk": 50.0}', '["CRITICAL SANCTIONS ALERT: Destination country on OFAC high-risk embargo watch", "Automated settlement halted"]', '2026-10-30 08:20:05+00'),
(7, 7, 1, 5.00, 'LOW', '{"amount_risk": 1.0, "velocity_risk": 0.0, "destination_risk": 0.0, "history_risk": 4.0}', '["Verified Domestic Vendor"]', '2026-10-31 13:00:03+00'),
(8, 8, 3, 45.00, 'MEDIUM', '{"amount_risk": 15.0, "velocity_risk": 10.0, "destination_risk": 5.0, "ledger_integrity_risk": 15.0}', '["Ledger Imbalance Detected: Missing matching credit entry", "Awaiting reconciliation"]', '2026-11-01 04:30:05+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval('risk_scores_id_seq', (SELECT MAX(id) FROM risk_scores));

-- 7. Insert Alerts
INSERT INTO alerts (id, alert_code, transaction_id, organization_id, severity, title, description, status, resolution_notes, resolved_by, created_at)
VALUES
(1, 'ALT-2026-0091', 6, 2, 'CRITICAL', 'Sanctions Embargo Policy Breach', 'Outbound wire transfer initiated towards a destination entity flagged under international embargo watchlist.', 'NEW', NULL, NULL, '2026-10-30 08:20:06+00'),
(2, 'ALT-2026-0092', 3, 2, 'HIGH', 'High-Risk Tax Haven Settlement', 'Wire transfer amount EUR 98,500 routed to Cayman Islands entity without pre-cleared compliance manifest.', 'IN_REVIEW', 'Requested corporate source-of-wealth documentation from Meridian Treasury.', 2, '2026-10-27 14:46:00+00'),
(3, 'ALT-2026-0093', 4, 3, 'HIGH', 'Potential Smurfing / CTR Evasion', 'Transaction of $9,950 initiated exactly $50 below regulatory currency reporting threshold ($10,000 USD).', 'NEW', NULL, NULL, '2026-10-28 16:11:00+00'),
(4, 'ALT-2026-0094', 8, 3, 'MEDIUM', 'Ledger Double-Entry Inconsistency', 'Single debit entry posted for $32,000 without corresponding credit ledger record. Potential bookkeeping anomaly.', 'NEW', NULL, NULL, '2026-11-01 04:31:00+00'),
(5, 'ALT-2026-0088', 1, 1, 'LOW', 'Large Value Outflow Threshold', 'Routine treasury reallocation of $125,000 exceeded standard automated approval tier ($100,000).', 'RESOLVED', 'Verified with Board approved treasury mandate #2026-Q4-01.', 2, '2026-10-25 09:30:00+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval('alerts_id_seq', (SELECT MAX(id) FROM alerts));

-- 8. Insert Documents (S3 Storage Vault Architecture)
INSERT INTO documents (id, organization_id, user_id, transaction_id, file_name, file_type, file_size, storage_path, s3_bucket, s3_key, checksum, document_category, status)
VALUES
(1, 1, 1, 1, 'Geneva_Subsidiary_Capital_Call_Q4.pdf', 'application/pdf', 245192, 'uploads/docs/Geneva_Subsidiary_Capital_Call_Q4.pdf', 'fintrust-vault', 'org-1/2026/10/tx-1-Geneva_Capital.pdf', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'CONTRACT', 'VERIFIED'),
(2, 1, 4, 2, 'Cloud_License_Renewal_INV_994.pdf', 'application/pdf', 104820, 'uploads/docs/Cloud_License_Renewal_INV_994.pdf', 'fintrust-vault', 'org-1/2026/10/tx-2-Cloud_License.pdf', 'cca9cf445b35438aaf00e8f8e526e2fc3b563f6da2918654b088f28423d64d95', 'INVOICE', 'VERIFIED'),
(3, 2, 4, 3, 'Meridian_Cayman_Custody_Form.pdf', 'application/pdf', 382100, 'uploads/docs/Meridian_Cayman_Custody_Form.pdf', 'fintrust-vault', 'org-2/2026/10/tx-3-Cayman_Custody.pdf', 'd4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35', 'PROOF_OF_FUNDS', 'FLAGGED')
ON CONFLICT (id) DO NOTHING;

SELECT setval('documents_id_seq', (SELECT MAX(id) FROM documents));

-- 9. Insert Audit Logs
INSERT INTO audit_logs (id, user_id, user_email, organization_id, action, entity_type, entity_id, ip_address, old_values, new_values, created_at)
VALUES
(1, 1, 'admin@fintrust.io', 1, 'USER_LOGIN', 'USER', '1', '192.168.1.10', NULL, '{"login_method": "JWT", "device": "Chrome Windows"}', '2026-10-25 09:00:00+00'),
(2, 1, 'admin@fintrust.io', 1, 'TRANSACTION_CREATED', 'TRANSACTION', '1', '192.168.1.10', NULL, '{"ref": "TX-2026-8801", "amount": 125000.00, "currency": "USD"}', '2026-10-25 09:15:00+00'),
(3, 3, 'auditor@fintrust.io', 1, 'LEDGER_VERIFIED', 'LEDGER', '1,2', '192.168.1.45', '{"status": "UNVERIFIED"}', '{"status": "VERIFIED", "balanced": true}', '2026-10-25 09:20:00+00'),
(4, 2, 'compliance@fintrust.io', 2, 'ALERT_UPDATED', 'ALERT', '2', '192.168.1.22', '{"status": "NEW"}', '{"status": "IN_REVIEW", "notes": "Requested corporate source-of-wealth documentation"}', '2026-10-27 15:00:00+00'),
(5, 1, 'admin@fintrust.io', 2, 'TRANSACTION_REJECTED', 'TRANSACTION', '6', '192.168.1.10', '{"status": "PENDING"}', '{"status": "REJECTED", "reason": "Sanctions check positive"}', '2026-10-30 08:25:00+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval('audit_logs_id_seq', (SELECT MAX(id) FROM audit_logs));
