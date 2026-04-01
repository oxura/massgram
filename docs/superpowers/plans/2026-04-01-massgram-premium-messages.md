# Massgram Premium Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current fake Telegram Premium override with a Massgram-only hidden text payload protocol so official clients see only `Unsupported Massgram message` while Massgram clients render the real content locally.

**Architecture:** Keep Telegram transport unchanged and send only normal text messages. Encode Massgram-only content inside a hidden, versioned payload carried by placeholder messages, decode it in `MessageObject`, and keep all Premium indication local to Massgram profile UI instead of core Premium state.

**Tech Stack:** Telegram Android Java codebase, existing `MassgramCryptoManager`, `UserConfig`, `MessagesController`, `SendMessagesHelper`, `ProfileActivity`, Android resources, Gradle test/build tasks.

---

### Task 1: Finalize Massgram premium-mode configuration semantics

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MassgramConfigManager.java`
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/MassgramSettingsActivity.java`
- Modify: `TMessagesProj/src/main/res/values/strings.xml`
- Modify: `TMessagesProj/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Inspect the current setting owner path**

Run: `git grep -n -I "isPremiumUnlockEnabled\\|setPremiumUnlockEnabled\\|applyMassgramPremiumOverride" -- TMessagesProj/src/main/java`
Expected: only the current Massgram toggle path and premium override call sites are listed.

- [ ] **Step 2: Rename the user-facing setting from fake Premium unlock to Massgram premium messages**

Update the strings so the setting describes hidden Massgram-only messages instead of Telegram Premium entitlement.

```xml
<string name="MassgramUnlockPremium">Massgram premium messages</string>
<string name="MassgramUnlockPremiumInfo">Sends Massgram-only premium text messages. Official Telegram clients only see "Unsupported Massgram message", while Massgram clients decode and show the hidden content locally.</string>
```

```xml
<string name="MassgramUnlockPremium">Премиум-сообщения Massgram</string>
<string name="MassgramUnlockPremiumInfo">Отправляет премиум-сообщения только для Massgram. Обычные клиенты Telegram видят только "Unsupported Massgram message", а Massgram локально декодирует скрытое содержимое.</string>
```

- [ ] **Step 3: Remove runtime calls that refresh global Premium state when the toggle changes**

Keep the persisted flag, but stop calling account-wide Premium refresh from the setting row callback.

```java
() -> {
    boolean newValue = !configManager.isPremiumUnlockEnabled();
    configManager.setPremiumUnlockEnabled(newValue);
    refreshSettingsState();
}
```

- [ ] **Step 4: Run a targeted compile check for the settings file**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 5: Commit**

```bash
git add TMessagesProj/src/main/java/org/telegram/messenger/MassgramConfigManager.java TMessagesProj/src/main/java/org/telegram/ui/MassgramSettingsActivity.java TMessagesProj/src/main/res/values/strings.xml TMessagesProj/src/main/res/values-ru/strings.xml
git commit -m "refactor: repurpose massgram premium toggle"
```

### Task 2: Remove fake Telegram Premium overrides from core account state

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java`
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`

- [ ] **Step 1: Write a regression checklist for removed override points**

Create a temporary notes block while editing to ensure these branches are removed:

```text
- setCurrentUser must not force user.premium
- loadConfig must not force currentUser.premium
- isPremium must return actual Telegram state only
- applyMassgramPremiumOverride must be removed or made a no-op helper not used by settings
- premiumFeaturesBlocked must not be bypassed by Massgram setting
- premiumPurchaseBlocked must not be bypassed by Massgram setting
- isPremiumUser must not special-case self due to Massgram setting
```

- [ ] **Step 2: Restore `UserConfig` to real Premium semantics**

Keep Massgram setting storage, but stop mutating `TLRPC.User.premium`.

```java
public void setCurrentUser(TLRPC.User user) {
    synchronized (sync) {
        TLRPC.User oldUser = currentUser;
        currentUser = user;
        clientUserId = user.id;
        checkPremiumSelf(oldUser, user);
    }
}

public boolean isPremium() {
    TLRPC.User user = currentUser;
    return user != null && user.premium;
}
```

- [ ] **Step 3: Remove Massgram bypasses from `MessagesController`**

Make Premium checks depend only on actual Telegram state.

```java
public boolean premiumFeaturesBlocked() {
    return premiumLocked && !getUserConfig().isPremium();
}

public boolean premiumPurchaseBlocked() {
    return premiumLocked;
}

