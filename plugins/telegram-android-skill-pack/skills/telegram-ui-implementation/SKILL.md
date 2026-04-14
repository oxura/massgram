---
name: telegram-ui-implementation
description: Implement Telegram-native Android UI for fragments, custom views, cells, settings pages, dialogs, sheets, message-adjacent surfaces, and interaction flows. Use when Codex is writing or editing Android UI code in Telegram Android or its forks and needs to keep structure, density, theme usage, and interactions aligned with Telegram rather than generic app design trends.
---

# Telegram UI Implementation

## Start from the nearest local precedent

- Search for the closest existing screen, cell, sheet, dialog, or menu pattern before writing code.
- Prefer a local equivalent of familiar Telegram Android primitives such as `BaseFragment`, `ActionBar`, `RecyclerListView`, custom `Cell` views, `BottomSheet`, themed drawables, `ThemeDescription`, `BulletinFactory`, or `UndoView`.
- If the fork renamed or wrapped these primitives, follow the local version instead of importing an outside pattern.

## Build the UI the Telegram way

- Extend or adapt existing cells before creating a new mini design system.
- Keep rows compact, labels short, summaries secondary, and actions obvious.
- Reuse existing theme keys, string localization paths, icons, and drawable idioms.
- Keep the touched area visually consistent with nearby screens, not with abstract design purity.
- Match the language already used in that area of the app; do not modernize an old screen just because you touched it.

## Implement each surface conservatively

- Build settings and utility pages from existing row and section patterns.
- Build dialogs only for destructive, permission, or high-risk forks in user flow.
- Build sheets as concise action choosers with little explanatory overhead.
- Build message-related UI with extreme caution; protect density, grouping, swipe behavior, scroll behavior, status indicators, and quick actions.
- Build transient feedback with existing bulletin, undo, or toast-like patterns if the codebase already uses them.

## Wire the behavior carefully

- Use existing adapters, view types, controllers, delegates, and callbacks where possible.
- Keep theme invalidation, locale updates, and state refresh logic consistent with nearby code.
- Respect view recycling, measurement, animation, and scrolling performance on chat and list surfaces.
- Preserve accessibility labels, tap targets, dark mode support, and RTL behavior if the local codebase already supports them.

## Keep the diff small

- Prefer local edits to an existing fragment, cell, or adapter over introducing a new shared widget.
- Prefer composing a known pattern from existing pieces over inventing new chrome.
- Prefer deleting extra UI ideas before deleting density or directness.

## Read the references that matter

- Read `../../references/design-principles.md` for baseline visual and interaction rules.
- Read `../../references/ui-anti-patterns.md` before changing layout structure or visual treatment.
- Read `../../references/regression-review-checklist.md` after implementing any non-trivial UI change.
