# Charter

## Metadata

| Field | Value |
|-------|-------|
| Rulebook Name | Java and .NET Modernization Rulebook |
| Version | 1.0.0 |

## Scope

### Covered Applications and Languages

Java and .NET applications are in scope for modernization.

## Modernization Strategy (6R Guidelines)

Of the 6R strategies, this rulebook covers **Rehost**, **Replatform**, and **Refactor** — the three that involve app modernization. Retire, Retain, and Repurchase are outside the scope of app modernization.

Supported strategies: **Rehost** (lift-and-shift, no code changes), **Replatform** (minimal code changes — containerize, adopt managed services), **Refactor** (modify code/architecture — decompose, upgrade).

| Application Type | Default Strategy | Override Conditions |
|------------------|-----------------|---------------------|
| Java | Refactor | Use latest LTS runtime as upgrade target |
| .NET | Refactor | Use latest LTS runtime as upgrade target |

## Principles

- Modernization conventions apply to all Java and .NET applications in scope.
- Runtime upgrades must target the latest LTS release.
