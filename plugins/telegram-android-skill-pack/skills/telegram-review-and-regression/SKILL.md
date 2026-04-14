---
name: telegram-review-and-regression
description: Maintainer-style review workflow for Telegram Android changes. Use when Codex needs to review its own work or someone else's work for correctness, regressions, product fit, Telegram-native UI quality, architectural restraint, lifecycle safety, and AI-style implementation mistakes before finalizing a change.
---

# Telegram Review and Regression

## Review in the right order

1. Check correctness and user-visible behavior first.
2. Check product fit and Telegram-native interaction quality next.
3. Check reuse, architectural restraint, and maintainability after that.
4. Check regression risk across nearby flows before finalizing.

## Look for Telegram-specific failure modes

- Look for UI that feels louder, rounder, emptier, or more decorative than surrounding Telegram surfaces.
- Look for features that add steps, explanations, confirmations, or marketing language to routine actions.
- Look for code that invents new abstractions instead of following existing controllers, cells, fragments, or config paths.
- Look for message-flow regressions in density, scroll behavior, action timing, grouping, or interaction speed.
- Look for list and chat performance risks caused by extra work in bind, layout, or animation paths.

## Detect AI-smell directly

- Reject generic helper classes with vague names.
- Reject unnecessary renames, moves, or cleanup-only edits hidden inside feature work.
- Reject design choices that read as "modern" but not "Telegram."
- Reject comments and abstractions that compensate for a diff being too broad.
- Reject new dependencies unless the task clearly demanded them.

## Finish with an explicit checklist

- Compare the new path against the old default path.
- Check theming, localization, RTL, and device-mode impacts that apply to the touched surface.
- Check lifecycle cleanup, observer cleanup, and persistence compatibility where relevant.
- Report must-fix issues first and optional polish only after correctness, safety, and fit are covered.

## Read the references that matter

- Read `../../references/code-review-rules.md` for review standards.
- Read `../../references/regression-review-checklist.md` for verification coverage.
- Read `../../references/design-principles.md` and `../../references/ui-anti-patterns.md` for UI-facing changes.
