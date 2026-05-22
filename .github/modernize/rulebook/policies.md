# Policies

Enforceable standards and hard boundaries for .NET and Java modernization. Every policy here is validatable against generated artifacts.

## Guardrails (Hard Boundaries)

### Prohibited Patterns

| Pattern | Reason | Approved Alternative |
|---------|--------|---------------------|
| Breaking public API contracts | Public APIs must remain backward compatible across modernization changes | Additive changes only; use versioning for breaking changes |

## Validation & Quality Gates

### Pipeline Gates

- Pull requests must include unit tests.
