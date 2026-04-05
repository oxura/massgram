package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MassgramConfigManager;
import org.telegram.messenger.MassgramTelemetryManager;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class MassgramIssueDetailActivity extends BaseFragment {

    private final String fingerprint;

    private LinearLayout contentLayout;
    private TextView statusView;
    private SectionCard overviewCard;
    private SectionCard versionsCard;
    private SectionCard devicesCard;
    private SectionCard usersCard;
    private SectionCard stacktraceCard;
    private SectionCard breadcrumbsCard;
    private SectionCard contextCard;

    public MassgramIssueDetailActivity(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MassgramOwnerIssueDetailTitle));
        actionBar.setOccupyStatusBar(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(16), dp(16), dp(16), dp(28));
        scrollView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        statusView = new TextView(context);
        statusView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setTextSize(14);
        statusView.setPadding(dp(6), 0, dp(6), dp(12));
        contentLayout.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        overviewCard = addSection(context, R.string.MassgramOwnerOverview);
        versionsCard = addSection(context, R.string.MassgramOwnerIssueVersions);
        devicesCard = addSection(context, R.string.MassgramOwnerIssueDevices);
        usersCard = addSection(context, R.string.MassgramOwnerIssueAffectedUsers);
        stacktraceCard = addSection(context, R.string.MassgramOwnerIssueStacktrace);
        breadcrumbsCard = addSection(context, R.string.MassgramOwnerIssueBreadcrumbs);
        contextCard = addSection(context, R.string.MassgramOwnerIssueContext);

        bindLoading(LocaleController.getString(R.string.MassgramOwnerStatsLoading));
        bindEmptyState();

        fragmentView = scrollView;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!MassgramConfigManager.getInstance().isOwner(getUserConfig().getClientUserId())) {
            finishFragment();
            return;
        }
        loadIssueDetail();
    }

    private SectionCard addSection(Context context, int titleRes) {
        TextView header = new TextView(context);
        header.setText(LocaleController.getString(titleRes));
        header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setTextSize(15);
        header.setTypeface(AndroidUtilities.bold());
        header.setPadding(dp(6), 0, dp(6), dp(8));
        contentLayout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        SectionCard card = new SectionCard(context);
        contentLayout.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View spacer = new View(context);
        contentLayout.addView(spacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 14));
        return card;
    }

    private void bindLoading(CharSequence text) {
        statusView.setText(text);
    }

    private void bindEmptyState() {
        overviewCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueFingerprint), fingerprint));
        versionsCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueVersions), LocaleController.getString(R.string.MassgramOwnerIssueNone)));
        devicesCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueDevices), LocaleController.getString(R.string.MassgramOwnerIssueNone)));
        usersCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueAffectedUsers), LocaleController.getString(R.string.MassgramOwnerIssueNone)));
        stacktraceCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueStacktrace), LocaleController.getString(R.string.MassgramOwnerIssueNone)));
        breadcrumbsCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueBreadcrumbs), LocaleController.getString(R.string.MassgramOwnerIssueNone)));
        contextCard.setRows(simpleRows(LocaleController.getString(R.string.MassgramOwnerIssueContext), LocaleController.getString(R.string.MassgramOwnerIssueNone)));
    }

    private ArrayList<RowData> simpleRows(CharSequence title, CharSequence value) {
        ArrayList<RowData> rows = new ArrayList<>();
        rows.add(new RowData(title, value, null));
        return rows;
    }

    private void loadIssueDetail() {
        bindLoading(LocaleController.getString(R.string.MassgramOwnerStatsLoading));
        MassgramTelemetryManager.getInstance().loadOwnerIssueDetail(getUserConfig().getClientUserId(), fingerprint, (data, error) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (!TextUtils.isEmpty(error) || data == null) {
                bindLoading(!TextUtils.isEmpty(error) ? error : LocaleController.getString(R.string.MassgramOwnerStatsLoadFailed));
                return;
            }
            bindLoading(LocaleController.formatString("MassgramOwnerStatsUpdated", R.string.MassgramOwnerStatsUpdated, LocaleController.getInstance().getFormatterStats().format(data.loadedAt)));
            bindDetail(data);
        });
    }

    private void bindDetail(MassgramTelemetryManager.OwnerIssueDetail data) {
        overviewCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueFingerprint), data.fingerprint, null));
            String titleValue = data.title;
            if (!TextUtils.isEmpty(data.severity)) {
                String severity = getSeverityLabel(data.severity);
                titleValue = TextUtils.isEmpty(titleValue) ? severity : titleValue + " • " + severity;
            }
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerTopIssue), titleValue, null));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueFirstSeen), formatTime(data.firstOccurredAt), null));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueLastSeenLabel), formatTime(data.lastOccurredAt), null));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueOccurrences), String.valueOf(data.totalEvents), null));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueUsers), String.valueOf(data.uniqueUsers), null));
        }});
        versionsCard.setRows(listRows(data.affectedVersions));
        devicesCard.setRows(listRows(data.affectedDevices));
        usersCard.setRows(buildUserRows(data.users));
        stacktraceCard.setRows(listRows(data.sampleStacktrace));
        breadcrumbsCard.setRows(buildBreadcrumbRows(data.sampleBreadcrumbs));
        contextCard.setRows(buildContextRows(data.sampleContext));
    }

    private ArrayList<RowData> listRows(ArrayList<String> values) {
        ArrayList<RowData> rows = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            rows.add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueNone), "", null));
            return rows;
        }
        for (String value : values) {
            rows.add(new RowData(value, "", null));
        }
        return rows;
    }

    private ArrayList<RowData> buildBreadcrumbRows(ArrayList<MassgramTelemetryManager.OwnerIssueBreadcrumb> breadcrumbs) {
        ArrayList<RowData> rows = new ArrayList<>();
        if (breadcrumbs == null || breadcrumbs.isEmpty()) {
            rows.add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueNone), "", null));
            return rows;
        }
        for (MassgramTelemetryManager.OwnerIssueBreadcrumb breadcrumb : breadcrumbs) {
            StringBuilder subtitle = new StringBuilder();
            if (!TextUtils.isEmpty(breadcrumb.screen)) {
                subtitle.append(breadcrumb.screen);
            }
            if (breadcrumb.timestamp > 0) {
                if (subtitle.length() > 0) {
                    subtitle.append(" • ");
                }
                subtitle.append(formatTime(breadcrumb.timestamp));
            }
            if (!breadcrumb.context.isEmpty()) {
                for (Map.Entry<String, String> entry : breadcrumb.context.entrySet()) {
                    subtitle.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
                }
            }
            rows.add(new RowData(breadcrumb.action, subtitle.toString(), null));
        }
        return rows;
    }

    private ArrayList<RowData> buildContextRows(LinkedHashMap<String, String> context) {
        ArrayList<RowData> rows = new ArrayList<>();
        if (context == null || context.isEmpty()) {
            rows.add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueNone), "", null));
            return rows;
        }
        for (Map.Entry<String, String> entry : context.entrySet()) {
            rows.add(new RowData(entry.getKey(), entry.getValue(), null));
        }
        return rows;
    }

    private ArrayList<RowData> buildUserRows(ArrayList<MassgramTelemetryManager.OwnerIssueUser> users) {
        ArrayList<RowData> rows = new ArrayList<>();
        if (users == null || users.isEmpty()) {
            rows.add(new RowData(LocaleController.getString(R.string.MassgramOwnerIssueNone), "", null));
            return rows;
        }
        for (MassgramTelemetryManager.OwnerIssueUser user : users) {
            String title = !TextUtils.isEmpty(user.displayName) ? user.displayName : (!TextUtils.isEmpty(user.username) ? "@" + user.username : String.valueOf(user.userId));
            String subtitle = formatTime(user.occurredAt);
            if (!TextUtils.isEmpty(user.appVersion)) {
                subtitle += " • " + user.appVersion;
            }
            rows.add(new RowData(title, subtitle, () -> openUser(user)));
        }
        return rows;
    }

    private void openUser(MassgramTelemetryManager.OwnerIssueUser entry) {
        TLRPC.User user = getMessagesController().getUser(entry.userId);
        if (user != null) {
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("user_id", entry.userId);
            presentFragment(new ProfileActivity(args));
            return;
        }
        if (!TextUtils.isEmpty(entry.username)) {
            getMessagesController().openByUserName(entry.username, this, 0);
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.MassgramKnownUsersProfileUnavailable)).show();
    }

    private String formatTime(long value) {
        if (value <= 0) {
            return LocaleController.getString(R.string.MassgramOwnerIssueNone);
        }
        return LocaleController.getInstance().getFormatterStats().format(value);
    }

    private String getSeverityLabel(String severity) {
        if ("fatal".equals(severity)) {
            return LocaleController.getString(R.string.MassgramOwnerSeverityFatal);
        } else if ("warning".equals(severity)) {
            return LocaleController.getString(R.string.MassgramOwnerSeverityWarning);
        }
        return LocaleController.getString(R.string.MassgramOwnerSeverityError);
    }

    private static final class RowData {
        final CharSequence title;
        final CharSequence value;
        final Runnable action;

        private RowData(CharSequence title, CharSequence value, Runnable action) {
            this.title = title;
            this.value = value;
            this.action = action;
        }
    }

    private final class SectionCard extends LinearLayout {

        private SectionCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(18), getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_windowBackgroundWhiteGrayText2)));
        }

        private void setRows(ArrayList<RowData> rows) {
            removeAllViews();
            for (int i = 0; i < rows.size(); i++) {
                RowData row = rows.get(i);
                addView(createRow(getContext(), row, i != rows.size() - 1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        private View createRow(Context context, RowData row, boolean divider) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(16), dp(14), dp(16), dp(14));
            container.setBackground(Theme.getSelectorDrawable(false));
            if (row.action != null) {
                container.setOnClickListener(v -> row.action.run());
            } else {
                container.setClickable(false);
            }

            TextView title = new TextView(context);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(15);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(row.title);
            container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            if (!TextUtils.isEmpty(row.value)) {
                TextView value = new TextView(context);
                value.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                value.setTextSize(14);
                value.setLineSpacing(dp(2), 1f);
                value.setText(row.value);
                value.setPadding(0, dp(4), 0, 0);
                container.addView(value, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            if (divider) {
                View dividerView = new View(context);
                dividerView.setBackgroundColor(getThemedColor(Theme.key_divider));
                container.addView(dividerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, dp(14), 0, 0));
            }
            return container;
        }
    }
}
