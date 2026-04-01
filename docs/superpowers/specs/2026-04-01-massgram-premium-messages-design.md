# Massgram Premium Messages Design

## Problem

Massgram currently unlocks Telegram Premium by overriding core premium state in the client. This is unsafe because it makes the fork behave like a real Premium client across unrelated code paths, including UI, limits, badges, reactions, stickers, and cached user state.

The requested behavior is different:

- Premium-like features should exist only between Massgram clients.
- Official Telegram clients must not see the protected content.
- Profile indication must stay local to Massgram and only appear when the other peer is known to use Massgram.
- The fork must stop pretending to be real Telegram Premium.

## Goal

Replace global fake-Premium behavior with a Massgram-only message protocol that:

- sends a safe placeholder to Telegram servers;
- carries hidden Massgram payload data alongside the placeholder;
- restores the real content only on Massgram clients;
- keeps profile indication local and capability-based.

## Non-goals for v1

- No real Telegram Premium entitlement emulation.
- No attempt to bypass server-enforced Premium limits.
- No custom backend storage for hidden media payloads.
- No full support for native Premium stickers, emoji status, reactions, or other Telegram media objects when sent as real Telegram Premium entities.

## Constraints

1. Telegram servers distribute canonical message state to every client.
2. If Massgram sends a normal Telegram message, official clients will see it.
3. If Massgram deletes a message after send, that deletion is also distributed to Massgram clients, so delete-after-send cannot be the primary transport.
4. Therefore the only safe v1 approach is to send a normal placeholder plus a hidden Massgram payload encoded into the same text message.

## Chosen approach

Use a Massgram-specific text transport:

- Visible text sent to Telegram: `Unsupported Massgram message`
- Hidden payload appended using an invisible marker and encoded data
- Massgram clients detect the payload, suppress the placeholder, and render the decoded content locally
- Official clients only see the placeholder text

This approach is intentionally boring and server-compatible. It does not require changing Telegram transport semantics or introducing new infrastructure.

## Protocol design

### Envelope

Massgram premium messages will be encoded as a text message with:

- a human-visible placeholder: `Unsupported Massgram message`
- a hidden protocol marker
- a serialized payload

The message must still be valid as plain Telegram text if decoded nowhere.

### Payload format

Versioned payload format:

- protocol name: `MGP1`
- payload type: string enum
- JSON body: UTF-8 serialized
- encoded body: Base64

Logical structure:

```text
Unsupported Massgram message + [hidden marker] + MGP1:<type>:<base64-json>
```

Initial v1 payload types:

- `premium_text`

Initial `premium_text` body:

```json
{
  "text": "original text shown only in Massgram",
  "format": "plain"
}
```

v1 deliberately supports only text-based premium content, including task lists represented as text. This keeps transport predictable and avoids pretending to support hidden Telegram media objects that cannot be reconstructed safely from a plain text payload.

## Send behavior

### Toggle semantics

The existing "Unlock Premium" toggle must be repurposed. It should no longer mean "force this account to be Premium".

New meaning:

- enable Massgram premium messages;
- show local profile indication for known Massgram peers;
- allow composing and sending Massgram-only premium text payloads.

### Outgoing messages

When the toggle is disabled:

- send messages normally;
- do not encode Massgram premium payloads.

When the toggle is enabled and the user sends content through the Massgram premium path:

- build a `premium_text` payload from the original entered text;
- replace outgoing Telegram text with the placeholder envelope;
- strip normal entities that would leak content to official clients;
- disable link previews for payload messages;
- preserve Telegram server compatibility by sending only a normal text message.

Normal messages must remain unaffected.

## Receive and render behavior

When a message is received or loaded from cache:

1. Detect whether the text contains the hidden Massgram premium marker.
2. If not, render normally.
3. If yes:
   - parse the payload;
   - if payload is valid and supported, render decoded Massgram content instead of the placeholder;
   - if payload is invalid, fall back to showing the placeholder text.

This must work for:

- newly received messages;
- cached history;
- edited messages when the edited content still contains a Massgram payload.

## Profile indication

Profile indication remains local to Massgram and must not depend on Telegram Premium state.

Rules:

- Massgram detection stays based on existing peer detection/capability logic.
- The profile "Massgram detected" row remains the primary compatibility indicator.
- The premium-style star is shown only in profile surfaces, never as a global Premium state.
- The star is shown only when:
  - local Massgram premium messages mode is enabled; and
  - the viewed peer is already detected as a Massgram client.

The star must not be driven by `user.premium`, `isPremium()`, or server-backed Premium APIs.

## Removal of unsafe behavior

The following behavior must be removed:

- forcing `currentUser.premium = true`;
- returning true from `UserConfig.isPremium()` because of Massgram settings;
- forcing `MessagesController.isPremiumUser()` for self because the toggle is enabled;
- suppressing premium purchase or lock checks globally due to the Massgram toggle.

After this change, Telegram Premium logic must again reflect only actual Telegram Premium state.

## UX boundaries for v1

- v1 supports hidden Massgram text content only.
- If the user tries to use a Premium feature that depends on real Telegram media transport, Massgram should not silently fake it.
- Such flows should either:
  - continue using normal Telegram behavior; or
  - be blocked from the Massgram premium transport path until a real media payload design exists.

This is the safest way to avoid shipping a half-working protocol that claims to hide stickers but actually leaks them.

## Migration and compatibility

- Existing cached Massgram fake-Premium state must stop affecting runtime after the change.
- Old messages without the new payload remain unchanged.
- Payload parsing must be versioned so future protocol types can be added without breaking v1.

## Risks and mitigations

### Risk: hidden payload leaks visible characters on some clients

Mitigation:

- use a stable hidden marker strategy already proven in `MassgramCryptoManager`;
- keep the visible placeholder explicit so even worst-case fallback stays safe.

### Risk: message editing leaks original text

Mitigation:

- edit path must re-encode payloads instead of editing the decoded visible text directly.

### Risk: UI still treats the account as real Premium elsewhere

Mitigation:

- remove all Massgram-driven overrides from `UserConfig` and `MessagesController`;
- keep Massgram indication in profile-only code paths.

### Risk: users expect Premium stickers to work in v1

Mitigation:

- do not advertise hidden sticker transport as working in v1;
- keep scope to hidden text payloads only.

## Files expected to change

- `TMessagesProj/src/main/java/org/telegram/messenger/MassgramConfigManager.java`
- `TMessagesProj/src/main/java/org/telegram/messenger/MassgramCryptoManager.java`
- `TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java`
- `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`
- `TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java`
- `TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java`
- `TMessagesProj/src/main/java/org/telegram/ui/MassgramSettingsActivity.java`
- `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`
- `TMessagesProj/src/main/res/values/strings.xml`
- `TMessagesProj/src/main/res/values-ru/strings.xml`

## Acceptance criteria

1. Enabling the Massgram toggle no longer makes the account globally Premium.
2. Official Telegram clients only see `Unsupported Massgram message` for Massgram premium text messages.
3. Massgram clients restore and display the original hidden text correctly in chats and history.
4. Profile indication remains local to Massgram and appears only for known Massgram peers.
5. No unrelated Telegram Premium code path is unlocked by the Massgram toggle.
