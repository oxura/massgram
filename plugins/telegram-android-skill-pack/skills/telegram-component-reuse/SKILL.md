---
name: telegram-component-reuse
description: Pattern-finding and reuse discipline for Telegram Android and Telegram-style forks. Use before implementing UI, controllers, flows, settings rows, message actions, or state handling so Codex searches for analogous existing structures, spacing, typography, icons, interaction models, and controller patterns before inventing new code.
---

# Telegram Component Reuse

## Treat reuse as the default

- Assume the correct solution probably already exists in some nearby form.
- Search for the same surface first, then the same interaction, then the same state behavior.
- Prefer literal reuse or a small local extension over a brand-new shared abstraction.
- Prefer matching established row, cell, icon, spacing, adapter, and controller patterns over "cleaner" reinvention.

## Search deliberately

- Search by surface names such as `Cell`, `Row`, `Settings`, `Privacy`, `Notification`, `Chat`, `Message`, `Media`, `Search`, `Filter`, `Archive`, `Sheet`, and `Alert`.
- Search by common Telegram Android primitives or their fork-local equivalents such as `BaseFragment`, `RecyclerListView`, `BottomSheet`, `ThemeDescription`, `LocaleController`, `NotificationCenter`, `MessagesController`, `SharedConfig`, `BulletinFactory`, and `UndoView`.
- Search by behavior, not only by labels: toggles, destructive actions, badges, inline counters, multi-select, swipe actions, undo, preview rows, or summary text.
- Search the touched module first, then adjacent modules with similar user intent.

## Reuse in the right order

1. Reuse the exact existing component or pattern.
2. Reuse the same structure with small local branching.
3. Copy a proven local pattern inside the same feature area when shared extraction would add risk.
4. Create a new component only when no close precedent exists and the task genuinely needs new behavior.

## Avoid false improvements

- Do not replace simple rows with cards.
- Do not create a new generic manager just to avoid touching an existing controller.
- Do not rename or move working code only to make a small feature feel more "architected."
- Do not introduce a new iconography, spacing scale, or motion language for one feature.

## Hand off correctly

- After identifying the closest precedent, switch to `telegram-ui-implementation`, `telegram-feature-development`, or `telegram-legacy-modification` as appropriate.
- Keep notes on which local pattern you are following so the final review can judge whether the new code stayed faithful.
