# Telegram Android Legacy Edit Checklist

## Before Editing

- Identify the narrowest owning class or method.
- Identify immediate call sites, delegates, listeners, and adapters.
- Identify whether the state is account-scoped, persisted, cached, or server-backed.
- Identify whether the UI path differs across tablets, alternate fragments, or special modes.

## While Editing

- Keep the old code path recognizable.
- Prefer a narrow branch or helper over a structural rewrite.
- Preserve naming and file placement unless a move is required for correctness.
- Avoid mixing cleanup work into the same change unless it directly reduces risk.

## Hidden Coupling Checks

- Observer registration and cleanup
- Adapter positions and view types
- Theme keys and UI invalidation
- Locale or string refresh behavior
- Saved state, fragment args, and bundle keys
- Persistence keys, migrations, and defaults
- Network callbacks and stale UI updates

## After Editing

- Compare old and new default behavior side by side.
- Verify fallback behavior when the new path is disabled, unavailable, or unsupported.
- Verify that unchanged neighboring behaviors still look untouched.
- Run the regression checklist with emphasis on the touched surface.

## Strong Default

- If a refactor is optional, skip it.
- If two solutions work, choose the smaller diff.
- If the code feels fragile, preserve the shape and patch only the behavior.
