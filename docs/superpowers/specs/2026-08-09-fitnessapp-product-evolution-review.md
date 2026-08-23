# FitnessApp: документ решений по развитию продукта

> **Статус:** review завершён на уровне направления; Stage 2 принят к исполнению
> 2026-08-12; Phase 0 Truth and Recovery реализована и прошла exit gate
> 2026-08-23; следующий engineering scope — Phase 1 Debugger Alpha.
>
> **Дата:** 2026-08-09
>
> **Текущий первый рынок:** люди, регулярно занимающиеся силовыми тренировками и уже фиксирующие питание и тренировки.
>
> **Рабочее направление:** Personal Performance Debugger / Personal Performance Lab.
>
> **Это не implementation plan.** Авторитетное видение находится в `docs/PROJECT_VISION.md`. Канонический порядок Stage 2 и отдельные технические планы зафиксированы в `docs/superpowers/specs/2026-08-12-stage-2-roadmap-design.md` и `docs/superpowers/plans/2026-08-12-phase-*.md`.

> **Терминология Stage 2:** поведение problem workspace из `PRD-001` реализуется как `Investigation`. Имя `Topic` зарезервировано для будущих living research topics/knowledge areas, чтобы не смешивать два разных понятия.

> **Границы статуса:** technical implementation status ведётся в register ниже
> и в Stage 2 roadmap. Пустые исторические поля ответа не отменяют уже принятые
> решения. `PRD-007`/`VAL-001`/`VAL-002` остаются отдельным эмпирическим
> product-validation gate и не могут считаться выполненными на основании
> автоматических тестов.

## Как работать с документом

У каждого решения есть постоянный ID. Под каждым пунктом расположен блок для ответа владельца проекта.

Рекомендуемый формат ответа:

```text
Решение: согласен / частично согласен / не согласен / нужно обсудить
Комментарий: почему
Альтернатива: что предлагается вместо этого
Приоритет: сейчас / позже / никогда
```

Не нужно соглашаться ради движения вперёд. Цель review — найти противоречия до того, как они превратятся в код и миграции.

### Карта решений

| Блок | ID | Что решаем |
|---|---|---|
| Стратегия | `STR-001`–`STR-005` | форма продукта, первый User, promise, moat, invalidating assumption |
| Фундамент | `FND-001`–`FND-006` | FatSecret, privacy, events, recovery, migrations, platform baseline |
| Debugger alpha | `PRD-001`–`PRD-007` | Investigation, Goal, Experiment, adherence, Outcome и первый workflow |
| Knowledge | `KNO-001`–`KNO-005` | canonical claims, RAG projection, provenance, context, contradictions |
| Agent | `AGT-001`–`AGT-005` | Investigator, run state, approvals, prompt security, evaluation |
| Новые сигналы | `SIG-001`–`SIG-007` | Observation contract, sleep, mood, work/focus и wearables |
| Архитектура | `ARC-001`–`ARC-006` | module ownership, reports, public API, Telegram и business domain |
| UX | `UX-001`–`UX-004` | интерфейс решения, evidence, отрицательные результаты, история решений |
| Метрики | `MET-001`–`MET-004` | продуктовый loop, epistemic quality и north-star metric |
| Валидация | `VAL-001`–`VAL-003` | dogfooding, первые семь дней и monetization timing |
| Non-goals | `NOG-001` | что намеренно не строить сейчас |

### Decision register

`Owner` означает продуктовую развилку. `Shared` требует продуктового и инженерного согласия. `Engineering` — рекомендуемый технический default, который можно оспорить, но не обязательно решать до product review.

Для `Engineering`-пункта пустой ответ можно считать согласием с default. Комментарий нужен, если решение кажется неверным, чрезмерным или противоречит желаемому продукту.

| ID | Тип | Рекомендуемое решение | Зависит от | Phase | Когда решать |
|---|---|---|---|---|---|
| `STR-001` | Owner | Performance Debugger | — | Strategy | Сейчас |
| `STR-002` | Owner | Strength-training beachhead | `STR-001` | Strategy | Сейчас |
| `STR-003` | Owner | Promise про одно проверяемое изменение | `STR-001` | Strategy | Сейчас |
| `STR-004` | Shared | Moat = evidence-bearing learning loop | `STR-003` | Strategy | Сейчас |
| `STR-005` | Owner | Проверять готовность завершать цикл и терпеть uncertainty | `STR-001` | Validation | Сейчас |
| `FND-001` | Shared | Принять FatSecret storage/ownership policy | — | 0 | Закрыто 2026-08-23 |
| `FND-002` | Engineering | Versioned PII-safe lifecycle/replay | `FND-001` частично | 0 | Закрыто 2026-08-23 |
| `FND-003` | Engineering | Атомарный state transition + durable intent | `FND-002` | 0 | Закрыто 2026-08-23 |
| `FND-004` | Engineering | Lease fencing и terminal recovery | — | 0 | Закрыто 2026-08-23 |
| `FND-005` | Engineering | Dirty-upgrade fixtures | — | 0 | Закрыто 2026-08-23 |
| `FND-006` | Engineering | Supported platform baseline и честные docs | — | 0 | Закрыто 2026-08-23 |
| `PRD-001` | Owner | Investigation = problem workspace; Topic зарезервирован | `STR-001` | 1 | Следующий scope |
| `PRD-002` | Shared | Один canonical Goal lifecycle | `PRD-001` | 1 | Сейчас |
| `PRD-003` | Shared | One-variable Experiment state machine | `PRD-002` | 1 | Сейчас |
| `PRD-004` | Owner | Adherence обязателен для Evaluation | `PRD-003` | 1 | Сейчас |
| `PRD-005` | Shared | Outcome отдельно от Evaluation; `INCONCLUSIVE` допустим | `PRD-003` | 1 | Сейчас |
| `PRD-006` | Owner | Deterministic/manual alpha, optional grounded AI draft | `PRD-001`–`PRD-005` | 1 | Сейчас |
| `PRD-007` | Owner | Alpha gate с реальными завершёнными циклами | `VAL-001`, `VAL-002` | 1 | Сейчас |
| `KNO-001` | Engineering | PostgreSQL truth, pgvector projection | `PRD-005` | 2 | Позже |
| `KNO-002` | Shared | Typed Claim с trust/provenance | `KNO-001` | 2 | До agent |
| `KNO-003` | Owner | User-visible Memory Inspector | `KNO-002` | 2 | До UX design |
| `KNO-004` | Engineering | Use-case context projections, не god DTO | `KNO-002` | 2 | Позже |
| `KNO-005` | Shared | Показывать contradictions, не решать молча | `KNO-002` | 2 | Позже |
| `AGT-001` | Owner | Первый agent только read-only Investigator | `KNO-004` | 3 | После alpha |
| `AGT-002` | Engineering | Agent Run отделён от chat/insight | `AGT-001` | 3 | Позже |
| `AGT-003` | Owner | Любой write требует preview и approval | `AGT-001` | 3 | Принцип сейчас |
| `AGT-004` | Engineering | Retrieved text всегда untrusted data | `KNO-004` | 3 | До agent |
| `AGT-005` | Shared | Grounding/abstention/safety evaluation suite | `AGT-001` | 3 | До agent release |
| `SIG-001` | Engineering | Typed Observation contribution contract | `PRD-005` | 4+ | Позже |
| `SIG-002` | Shared | Signal admission gate | `STR-003` | 4+ | Принцип сейчас |
| `SIG-003` | Owner | Sleep сначала manual | `SIG-002` | 4 | Позже |
| `SIG-004` | Owner | Mood = sensitive context, не diagnosis | `SIG-002` | 4 | Принцип сейчас |
| `SIG-005` | Owner | Work/focus только под конкретный outcome | `SIG-002` | 5 | Позже |
| `SIG-006` | Shared | Один wearable после manual proof | `SIG-003` | 6 | Позже |
| `SIG-007` | Owner | Источники расширяются по доказанной ценности | `SIG-002` | 4–6 | Сейчас |
| `ARC-001` | Engineering | Сохранить modular monolith/PostgreSQL | — | Все | Default сейчас |
| `ARC-002` | Engineering | Module-owned personal data lifecycle | `FND-002` | 0 | Сейчас |
| `ARC-003` | Engineering | DateRange snapshot contributions | `SIG-001` | 2–4 | При новом source |
| `ARC-004` | Engineering | Сузить AI/API public surface | `ARC-001` | 2 | Позже |
| `ARC-005` | Engineering | Telegram SDK остаётся adapter concern | `PRD-006` | 1 | При alpha commands |
| `ARC-006` | Shared | Product state не принадлежит `ai` | `PRD-003` | 1 | Сейчас |
| `UX-001` | Owner | Главный экран = Decision/Experiment, не dashboard | `STR-003` | 1+ | До UI |
| `UX-002` | Owner | Recommendation Card с evidence/stop condition | `PRD-006` | 1+ | До UI |
| `UX-003` | Owner | Negative/inconclusive result имеет value | `PRD-005` | 1+ | Сейчас |
| `UX-004` | Owner | Decision Archaeology как differentiator | `KNO-002` | 2+ | Позже |
| `MET-001` | Owner | Не оптимизировать chat/insight volume | `STR-003` | Все | Сейчас |
| `MET-002` | Shared | Instrument complete loop | `PRD-003` | 1 | До alpha |
| `MET-003` | Engineering | Измерять epistemic quality | `KNO-002`, `AGT-005` | 2–3 | Позже |
| `MET-004` | Owner | North star = completed evidence-bearing loops | `MET-002` | 1+ | Сейчас |
| `VAL-001` | Owner | Dogfood + 3–5 external target Users | `STR-002` | 1 | Сейчас |
| `VAL-002` | Owner | Ценность до завершения 7–14 дней | `PRD-006` | 1 | Сейчас |
| `VAL-003` | Owner | Monetization после repeat-loop signal | `VAL-001` | Post-alpha | Позже |
| `NOG-001` | Owner | Зафиксировать non-goals | `STR-001` | Все | Сейчас |

