# CI/CD Workflow Guide

This document describes the complete CI/CD pipeline for this project.

## Workflows Overview

| # | Workflow | Trigger | Purpose |
|---|---|---|---|
| 1 | `ci.yml` | Pull requests only | Quality gate before merge |
| 2 | `snapshot.yml` | Push to `main` or `release/*` | Publish SNAPSHOT to Maven Central |
| 3 | `release.yml` | Manual (`workflow_dispatch`) | Stable or pre-release (beta, RC) |
| 4 | `feature-snapshot.yml` | Push to `feat/*` or `feature/*` | Publish feature branch SNAPSHOT |

## Workflow Details

### 1. CI (`ci.yml`)

**Trigger:** `pull_request` (any branch, any PR)

**What it does:**
- Runs `./gradlew quality` (all tests + coverage)

**Purpose:** Gate that blocks merging until all tests pass and coverage meets the 100% threshold.

---

### 2. Snapshot (`snapshot.yml`)

**Trigger:** Push to `main` or `release/*` (excluding version tags)

**What it does:**
1. Resolves the snapshot version from `gradle.properties` (source of truth)
2. Falls back to deriving from the last git tag if `gradle.properties` is not a SNAPSHOT
3. Runs `./gradlew quality`
4. Publishes the SNAPSHOT to Maven Central

**Version resolution logic:**
```bash
# Primary: read from gradle.properties
VERSION=$(grep '^version=' gradle.properties | cut -d= -f2)

# If it ends with -SNAPSHOT, use it directly
# Otherwise, derive from last tag: vX.Y.Z -> X.Y.(Z+1)-SNAPSHOT
```

**Why no duplication with CI:** `ci.yml` only runs on PRs. `snapshot.yml` only runs on push. They never fire for the same event.

---

### 3. Release (`release.yml`)

**Trigger:** Manual (`workflow_dispatch`)

**Inputs:**
| Input | Required | Default | Description |
|---|---|---|---|
| `version` | Yes | — | Version string (e.g., `1.0.0`, `1.0.0-beta.1`, `1.0.0-rc.1`) |
| `branch` | No | `main` | Branch to release from |

**What it does (stable release `X.Y.Z`):**
1. Validates version format (must be `X.Y.Z`)
2. Checks tag does not already exist
3. Runs `./gradlew quality`
4. Generates CHANGELOG with git-cliff
5. Commits `CHANGELOG.md` to the target branch + creates annotated tag `vX.Y.Z`
6. Bumps `gradle.properties` to `X.Y.(Z+1)-SNAPSHOT` and commits
7. Publishes `X.Y.Z` to Maven Central
8. Creates GitHub Release with changelog body

**What it does (pre-release `X.Y.Z-beta.N` or `X.Y.Z-rc.N`):**
1. Validates version format
2. Runs `./gradlew quality`
3. Creates annotated tag `vX.Y.Z-beta.N`
4. Publishes to Maven Central
5. Creates GitHub Release marked as **Pre-release**

**Pre-releases do NOT:**
- Modify `CHANGELOG.md`
- Bump `gradle.properties`
- Commit anything to the branch

---

### 4. Feature Snapshot (`feature-snapshot.yml`)

**Trigger:** Push to `feat/*` or `feature/*` branches

**What it does:**
1. Reads base version from `gradle.properties`
2. Sanitizes branch name (e.g., `feat/v2-api` → `v2-api`)
3. Publishes as `{BASE}-{branch}-SNAPSHOT` (e.g., `0.1.0-v2-api-SNAPSHOT`)
4. Runs `./gradlew quality`
5. Publishes to Maven Central

**Purpose:** Allows long-running feature branches to publish testable artifacts without affecting `main` snapshots.

---

## Complete Workflow Scenarios

### Scenario 1: Incremental development on `main`

```
gradle.properties: version=0.1.0-SNAPSHOT

PR: feat/add-or-support
  └── ci.yml: quality ✅ → merge
      └── snapshot.yml: publishes 0.1.0-SNAPSHOT

PR: fix/path-resolution
  └── ci.yml: quality ✅ → merge
      └── snapshot.yml: publishes 0.1.0-SNAPSHOT (overwrite)
```

### Scenario 2: Feature branch (long-running)

```
main: 0.1.0-SNAPSHOT
  └── git checkout -b feat/v2-api
      ├── push → feature-snapshot.yml: publishes 0.1.0-v2-api-SNAPSHOT
      ├── more commits and pushes...
      └── PR → ci.yml: quality ✅ → merge to main
          └── snapshot.yml: publishes 0.1.0-SNAPSHOT
```

### Scenario 3: Beta release

```
[manual] release.yml
  Inputs: version="0.2.0-beta.1", branch="main"
  ├── Validates format ✅
  ├── Quality ✅
  ├── Creates tag v0.2.0-beta.1
  ├── Publishes 0.2.0-beta.1 to Maven Central
  └── GitHub Release (Pre-release badge)
```

You can iterate `beta.1`, `beta.2`, `beta.3`... without touching `main` or `gradle.properties`.

### Scenario 4: Release Candidate

```
[manual] release.yml
  Inputs: version="0.2.0-rc.1", branch="main"
  ├── Validates format ✅
  ├── Quality ✅
  ├── Creates tag v0.2.0-rc.1
  ├── Publishes 0.2.0-rc.1 to Maven Central
  └── GitHub Release (Pre-release badge)
```

