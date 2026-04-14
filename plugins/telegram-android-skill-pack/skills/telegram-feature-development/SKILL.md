---
name: telegram-feature-development
description: Incremental feature-development workflow for Telegram Android and similar forks. Use when Codex is adding new behavior, new entry points, new settings, new actions, or new message-adjacent features and must keep the implementation practical, low-risk, Telegram-consistent, and friendly to an established Android codebase rather than rewriting large subsystems.
---

# Telegram Feature Development

## Frame the feature correctly

- Identify the user utility before touching code.
- Check whether the feature belongs in Telegram-style product scope with `telegram-product-consistency`.
- Identify where the feature should enter the app with the least disruption to existing flows.
- Prefer adding to an existing screen, menu, row set, or controller path over creating a new navigation branch.

## Build from precedent

- Find a local feature with similar user intent, even if the exact domain differs.
- Reuse the same UI surface, state flow, controller ownership, config storage path, and feedback model when practical.
- Treat upstream Telegram patterns as hints, not mandates, when the fork already established a local variant.

## Slice the implementation conservatively

- Change the smallest number of files that can safely own the feature.
- Keep product entry, controller logic, persistence, and UI connected through existing boundaries.
- Reuse existing config, settings, account, or storage machinery before creating new state plumbing.
- Prefer additive behavior behind existing menus, rows, or options instead of promoting the feature to primary chrome.

## Protect the risky edges

- Check account scoping, persistence keys, serialization, and compatibility when the feature stores state.
- Check observers, notifications, callbacks, and lifecycle cleanup when the feature updates live UI.
- Check list performance, chat performance, and recycler behavior when the feature adds visible UI.
- Check fallback behavior when the feature is unavailable, disabled, or backed by server-provided state.

## Deliver incrementally

- Prefer the smallest useful version first.
- Avoid cross-cutting refactors unless they are already in progress and necessary for correctness.
- Avoid new dependencies and new frameworks unless the existing codebase already uses them for the same purpose.
- Leave clear seams for future expansion only when the next extension is already evident in nearby code.

## Read the references that matter

- Read `../../references/feature-development-playbook.md` for the full implementation workflow.
- Read `../../references/product-consistency-rules.md` before introducing user-visible choices or new affordances.
- Read `../../references/regression-review-checklist.md` before finalizing.
