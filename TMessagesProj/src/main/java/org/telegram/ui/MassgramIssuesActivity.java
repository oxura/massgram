package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MassgramConfigManager;
import org.telegram.messenger.MassgramTelemetryManager;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchField;

import java.util.ArrayList;

public class MassgramIssuesActivity extends BaseFragment {

    private final ArrayList<MassgramTelemetryManager.OwnerIssueEntry> issues = new ArrayList<>();

    private SearchField searchField;
    private TextView resultsLabel;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private ListAdapter adapter;
    private Runnable searchRunnable;
    private String currentQuery = "";
    private long lastLoadedAt;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MassgramOwnerIssuesTitle));
        actionBar.setOccupyStatusBar(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        searchField = new SearchField(context, LocaleController.isRTL, resourceProvider) {
            @Override
            public void onTextChange(String text) {
                scheduleSearch(text);
            }
        };
        searchField.setHint(LocaleController.getString(R.string.MassgramOwnerIssuesSearchHint));
        content.addView(searchField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58));

        resultsLabel = new TextView(context);
        resultsLabel.setTextSize(14);
        resultsLabel.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        resultsLabel.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(2), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        content.addView(resultsLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout listContainer = new FrameLayout(context);
        content.addView(listContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        emptyView = new EmptyTextProgressView(context, null, resourceProvider);
        emptyView.setShowAtTop(false);
        emptyView.setText(LocaleController.getString(R.string.MassgramOwnerIssuesEmpty));
        listContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position < 0 || position >= issues.size()) {
                return;
            }
            MassgramTelemetryManager.OwnerIssueEntry entry = issues.get(position);
            if (!TextUtils.isEmpty(entry.fingerprint)) {
                presentFragment(new MassgramIssueDetailActivity(entry.fingerprint));
            }
        });
        listContainer.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        long clientUserId = getUserConfig().getClientUserId();
        if (!MassgramConfigManager.getInstance().isOwner(clientUserId)) {
            finishFragment();
            return;
        }
        if (System.currentTimeMillis() - lastLoadedAt > 15_000L || issues.isEmpty()) {
            loadIssues(true, currentQuery);
        } else {
            updateResultsLabel();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        }
    }

    private void scheduleSearch(String query) {
        currentQuery = query == null ? "" : query.trim();
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        }
        searchRunnable = () -> loadIssues(true, currentQuery);
        AndroidUtilities.runOnUIThread(searchRunnable, 220L);
    }

    private void loadIssues(boolean force, String query) {
        emptyView.showProgress();
        MassgramTelemetryManager.getInstance().loadOwnerIssues(getUserConfig().getClientUserId(), query, force, (data, error) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (!TextUtils.isEmpty(error)) {
                emptyView.setText(error);
                emptyView.showTextView();
                return;
            }
            issues.clear();
            if (data != null) {
                issues.addAll(data.issues);
                lastLoadedAt = data.loadedAt;
            }
            adapter.notifyDataSetChanged();
            updateResultsLabel();
            emptyView.setText(TextUtils.isEmpty(query) ? LocaleController.getString(R.string.MassgramOwnerIssuesEmpty) : LocaleController.getString(R.string.NoResult));
            emptyView.showTextView();
        });
    }

    private void updateResultsLabel() {
        if (resultsLabel == null) {
            return;
        }
        if (TextUtils.isEmpty(currentQuery)) {
            resultsLabel.setText(LocaleController.formatString("MassgramOwnerIssuesCount", R.string.MassgramOwnerIssuesCount, issues.size()));
        } else {
            resultsLabel.setText(LocaleController.formatString("MassgramOwnerIssuesFiltered", R.string.MassgramOwnerIssuesFiltered, issues.size()));
        }
    }

    private CharSequence buildSubtitle(MassgramTelemetryManager.OwnerIssueEntry entry) {
        String severity = getSeverityLabel(entry.severity);
        String users = LocaleController.formatString("MassgramOwnerIssueUsers", R.string.MassgramOwnerIssueUsers, formatCount(entry.uniqueUsers));
        String occurrences = LocaleController.formatString("MassgramOwnerIssueOccurrences", R.string.MassgramOwnerIssueOccurrences, formatCount(entry.totalEvents));
        String lastSeen = entry.lastOccurredAt > 0
            ? LocaleController.formatString("MassgramOwnerIssueLastSeen", R.string.MassgramOwnerIssueLastSeen, LocaleController.getInstance().getFormatterStats().format(entry.lastOccurredAt))
            : null;
        StringBuilder builder = new StringBuilder();
        builder.append(severity);
        if (!TextUtils.isEmpty(entry.screen)) {
            builder.append(" • ").append(entry.screen);
        }
        builder.append(" • ").append(users);
        builder.append(" • ").append(occurrences);
        if (!TextUtils.isEmpty(lastSeen)) {
            builder.append('\n').append(lastSeen);
        }
        if (!TextUtils.isEmpty(entry.appVersion)) {
            builder.append(" • ").append(entry.appVersion);
        }
        return builder.toString();
    }

    private String getSeverityLabel(String severity) {
        if ("fatal".equals(severity)) {
            return LocaleController.getString(R.string.MassgramOwnerSeverityFatal);
        } else if ("warning".equals(severity)) {
            return LocaleController.getString(R.string.MassgramOwnerSeverityWarning);
        }
        return LocaleController.getString(R.string.MassgramOwnerSeverityError);
    }

    private String formatCount(long value) {
        return String.valueOf(value);
    }

    private final class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return issues.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new IssueCell(context));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            IssueCell cell = (IssueCell) holder.itemView;
            MassgramTelemetryManager.OwnerIssueEntry entry = issues.get(position);
            String title = !TextUtils.isEmpty(entry.title) ? entry.title : entry.fingerprint;
            cell.bind(title, buildSubtitle(entry), position != issues.size() - 1);
        }
    }

    private final class IssueCell extends FrameLayout {
        private final TextView titleView;
        private final TextView subtitleView;
        private boolean needDivider;

        private IssueCell(Context context) {
            super(context);
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(18), AndroidUtilities.dp(14));

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(16);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(13);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            container.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        }

        private void bind(CharSequence title, CharSequence subtitle, boolean divider) {
            titleView.setText(title);
            subtitleView.setText(subtitle);
            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(18), getHeight() - 1, getWidth(), getHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(getMeasuredWidth(), Math.max(getMeasuredHeight(), AndroidUtilities.dp(88)));
        }
    }
}