### Implementation status register

| Scope | Decision IDs | Статус реализации | Что означает |
|---|---|---|---|
| Phase 0 — Truth and Recovery | `FND-001`–`FND-006` | ✅ Complete, 2026-08-23 | Код, V27–V33, lifecycle/replay, dirty upgrades, privacy, operations и supported Spring baseline прошли полный technical gate |
| Phase 1 — Debugger Alpha engineering | `PRD-001`–`PRD-006`, `MET-002` | ⏭ Next; not implemented | Выполняется по canonical Phase 1 plan, начиная с `Investigation` |
| Phase 1 — product validation | `PRD-007`, `VAL-001`, `VAL-002` | ⏳ Not proven | Требует реальных завершённых циклов и внешних target Users; тестами не закрывается |
| Phase 2 — Trustworthy Personal Context | `KNO-001`–`KNO-005` | Planned; not implemented | Начинается после Phase 1 engineering-ready gate |
| Phase 3+ | `AGT-*`, `SIG-*`, поздние UX/metrics | Deferred | Не входит в текущий Stage 2 execution scope |

Phase 0 evidence by decision:

| ID | Статус | Реализационное доказательство |
|---|---|---|
| `FND-001` | ✅ Complete | ADR-0015 и V32 закрепляют identifier-only FatSecret retention; disconnect/export/delete/replay покрыты PostgreSQL tests |
| `FND-002` | ✅ Complete | Module-owned lifecycle, versioned metadata/lifecycle epoch, safe replay/no-op and complete export/delete coverage |
| `FND-003` | ✅ Complete | Nutrition/workout source state и durable event intent фиксируются атомарно; stale projections fenced |
| `FND-004` | ✅ Complete | V29/V30: owner + monotonic lease generation, guarded outcomes, terminal durable recovery и Telegram `DELIVERY_UNKNOWN` |
| `FND-005` | ✅ Complete | Historical and dirty upgrade fixtures cover immutable migrations through V33 on PostgreSQL |
| `FND-006` | ✅ Complete | Boot 4.1.0, Spring AI 2.0.0, Modulith 2.1.0; dependency analysis and all Maven/privacy gates green |

Документ большой намеренно, но его необязательно заполнять за один проход:

1. Сначала `STR`, `PRD` и `VAL` — кем и каким должен быть продукт.
2. Затем `FND` — какие риски нужно закрыть до реальных пользователей.
3. Затем `KNO` и `AGT` — что считать знанием и насколько автономным будет AI.
4. Потом `SIG` — как именно допускать sleep, mood, work и wearables.
5. В конце `ARC`, `UX`, `MET`, `NOG` и общую последовательность.

### Предлагаемая шкала приоритета

- `P0 / сейчас` — блокирует безопасную работу с реальными пользователями или саму продуктовую модель.
- `P1 / следующий продуктовый slice` — нужен для первого полного Debugger loop.
- `P2 / после проверки loop` — knowledge, agent и trust capabilities.
- `P3 / expansion` — новые domains/providers после прохождения signal admission gate.
- `Не делать` — осознанный non-goal, пока не появится новое доказательство.

---

## 1. Исходный диагноз

FitnessApp уже имеет серьёзную backend-базу: nutrition sync, workout import, заметки, Telegram, AI insights, pgvector memory, durable jobs, outbox, PostgreSQL/Flyway и отдельные test gates. Но продуктовый цикл заканчивается на `observe -> suggest`.

Сейчас отсутствуют:

- явное решение пользователя принять или изменить рекомендацию;
- состояние выполняемого изменения;
- фиксация соблюдения;
- измеримый outcome;
- обновление гипотезы по результату;
- разделение подтверждённых фактов и AI-предположений.

Следовательно, следующий этап развития — не добавление максимального количества источников данных, а создание замкнутого цикла:

```text
наблюдение
  -> пробел данных или гипотеза
  -> одно предложенное изменение
  -> подтверждение пользователем
  -> выполнение
  -> измерение результата
  -> обновление персональных знаний
```

---

## 2. Стратегические варианты

### STR-001 — Выбрать основную форму продукта

#### Вариант A — Performance Debugger, рекомендуется

Приложение помогает найти причину застоя или нестабильного результата, предлагает один ограниченный эксперимент и учится по его outcome.

Плюсы:

- использует уже существующие nutrition/workout history;
- имеет понятный повторяемый workflow;
- создаёт данные, которые со временем трудно скопировать;
- допускает сон, настроение, рабочую нагрузку и wearables как дополнительный контекст;
- не требует сразу строить полноценный tracker для каждого домена.

Риск: пользователь должен согласиться выполнять эксперимент и регулярно отмечать adherence. Без этого цикл не замкнётся.

#### Вариант B — Adaptive Daily Coach

Каждый день приложение выдаёт next-best action по питанию, тренировке и восстановлению.

Плюсы: быстрый ежедневный value moment и высокая частота взаимодействия.

Риски: нужен план будущей тренировки, актуальные данные в течение дня и высокая точность. Прямая конкуренция с MacroFactor, WHOOP, Oura, Fitbod и похожими продуктами.

#### Вариант C — Personal Life/Health OS

Сразу объединить питание, тренировки, сон, работу, настроение и wearables в универсальный граф жизни.

Плюсы: максимальная гибкость и технически интересная платформа.

Риски: нет одного Job To Be Done; сложно объяснить ценность; большой ontology/platform effort до получения продуктового сигнала; самый высокий риск feature soup.

**Рекомендация:** начать с варианта A. Вариант B может стать ежедневным интерфейсом после появления накопленной experiment history. Вариант C допустим только как дальнее архитектурное направление, но не как первый продукт.

#### Ответ владельца по STR-001

- Решение:
- Комментарий:Мне нравится И Вариант А и С - хотел бы их как-то совместить.
- Альтернатива:Возможно например дебаггер как автоновная фича в варинте С?
- Приоритет:

### STR-002 — Зафиксировать первый сегмент

Предлагаемый beachhead:

> Человек 25–40 лет, занимающийся силовыми тренировками 3–5 раз в неделю, уже ведущий питание и тренировочный лог и имеющий конкретную цель: рост силы, рекомпозиция, lean gain или cut без заметной потери performance.

