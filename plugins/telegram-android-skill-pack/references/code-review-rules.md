# Telegram Android Code Review Rules

## Review Order

1. Correctness
2. Regression risk
3. Product and UI fit
4. Maintainability
5. Style and polish

## Correctness and Safety

- Check whether the new behavior matches the requested intent.
- Check lifecycle cleanup, observer cleanup, and callback ordering.
- Check persistence keys, defaults, and compatibility when state is stored.
- Check account scoping and stale state handling where relevant.

## UI and Product Fit

- Check whether the UI still feels like Telegram Android rather than a generic redesign.
- Check whether the flow got slower, more modal, or more explanatory.
- Check whether density and scan speed were preserved.

## Architecture and Maintainability

- Check whether the change followed an existing owner, pattern, or boundary.
- Check whether a new abstraction was truly necessary.
- Check whether names, file placement, and code shape match surrounding code.
- Check whether the diff is larger than the problem warrants.

## Performance and Platform Fit

- Check list and chat performance for per-bind, per-layout, or per-scroll work.
- Check theming, localization, RTL, and device-mode behavior relevant to the touched surface.
- Check whether measurement, invalidation, or animation work was added unnecessarily.

## AI-Smell

- Generic helper or manager names with vague responsibilities
- Architectural layering added for style rather than need
- Decorative UI choices justified as modernization
- Large cleanup refactors hidden inside small feature work
- Comments that narrate obvious code instead of clarifying a real invariant
