# FitnessApp Context

FitnessApp helps a User combine nutrition, workout, personal notes, Telegram interactions, and AI-generated guidance into a private fitness record.

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
The act of importing or refreshing nutrition and weight data for a User from FatSecret.
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

### Memory

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
