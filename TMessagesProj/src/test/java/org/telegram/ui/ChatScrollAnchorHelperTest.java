package org.telegram.ui;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChatScrollAnchorHelperTest {

    @Test
    public void findsAnchorByStableIdsWhenObjectReferenceWasReplaced() {
        TestRow oldAnchor = new TestRow(100L, 7, false);
        TestRow replacementAnchor = new TestRow(100L, 7, false);
        List<TestRow> rows = Arrays.asList(
            new TestRow(100L, 8, false),
            replacementAnchor,
            new TestRow(100L, 6, false)
        );

        int index = ChatScrollAnchorHelper.findAnchorIndex(
            rows,
            oldAnchor,
            ChatScrollAnchorHelper.MessageAnchor.of(oldAnchor.dialogId, oldAnchor.messageId),
            row -> row.messageId,
            row -> row.dialogId
        );

        assertEquals(1, index);
    }

    @Test
    public void returnsMissingIndexWhenAnchorDoesNotExist() {
        List<TestRow> rows = Arrays.asList(
            new TestRow(100L, 8, false),
            new TestRow(100L, 6, false)
        );

        int index = ChatScrollAnchorHelper.findAnchorIndex(
            rows,
            null,
            ChatScrollAnchorHelper.MessageAnchor.of(100L, 7),
            row -> row.messageId,
            row -> row.dialogId
        );

        assertEquals(-1, index);
    }

    @Test
    public void clampsBottomPositionWhenAllLeadingRowsAreSponsored() {
        List<TestRow> rows = Arrays.asList(
            new TestRow(100L, 8, true),
            new TestRow(100L, 7, true)
        );

        int index = ChatScrollAnchorHelper.resolveBottomPosition(rows, true, row -> row.sponsored);

        assertEquals(1, index);
    }

    private static final class TestRow {
        private final long dialogId;
        private final int messageId;
        private final boolean sponsored;

        private TestRow(long dialogId, int messageId, boolean sponsored) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.sponsored = sponsored;
        }
    }
}
