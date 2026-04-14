---
name: telegram-code-style-and-architecture
description: "Conservative Android engineering guidance for Telegram Android and similar forks. Use when Codex is making structural decisions, adding logic around controllers, storage, observers, threads, models, or UI ownership, and must keep the solution aligned with Telegram-style codebases: explicit, local, dependency-light, reuse-heavy, and resistant to overengineering."
---

# Telegram Code Style and Architecture

## Match the existing codebase before improving it

- Match the language, file layout, naming style, and ownership model of the touched area.
- Prefer local consistency over abstract elegance.
- Do not migrate old Java-style or mixed Java/Kotlin code into a new style unless the task explicitly requires it.
- Do not introduce Compose, DI frameworks, state containers, or new reactive layers into code that does not already use them.

## Keep the architecture conservative

- Prefer local helpers and existing controllers over new service or manager layers.
- Prefer explicit data flow over generic extension points.
- Prefer existing config, storage, and event paths over new plumbing.
- Prefer small shared abstractions only when two or more existing call sites clearly need them now.
- Keep side effects visible and owned by the layer that already owns similar work.

## Respect common Telegram-style boundaries

- Keep fragments, screens, and views responsible for UI wiring, not unrelated business logic.
- Keep account-scoped state, server-backed state, and persistent settings in the same ownership model already used nearby.
- Keep observer registration and cleanup symmetrical.
- Keep chat and list code performance-sensitive; avoid work that scales per bind, per draw, or per scroll event without need.

## Avoid architecture smell

- Avoid generic repositories, coordinators, or helpers that exist only to make the diff feel cleaner.
- Avoid renaming files, moving classes, or extracting interfaces without a concrete maintenance payoff.
- Avoid adding a dependency when a few lines of local code or an existing utility already solve the problem.
- Avoid comments that explain away confusing code instead of simplifying it.

## Finish with a maintainability check

- Ask whether another Telegram Android maintainer would recognize the shape of the change immediately.
- Ask whether the smallest correct diff would be easier to review and safer to ship.
- Switch to `telegram-review-and-regression` for the final pass.

## Read the references that matter

- Read `../../references/code-review-rules.md` for review criteria.
- Read `../../references/legacy-edit-checklist.md` when the change touches fragile or older code.
- Read `../../references/feature-development-playbook.md` when the change adds new product behavior.
