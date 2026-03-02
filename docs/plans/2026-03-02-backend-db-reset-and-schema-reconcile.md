# Backend DB Reset And Schema Reconcile Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Clear all existing MySQL data and recreate the latest full `qforge` schema aligned with code and docs, then seed `admin` test account.

**Architecture:** Use `backend/sql/init-schema.sql` as the canonical full schema because it already merges base schema and migration deltas (`V2` XML stem storage, `V3` decimal difficulty) and matches current entities. Perform reset by dropping/recreating schema in container MySQL, then executing canonical init SQL and verification SQL.

**Tech Stack:** Docker Compose, MySQL 8.4, PowerShell, SQL.

---

### Task 1: Confirm Canonical Schema Source

**Files:**
- Read: `docs/current-database-schema.md`
- Read: `backend/sql/init-schema.sql`
- Read: `backend/services/*/src/main/resources/db/migration/*.sql`
- Read: `backend/services/*/src/main/java/**/entity/*.java`

**Step 1: Compare docs and init SQL**
- Check all table names, key columns, and FK relationships.

**Step 2: Compare migrations against init SQL**
- Ensure V2/V3 deltas are already folded into `init-schema.sql`.

**Step 3: Compare entities against init SQL**
- Ensure field-to-column mapping is consistent for all persisted entities.

### Task 2: Recreate Schema From Scratch

**Files:**
- Use: `backend/sql/init-schema.sql`

**Step 1: Drop and recreate database**
- Run SQL in MySQL container:
```sql
DROP DATABASE IF EXISTS qforge;
CREATE DATABASE qforge CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

**Step 2: Apply full schema + seeds**
- Execute `backend/sql/init-schema.sql` against `qforge`.

### Task 3: Verify Runtime-Ready State

**Files:**
- Verify against: `backend/sql/init-schema.sql`

**Step 1: Verify expected tables exist**
- Check presence of:
`user_account`, `q_question`, `q_tag_category`, `q_tag`, `q_answer`, `q_question_asset`, `q_question_tag_rel`, `q_question_ocr_task`, `q_ocr_task`.

**Step 2: Verify critical columns/fks**
- Confirm `q_question.difficulty` is `DECIMAL(3,2)`.
- Confirm `q_question.stem_image_id` FK points to `q_question_asset(id)` with `ON DELETE SET NULL`.
- Confirm logical-delete columns in `q_question`, `q_answer`, `q_question_asset`.

**Step 3: Verify admin account**
- Confirm one row with `username='admin'` and password preset from seed script.
