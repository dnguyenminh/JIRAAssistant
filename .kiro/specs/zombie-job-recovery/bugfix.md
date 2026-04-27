# Bugfix: Zombie Job Blocks BRD Generation

## Symptom
BRD generation stuck at "AGGREGATING_DATA 10%" on Ticket Intelligence page.
Clicking "Generate BRD" shows progress overlay but never advances past 10%.

## Root Cause
A previous BRD generation job (`feeea8ee`) for ticket ICL2-15 crashed mid-execution
(coroutine lost during AGGREGATING_DATA phase) but its DB status was never updated
from `RUNNING`. This zombie job permanently blocked new generation attempts because
`JobManager.createJob()` checks `findByTicketIdAndTypeActive()` which matches
`status IN ('QUEUED','RUNNING')` — throwing `GenerationLockException`.

## Evidence
- DB query: `generation_jobs` had job `feeea8ee` with `status=RUNNING, progress=10,
  phase=AGGREGATING_DATA` since `2026-04-17T08:50:11Z` (never updated)
- API call to `/api/analysis/ICL2-15/generate-brd` returned 409:
  `"Tài liệu đang được sinh, vui lòng chờ hoàn tất"`

## Fix Applied

### 1. Immediate: Marked zombie job as FAILED in DB
```sql
UPDATE generation_jobs SET status='FAILED', error_message='Stale job recovered'
WHERE job_id='feeea8ee-af7f-4edc-a092-1bc4686b83bf'
```

### 2. Structural: Stale job auto-recovery in `JobManager.kt`
- `createJob()` now detects stale RUNNING jobs (>5 min old) and auto-fails them
- `executeJobSafe()` wrapped with `withTimeout(5 min)` to prevent infinite hangs
- `cancelJob()` now allows cancelling RUNNING jobs (not just QUEUED/PAUSED)

### 3. Test update: `JobTestHelpers.kt`
- Added `CANCELLED` to valid transitions from `RUNNING` state

## Verification
After fix, new BRD job `deac6861` completed successfully (QUEUED → COMPLETED 100%).
