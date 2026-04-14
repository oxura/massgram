---
name: telegram-legacy-modification
description: Surgical change discipline for older or fragile Telegram Android code paths. Use when Codex must edit existing features, fix bugs in long-lived code, patch custom views or controllers, or adjust behavior in areas where broad refactors would increase regression risk. Favor minimal invasive edits, preserved behavior, and strong awareness of side effects.
---

# Telegram Legacy Modification

## Treat existing behavior as expensive to break

- Assume older code may encode product decisions, edge cases, or lifecycle expectations that are not obvious at first glance.
- Prefer narrow edits in the owning class or method over cleanup-driven restructuring.
- Preserve naming, file placement, and surrounding style unless change is required for correctness.
- Avoid opportunistic refactors while fixing a single behavior.

## Build only the local context you need

- Identify the exact entry point, owner, and immediate collaborators.
- Read call sites, observers, delegates, adapters, or fragments that consume the touched behavior.
- Look for hidden coupling in shared cells, view types, notification listeners, account-scoped state, serialization keys, and fragment arguments.
- Stop expanding outward once you can explain the local behavior and patch it safely.

## Modify conservatively

- Prefer a narrow conditional branch, local helper, or small data extension over moving responsibilities across classes.
- Preserve old control flow when the new behavior can fit inside it.
- Respect the touched area's language choice, style, and threading model.
- Keep feature flags, config reads, storage writes, and UI invalidation inside the existing control path when possible.

## Watch the common Telegram-style risk areas

- Watch `NotificationCenter`-style observers or equivalent event buses for leaks and duplicate updates.
- Watch adapter position assumptions, magic view types, and view recycling on list-heavy screens.
- Watch theme keys, locale updates, and account switching if the code is user-facing.
- Watch persistence keys, cached objects, and network-backed state if the change touches configuration or messages.

## Finish with a regression mindset

- Compare the new behavior against the prior default path, not only against the new happy path.
- Check whether the touched area has a second code path for tablets, RTL, old Android versions, or alternate fragment modes.
- Use `telegram-review-and-regression` before finalizing.

## Read the references that matter

- Read `../../references/legacy-edit-checklist.md` before changing existing code.
- Read `../../references/regression-review-checklist.md` after implementation.
- Read `../../references/code-review-rules.md` if the change pressures architecture or maintainability.
