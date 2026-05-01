# Database Schema — skip-tracing-workflow

**Last Updated:** 2026-04-30T09:34:47.467Z

## Data Sources

| Name | Type | Host | Database | Access |
|------|------|------|----------|--------|
| primary-db | PostgreSQL | localhost:5432 | skiptracing | read-write |

## primary-db (PostgreSQL)

### Schemas

| Schema | Tables | Description |
|--------|--------|-------------|
| public | 2 | Main application schema |

### Tables — public schema

| Table | Columns | Rows (approx) | Description |
|-------|---------|---------------|-------------|
| users | 3 | 1500 | Application users |
| cases | 3 | 50000 | Skip tracing cases |

### Table Details

#### users

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | bigint | NO | nextval() | Primary key |
| email | varchar(255) | NO | — | User email |
| created_at | timestamp | NO | now() | Creation timestamp |

#### cases

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | bigint | NO | nextval() | Primary key |
| user_id | bigint | NO | — | FK to users |
| status | varchar(50) | NO | OPEN | Case status |