Это не означает, что продукт навсегда останется только для силовых тренировок. Первый сегмент нужен, чтобы у наблюдений, гипотез и outcomes была общая семантика.

Не рекомендуется на первом этапе одновременно обслуживать:

- новичков без истории данных;
- профессиональных тренеров и их клиентов;
- endurance athletes;
- clinical/medical use cases;
- пользователей, которым нужен только calorie counter;
- пользователей, ожидающих полностью автоматически созданную тренировочную программу.

#### Ответ владельца по STR-002

- Решение:
- Комментарий: Тут стоит отталкиваться от Goals
- Альтернатива:
- Приоритет:

### STR-003 — Сформулировать обещание продукта

Рабочая формулировка:

> FitnessApp помогает понять, почему личный прогресс остановился, выбрать одно проверяемое изменение и узнать по собственным данным, сработало ли оно.

Проверка для любой новой функции:

1. Она помогает сформулировать гипотезу?
2. Она помогает принять решение?
3. Она помогает выполнить изменение?
4. Она помогает измерить outcome?
5. Она помогает скорректировать персональную модель?

Если функция не проходит ни один вопрос, она не относится к ядру продукта.

#### Ответ владельца по STR-003

- Решение:
- Комментарий: Это выходит из STR-001
- Альтернатива:
- Приоритет:

### STR-004 — Определить настоящий moat

Не считать moat следующими элементами по отдельности:

- FatSecret integration;
- AI chat;
- RAG или knowledge graph;
- Telegram bot;
- sleep/wearable dashboard;
- большое количество провайдеров моделей.

Предлагаемый moat:

```text
Hypothesis
  -> User Decision
  -> Intervention
  -> Adherence
  -> Outcome
  -> Evidence
  -> Updated Personal Model
```

Ценность растёт с каждым завершённым циклом, потому что приложение накапливает не просто логи, а историю реакции конкретного человека на конкретные изменения.

#### Ответ владельца по STR-004

- Решение:
- Комментарий: Поясни за Moat
- Альтернатива:
- Приоритет:

### STR-005 — Признать главную гипотезу, способную опровергнуть продукт

Главная invalidating assumption:

> Истории пользователя, adherence и окна 7–14 дней достаточно хотя бы для полезного уменьшения неопределённости, а пользователь готов соблюдать протокол и фиксировать outcome.

Если это неверно, Debugger снова станет генератором правдоподобных объяснений.

Поэтому продукт не должен обещать доказанную персональную причинность после одного короткого цикла. Он должен:

- различать exploratory Experiment и повторную проверку;
- показывать baseline, noise и confounders;
- уметь рекомендовать повторение;
- признавать `INCONCLUSIVE`;
- сравнивать outcome только с заранее выбранной metric;
- не менять несколько основных variables одновременно;
- измерять, насколько check-in burden приемлем пользователю.

Раннее исследование должно проверять не «нравится ли идея AI coach», а готовность реальных пользователей завершить такой цикл.

#### Ответ владельца по STR-005

- Решение:
- Комментарий: У меня есть вцелом идея наприсать что-то типо harness , Вот мое вдохновение https://github.com/ShenSeanChen/waku-agent . ЭТО ОЧЕНЬ ВАЖНЫЙ МОМЕНТ!
- Что могло бы опровергнуть направление:
- Приоритет:

---

## 3. Непосредственные блокеры развития — историческое обоснование Phase 0

Все шесть блокеров ниже закрыты 2026-08-23. Формулировки и пустые owner-response
поля сохранены как история принятия решений; актуальное доказательство
выполнения находится в implementation status register и canonical Phase 0 plan.

### FND-001 — Принять решение о правах хранения FatSecret data

Сейчас приложение долговременно хранит nutrition days, food entry names, macros и weights. Публичные FatSecret Terms по умолчанию разрешают хранить больше 24 часов только ограниченный список идентификаторов.

Нужно выбрать и документировать один путь:

1. Получить соглашение, разрешающее текущую модель хранения.
2. Сделать FitnessApp владельцем первичных пользовательских nutrition records, используя FatSecret только для поиска или импорта.
3. Хранить разрешённые идентификаторы и получать остальное повторно.
4. Отказаться от FatSecret как стратегического источника.

Пока решение не принято, нельзя строить долгосрочные experiments и knowledge claims, зависящие от исторических FatSecret-derived данных.

**Exit criteria:**

- решение записано в отдельном ADR;
- определены разрешённые raw и derived fields;
- определён срок хранения;
- disconnect/delete/export соответствуют решению;
- integration tests доказывают выбранную retention policy.

#### Ответ владельца по FND-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### FND-002 — Закрыть account deletion, PII retention и replay

Нужно исключить сохранение raw notes и AI content в долговечных глобальных событиях. Одной ссылки на mutable source недостаточно: replay должен понимать, к какой версии относится intent.

Необходимые изменения:

- event envelope содержит `eventId`, `userId`, `sourceId`, `sourceVersion`, `changeType`, `contentHash`, `lifecycleEpoch` и schema version;
- raw PII остаётся в owning Module либо в короткоживущем module-owned outbox с отдельной retention policy;
- каждый consumer читает конкретную committed source version через разрешённый Interface либо выполняет version-aware state convergence;
- если source удалён, consumer завершает событие безопасным no-op и удаляет/не создаёт projection;
- если source уже новее, consumer не может перезаписать новую projection старой версией;
- immutable/versioned source, module-owned outbox и rebuild-from-current-state являются допустимыми стратегиями; выбор фиксируется отдельно для каждого типа projection;
- account deletion отменяет пользовательские jobs/outbox/publications;
- completed publication имеет явную retention/redaction policy;
- `ai_usage_budget` входит в export/delete contract;
- deletion tombstone или lifecycle epoch запрещает старому event воскресить данные;
- `user_memory` и будущие knowledge tables имеют реальный ownership constraint;
- replay-after-delete, replay старой версии и duplicate delivery после crash проверяются в PostgreSQL integration tests.

**Exit criteria:** после удаления User restart/replay не создаёт ни одной принадлежащей ему записи, а export перечисляет все категории хранимых данных.

#### Ответ владельца по FND-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### FND-003 — Сделать `данные + событие` атомарным workflow

Сохранение nutrition/workout/experiment transition и фиксация события должны находиться в одной надёжной границе. Отдельная `REQUIRES_NEW` публикация после commit оставляет окно безвозвратной потери события.

Целевой принцип:

> Domain state transition и durable intent публикуются атомарно; provider call и Telegram delivery выполняются после commit.

Consumer применяет intent идемпотентно по `eventId + sourceVersion`. Для convergent projection старая версия не может перезаписать более новую.

**Exit criteria:** fault-injection test падает между persistence и dispatch, затем recovery гарантированно доставляет intent ровно в пределах заявленной at-least-once семантики.

#### Ответ владельца по FND-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### FND-004 — Исправить lease/recovery semantics jobs и Telegram outbox

Нужны:

- `claim_token` или `lease_generation`;
- `lease_owner`;
- heartbeat или безопасная максимальная длительность;
- guarded `complete/fail`, проверяющий актуальный claim;
- запрет ручного retry для `RUNNING` и `SUCCEEDED` без отдельного operator workflow;
- terminal state для исчерпанных Telegram attempts;
- stale claimant tests;
- crash на последней попытке;
- два executor, один просроченный lease;
- идемпотентность внешних side effects там, где это возможно.

#### Ответ владельца по FND-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### FND-005 — Проверить реальные upgrade paths

Clean-schema migration tests недостаточны для уже существующей БД.

Нужно добавить fixtures как минимум для:

- исторически невалидных строк перед `CHECK` constraints;
- старых naive timestamps и DST;
- незавершённых event publications;
- старых jobs/outbox rows;
- malformed memory metadata;
- rollback/failure в середине forward-only migration.

Миграции остаются immutable и forward-only. Для больших constraints использовать staged cleanup и, где уместно, `NOT VALID -> VALIDATE`.

#### Ответ владельца по FND-005

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### FND-006 — Обновить platform baseline и документацию

Нужно:

