---
name: telegram-design-principles
description: Telegram-native UI judgment for Android screens, cells, settings pages, dialogs, sheets, chat surfaces, and feature proposals. Use when Codex needs to decide how Telegram-like UI should feel, compare design options, reject generic AI-looking visuals, or keep a new flow aligned with Telegram's dense, calm, utility-first visual language.
---

# Telegram Design Principles

## Hold the Telegram baseline

- Optimize for utility first, not expressiveness first.
- Keep layouts calm, dense, and practical.
- Use structure, spacing, typography, and alignment to create hierarchy.
- Avoid decoration as a substitute for clarity.
- Make new UI feel like it belongs beside existing Telegram surfaces, not like a new design language landed inside the app.

## Preserve the visual feel

- Prefer flat or lightly separated surfaces over cards and panels.
- Keep radii restrained and consistent with nearby UI.
- Use color to clarify state and emphasis, not to create atmosphere.
- Keep headers, section labels, and summaries functional.
- Preserve information density on chat, list, settings, and utility screens.

## Preserve the interaction feel

- Prefer direct actions over guided flows.
- Prefer familiar Android and Telegram interaction patterns over novelty.
- Keep taps predictable, feedback quick, and state transitions obvious.
- Use sheets, dialogs, bulletins, and inline state changes only when they genuinely reduce friction.
- Protect core messaging flows from extra chrome or attention-seeking UI.

## Judge each surface correctly

- Treat settings pages as compact lists of practical rows, not marketing pages.
- Treat dialogs as interruption tools for destructive or high-risk choices, not routine education.
- Treat bottom sheets as compact action choosers, not miniature landing pages.
- Treat message UI as performance-sensitive and density-sensitive; do not loosen spacing or increase ornament.
- Treat utility screens such as privacy, storage, folders, and filters as tools that should feel immediate and repeatable.

## Reject the wrong instincts

- Reject glassmorphism, neumorphism, decorative shadows, random gradients, and novelty effects.
- Reject oversized cards, oversized radii, centered empty layouts, and ornamental floating elements.
- Reject hero headers, large banners, and "premium-looking" whitespace on routine screens.
- Reject Dribbble-style redesigns, mood-board visuals, and interfaces that are cleaner only because they removed density.

## Read the references that matter

- Read `../../references/design-principles.md` first for concrete Telegram-style design rules.
- Read `../../references/ui-anti-patterns.md` whenever evaluating redesign ideas or proposing new UI structure.
- Read `../../references/product-consistency-rules.md` if the design question changes user flow or product scope.
