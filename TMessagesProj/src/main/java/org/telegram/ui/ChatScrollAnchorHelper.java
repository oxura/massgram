package org.telegram.ui;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

final class ChatScrollAnchorHelper {

    private ChatScrollAnchorHelper() {
    }

    static <T> int findAnchorIndex(List<T> items, T directReference, MessageAnchor anchor, ToIntFunction<T> messageIdExtractor, ToLongFunction<T> dialogIdExtractor) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        if (directReference != null) {
            int directIndex = items.indexOf(directReference);
            if (directIndex >= 0) {
                return directIndex;
            }
        }
        if (anchor == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            if (item != null && anchor.matches(dialogIdExtractor.applyAsLong(item), messageIdExtractor.applyAsInt(item))) {
                return i;
            }
        }
        return -1;
    }

    static <T> int resolveBottomPosition(List<T> items, boolean skipLeadingItems, Predicate<T> skipPredicate) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        int position = 0;
        if (skipLeadingItems) {
            while (position < items.size() && skipPredicate.test(items.get(position))) {
                position++;
            }
        }
        return Math.min(position, items.size() - 1);
    }

    static final class MessageAnchor {
        private final long dialogId;
        private final int messageId;

        private MessageAnchor(long dialogId, int messageId) {
            this.dialogId = dialogId;
            this.messageId = messageId;
        }

        static MessageAnchor of(long dialogId, int messageId) {
            return new MessageAnchor(dialogId, messageId);
        }

        boolean matches(long otherDialogId, int otherMessageId) {
            return dialogId == otherDialogId && messageId == otherMessageId;
        }
    }
}