public boolean isPremiumUser(TLRPC.User currentUser) {
    return currentUser != null && !premiumFeaturesBlocked() && currentUser.premium && !isSupportUser(currentUser);
}
```

- [ ] **Step 4: Delete dead helper usage**

Remove `applyMassgramPremiumOverride()` if it becomes unused. If keeping it for binary safety, convert it to:

```java
public void applyMassgramPremiumOverride() {
    // Intentionally left empty. Massgram premium mode no longer mutates Telegram Premium state.
}
```

- [ ] **Step 5: Run compile and grep verification**

Run: `git grep -n -I "currentUser\\.premium = true\\|return MassgramConfigManager.getInstance().isPremiumUnlockEnabled()\\|MassgramConfigManager.getInstance().isPremiumUnlockEnabled()" -- TMessagesProj/src/main/java/org/telegram/messenger`
Expected: no remaining core fake-Premium branches other than pure config access.

- [ ] **Step 6: Run targeted compile**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 7: Commit**

```bash
git add TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
git commit -m "refactor: remove fake telegram premium overrides"
```

### Task 3: Add hidden Massgram premium payload protocol

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MassgramCryptoManager.java`

- [ ] **Step 1: Add protocol constants and helper model**

Introduce explicit placeholder and protocol version constants.

```java
private static final String PREMIUM_PLACEHOLDER = "Unsupported Massgram message";
private static final String PREMIUM_PAYLOAD_PREFIX = "\u2063\u2060MGP1:";

public static final class PremiumPayload {
    public final String type;
    public final String text;

    private PremiumPayload(String type, String text) {
        this.type = type;
        this.text = text;
    }
}
```

- [ ] **Step 2: Add encoder for premium text payloads**

Encode visible placeholder plus invisible versioned payload.

```java
public String encodePremiumTextPayload(String text) {
    if (TextUtils.isEmpty(text)) {
        return null;
    }
    try {
        JSONObject object = new JSONObject();
        object.put("text", text);
        object.put("format", "plain");
        String encoded = Base64.encodeToString(object.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return PREMIUM_PLACEHOLDER + PREMIUM_PAYLOAD_PREFIX + "premium_text:" + encoded;
    } catch (Exception e) {
        FileLog.e(e);
        return null;
    }
}
```

- [ ] **Step 3: Add parser and display helpers**

Add helpers that detect payload messages and recover visible text for Massgram.

```java
public boolean isPremiumPayloadMessage(String text) {
    return !TextUtils.isEmpty(text) && text.contains(PREMIUM_PAYLOAD_PREFIX);
}

public PremiumPayload parsePremiumPayload(String text) {
    if (!isPremiumPayloadMessage(text)) {
        return null;
    }
    try {
        int index = text.indexOf(PREMIUM_PAYLOAD_PREFIX);
        String raw = text.substring(index + PREMIUM_PAYLOAD_PREFIX.length());
        int typeSeparator = raw.indexOf(':');
        if (typeSeparator <= 0) {
            return null;
        }
        String type = raw.substring(0, typeSeparator);
        String encoded = raw.substring(typeSeparator + 1);
        JSONObject object = new JSONObject(new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8));
        return new PremiumPayload(type, object.optString("text"));
    } catch (Exception ignore) {
        return null;
    }
}

public String getPremiumPlaceholder() {
    return PREMIUM_PLACEHOLDER;
}
```

- [ ] **Step 4: Keep the hidden part invisible on normal clients**

Ensure the invisible marker remains in zero-width characters only. Do not append human-readable protocol text outside the hidden section.

- [ ] **Step 5: Run targeted compile**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 6: Commit**

```bash
git add TMessagesProj/src/main/java/org/telegram/messenger/MassgramCryptoManager.java
git commit -m "feat: add hidden massgram premium payload protocol"
```

### Task 4: Encode outgoing Massgram premium text messages safely

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java`

- [ ] **Step 1: Find the narrow send path for plain text messages**

Run: `git grep -n -I "message == null && caption == null\\|retryMessageObject == null && DialogObject.isUserDialog" -- TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java`
Expected: the current text send path and existing Massgram crypto path are listed.

- [ ] **Step 2: Introduce a small predicate for premium payload sending**

Only use the payload path when:

- Massgram premium mode is enabled
- outgoing message has plain text
- the message is not a retry
- the content is not already encrypted payload text

```java
private boolean shouldSendMassgramPremiumText(MessageObject retryMessageObject, String message) {
    return retryMessageObject == null
        && MassgramConfigManager.getInstance().isPremiumUnlockEnabled()
        && !TextUtils.isEmpty(message);
}
```

- [ ] **Step 3: Replace outgoing text with encoded placeholder payload**

Apply the new protocol before normal send when the predicate matches.

```java
if (shouldSendMassgramPremiumText(retryMessageObject, message)) {
    MassgramCryptoManager cryptoManager = MassgramCryptoManager.getInstance(currentAccount);
    String payloadMessage = cryptoManager.encodePremiumTextPayload(message);
    if (payloadMessage == null) {
        return;
    }
    message = payloadMessage;
    searchLinks = false;
    webPage = null;
    mediaWebPage = null;
    entities = null;
}
```

- [ ] **Step 4: Apply the same logic to edited text messages**

Mirror the encoding behavior in `editMessage(...)` so edits do not leak original text.

```java
if (MassgramConfigManager.getInstance().isPremiumUnlockEnabled() && !TextUtils.isEmpty(message)) {
    String payloadMessage = MassgramCryptoManager.getInstance(currentAccount).encodePremiumTextPayload(message);
    if (payloadMessage == null) {
        return 0;
    }
    message = payloadMessage;
    searchLinks = false;
    entities = null;
}
```

- [ ] **Step 5: Preserve normal encryption path precedence**

Keep existing private-chat encryption logic first. Do not encode premium payloads on top of encrypted payloads. If both paths could apply, the existing encrypted path wins.

- [ ] **Step 6: Run targeted compile**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 7: Commit**

```bash
git add TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java
git commit -m "feat: encode massgram premium text messages"
```

### Task 5: Decode and render Massgram premium payloads in message text

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java`