- перейти с завершившей OSS support ветки Spring Boot 3.4.x на поддерживаемую линию;
- проверить совместимость Spring AI и Spring Modulith;
- превратить warnings `dependency:analyze` в честный результат CI;
- синхронизировать `TESTING.md`, stable-base evaluation и backlog;
- перестать обозначать crash/replay свойства как доказанные, пока соответствующих тестов нет.

#### Ответ владельца по FND-006

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 4. Первый продуктовый вертикальный срез: Progress Debugger

### PRD-001 — Ввести Investigation как рабочее пространство проблемы

`Investigation` не является универсальной папкой для любых данных. Это
ограниченный контекст, в котором пользователь пытается понять или изменить
результат. Историческое имя `Topic` зарезервировано для будущих living research
topics/knowledge areas.

Примеры:

- `Deadlift plateau`;
- `Cut without strength loss`;
- `Low energy on morning workouts`;
- в будущем — `Afternoon focus decline` или `Sleep consistency`.

Предлагаемый lifecycle:

```text
OPEN -> INVESTIGATING -> EXPERIMENTING -> RESOLVED | ARCHIVED
```

Investigation содержит ссылки на Goals, Observations, Hypotheses, Experiments,
Decisions и Outcomes, но не владеет их raw source data.

#### Ответ владельца по PRD-001

- Решение:
- Комментарий: Идея ТОпика была в Юзер коновледж Графе , где например есть топики питание тренировки привычки болезни с фактами о конретном юзере. Не уверен что есть смысл в топиках дебаггера, хотя возможно можно модель научить искать проблемы.
- Альтернатива:
- Приоритет:

### PRD-002 — Канонизировать Goal

`GOAL` note остаётся пользовательским narrative, но не является canonical goal.

Минимальный Goal должен иметь:

- тип и пользовательское название;
- целевой metric/range, если применимо;
- status;
- deadline, если применимо;
- priority;
- `createdAt`, `completedAt`;
- source/provenance;
- ссылку на superseded Goal;
- связь с Investigation.

Предлагаемый lifecycle:

```text
DRAFT -> ACTIVE -> PAUSED -> ACHIEVED | ABANDONED | SUPERSEDED
```

В один момент может быть несколько Goals, но должен существовать явный primary или приоритетный набор, а не неявная строка в Profile.

#### Ответ владельца по PRD-002

- Решение:
- Комментарий: Note - факт юзера , аллергии или подобное, Goal - это что хочет пользователь похудеть или просто в норме находиться, набрться силы и тд. Это хотел сделать через @tool для моделей и распределить по категориям
- Альтернатива:
- Приоритет:

### PRD-003 — Создать Experiment state machine Коментарий Выше сначало нужно разьяснить

Минимальный Experiment:

- Investigation и Goal;
- исходная Hypothesis;
- baseline window;
- ровно одна основная Intervention;
- продолжительность;
- adherence protocol;
- primary outcome metric;
- secondary/context metrics;
- stop conditions;
- confounders;
- итоговая Evaluation.

Предлагаемый lifecycle:

```text
DRAFT
  -> PROPOSED
  -> ACCEPTED
  -> ACTIVE
  -> PAUSED | COMPLETED | ABORTED
  -> EVALUATED
```

Каждый переход должен быть идемпотентным, принадлежать Current User и иметь optimistic locking/version.

AI может предложить Experiment, но только User переводит его в `ACCEPTED`.

Для alpha рекомендуется максимум один in-flight Experiment на User. Слот занимают `ACCEPTED`, `ACTIVE` и `PAUSED`; `DRAFT`/`PROPOSED` слот не занимают. Ограничение можно ослабить позже, если реальные сценарии потребуют параллельных независимых Experiments.

Optimistic locking одной строки недостаточен против одновременного принятия двух разных Experiments. Межстрочный инвариант должен обеспечиваться БД — рекомендуемый вариант для alpha: partial unique index по `user_id` для in-flight statuses. Команда принятия дополнительно имеет idempotency key. Альтернатива — отдельная user experiment-slot row с serial lock, если lifecycle усложнится.

#### Ответ владельца по PRD-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### PRD-004 — Фиксировать adherence, а не только outcome

Без знания, выполнялась ли Intervention, нельзя оценивать её эффект.

Минимальная запись adherence:

- scheduled date/time window;
- performed: yes/no/partial;
- фактическое значение, если применимо;
- причина отклонения;
- короткая subjective note;
- source: manual/imported/inferred;
- timestamp и timezone.

Принцип: пропущенная отметка не означает `no`. Она означает `unknown`.

#### Ответ владельца по PRD-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### PRD-005 — Ввести Outcome и Evaluation

Outcome — наблюдаемый результат. Evaluation — вывод о Hypothesis.

Не смешивать:

- `bench top set = 100 x 5 @ RPE 8` — Observation/Outcome;
- `pre-workout carbs improved performance` — Evaluation/Claim;
- `продолжить protocol ещё неделю` — Decision.

Возможные Evaluation statuses:

- `SUPPORTED`;
- `NOT_SUPPORTED`;
- `INCONCLUSIVE`;
- `INVALIDATED_BY_NON_ADHERENCE`;
- `STOPPED_FOR_SAFETY`.

Система обязана уметь завершить эксперимент с `INCONCLUSIVE`. Генерация уверенного положительного вывода не является обязательной.

#### Ответ владельца по PRD-005

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### PRD-006 — Первый пользовательский workflow

Предлагаемый alpha-flow через API и Telegram:

1. User создаёт Investigation и описывает проблему.
2. Детерминированный `AlphaExperimentContext` показывает data coverage и задаёт максимум один уточняющий вопрос за шаг.
3. User формулирует Hypothesis вручную. Optional AI draft существует только за выключенным по умолчанию feature flag, пока не пройдёт минимальный Phase 1 AI safety gate. Полноценный tool-using Investigator в alpha не используется.
4. Система или User создаёт один Experiment proposal.
5. User принимает, редактирует или отклоняет его.
6. Приложение напоминает о минимальном adherence check-in.
7. По завершении собирается Outcome.
8. Evaluation показывает evidence, confounders и что изменило мнение системы.
9. Только завершённая Evaluation позднее становится кандидатом в долговременное персональное знание.

Не генерировать обязательные 2–3 рекомендации. Ценность — в выборе одной проверяемой следующей вещи.

Alpha обязан работать полностью без AI. Для реальных alpha-пользователей optional AI draft включается только после минимального Phase 1 gate:

- free-text Investigation/Notes передаются как untrusted tagged data, а не instructions;
- RAG и прошлые AI insights не используются;
- output проходит domain и safety validation;
- каждый claim draft ссылается на разрешённый `EvidenceRef`;
- medical/unsafe запрос приводит к отказу или безопасной маршрутизации;
- offline fixtures покрывают prompt injection, unsupported claim и insufficient data;
- feature flag позволяет немедленно отключить AI draft.

Это не Agent Run и не зависит от полного KnowledgeClaim graph или Phase 3 tool runtime. Расширенная stored-memory/tool security остаётся в Phase 3.

#### Ответ владельца по PRD-006

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### PRD-007 — Определить продуктовый exit gate alpha

Предлагаемый минимальный gate:

- полный workflow работает без ручного редактирования БД;
- Experiment можно принять, изменить, остановить и завершить;
- proposal/evaluation содержит минимальные `EvidenceRef` и data coverage без полного KnowledgeClaim graph;
- deterministic data-sufficiency rule не позволяет запросить AI draft при критически неполном context;
- optional AI draft не является обязательным для прохождения workflow;
- optional AI draft выключен по умолчанию, пока Phase 1 safety/grounding fixtures не проходят;
- повторная команда не создаёт дубликат transition/outcome;
- account deletion удаляет весь workflow;
- пять целевых пользователей начали Experiment;
- минимум три дошли до Evaluation;
- минимум два захотели запустить следующий Experiment.

Последние три числа — продуктовая гипотеза для обсуждения, а не доказанный стандарт.

#### Ответ владельца по PRD-007

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 5. User Knowledge и память Коментарий : Давай обдумаем этот момент вот вдохновение , может есть еще опенсор варианты? https://github.com/ShenSeanChen/waku-agent

