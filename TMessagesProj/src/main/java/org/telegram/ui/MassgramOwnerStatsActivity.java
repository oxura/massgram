package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MassgramConfigManager;
import org.telegram.messenger.MassgramTelemetryManager;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class MassgramOwnerStatsActivity extends BaseFragment {

    private static final String REPO_API_URL = "https://api.github.com/repos/oxura/massgram";
    private static final String RELEASES_API_URL = "https://api.github.com/repos/oxura/massgram/releases?per_page=20";

    private LinearLayout contentLayout;
    private TextView statusView;
    private StatsCard overviewCard;
    private StatsCard problemsCard;
    private StatsCard usersCard;
    private StatsCard releasesCard;
    private StatsCard repositoryCard;
    private StatsCard deviceCard;
    private long lastLoadedAt;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MassgramOwnerStats));
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
        problemsCard = addSection(context, R.string.MassgramOwnerProblems);
        usersCard = addSection(context, R.string.MassgramOwnerUsersSection);
        releasesCard = addSection(context, R.string.MassgramOwnerReleaseChannel);
        repositoryCard = addSection(context, R.string.MassgramOwnerRepository);
        deviceCard = addSection(context, R.string.MassgramOwnerLocalBuild);

        bindLoading(LocaleController.getString(R.string.MassgramOwnerStatsLoading));
        bindStaticRows();

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
        if (System.currentTimeMillis() - lastLoadedAt > 60_000L) {
            loadStats();
        }
    }

    private StatsCard addSection(Context context, int titleRes) {
        TextView header = new TextView(context);
        header.setText(LocaleController.getString(titleRes));
        header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setTextSize(15);
        header.setTypeface(AndroidUtilities.bold());
        header.setPadding(dp(6), 0, dp(6), dp(8));
        contentLayout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        StatsCard card = new StatsCard(context);
        contentLayout.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(createSpacer(context, 14), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return card;
    }

    private View createSpacer(Context context, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, heightDp));
        return spacer;
    }

    private void bindStaticRows() {
        overviewCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerTotalUsers), "—"));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerActiveUsers), "—"));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerOfflineUsers), "—"));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerBetaUsers), "—"));
        }});
        problemsCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerCrashUsers24h), "\u2014"));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerNewIssues24h), "\u2014"));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerTopIssue), "\u2014"));
            add(new RowData(
                LocaleController.getString(R.string.MassgramOwnerIssuesInbox),
                LocaleController.getString(R.string.Open),
                LocaleController.getString(R.string.MassgramOwnerIssuesInfo),
                () -> presentFragment(new MassgramIssuesActivity())
            ));
        }});
        usersCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(
                LocaleController.getString(R.string.MassgramKnownUsers),
                LocaleController.getString(R.string.Open),
                LocaleController.getString(R.string.MassgramOwnerUsersInfo),
                () -> presentFragment(new MassgramKnownUsersActivity())
            ));
        }});
        deviceCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerInstalledVersion), BuildVars.BUILD_VERSION_STRING));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerPackage), ApplicationLoader.getApplicationId()));
        }});
    }

    private void bindLoading(CharSequence status) {
        statusView.setText(status);
    }

    private void loadStats() {
        bindLoading(LocaleController.getString(R.string.MassgramOwnerStatsLoading));
        loadDashboardStats();
        loadIssuesStats();
        loadRepositoryStats();
    }

    private void loadDashboardStats() {
        MassgramTelemetryManager.getInstance().loadOwnerDashboard(getUserConfig().getClientUserId(), "", true, (data, error) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (error != null) {
                bindLoading(LocaleController.getString(R.string.MassgramOwnerStatsLoadFailed));
                return;
            }
            if (data != null) {
                bindLoading(LocaleController.formatString("MassgramOwnerStatsUpdated", R.string.MassgramOwnerStatsUpdated, LocaleController.getInstance().getFormatterStats().format(data.loadedAt)));
                overviewCard.setRows(new ArrayList<RowData>() {{
                    add(new RowData(LocaleController.getString(R.string.MassgramOwnerTotalUsers), formatCount(data.totalUsers)));
                    add(new RowData(LocaleController.getString(R.string.MassgramOwnerActiveUsers), formatCount(data.activeUsers), LocaleController.getString(R.string.MassgramOwnerActiveUsersInfo)));
                    add(new RowData(LocaleController.getString(R.string.MassgramOwnerOfflineUsers), formatCount(data.offlineUsers)));
                    add(new RowData(LocaleController.getString(R.string.MassgramOwnerBetaUsers), formatCount(data.betaUsers)));
                }});
            }
        });
    }

    private void loadIssuesStats() {
        MassgramTelemetryManager.getInstance().loadOwnerIssues(getUserConfig().getClientUserId(), "", true, (data, error) -> {
            if (getParentActivity() == null || data == null || error != null) {
                return;
            }
            problemsCard.setRows(new ArrayList<RowData>() {{
                add(new RowData(LocaleController.getString(R.string.MassgramOwnerCrashUsers24h), formatCount(data.summary.crashUsers24h)));
                add(new RowData(LocaleController.getString(R.string.MassgramOwnerNewIssues24h), formatCount(data.summary.newIssues24h)));
                add(new RowData(
                    LocaleController.getString(R.string.MassgramOwnerTopIssue),
                    !TextUtils.isEmpty(data.summary.topTitle) ? data.summary.topTitle : LocaleController.getString(R.string.MassgramOwnerIssueNone),
                    !TextUtils.isEmpty(data.summary.topFingerprint) ? data.summary.topFingerprint : null
                ));
                add(new RowData(
                    LocaleController.getString(R.string.MassgramOwnerIssuesInbox),
                    LocaleController.getString(R.string.Open),
                    LocaleController.getString(R.string.MassgramOwnerIssuesInfo),
                    () -> presentFragment(new MassgramIssuesActivity())
                ));
            }});
        });
    }

    private void loadRepositoryStats() {
        Utilities.globalQueue.postRunnable(() -> {
            OwnerStatsData data = new OwnerStatsData();
            String error = null;
            try {
                JSONObject repo = new JSONObject(readUrl(REPO_API_URL));
                JSONArray releases = new JSONArray(readUrl(RELEASES_API_URL));

                data.stars = repo.optInt("stargazers_count");
                data.forks = repo.optInt("forks_count");
                data.watchers = repo.optInt("subscribers_count");
                data.openIssues = repo.optInt("open_issues_count");

                long totalDownloads = 0;
                long stableDownloads = 0;
                long betaDownloads = 0;

                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    if (release.optBoolean("draft")) {
                        continue;
                    }
                    String tagName = release.optString("tag_name");
                    boolean beta = "beta".equalsIgnoreCase(tagName) || release.optBoolean("prerelease");
                    if (!beta && data.latestStableTag == null) {
                        data.latestStableTag = tagName;
                        data.latestStableName = release.optString("name");
                    }
                    if (beta && data.latestBetaTag == null) {
                        data.latestBetaTag = tagName;
                        data.latestBetaName = release.optString("name");
                    }

                    JSONArray assets = release.optJSONArray("assets");
                    if (assets == null) {
                        continue;
                    }
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String name = asset.optString("name");
                        long downloads = asset.optLong("download_count");
                        if (name.endsWith(".apk")) {
                            totalDownloads += downloads;
                            if (beta) {
                                betaDownloads += downloads;
                            } else {
                                stableDownloads += downloads;
                            }
                        }
                    }
                }

                data.totalDownloads = totalDownloads;
                data.stableDownloads = stableDownloads;
                data.betaDownloads = betaDownloads;
                data.approxUsers = stableDownloads;
                data.loadedAt = System.currentTimeMillis();
            } catch (Exception e) {
                FileLog.e(e);
                error = e.getMessage();
            }

            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalError != null) {
                    return;
                }
                lastLoadedAt = data.loadedAt;
                bindOwnerStats(data);
            });
        });
    }

    private void bindOwnerStats(OwnerStatsData data) {
        bindLoading(LocaleController.formatString("MassgramOwnerStatsUpdated", R.string.MassgramOwnerStatsUpdated, LocaleController.getInstance().getFormatterStats().format(data.loadedAt)));

        releasesCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerLatestStable), data.latestStableName != null ? data.latestStableName : "-",
                data.latestStableTag != null ? data.latestStableTag : null));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerLatestBeta), data.latestBetaName != null ? data.latestBetaName : "-",
                data.latestBetaTag != null ? data.latestBetaTag : null));
        }});

        repositoryCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerStars), formatCount(data.stars)));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerForks), formatCount(data.forks)));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerWatchers), formatCount(data.watchers)));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerOpenIssues), formatCount(data.openIssues)));
        }});

        deviceCard.setRows(new ArrayList<RowData>() {{
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerInstalledVersion), BuildVars.BUILD_VERSION_STRING));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerPackage), ApplicationLoader.getApplicationId()));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerOwnerAccess), LocaleController.getString(R.string.MassgramOwnerEnabled)));
            add(new RowData(LocaleController.getString(R.string.MassgramOwnerBetaAccess), ApplicationLoader.applicationLoaderInstance.isBetaTester(getUserConfig().getClientUserId()) ? LocaleController.getString(R.string.MassgramOwnerEnabled) : LocaleController.getString(R.string.MassgramOwnerDisabled)));
        }});
    }

    private String readUrl(String urlString) throws Exception {
        HttpURLConnection connection = null;
        BufferedInputStream inputStream = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Massgram/" + BuildVars.BUILD_VERSION_STRING);
            connection.connect();
            inputStream = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignore) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String formatCount(long value) {
        if (value < 1000) {
            return String.valueOf(value);
        }
        DecimalFormat decimalFormat = new DecimalFormat(value >= 10000 ? "0.#k" : "0.0k");
        return decimalFormat.format(value / 1000f);
    }

    private static final class OwnerStatsData {
        long loadedAt;
        long approxUsers;
        long totalDownloads;
        long stableDownloads;
        long betaDownloads;
        int stars;
        int forks;
        int watchers;
        int openIssues;
        String latestStableTag;
        String latestStableName;
        String latestBetaTag;
        String latestBetaName;
    }

    private static final class RowData {
        final CharSequence title;
        final CharSequence value;
        final CharSequence subtitle;
        final Runnable action;

        private RowData(CharSequence title, CharSequence value) {
            this(title, value, null, null);
        }

        private RowData(CharSequence title, CharSequence value, CharSequence subtitle) {
            this(title, value, subtitle, null);
        }

        private RowData(CharSequence title, CharSequence value, CharSequence subtitle, Runnable action) {
            this.title = title;
            this.value = value;
            this.subtitle = subtitle;
            this.action = action;
        }
    }

    private final class StatsCard extends LinearLayout {

        private final ArrayList<StatRow> rows = new ArrayList<>();

        private StatsCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            int normal = getThemedColor(Theme.key_windowBackgroundWhite);
            int pressed = ColorUtils.blendARGB(normal, getThemedColor(Theme.key_listSelector), 0.22f);
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22), normal, pressed));
            setClipToOutline(true);
        }

        private void setRows(ArrayList<RowData> data) {
            while (rows.size() < data.size()) {
                StatRow row = new StatRow(getContext());
                rows.add(row);
                addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            for (int i = 0; i < rows.size(); i++) {
                StatRow row = rows.get(i);
                if (i < data.size()) {
                    RowData rowData = data.get(i);
                    row.bind(rowData.title, rowData.value, rowData.subtitle, i == data.size() - 1);
                    row.setOnClickListener(rowData.action == null ? null : v -> rowData.action.run());
                    row.setEnabled(rowData.action != null);
                    row.setVisibility(VISIBLE);
                } else {
                    row.setOnClickListener(null);
                    row.setEnabled(false);
                    row.setVisibility(GONE);
                }
            }
        }
    }

    private final class StatRow extends FrameLayout {

        private final TextView titleView;
        private final TextView valueView;
        private final TextView subtitleView;
        private final View divider;

        private StatRow(Context context) {
            super(context);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(13), dp(18), dp(13));
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            titleView = new TextView(context);
            titleView.setTextSize(16);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            valueView = new TextView(context);
            valueView.setTextSize(16);
            valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
            valueView.setTypeface(AndroidUtilities.bold());
            row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(13);
            subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setPadding(dp(18), 0, dp(18), dp(12));
            container.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            divider = new View(context);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_divider), 144));
            container.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 18, 0, 18, 0));
        }

        private void bind(CharSequence title, CharSequence value, CharSequence subtitle, boolean last) {
            titleView.setText(title);
            valueView.setText(value);
            subtitleView.setVisibility(subtitle == null || subtitle.length() == 0 ? GONE : VISIBLE);
            subtitleView.setText(subtitle);
            divider.setVisibility(last ? GONE : VISIBLE);
        }
    }
}