### Scenario 5: Stable release (patch / minor / major)

```
[manual] release.yml
  Inputs: version="0.2.0", branch="main"
  ├── Validates format ✅
  ├── Quality ✅
  ├── Generates CHANGELOG (commits since last tag)
  ├── Commits CHANGELOG.md + tag v0.2.0
  ├── gradle.properties → 0.2.1-SNAPSHOT
  ├── Commits version bump
  ├── Publishes 0.2.0 to Maven Central
  └── GitHub Release (stable, with changelog)

  The bump commit triggers snapshot.yml:
    └── Reads gradle.properties → 0.2.1-SNAPSHOT → publishes
```

### Scenario 6: Hotfix on an older version

```
main: 0.3.0-SNAPSHOT (current development)
release/0.1.x: created from v0.1.0, gradle.properties: 0.1.1-SNAPSHOT

  Fix applied on release/0.1.x
    └── PR → ci.yml: quality ✅ → merge to release/0.1.x
        └── snapshot.yml: publishes 0.1.1-SNAPSHOT

  [manual] release.yml
    Inputs: version="0.1.1", branch="release/0.1.x"
    ├── Quality ✅ (runs on release/0.1.x)
    ├── CHANGELOG generated (commits on release/0.1.x since v0.1.0)
    ├── Commits CHANGELOG + tag v0.1.1 on release/0.1.x
    ├── gradle.properties → 0.1.2-SNAPSHOT on release/0.1.x
    ├── Commits bump
    ├── Publishes 0.1.1 to Maven Central
    └── GitHub Release

  main is untouched. Development on main continues at 0.3.0-SNAPSHOT.
```

### Scenario 7: Multiple active maintenance lines

```
main (0.3.0-SNAPSHOT)          release/0.1.x              release/0.2.x
  │                              │                          │
  push → snapshot                push → snapshot            push → snapshot
  0.3.0-SNAPSHOT                 0.1.1-SNAPSHOT             0.2.1-SNAPSHOT
  │                              │                          │
  [manual] release               [manual] release           [manual] release
  branch: main                   branch: release/0.1.x      branch: release/0.2.x
  version: 0.3.0                 version: 0.1.1             version: 0.2.5
  → tag v0.3.0                   → tag v0.1.1               → tag v0.2.5
  → bump 0.3.1-SNAPSHOT          → bump 0.1.2-SNAPSHOT      → bump 0.2.6-SNAPSHOT
  → publish 0.3.0                → publish 0.1.1            → publish 0.2.5
```

Each branch is fully independent with its own `gradle.properties`, snapshots, and releases.

---

## Trigger Map

| Event | Workflows triggered |
|---|---|
| PR opened / updated | `ci.yml` |
| PR merged to `main` | `snapshot.yml` |
| PR merged to `release/0.1.x` | `snapshot.yml` |
| Push to `feat/v2-api` | `feature-snapshot.yml` |
| Push to `feature/new-design` | `feature-snapshot.yml` |
| Release manual (`branch: main`) | `release.yml` → (bump push) → `snapshot.yml` |
| Release manual (`branch: release/0.1.x`) | `release.yml` → (bump push) → `snapshot.yml` |
| Dependabot PR | `ci.yml` |

---

## Branch Naming Convention

| Pattern | Purpose |
|---|---|
| `main` | Current development line |
| `feat/<description>` | Feature branches (auto-publish snapshots) |
| `feature/<description>` | Alternative feature branch prefix |
| `fix/<description>` | Bug fix branches |
| `docs/<description>` | Documentation-only changes |
| `refactor/<description>` | Code restructuring |
| `release/X.Y.x` | Maintenance branch for older versions |

---

## Version Format Rules

| Format | Example | Release type | Modifies CHANGELOG? | Bumps gradle.properties? | GitHub Release badge |
|---|---|---|---|---|---|
| `X.Y.Z` | `1.0.0` | Stable | Yes | Yes | None |
| `X.Y.Z-alpha.N` | `1.0.0-alpha.1` | Pre-release | No | No | Pre-release |
| `X.Y.Z-beta.N` | `1.0.0-beta.2` | Pre-release | No | No | Pre-release |
| `X.Y.Z-rc.N` | `1.0.0-rc.1` | Pre-release | No | No | Pre-release |

---

## Secrets Required

| Secret | Purpose |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Sonatype / Maven Central authentication |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype / Maven Central authentication |
| `GPG_SIGNING_KEY` | In-memory GPG signing key for artifact signatures |
| `GPG_SIGNING_PASSWORD` | Password for the GPG signing key |

---

## Creating a Maintenance Branch

When a critical bug needs fixing in an already-released version while `main` has moved forward:

```bash
# 1. Create the maintenance branch from the release tag
git checkout -b release/0.1.x v0.1.0

# 2. Set the snapshot version for this branch
#    Edit gradle.properties: version=0.1.1-SNAPSHOT

# 3. Push the branch
git push -u origin release/0.1.x

# 4. Apply fixes, commit, and PR to release/0.1.x
# 5. Once merged, trigger a release:
#    release.yml → version: "0.1.1", branch: "release/0.1.x"
```
