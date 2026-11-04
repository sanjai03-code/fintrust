# FinTrust – Financial Transaction and Risk Management System

[![Architecture](https://img.shields.io/badge/Architecture-Microservices-blue.svg)](#architecture)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%2014+-336791.svg)](#database-schema)
[![PHP](https://img.shields.io/badge/PHP-8.2+-777BB4.svg)](#php-microservice)
[![Java](https://img.shields.io/badge/Java-21%20Jakarta%20Servlets-ED8B00.svg)](#java-microservice)
[![Frontend](https://img.shields.io/badge/Frontend-Vanilla%20JS%20%7C%20Bootstrap%205%20%7C%20Chart.js-0066FF.svg)](#frontend-user-interface)

**FinTrust** is an enterprise-grade financial transaction monitoring, double-entry ledger verification, and surveillance risk management platform. Built using a microservice architecture with PHP 8+ and Java Jakarta Servlets connected through RESTful APIs and shared HMAC SHA-256 JWT security tokens.

---

## 1. System Architecture

```
                                  +---------------------------------------+
                                  |         FinTrust Web Frontend         |
                                  |   HTML5 / Vanilla JS / Bootstrap 5   |
                                  |         Chart.js Visualizations       |
                                  +---------------------------------------+
                                                      |
                                                      | HTTP / REST (JWT Bearer)
                                                      v
                                  +---------------------------------------+
                                  |         Unified API Gateway           |
                                  |   Reverse Proxy & Route Dispatcher    |
                                  +---------------------------------------+
                                          /                       \
                      /api/auth, /api/orgs,                       /api/transactions,
                      /api/documents, /api/reports                /api/ledger, /api/risk,
                                        /                           /api/audit
                                       v                             v
                   +---------------------------+       +---------------------------+
                   |       PHP Service         |       |      Java Service         |
                   |   PHP 8+ / PDO / REST     |       |   Jakarta Servlets / JDBC |
                   |  (Port: 8000)             |       |  (Port: 8080)             |
                   |                           |       |                           |
                   | - Auth & Users (JWT)      |       | - Transaction Processing  |
                   | - Organization Roster     |       | - Double-Entry Ledger     |
                   | - S3 Document Vault       |       | - Risk Engine (0-100)     |
                   | - Reporting & Analytics   |       | - Immutable Audit Trail   |
                   +---------------------------+       +---------------------------+
                                        \                             /
                                         \                           /
                                          v                         v
                                  +---------------------------------------+
                                  |      PostgreSQL Database (14+)        |
                                  |  (Tables: users, orgs, transactions,  |
                                  |   ledger_entries, risk_scores,        |
                                  |   alerts, audit_logs, documents)      |
                                  +---------------------------------------+
                                                      |
                                                      v
                                  +---------------------------------------+
                                  |    S3-Compatible Storage Vault        |
                                  |   SHA-256 Checksums & Encrypted Store |
                                  +---------------------------------------+
```

---

## 2. Technology Stack

### Frontend
- **HTML5 & CSS3**: Custom financial SaaS design system with CSS custom properties, responsive grid, and typography (`Inter` and `JetBrains Mono`).
- **Vanilla JavaScript (ES6+)**: Zero framework bloat. Modular client architecture (`api.js`, `auth.js`, `dashboard.js`, `organizations.js`, `transactions.js`, `ledger.js`, `risk.js`, `audit.js`, `reports.js`).
- **Bootstrap 5.3**: Responsive cards, modals, tables, and form components.
- **Chart.js 4+**: Real-time transaction velocity trends, risk tier distributions, and debit/credit bar charts.

### PHP Backend Microservice (Port 8000)
- **PHP 8+**: Native object-oriented REST architecture.
- **PDO**: Prepared statements, transaction isolation, and secure SQL binding.
- **JWT (HS256)**: Shared HMAC SHA-256 token encoding and validation.
- **S3 Storage Vault**: S3-compatible document store with SHA-256 checksum generation for invoices and contracts.

### Java Backend Microservice (Port 8080)
- **Java 21**: Jakarta Servlet 6.0 standards.
- **JDBC**: High-performance database operations and connection management.
- **Maven**: Standard `pom.xml` layout and `web.xml` configuration for Apache Tomcat deployment.
- **Standalone Runner**: Embedded HTTP server enabling zero-dependency instant execution via JDK standard libraries.

### Database & Storage
- **PostgreSQL**: Production-grade relational database with foreign keys, constraints, and index optimization.
- **SQLite Engine**: Integrated, zero-config local fallback allowing testing without requiring external database provisioning.
- **S3 Architecture**: S3 key paths, SHA-256 integrity verification, and bucket abstraction.

---

## 3. Database Schema

The database includes 9 core tables:

| Table | Description |
|---|---|
| `users` | Accounts, bcrypt password hashes, department, and role-based permissions (`ADMIN`, `COMPLIANCE_OFFICER`, `AUDITOR`, `MANAGER`, `ANALYST`). |
| `organizations` | Registered corporate entities, tax IDs, country jurisdiction, base currency, and annual turnover. |
| `employees` | Maps users to organizations with specific designations, departments, and entity-level roles. |
| `transactions` | Core financial transactions (`TRANSFER`, `PAYMENT`, `WITHDRAWAL`, `DEPOSIT`), amounts, accounts, corridor country, and status. |
| `ledger_entries` | Strict double-entry accounting records (`DEBIT` and `CREDIT` entries) with post-transaction balances and verification status. |
| `risk_scores` | Rule-based calculated risk score (0 to 100), risk tier (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), and factors breakdown JSON. |
| `alerts` | Suspicious activity and compliance alerts triggered by the surveillance engine. |
| `audit_logs` | Immutable audit trail capturing user IDs, actions, entity types, IP addresses, and JSON before/after state diffs. |
| `documents` | S3-compatible metadata registry storing document categories, storage paths, S3 keys, and SHA-256 checksums. |

---

## 4. Default Seed Users & Credentials

Local demo accounts use the standard password: `Admin@123`. Production deployments do not seed these accounts unless explicitly configured.

| User | Role | Email | Responsibilities |
|---|---|---|---|
| **Alexander Wright** | `ADMIN` / Chief Risk Officer | `admin@fintrust.io` | Superuser administration & policy enforcement |
| **Elena Rostova** | `COMPLIANCE_OFFICER` | `compliance@fintrust.io` | Surveillance alert resolution & CTR compliance |
| **Marcus Vance** | `AUDITOR` | `auditor@fintrust.io` | Double-entry ledger audit & reconciliation |
| **Sarah Jenkins** | `MANAGER` | `manager@fintrust.io` | Treasury & corporate entity management |
| **David Kim** | `ANALYST` | `analyst@fintrust.io` | Transaction surveillance & intake |

---

## 5. Quick Start & Execution

### Option A: One-Click Launcher (Windows)

Double click `start-all.bat` or run in terminal:
```cmd
start-all.bat
```
Or with PowerShell:
```powershell
.\start-all.ps1
```

### Option B: Manual Step-by-Step Execution

#### 1. Initialize Database & Migrations
```bash
php database/migrate.php
```

#### 2. Run Java Microservice (Port 8080)
```cmd
cd java-service
build.bat
run.bat
```

#### 3. Run PHP Service & Web Portal (Port 8000)
```bash
php -S 127.0.0.1:8000 php-service/public/index.php
```

#### 4. Access FinTrust
Open your browser to: **`http://127.0.0.1:8000`**

---

## 6. API Route Inventory

### PHP Service (`http://127.0.0.1:8000`)
- `POST /api/auth/login` – Issue JWT authentication token
- `POST /api/auth/register` – Register new user
- `GET  /api/auth/me` – Current user profile
- `GET  /api/auth/users` – List registered users
- `GET  /api/orgs` – List organizations with employee counts & volume
- `POST /api/orgs` – Create new corporate organization
- `GET  /api/orgs/{id}/employees` – Organization member roster
- `POST /api/orgs/{id}/employees` – Assign employee to organization
- `GET  /api/documents` – List S3 vault documents
- `POST /api/documents/upload` – Upload document to S3 vault
- `GET  /api/reports/summary` – Aggregate KPI metrics & trend charts
- `GET  /api/reports/compliance` – Compliance audit statement

### Java Service (`http://127.0.0.1:8080`)
- `GET  /api/transactions` – Filterable transaction feed
- `POST /api/transactions` – Post transaction, compute risk score, and generate double-entry ledger records
- `PUT  /api/transactions/{id}/status` – Transition status (`COMPLETED`, `FLAGGED`, `REJECTED`)
- `GET  /api/ledger` – Double-entry ledger postings
- `POST /api/ledger/verify` – Automated reconciliation & duplicate detection algorithm
- `GET  /api/risk/alerts` – Suspicious transaction alerts feed
- `POST /api/risk/alerts/{id}/resolve` – Review and resolve compliance alert
- `POST /api/risk/evaluate` – Pre-execution risk score calculation
- `GET  /api/audit` – Immutable audit trail & state diff records

## 7. Deploying on Render

The root `render.yaml` provisions a PHP/Java Docker web service and PostgreSQL. Create a Render Blueprint from the public GitHub repository and review the plan and pricing before applying it. Render generates the production JWT secret automatically; do not commit `.env` or paste secrets into the repository.

Production migrations do not load the documented demo accounts or records by default. New public registrations are limited to the `ANALYST` role. This project is a demonstration; do not store real financial or personal data. Uploaded documents use local container storage and are not durable across redeploys.

The free PostgreSQL plan is temporary and may expire; use a persistent paid database for a lasting deployment.
