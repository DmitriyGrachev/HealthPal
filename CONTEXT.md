# FitnessApp Context

FitnessApp helps a User investigate progress, run a controlled Experiment, evaluate evidence and retain
explainable personal knowledge. The deterministic workflow works without AI. The approved engineering
scope and execution order are in `docs/superpowers/specs/2026-08-12-stage-2-roadmap-design.md`.

## Language

### Identity

**User**:
The person whose fitness data, connected accounts, memories, and AI insights belong together in FitnessApp.
_Avoid_: Customer, account, member.

**Current User**:
The User resolved from the current authenticated web request or linked Telegram conversation.
_Avoid_: Principal, subject.

**Role**:
A permission level that decides whether a User can access ordinary personal workflows, privileged imports, or administrative batch workflows.
_Avoid_: Tier, plan.

**User Note**:
A dated piece of personal context supplied by a User, such as a goal, preference, allergy, illness, travel, injury, or mood note.
An old goal-shaped note is narrative context, not a canonical Goal or automatically verified constraint.
_Avoid_: Memo, journal entry.

### Nutrition

**FatSecret Connection**:
The external nutrition account link that allows FitnessApp to read or update a User's FatSecret data.
_Avoid_: Integration token, nutrition account.

**Nutrition Day**:
A User's food and macro summary for one calendar day.
_Avoid_: Daily diet, food log.

**Nutrition Month**:
A User's nutrition aggregate for a calendar month, including day-level breakdown when available.
_Avoid_: Monthly diet, report.

**Nutrition Sync**:
An explicit refresh of permitted identifiers for the current FatSecret Connection. Restricted provider
nutrition and weight content is not imported into durable canonical data; immediate provider reads are ephemeral.
_Avoid_: Scrape, fetch job.

### Workout

**Workout Session**:
A completed training session belonging to one User, with exercises and set-level effort.
_Avoid_: Training, exercise log.

**Workout Import**:
The workflow that turns an uploaded external workout file into one or more Workout Sessions for the Current User.
_Avoid_: Upload, migration.

**Workout Summary**:
An aggregate view of a User's Workout Sessions over a period, such as weekly volume or session count.
_Avoid_: Analytics, stats.

### Investigation and Experiment

**Investigation**: a bounded progress problem workspace; not the future broader research Topic.

**Goal**: an owned, versioned target with a metric, target range and lifecycle. Only one primary Goal
can be ACTIVE per User. A note mentioning a goal does not create one.

**Experiment**: one controlled intervention with a hypothesis, baseline, duration, primary metric
and stop conditions. ACCEPTED, ACTIVE and PAUSED share the User's single in-flight slot.

**Check-in**: adherence (`YES`, `NO`, `PARTIAL`, `UNKNOWN`) and optional bounded context for an
Experiment date. A missing check-in is unknown, not non-adherence.

**EvidenceRef**: an owned source identity with version, content hash and observation time; not copied
provider content or a model's confidence score.

**Evaluation**: a deterministic result with calculation inputs, coverage, adherence, freshness and
confounder reason codes. Its recommendation is `KEEP`, `MODIFY`, `DROP` or `INCONCLUSIVE`.

**Decision**: the User's explicit choice following Evaluation. AI never makes the lifecycle transition
or final Decision. Optional cited Claims are checked and recorded with their exact versions/hashes.

### Knowledge and Memory

**KnowledgeClaim**: a typed, owned assertion with provenance, observation/validity time, verification,
temporal status and supersession history. PostgreSQL is canonical; an AI hypothesis starts PROPOSED.

**Claim usage**: a record of the exact Claim version/hash declared used by a durable Decision or AI
output. Retrieval alone is not usage; a model citation is not proof of its internal reasoning.

**Context**: purpose-specific canonical slices with missingness, freshness and trust labels. Optional
semantic narratives cannot override constraints or promote hypotheses into verified facts.

**Memory projection**: a rebuildable semantic search representation of canonical Claims. An ACTIVE
projection generation is a search index, not another source of truth.

**User Memory**:
Retained personal context for one User that can be retrieved later to improve AI guidance.
_Avoid_: Vector row, embedding document.

**Memory Horizon**:
How long a User Memory is expected to remain useful: short-term, mid-term, or long-term.
_Avoid_: TTL, expiration policy.

**Memory Isolation**:
The rule that a User can only retrieve or influence their own User Memories.
_Avoid_: Tenant filter, metadata check.

### Telegram

**Telegram Link**:
The relationship between a Telegram identity/chat and a FitnessApp User.
_Avoid_: Telegram login, bot session.

**Telegram Link Code**:
A short-lived code used to prove that a Telegram identity should be linked to a FitnessApp User.
_Avoid_: Password, token, invite.

**Telegram Command**:
A user-facing bot instruction such as link, ask, note, today, or weight.
_Avoid_: Message handler, bot action.

### AI Guidance

**AI Insight**:
Generated fitness guidance for a User based on nutrition, workout, notes, and memory context.
_Avoid_: Completion, response.

**Daily Insight**:
An AI Insight scoped to one calendar day.
_Avoid_: Daily report.

**Weekly Report**:
An AI Insight that summarizes a User's week across nutrition and workout data.
_Avoid_: Weekly job, week endpoint.

**Monthly Report**:
An AI Insight that summarizes a User's month across nutrition and workout data.
_Avoid_: Monthly job, month endpoint.

**Telegram AI Reply**:
The answer sent back to a linked Telegram conversation after a User asks a fitness question.
_Avoid_: Bot response, chat completion.