### KNO-001 — Не заменять RAG большим графовым rewrite

Разделить ответственность:

```text
Source Records              Canonical Context
nutrition/workout/notes --> Goal / Claim / Observation / Experiment
        |                              |
        |                              v
        +----------------------> UserContextBundle
                                       ^
                                       |
                              pgvector semantic projection
```

Source records и canonical context являются truth. `user_memory` — rebuildable semantic index.

Не добавлять Neo4j, RDF/OWL, отдельную vector DB или event sourcing до появления измеренного ограничения PostgreSQL.

#### Ответ владельца по KNO-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### KNO-002 — Ввести typed KnowledgeClaim

Минимальный Claim должен хранить:

- subject;
- predicate;
- typed value;
- source type и source ID;
- observed/created time;
- validity interval;
- trust level;
- status;
- confidence basis;
- superseded claim;
- content hash/schema version.

Trust levels должны различать как минимум:

- `USER_CONFIRMED`;
- `IMPORTED_OBSERVATION`;
- `DETERMINISTICALLY_DERIVED`;
- `AI_HYPOTHESIS`;
- `EXPERIMENT_SUPPORTED`;
- `DISPUTED`;
- `STALE`.

AI insight не повышается до факта автоматически.

#### Ответ владельца по KNO-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### KNO-003 — Сделать provenance видимым пользователю

Memory Inspector должен позволять:

- увидеть, что приложение считает известным;
- открыть источник;
- увидеть дату и срок действия;
- понять, это факт, наблюдение или AI-гипотеза;
- подтвердить, исправить, оспорить или забыть запись;
- увидеть, какие Decisions использовали Claim;
- увидеть, какая новая Evidence изменила Claim.

Это продуктовая функция доверия, а не только admin/debug screen.

#### Ответ владельца по KNO-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### KNO-004 — Собирать единый `UserContextBundle`

AI workflow не должен самостоятельно делать несколько несогласованных reads.

Context Bundle должен содержать:

- active Goals;
- Investigation и текущий Experiment;
- verified constraints/allergies/injuries;
- period observations;
- relevant claims с provenance;
- previous decisions и outcomes;
- data coverage/missing fields;
- freshness;
- token budget;
- trust labels.

Deterministic SQL выбирает Goals/Facts/Observations. Semantic search используется только для релевантных narratives и past patterns. Перед prompt выполняются deduplication и trust-aware ranking.

#### Ответ владельца по KNO-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### KNO-005 — Ввести contradiction и drift detection

Полезные случаи:

- новая Goal supersedes старую;
- старая preference больше не соответствует поведению;
- User оспорил AI claim;
- injury/illness делает активный Experiment небезопасным;
- wearable observation противоречит manual observation;
- source перестал обновляться, а Claim всё ещё используется.

Система не должна автоматически решать содержательные противоречия. Она показывает их User или снижает trust/abstains.

#### Ответ владельца по KNO-005

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 6. Agentic roadmap Коментарий: сначало продумаем архитектуру потом как это реализовавывать , но есть мысль добавить Koog Framework для общения с юзером и тул колингом. И попробывать сделать агентов более менее автономными

### AGT-001 — Сначала read-only Investigator

Первый агент получает только allowlisted read tools:

- загрузить Investigation и Goal;
- получить period observations;
- получить relevant claims с provenance;
- проверить data coverage;
- найти contradictions;
- показать прошлые Experiments и Outcomes.

Он не может менять Goal, создавать активный Experiment или отправлять внешние команды без подтверждения.

Результат Investigator:

- найденная проблема или data gap;
- evidence;
- альтернативные объяснения;
- одна Hypothesis;
- один уточняющий вопрос либо предложение Experiment;
- основание для abstention.

#### Ответ владельца по AGT-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### AGT-002 — Отделить agent run от chat и AI insight

Нужны отдельные сущности:

- Agent Run;
- Run Step;
- Tool Request/Result;
- Approval Request/Decision;
- budget/cost;
- status/checkpoint;
- audit record;
- correlation с Investigation/Experiment.

Chat history не является state machine агента. `NutritionInsightResponse` не является контрактом универсального агента.

#### Ответ владельца по AGT-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### AGT-003 — Добавлять write-actions только через approval

Порядок расширения:

1. Read-only investigation.
2. Draft Experiment.
3. User approve/edit/reject.
4. Idempotent creation/transition.
5. Напоминания и check-ins.
6. Только в дальнейших версиях — внешние actions.

Каждый write tool должен иметь:

- ownership check;
- idempotency key;
- preview;
- explicit approval;
- bounded arguments;
- audit trail;
- retry semantics;
- compensating или cancel action, если применимо.

#### Ответ владельца по AGT-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### AGT-004 — Защитить prompts и memory от persistent injection

Нужно:

- отделять instructions от untrusted user/source text;
- передавать notes/memory как tagged data;
- запретить retrieved content задавать tool policy;
- сохранять trust и provenance до prompt renderer;
- тестировать stored injection через Note, AI insight и imported text;
- не исполнять инструкции из external provider payload;
- ограничить tool selection серверной allowlist, а не текстом prompt.

#### Ответ владельца по AGT-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### AGT-005 — Создать AI evaluation suite

Не оценивать качество только валидностью JSON.

Минимальные offline fixtures:

- достаточно данных и простая поддерживаемая Hypothesis;
- недостаточно данных;
- contradictory data;
- non-adherence;
- новая Goal supersedes старую;
- unsafe/medical request;
- stored prompt injection;
- повтор старого AI claim без новой Evidence;
- wrong-user memory attempt;
- provider timeout/fallback.

Rubric:

- grounding;
- correct abstention;
- provenance completeness;
- отсутствие выдуманной причинности;
- одна Intervention;
- safety;
- stable structured output;
- token/cost bounds.

#### Ответ владельца по AGT-005

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 7. Сон, настроение, работа и wearables без feature soup Коментарий: Эти фичи на будущее пока не думаем о них!

### SIG-001 — Ввести единый контракт Observation

Каждый будущий источник преобразует raw data в typed Observation.

Рекомендуемые общие поля:

- `userId`;
- observation kind;
- typed value и unit;
- interval start/end;
- observed time и recorded time;
- source type/reference;
- timezone;
- data quality/completeness;
- transform/schema version;
- optional confidence;
- privacy/retention class.

Raw vendor data остаётся у owning Module. Общий contract не должен превращаться в огромный nullable DTO для всех возможных метрик.

#### Ответ владельца по SIG-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### SIG-002 — Ввести admission gate для нового signal source

Новый источник добавляется только если выполнены все условия:

1. Названо конкретное Decision или Evaluation, которое он улучшает.
2. Определено, как измеряется уменьшение uncertainty или улучшение outcome.
3. Продукт корректно работает при полном отсутствии источника.
4. Есть ownership, consent, export, deletion и retention policy.
5. Определены units, timezone, missingness и quality.
6. Raw external payload не попадает непосредственно в prompt.
7. Есть fixture и PostgreSQL integration test.
8. Есть план отключения/смены provider.

Этот gate важнее количества интеграций.

#### Ответ владельца по SIG-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### SIG-003 — Сон: сначала manual context, затем integrations

Первый sleep slice не требует wearable:

- bedtime/wake time или duration;
- субъективное качество;
- interruptions;
- optional note;
- confidence/source.

Цель — проверить, меняет ли sleep context Evaluation тренировочного Experiment.

После подтверждения ценности можно добавить одну интеграцию. Wearable sleep stages нельзя представлять как медицинскую истину; vendor metrics должны сохранять source и algorithm version, когда это доступно.

Не строить отдельный sleep coach до доказательства отдельного Job To Be Done.

#### Ответ владельца по SIG-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### SIG-004 — Настроение: контекст, не диагноз

Mood tracking может включать:

- valence;
- energy;
- stress;
- motivation;
- optional note;
- связь с Investigation/Experiment.

Ограничения:

- никаких psychiatric diagnosis;
- не объявлять корреляцию причинностью;
- sensitive retention class;
- пользователь контролирует включение mood в AI context;
- кризисные/high-risk формулировки требуют отдельной safety policy, а не обычного fitness prompt.

#### Ответ владельца по SIG-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### SIG-005 — Работа/focus: сначала определить outcome

`Work tracker` слишком неоднозначен. Возможные продукты:

- focus sessions;
- cognitive workload;
- meetings/context switching;
- subjective productivity;
- time allocation;
- влияние работы на тренировочное восстановление.

Рекомендация: первый slice — не общий time tracker, а минимальный
`Workload/Focus Observation`, используемый в конкретном Investigation или
Experiment.

Не добавлять:

- keylogging;
- screen surveillance;
- скрытый employee monitoring;
- сбор содержимого рабочих документов;
- интеграции с календарём только ради красивого dashboard.

#### Ответ владельца по SIG-005

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### SIG-006 — Wearables: adapter, а не новая истина

Последовательность:

1. Доказать ценность manual sleep/mood/readiness.
2. Выбрать один provider по запросам реальных пользователей.
3. Хранить provider-specific raw payload в owning adapter/module с retention policy.
4. Нормализовать только нужные Observations.
5. Версионировать transforms.
6. Показывать source и missing periods.
7. Сравнивать wearable observation с manual report, не молча заменять одно другим.

Не проектировать универсальную wearable abstraction для всех vendors заранее.

#### Ответ владельца по SIG-006

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### SIG-007 — Порядок расширения источников

Рекомендуемый порядок:

1. Nutrition + completed workouts + manual outcome.
2. Manual readiness/energy/sleep quality внутри Experiment check-in.
3. Mood/stress как optional context.
4. Отдельные Sleep Observations.
5. Workload/Focus только для проверенного use case.
6. Один wearable provider.
7. Дополнительные providers после измеренной потребности.

Такой порядок проверяет ценность данных до стоимости интеграции.

#### Ответ владельца по SIG-007

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 8. Архитектурное направление

### ARC-001 — Сохранить modular monolith

Микросервисы не нужны. Целевая форма остаётся Java 21 / Spring Boot modular monolith с PostgreSQL.

Предлагаемая логическая карта:

```text
auth                 identity, Current User, lifecycle orchestration
nutrition            raw nutrition ownership and observations
workout              raw workout ownership and observations
sleep (later)        raw/manual sleep ownership and observations
dailycontext (first) bounded manual sleep/readiness/mood/workload observations
mood/work (later)    separate only after an independent domain/lifecycle appears
wearable adapters    provider-specific ingestion

experiment           Investigation/Hypothesis/Experiment/Decision/Outcome workflow
memory               semantic retrieval projection
ai                   model execution, prompts, routing, validation
analytics            period feature/snapshot assembly
telegram             channel adapter and delivery
api                  neutral cross-module contracts only
```

Отдельный top-level `knowledge` или `context` Module создавать только когда canonical claims перестанут естественно помещаться рядом с experiment/memory. Не создавать его ради красивой схемы.

#### Ответ владельца по ARC-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### ARC-002 — Вернуть personal-data ownership модулям

`auth` оркестрирует account lifecycle, но не знает SQL и таблицы nutrition/workout/ai/memory/telegram.

Каждый owning Module предоставляет узкий contract:

- export owned data;
- erase owned data;
- disconnect external source, если применимо;
- report completion/failure.

Это обязательный extension seam для sleep/work/mood/wearables.

#### Ответ владельца по ARC-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### ARC-003 — Сделать period snapshot расширяемым по источникам

Не умножать `weekly/monthly x nutrition/workout/sleep/mood/work` Interfaces.

Использовать `DateRange` и domain-owned snapshot contributions. Weekly/Monthly остаются разными scheduling/AI policies, но используют согласованный period context.

Новый источник добавляет contribution локально, не меняя каждый существующий event и hasher вручную.

#### Ответ владельца по ARC-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### ARC-004 — Сузить публичную поверхность AI и `api`

Публичными должны быть caller-facing use cases/events. Controllers, JPA entities, repositories, router, guard и provider configuration являются Implementation.

`api` должен содержать только нейтральные cross-module contracts. Provider-specific FatSecret/Scribe implementation не должен жить в глобальном API-модуле.

После определения target dependency DAG добавить `allowedDependencies` или эквивалентные архитектурные tests.

#### Ответ владельца по ARC-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### ARC-005 — Отделить Telegram command kernel от SDK

Telegram adapter должен один раз:

- нормализовать SDK Update;
- проверить private chat;
- разрешить linked Current User;
- превратить вход в typed command.

Application command handlers не должны зависеть от Telegram SDK/JPA entities. Это позволит использовать один Experiment workflow из Telegram, REST и будущего UI.

#### Ответ владельца по ARC-005

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### ARC-006 — Не отдавать продуктовый домен модулю `ai`

Investigation, Goal, Hypothesis, Experiment, Decision и Outcome являются
business state, а не результатами работы модели.

Рекомендуемый первый владелец — focused top-level Module `experiment`. Альтернатива — `performance`, если в нём заранее запрещён рост в generic god-module. Выбор имени вторичен относительно правила:

- workflow работает без AI на deterministic fixtures;
- AI создаёт proposal, но не владеет lifecycle;
- Telegram/REST/UI вызывают один application contract;
- provider replacement не меняет domain state machine.

#### Ответ владельца по ARC-006

- Решение:
- Предпочтительное имя модуля:
- Альтернатива:
- Приоритет:

---

## 9. UX, который выражает уникальность

### UX-001 — Главный экран не должен быть dashboard всех метрик

Предлагаемая иерархия:

1. Current Investigation/Goal.
2. Current Experiment или следующий Decision.
3. Сегодняшний минимальный check-in.
4. Data gaps/contradictions.
5. Supporting metrics.

Nutrition, workout, sleep и mood charts являются evidence views, а не главной навигацией продукта.

#### Ответ владельца по UX-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### UX-002 — Сделать Recommendation Card проверяемой

Карточка должна показывать:

- что предлагается изменить;
- почему именно это;
- evidence sources;
- missing data;
- альтернативные объяснения;
- длительность;
- expected outcome без ложной гарантии;
- stop condition;
- `Accept`, `Edit`, `Reject`.

#### Ответ владельца по UX-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### UX-003 — Сделать отрицательный результат ценным

Завершение `NOT_SUPPORTED` или `INCONCLUSIVE` не должно выглядеть как ошибка.

Интерфейс показывает:

- что было проверено;
- что стало менее вероятным;
- какие confounders обнаружены;
- какие данные теперь не нужно собирать;
- какой следующий вопрос имеет наибольшую ценность.

Это отличает лабораторию от генератора советов.

#### Ответ владельца по UX-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### UX-004 — Decision Archaeology

User должен иметь возможность спросить:

- почему приложение считает Claim верным;
- какие Decisions основывались на нём;
- какой Experiment его поддержал;
- что изменило мнение системы;
- какой источник сейчас отсутствует или устарел.

Это один из наиболее сильных кандидатов на уникальную долгосрочную функцию.

#### Ответ владельца по UX-004

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 10. Метрики продукта и качества

### MET-001 — Не оптимизировать chat/insight volume

Не использовать как north-star:

- количество AI messages;
- число сгенерированных insights;
- количество интеграций;
- размер knowledge graph;
- token consumption;
- число ежедневных notifications.

#### Ответ владельца по MET-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### MET-002 — Измерять замкнутый цикл

Предлагаемые продуктовые метрики:

- доля Investigations, дошедших до Hypothesis;
- acceptance/edit/reject rate Experiments;
- доля Experiments, реально начатых;
- adherence completeness;
- доля Experiments с записанным Outcome;
- доля `SUPPORTED / NOT_SUPPORTED / INCONCLUSIVE`;
- повторный запуск второго Experiment;
- число исправленных/оспоренных Claims;
- время от проблемы до первого проверяемого Decision;
- субъективная полезность результата;
- retention пользователей, завершивших хотя бы один Experiment.

#### Ответ владельца по MET-002

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### MET-003 — Измерять epistemic quality

Технические/AI метрики:

- claim grounding rate;
- provenance coverage;
- correct abstention rate;
- contradiction detection rate;
- duplicate/self-reinforcing context rate;
- data freshness violations;
- prompt-injection resistance;
- cost per completed Experiment, не cost per chat;
- percentage Decisions, использующих AI-only Claim без подтверждения — целевое значение должно стремиться к нулю.

#### Ответ владельца по MET-003

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

### MET-004 — Выбрать north-star metric

Предлагаемая north-star metric:

> Количество завершённых evidence-bearing decision loops на активного пользователя за 28 дней.

Loop считается завершённым только если есть:

- исходная проблема/Goal;
- принятое Decision;
- выполненная или честно невыполненная Intervention;
- adherence data;
- Outcome;
- Evaluation.

В Phase 2 завершённая Evaluation дополнительно должна обновить, отклонить или создать Claim. Это усиление метрики, но не зависимость alpha.

Эта метрика может быть слишком редкой для ежедневного мониторинга, поэтому рядом нужны leading indicators из MET-002. Но она лучше отражает ценность, чем сообщения, графики или generated insights.

#### Ответ владельца по MET-004

- Решение:
- Комментарий:
- Альтернативная north-star metric:
- Приоритет:

---

## 10.1. Проверка продукта с реальными пользователями

### VAL-001 — Начать с dogfooding, но не закончить им

Первая demo Investigation должна быть реальной проблемой владельца проекта,
потому что это ускоряет уточнение workflow. Однако собственное использование
не доказывает рынок.

Предлагаемый validation loop:

1. Провести собственный Experiment от начала до Evaluation.
2. Записать, какие действия пришлось выполнить вне приложения.
3. Дать тот же workflow 3–5 людям из целевого сегмента.
4. Наблюдать за использованием, а не только собирать мнение об идее.
5. Проверить, начинают ли они второй цикл без принуждения.

#### Ответ владельца по VAL-001

- Решение:
- Первая личная Investigation:
- Где найти первых пользователей:
- Приоритет:

### VAL-002 — Определить value в первые семь дней

Experiment может длиться 7–14 дней, поэтому до Outcome пользователь должен получить промежуточную ценность:

- ясное описание проблемы;
- data coverage и missing data;
- очищенный timeline;
- одна понятная Hypothesis;
- простой protocol;
- объяснение, почему не предлагаются остальные изменения.

Нужно отдельно решить cold-start для User без достаточной истории. Рекомендуемый ответ — режим сбора baseline, а не выдуманная персонализация.

#### Ответ владельца по VAL-002

- Решение:
- Какая ценность должна появиться в первый день:
- Допустимая длительность baseline:
- Приоритет:

### VAL-003 — Не фиксировать монетизацию до подтверждения повторного цикла

Рабочие варианты для будущего обсуждения:

- индивидуальная подписка за Debugger/Experiment history;
- premium evidence analysis и дополнительные integrations;
- coach mode с прозрачным просмотром Experiments клиента;
- локальный/private deployment для особенно чувствительных данных.

Сначала нужно доказать, что User завершает цикл и хочет повторить его. Цена не должна компенсировать отсутствие повторяемой ценности.

#### Ответ владельца по VAL-003

- Решение:
- Предпочтительная модель:
- Что точно не хочется монетизировать:
- Приоритет:

---

## 11. Что намеренно не делать сейчас

### NOG-001 — Non-goals до подтверждения первого цикла

- универсальный multi-agent framework;
- автономные write-actions без approval;
- Neo4j/RDF/OWL;
- отдельная vector database;
- полноценный food logger с огромной базой продуктов до FatSecret decision;
- собственный workout planner/program generator;
- social feed;
- marketplace тренеров;
- medical diagnosis;
- полный sleep coach;
- календарь и work tracker общего назначения;
- интеграция сразу со всеми wearables;
- микросервисы;
- мобильный frontend до понимания alpha workflow;
- сложный MoE, если routing не улучшает измеренное качество.

Non-goal можно пересмотреть, но только с новым Job To Be Done и измеримым основанием.

#### Ответ владельца по NOG-001

- Решение:
- Комментарий:
- Альтернатива:
- Приоритет:

---

## 12. Предлагаемая последовательность развития

### Phase 0 — Truth and Recovery

**Статус: ✅ завершена 2026-08-23.**

Состав:

- FND-001–FND-006;
- честная документация доказанных и недоказанных свойств;
- module-owned personal data lifecycle foundation.

Exit gate пройден:

- identifier-only FatSecret policy закреплена ADR-0015 и V32;
- replay-after-delete и stale-version replay завершаются безопасно;
- crash/recovery и atomic publication доказаны PostgreSQL tests;
- stale claimant не может завершить чужой owner/generation lease;
- dirty upgrade fixtures проходят через V33;
- default, integration, architecture, dependency и privacy gates зелёные;
- baseline: Spring Boot 4.1.0, Spring AI 2.0.0, Spring Modulith 2.1.0.

### Phase 1 — Debugger Alpha

Состав:

- Investigation;
- canonical Goal;
- Experiment state machine;
- adherence;
- Outcome/Evaluation;
- минимальный `EvidenceRef` и `AlphaExperimentContext` без knowledge graph;
- deterministic data-sufficiency rules;
- manual-only mode как обязательный default;
- минимальный AI safety/grounding gate перед включением optional AI draft;
- API + Telegram workflow;
- минимум manual readiness/sleep/mood context внутри check-in.
- product instrumentation, recruitment и validation workstream из VAL-001/VAL-002.

На этом этапе readiness/sleep/mood — несколько optional полей конкретного Experiment check-in, а не самостоятельные trackers или универсальная Observation platform.

Exit gate: PRD-007. Временная alpha-метрика — доля начатых Experiments, дошедших до Evaluation, и доля Users, начавших второй цикл. Она не зависит от KnowledgeClaim Phase 2.

### Phase 2 — Trustworthy Personal Context

Состав:

- KnowledgeClaim;
- provenance/trust/supersession;
- UserContextBundle;
- vector memory как projection;
- Memory Inspector;
- contradiction/drift detection;
- rebuild pipeline.

Exit gate:

- любой Claim объясним источником;
- User может исправить/забыть Claim;
- vector store можно полностью перестроить;
- AI insight не становится verified fact автоматически;
- duplicate/self-reinforcing context тестируется.

### Phase 3 — Bounded Agent

Состав:

- read-only Investigator;
- Agent Run/Step/Audit;
- data sufficiency и abstention;
- draft Experiment;
- approval gate;
- AI evaluation suite.

Exit gate:

- ни один write не выполняется без approval;
- каждый tool call user-scoped и auditable;
- stored prompt injection не меняет tool policy;
- budget/deadline покрывает retrieval, embedding и generation;
- offline evaluation проходит установленный threshold.

### Phase 4 — Recovery Context

Состав:

- manual Sleep Observations;
- optional Mood/Stress;
- проверка влияния на реальные Evaluations;
- только после сигнала — один sleep/wearable provider.

Phase 4 начинается, если поля из alpha-check-in доказали полезность. Здесь они становятся first-class, independently editable/exportable Observations с собственным lifecycle.

Exit gate:

- источник изменяет Decision/Evaluation измеримым образом;
- продукт остаётся полезным без источника;
- privacy/delete/export покрыты.

### Phase 5 — Work/Focus и более широкий Personal Performance

Начинать только после формулировки конкретного outcome. Переиспользовать
Investigation/Experiment/Evidence loop, а не строить параллельный productivity
app.

Exit gate:

- существует отдельная проверенная проблема пользователя;
- определены объективные или честные субъективные outcomes;
- work data не используется для surveillance;
- расширение не ухудшает понятность основного продукта.

### Phase 6 — Wearable Ecosystem

Добавлять providers по одному, опираясь на admission gate SIG-002. Не обещать точность или совместимость, которую provider API и data rights не гарантируют.

#### Ответ владельца по общей последовательности

- Решение:
- Комментарий:
- Альтернатива:
- Какой Phase хочется поменять местами:

---

## 13. Технические gates для каждой продуктовой фичи

Каждый vertical slice должен включать:

