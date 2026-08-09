# Enforce Memory Isolation With Server-Side User Id

User Memory retrieval must be isolated by a server-side User id, not by caller-supplied metadata or prompt content. Memory can influence AI guidance only after the application has resolved which User owns the request.

## Considered Options

- Let prompts or request payloads describe which memories to search, which is flexible but lets user-controlled text affect isolation.
- Apply a server-side `user_id` filter before returning memories, which is less flexible but protects cross-user privacy.

## Consequences

Memory queries should always include a server-controlled User filter. Tests for memory and AI context should prove that another User's memories are not retrieved, even when prompt text or metadata-like input asks for them.
