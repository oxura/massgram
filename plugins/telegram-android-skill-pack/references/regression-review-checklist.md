# Telegram Android Regression Review Checklist

## General

- Verify navigation and back behavior.
- Verify dark mode and light mode where applicable.
- Verify localization and RTL on touched strings and layouts.
- Verify accessibility labels and practical tap targets where the codebase supports them.
- Verify account-scoped behavior if the feature can differ per account.

## Lists and Performance

- Verify recycler binding remains stable.
- Verify scrolling remains smooth.
- Verify row enable or disable states still render correctly.
- Verify no extra work was added in draw, measure, or bind paths without need.

## Settings, Sheets, and Dialogs

- Verify the row hierarchy still scans naturally.
- Verify destructive actions are still clearly marked.
- Verify outside tap, back press, and dismissal behavior.
- Verify summaries, toggles, and value text remain synchronized with stored state.

## Message-Related Surfaces

- Verify message density and grouping.
- Verify reply, forward, selection, and context-menu interactions if touched.
- Verify scroll-to-bottom and unread behavior if touched.
- Verify composer or quick-action behavior if touched.
- Verify transient feedback such as bulletin or undo timing if touched.

## State and Recovery

- Verify rotation, recreation, or return-to-screen behavior if relevant.
- Verify persisted values read old defaults safely.
- Verify disabled or unavailable states behave gracefully.
- Verify observers and listeners are detached when the screen goes away.
