---
name: telegram-orchestrator
description: Entry-point coordinator for Telegram Android and Telegram Android fork work. Use when Codex needs to plan or execute UI changes, feature work, legacy modifications, product-fit decisions, architecture-sensitive edits, or final reviews in Telegram-style Android codebases. Route to the right telegram-* skills, combine them when needed, and enforce Telegram-native product, visual, reuse, and low-risk engineering standards.
---

# Telegram Orchestrator

## Set the baseline

- Treat Telegram Android as the stylistic and engineering reference point.
- Do not begin with a repo-wide audit, class inventory, or broad architecture map.
- Gather only the task-local context required to act safely.
- Prefer small, reviewable diffs over ambitious rewrites.
- Preserve the fork's local conventions when they intentionally diverge from upstream Telegram behavior.

## Route the task

- Use `telegram-product-consistency` first when the request changes product scope, adds UX steps, or introduces a new action.
- Use `telegram-design-principles` when judging how a screen, row, sheet, dialog, or chat affordance should feel.
- Use `telegram-component-reuse` before writing any UI or controller code.
- Use `telegram-ui-implementation` for screens, cells, settings pages, sheets, dialogs, and message-related UI.
- Use `telegram-feature-development` for new behavior, new entry points, or new state transitions.
- Use `telegram-legacy-modification` when editing existing or fragile code paths.
- Use `telegram-code-style-and-architecture` when structural choices, naming, threading, storage, or dependency boundaries matter.
- Use `telegram-review-and-regression` before finalizing any non-trivial change.

## Follow the default workflow

1. Classify the task as product decision, UI implementation, new feature, legacy edit, architecture question, or review.
2. Read only the nearby code, the closest relevant precedent, and the shared reference docs needed for that task.
3. Find at least one analogous local implementation before inventing code or UI.
4. Pick the smallest diff that solves the real problem without changing unrelated structure.
5. Implement with the specialized skill guidance.
6. Run a maintainer-style self-review with `telegram-review-and-regression`.

## Enforce global standards

- Reject decorative redesigns, trend-driven visuals, and "cleanups" that only add space or novelty.
- Protect message flow clarity, interaction speed, and information density.
- Reuse existing primitives, themes, strings, controllers, cells, and interaction patterns whenever possible.
- Avoid new dependencies, new frameworks, and broad abstractions unless the existing codebase already made that choice.
- Prefer boring solutions that match surrounding code over technically fancier alternatives.

## Load shared references selectively

- Read `../../references/design-principles.md` for any UI-facing change.
- Read `../../references/ui-anti-patterns.md` when a design direction starts getting larger, emptier, rounder, or more decorative.
- Read `../../references/feature-development-playbook.md` for new features.
- Read `../../references/legacy-edit-checklist.md` for edits in existing code.
- Read `../../references/product-consistency-rules.md` for product and UX trade-offs.
- Read `../../references/code-review-rules.md` and `../../references/regression-review-checklist.md` before finalizing.