- [ ] **Step 1: Extend display text decoding**

Reuse the existing Massgram text hook instead of adding a second rendering path.

```java
public String getDisplayText(long dialogId, String text, boolean incoming) {
    PremiumPayload premiumPayload = parsePremiumPayload(text);
    if (premiumPayload != null && "premium_text".equals(premiumPayload.type) && !TextUtils.isEmpty(premiumPayload.text)) {
        if (incoming && supportsDialog(dialogId)) {
            markPeerDetected(dialogId);
        }
        return premiumPayload.text;
    }
    ...
}
```

- [ ] **Step 2: Keep fallback safe**

If parsing fails, `getDisplayText(...)` must return the visible placeholder string, never an empty string and never partial payload bytes.

```java
if (premiumPayload == null && isPremiumPayloadMessage(text)) {
    return getPremiumPlaceholder();
}
```

- [ ] **Step 3: Verify `messageText` generation still flows through the hook**

Run: `git grep -n -I "MassgramCryptoManager.getInstance(currentAccount).getDisplayText" -- TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java`
Expected: one central hook remains in the message text generation path.

- [ ] **Step 4: Run targeted compile**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 5: Commit**

```bash
git add TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java
git commit -m "feat: decode massgram premium payloads in chats"
```

### Task 6: Restrict Massgram premium indication to profile-only UI

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`

- [ ] **Step 1: Identify current Massgram detection rows and name badge path**

Run: `git grep -n -I "massgramClientRow\\|MassgramProfileDetected\\|isPremiumUser(user)" -- TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`
Expected: the Massgram profile rows and current premium badge path are listed.

- [ ] **Step 2: Add a local profile-only predicate**

Use a narrow helper for the star.

```java
private boolean shouldShowMassgramPremiumProfileBadge(TLRPC.User user) {
    return user != null
        && !myProfile
        && MassgramConfigManager.getInstance().isPremiumUnlockEnabled()
        && user.id != 0
        && MassgramCryptoManager.getInstance(currentAccount).isPeerDetected(user.id);
}
```

- [ ] **Step 3: Replace generic Premium badge usage in profile name header**

Only use the special star when the local Massgram predicate is true. Otherwise keep existing Telegram behavior.

```java
} else if (shouldShowMassgramPremiumProfileBadge(user)) {
    rightIconIsStatus = false;
    rightIconIsPremium = true;
    nameTextView[a].setRightDrawable(getEmojiStatusDrawable(null, false, false, a));
    nameTextViewRightDrawableContentDescription = LocaleController.getString(R.string.AccDescrPremium);
} else if (getMessagesController().isPremiumUser(user)) {
    ...
}
```

- [ ] **Step 4: Keep the dedicated detection row intact**

Do not remove the existing `Massgram detected` row. It remains the explicit compatibility signal.

- [ ] **Step 5: Run targeted compile**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 6: Commit**

```bash
git add TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java
git commit -m "feat: limit massgram premium badge to profiles"
```

### Task 7: Verify that normal Telegram behavior remains intact

**Files:**
- Modify: `docs/superpowers/specs/2026-04-01-massgram-premium-messages-design.md` (only if spec drift is found)

- [ ] **Step 1: Run grep checks for leftover fake-Premium behavior**

Run: `git grep -n -I "MassgramConfigManager.getInstance().isPremiumUnlockEnabled()" -- TMessagesProj/src/main/java`
Expected: matches remain only in Massgram config, payload send path, and profile-specific UI logic.

- [ ] **Step 2: Run a project compile**

Run: `./gradlew :TMessagesProj:compileDebugJavaWithJavac`
Expected: build succeeds or reports only unrelated pre-existing errors.

- [ ] **Step 3: Manual verification checklist**

Verify these cases in a debug build:

```text
1. Toggle off -> normal message send remains unchanged.
2. Toggle on -> sending plain text produces only "Unsupported Massgram message" in official Telegram.
3. Toggle on -> same message shows decoded original text in Massgram chat list and chat bubble.
4. Editing a Massgram premium text message keeps hidden payload behavior.
5. Profile of detected Massgram peer shows detection row and local star.
6. Non-detected peers do not show the local star.
7. Account no longer unlocks unrelated Premium UI or server-gated behavior.
```

- [ ] **Step 4: Commit final verification notes if any code changed**

```bash
git status --short
```

Expected: no unexpected files changed. If only code from this feature is staged, commit:

```bash
git add <relevant files>
git commit -m "test: verify massgram premium message transport"
```
