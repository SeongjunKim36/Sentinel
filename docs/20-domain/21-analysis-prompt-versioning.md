# 21. Analysis Prompt Versioning and Rollout

This document describes how Sentinel externalizes LLM prompts and rolls out new prompt versions safely.

## Goals

- Keep prompt text outside Kotlin source code.
- Track the exact prompt version used for each analysis result.
- Allow controlled rollout of new prompt versions before full adoption.

## Template Storage

OpenAI prompt templates are stored in versioned resource directories:

- `src/main/resources/prompts/openai-v1/`
- `src/main/resources/prompts/openai-v2/`

Each version has:

- `system.txt`
- `user.txt`

`user.txt` supports variable placeholders such as:

- `{{eventId}}`
- `{{tenantId}}`
- `{{sourceType}}`
- `{{sourceId}}`
- `{{category}}`
- `{{analyzable}}`
- `{{filterReason}}`
- `{{tags}}`
- `{{payloadJson}}`

## Configuration

Prompt configuration lives under `sentinel.analysis.llm`:

- `prompt-version`: default prompt version
- `prompt-templates`: version -> template resource path mapping
- `prompt-rollout.canary-version`: optional canary version
- `prompt-rollout.canary-percentage`: deterministic rollout percentage (0-100)
- `prompt-rollout.tenant-overrides`: tenant-specific version overrides

## Version Selection Order

For each classified event, Sentinel selects prompt version in this order:

1. Tenant override (`tenant-overrides[tenantId]`) if configured and valid
2. Canary version when rollout bucket falls into `canary-percentage`
3. Default `prompt-version`

If a configured override/canary version is missing from template definitions, Sentinel logs a warning and falls back safely.

## Traceability

The selected version is written to `AnalysisResult.llmMetadata.promptVersion`, so downstream auditing and comparisons can identify exactly which prompt generated each analysis.
