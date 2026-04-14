# Telegram Android Design Principles

## Baseline

- Design for utility first.
- Keep the interface calm, dense, and readable.
- Preserve the feeling that the app is a fast tool, not a showroom.
- Treat core messaging flows as the visual and interaction center of gravity.

## Layout and Density

- Prefer compact list and cell layouts over card stacks.
- Use whitespace to separate structure, not to manufacture luxury.
- Keep rows readable without becoming tall or spacious for their own sake.
- Avoid large banners, giant hero headers, and decorative top sections on utility screens.
- If a screen can be built from standard rows, sections, summaries, and toggles, do that.

## Hierarchy

- Build hierarchy with typography, spacing, and ordering.
- Keep primary text direct and secondary text genuinely secondary.
- Use color sparingly to communicate state, emphasis, or danger.
- Let icons support recognition, not replace labels.

## Visual Restraint

- Favor flat surfaces, subtle separators, and controlled radii.
- Avoid heavy shadows, layered panels, blurred surfaces, and ornamental backgrounds.
- Avoid random gradients or accent colors that create a second visual identity.
- Keep settings and tools visually subordinate to the user's content.

## Interaction Style

- Favor direct taps and predictable gestures over novelty.
- Favor quick, low-friction repeated use for power users.
- Favor inline state changes, sheets, and bulletins when they reduce interruption.
- Use dialogs for destructive or genuinely high-risk decisions, not for routine acknowledgment.

## Surface Rules

### Settings and utility screens

- Compose from existing row patterns.
- Keep copy short, specific, and practical.
- Prefer sections and summaries over custom explainer layouts.

### Sheets and dialogs

- Keep actions concise.
- Avoid converting a small chooser into a miniature feature page.
- Avoid using a dialog when a direct action or bulletin would suffice.

### Message-related UI

- Protect density, bubble rhythm, reaction and status placement, and scan speed.
- Avoid adding chrome that competes with message content.
- Keep action timing and gesture expectations familiar.

## Decision Questions

- Would this look normal next to Telegram's existing Android screens?
- Is the interface clearer because it is better structured, or only because it is emptier?
- Does the user get to the action faster?
- Does the new UI introduce a second design language?
- Would a strong Telegram Android maintainer see this as natural, not ornamental?