1. Domain invariants и ownership.
2. Flyway migration без изменения старых миграций.
3. Unit tests state transitions.
4. PostgreSQL integration tests для SQL/event/recovery semantics.
5. MockMvc security/validation tests для endpoint.
6. Architecture gate.
7. Privacy scan.
8. Dependency analysis для platform/dependency изменений.
9. Provider adapters mocked; никаких real API calls.
10. Export/delete coverage.
11. Observability без user-level PII labels/logs.
12. Failure/retry/idempotency test.
13. Документированное product metric событие.

Новая signal integration дополнительно требует:

- missing/partial data fixtures;
- timezone/unit normalization;
- provider disconnect;
- historical backfill behavior;
- retention policy;
- schema/transform versioning.

#### Ответ владельца по техническим gates

- Решение:
- Комментарий:
- Что кажется избыточным:
- Чего не хватает:

---

## 14. Решения, которые после review потребуют ADR

Предлагаемые ADR-кандидаты:

1. Product Core: Experiment/Evidence loop.
2. FatSecret storage and ownership policy.
3. Personal data lifecycle ownership by Module.
4. Canonical User Context vs semantic memory projection.
5. Trust/provenance model for AI-derived claims.
6. Agent approval and tool execution policy.
7. Signal-source admission contract.
8. Experiment transaction and event boundary.
9. Wearable raw-data retention and normalization policy, когда появится первый provider.

ADR создаются после согласования решений, а не вместо обсуждения.

#### Ответ владельца по ADR-кандидатам

- Решение:
- Комментарий:
- Какие ADR объединить или удалить:
- Какие добавить:

---

## 15. Быстрый owner-review

Чтобы не отвечать второй раз на те же вопросы, здесь собраны только ссылки на решения, которые определяют идентичность продукта. Ответы нужно оставлять в соответствующих блоках выше.

| Вопрос | Где ответить |
|---|---|
| Какой продукт строится первым и для кого? | `STR-001`, `STR-002` |
| Готов ли User выполнять один 7–14-дневный protocol и принимать `INCONCLUSIVE`? | `STR-005`, `PRD-005` |
| Какая реальная Investigation станет первым demo? | `VAL-001` |
| Что AI может только предложить, а что способен изменить? | `AGT-001`, `AGT-003` |
| Что является источником истины о User? | `KNO-001`, `KNO-002` |
| Как сон, mood и work допускаются в продукт? | `SIG-002`–`SIG-007` |
| Должен ли Work/Focus стать отдельным направлением? | `SIG-005` |
| Какое первое поведение докажет ценность? | `PRD-007`, `MET-004`, `VAL-002` |
| Какие возможности сознательно исключены? | `NOG-001` |
| Какая FatSecret/data модель допустима? | `FND-001` |

---

## 16. Критерий итогового успеха

Проект можно считать превратившимся в продукт, когда выполняется не только технический, но и поведенческий критерий:

> Пользователь приходит с реальной неопределённостью, принимает одно проверяемое решение, завершает цикл наблюдения и возвращается, потому что приложение помогло ему узнать что-то надёжное о себе.

Уникальность должна проявляться не в количестве модулей, а в качестве персонального learning loop, прозрачности evidence и способности системы честно менять своё мнение.

---

## 17. Исторический code-hotspot snapshot

Этот список зафиксирован на момент review 2026-08-09 и не является текущим
backlog: Phase 0 hotspots уже обработаны. Перед новым plan используйте canonical
roadmap, актуальный source tree и Graphify impact analysis; отсутствие или
перемещение указанного здесь файла не означает новую задачу.

### Privacy и lifecycle

- `src/main/java/com/fit/fitnessapp/auth/application/service/UserDataLifecycleService.java` — текущий cross-module SQL coordinator.
- `src/main/resources/db/migration/V1__init_schema.sql` — `event_publication` и первоначальная ownership schema.
- `src/main/resources/db/migration/V22__create_ai_usage_budget.sql` — user-scoped budget без полного lifecycle.
- `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java` — расширить на publication/replay/budget.

### Event и crash recovery

- `src/main/java/com/fit/fitnessapp/infrastructure/events/TransactionalEventPublisher.java` — транзакционная граница publication.
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java` — source mutation и событие.
- `src/main/java/com/fit/fitnessapp/job/application/service/DurableJobService.java` — lease/fencing/retry.
- `src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramBotService.java` — outbox claim/recovery.
- `src/test/java/com/fit/fitnessapp/job/DurableJobServiceIntegrationTest.java` — stale claimant scenarios.
- `src/test/java/com/fit/fitnessapp/telegram/application/service/TelegramOutboxConcurrencyIntegrationTest.java` — last-attempt crash и fencing.

### AI и context

- `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java` — сейчас объединяет context, prompt, execution, validation и persistence.
- `src/main/java/com/fit/fitnessapp/ai/application/service/AiContextService.java` — retrieval теряет provenance/trust.
- `src/main/java/com/fit/fitnessapp/ai/application/service/WeeklyReportService.java` — duplicated period context.
- `src/main/java/com/fit/fitnessapp/ai/application/service/MonthlyReportService.java` — duplicated period context.
- `src/main/java/com/fit/fitnessapp/ai/application/port/out/AiModelPort.java` — nutrition-specific response для разных AI tasks.
- `src/main/resources/ai/prompts/daily-insight-v1.md` — требования, не всегда поддержанные context data.
- `src/main/resources/ai/prompts/weekly-report-v1.md` — причинные формулировки для observational history.

### Memory и Goal

- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryService.java` — vector retrieval/filtering.
- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryEventListener.java` — AI insight/note projection и raw content.
- `src/main/java/com/fit/fitnessapp/auth/application/service/UserNoteService.java` — narrative Goals/Notes.
- `src/main/java/com/fit/fitnessapp/nutrition/domain/ProfileSummaryDto.java` — второе представление Goal.
- `src/main/java/com/fit/fitnessapp/nutrition/domain/ProfileUpdatedEvent.java` — объявленный, но не завершённый projection workflow.

### Расширение источников и reports

- `src/main/java/com/fit/fitnessapp/analytics/application/WeeklyReportTransactionService.java` — period/source matrix.
- `src/main/java/com/fit/fitnessapp/analytics/application/MonthlyReportTransactionService.java` — period/source matrix.
- `src/main/java/com/fit/fitnessapp/ai/application/service/ReportSnapshotHasher.java` — duplicated source-specific hashing.
- `src/main/java/com/fit/fitnessapp/domain/DateRange.java` — существующая хорошая основа period-neutral contracts.

### Public boundaries и Telegram

- `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java` — сохранить как facade, но сузить public surface вокруг него.
- `src/main/java/com/fit/fitnessapp/api/FatSecretLegacyApi.java` — provider implementation в нейтральном API-модуле.
- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/CommandHandler.java` — SDK-protocol leak.
- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java` — linked-user/context orchestration.
- `src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java` — дополнить target dependency policy после согласования DAG.

---

## Ссылки и исходные документы

- [`CONTEXT.md`](../../../CONTEXT.md)
- [`TESTING.md`](../../../TESTING.md)
- [`docs/STABLE_BASE_BACKLOG.md`](../../STABLE_BASE_BACKLOG.md)
- [`docs/evaluations/stable-base-product-quality.md`](../../evaluations/stable-base-product-quality.md)
- [`ADR-0010: durable jobs and outbox recovery`](../../adr/0010-durable-jobs-and-outbox-recovery.md)
- [`ADR-0011: AI execution bounds`](../../adr/0011-ai-execution-bounds.md)
- [`ADR-0012: derived memory provenance`](../../adr/0012-derived-memory-provenance.md)
- [`ADR-0014: AI report workflow boundaries`](../../adr/0014-ai-report-workflow-boundaries.md)
- [FatSecret Storable Data](https://platform.fatsecret.com/docs/guides/storable-data)
- [FatSecret Platform Terms](https://platform.fatsecret.com/terms)
- [Spring Boot 3.4.13 OSS support notice](https://spring.io/blog/2025/12/18/spring-boot-3-4-13-available-now/)
