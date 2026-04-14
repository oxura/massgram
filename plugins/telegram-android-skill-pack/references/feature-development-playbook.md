# Telegram Android Feature Development Playbook

## 1. Frame the real feature

- State the user utility in one sentence.
- Identify the core user action that should become faster or easier.
- Identify the smallest existing surface where the feature naturally belongs.
- Reject any requirement that exists only to make the feature look more impressive.

## 2. Find a local precedent

- Search for a feature with similar interaction shape, not only similar domain wording.
- Reuse the same kind of screen, menu placement, row type, toggle pattern, or controller ownership.
- If the fork differs from upstream Telegram, follow the fork's established pattern.

## 3. Choose the smallest integration path

- Prefer adding to an existing menu, row set, sheet, or settings section.
- Prefer a local action or setting over a new top-level destination.
- Prefer reusing existing state holders and config storage over inventing new plumbing.

## 4. Implement in narrow slices

- Product entry point
- UI surface
- Action handling and callbacks
- Persistence, config, or network changes
- Feedback and error handling

Keep each slice as local as possible. If one slice can be handled by an existing class, use it.

## 5. Protect the risky edges

- Preserve backward-compatible persistence or serialization behavior.
- Keep account scoping explicit where the app already distinguishes per-account state.
- Use existing observer or event patterns and clean them up properly.
- Keep list and chat performance stable.
- Provide graceful behavior when data or capability is missing.

## 6. Ship the conservative version

- Prefer the version that feels immediately native to Telegram.
- Prefer the version that adds the least visual and navigational surface area.
- Prefer the version that is easiest to review and least likely to regress other flows.

## 7. Finalize with review

- Run a product-fit check.
- Run a design-fit check.
- Run a regression checklist.
- If a diff feels larger than the feature warrants, cut scope before polishing.
