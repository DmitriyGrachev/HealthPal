# Telegram Bot — Implementation Plan

## Module Location
com.fit.fitnessapp.telegram

## Package Structure
telegram/
├── package-info.java              (@ApplicationModule)
├── adapter/in/TelegramUpdateHandler.java
├── api/
│   ├── TelegramNoteRequestedEvent.java
│   ├── TelegramWeightRequestedEvent.java
│   ├── TelegramAskRequestedEvent.java
│   └── TelegramAiResponseEvent.java
├── application/
│   ├── port/in/TelegramNotificationUseCase.java
│   └── service/
│       ├── TelegramBotService.java
│       ├── ConversationStateService.java
│       ├── TelegramNotificationService.java
│       └── handlers/
│           ├── StartCommandHandler.java
│           ├── TodayCommandHandler.java
│           ├── NoteCommandHandler.java
│           ├── WeightCommandHandler.java
│           ├── AskCommandHandler.java
│           └── WeekCommandHandler.java
├── domain/
│   ├── ConversationState.java     (enum: IDLE, WAITING_NOTE_TYPE,
│   │                               WAITING_NOTE_CONTENT, WAITING_CHECKIN)
│   └── TelegramUser.java
└── infrastructure/
├── config/TelegramBotConfig.java
├── persistence/
│   ├── entity/TelegramUserEntity.java
│   ├── entity/ConversationStateEntity.java
│   └── repository/TelegramUserRepository.java
└── scheduler/TelegramScheduler.java

## Events Published by Telegram Module
- TelegramNoteRequestedEvent → auth module listens
- TelegramWeightRequestedEvent → nutrition module listens
- TelegramAskRequestedEvent → ai module listens

## Events Telegram Module Listens To
- InsightGeneratedEvent (from ai) → send to user
- UserNoteCreatedEvent (from auth) → confirm to user
- TelegramAiResponseEvent (from ai) → send answer to user

## Commands
/start   → check link status, show menu
/link    → bind telegram to app account
/today   → get/generate daily insight
/week    → get/generate weekly insight
/note    → multi-turn: save user note with type
/weight  → record weight entry
/ask     → free question with full context
/memory  → show what AI knows about user

## DB Migrations Needed
V8: telegram_users table
V9: conversation_history table  
V10: conversation_state table

## Implementation Order
Step 1: Migrations + entities + repositories
Step 2: TelegramBotConfig (polling mode)
Step 3: ConversationStateService
Step 4: TelegramUpdateHandler (routing)
Step 5: /start and /link handlers
Step 6: /today handler + TelegramNotificationListener
Step 7: /note handler (multi-turn)
Step 8: /weight handler
Step 9: /ask handler with memory context
Step 10: Morning scheduler
Step 11: Tests

## Current Step
STEP 1 — Not started