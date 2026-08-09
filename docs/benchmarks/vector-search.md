# Vector Search Benchmark Harness

`MemoryPgVectorIntegrationTest.exactSearchCapacityHarnessMeetsDocumentedLatencySlo` is the repeatable PostgreSQL harness for vector capacity work.

The harness loads **512 memories per user**, runs a warm-up and 20 exact cosine searches, and enforces a **p95 SLO of 1,000 ms**. It records p50/p95 in the test run without logging user content. This is the current production-shaped capacity contract for the exact-scan baseline; a larger corpus or stricter SLO requires a new benchmark decision.

`MemoryPgVectorIntegrationTest.hnswCapacityHarnessDocumentsPgvectorDimensionLimit` also verifies the current pgvector limit: HNSW cannot index `vector(2048)` because pgvector caps HNSW dimensions at 2000. The runtime intentionally keeps `index-type=NONE`. Before enabling approximate search, choose and benchmark one of: a lower embedding dimension, `halfvec`, or an IVFFlat strategy compatible with the chosen model.
