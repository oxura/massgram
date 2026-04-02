package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.GhostModeManager;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MassgramConfigManager;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

public class MassgramSettingsActivity extends UniversalFragment {

    private static final int BUTTON_LIQUID_GLASS = 1;
    private static final int BUTTON_GHOST_MODE = 2;
    private static final int BUTTON_SAVE_DELETED = 3;
    private static final int BUTTON_FORCE_RELAY_CALLS = 4;
    private static final int BUTTON_DISABLE_LINK_PREVIEWS = 5;
    private static final int BUTTON_CHECK_UPDATES = 6;
    private static final int BUTTON_CHECK_BETA_UPDATES = 7;
    private static final int BUTTON_BLOCK_SPONSORED = 8;
    private static final int BUTTON_DISABLE_LOCAL_STATS = 9;
    private static final int BUTTON_OWNER_STATS = 10;
    private static final int BUTTON_UNLOCK_PREMIUM = 11;

    private final HashSet<Integer> expandedInfoRows = new HashSet<>();

    private LinearLayout settingsContentView;
    private LanguagePillView languagePillView;
    private MassgramSettingRow liquidGlassRow;
    private MassgramSettingRow ghostModeRow;
    private MassgramSettingRow saveDeletedRow;
    private MassgramSettingRow relayCallsRow;
    private MassgramSettingRow disableLinkPreviewRow;
    private MassgramSettingRow blockSponsoredRow;
    private MassgramSettingRow disableLocalStatsRow;
    private MassgramSettingRow unlockPremiumRow;
    private MassgramActionRow updateRow;
    private MassgramActionRow betaUpdateRow;
    private MassgramActionRow ownerStatsRow;
    private MassgramDeveloperCard developerCard;

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        ensureLanguagePill(context);
        bindSettingsContent();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ensureLanguagePill(getContext());
        bindSettingsContent();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MassgramSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ensureSettingsContent(listView != null ? listView.getContext() : getContext());
        bindSettingsContent();
        items.add(UItem.asFullyCustom(settingsContentView));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        // Весь интерактив живет внутри кастомного layout.
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void ensureLanguagePill(Context context) {
        if (context == null) {
            return;
        }
        if (languagePillView == null) {
            languagePillView = new LanguagePillView(context);
        }
        if (languagePillView.getParent() != actionBar) {
            if (languagePillView.getParent() instanceof ViewGroup) {
                ((ViewGroup) languagePillView.getParent()).removeView(languagePillView);
            }
            actionBar.addView(languagePillView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 34, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        }
        updateLanguagePill();
    }

    private void ensureSettingsContent(Context context) {
        if (settingsContentView != null) {
            return;
        }
        settingsContentView = new LinearLayout(context);
        settingsContentView.setOrientation(LinearLayout.VERTICAL);
        settingsContentView.setPadding(dp(16), dp(14), dp(16), dp(28));

        settingsContentView.addView(createSectionHeader(context, R.string.MassgramSettingsAppearance));
        LinearLayout appearanceCard = createSectionCard(context);
        liquidGlassRow = new MassgramSettingRow(context, R.drawable.msg_theme);
        appearanceCard.addView(liquidGlassRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        settingsContentView.addView(appearanceCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        settingsContentView.addView(createSpacer(context, 14));

        settingsContentView.addView(createSectionHeader(context, R.string.MassgramSettingsPrivacy));
        LinearLayout privacyCard = createSectionCard(context);
        ghostModeRow = new MassgramSettingRow(context, R.drawable.ghost);
        saveDeletedRow = new MassgramSettingRow(context, R.drawable.msg_delete);
        relayCallsRow = new MassgramSettingRow(context, R.drawable.outline_shield_lock_24);
        disableLinkPreviewRow = new MassgramSettingRow(context, R.drawable.msg_link);
        blockSponsoredRow = new MassgramSettingRow(context, R.drawable.outline_shield_check);
        disableLocalStatsRow = new MassgramSettingRow(context, R.drawable.mini_stats);
        unlockPremiumRow = new MassgramSettingRow(context, R.drawable.msg_premium_liststar);
        privacyCard.addView(ghostModeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        privacyCard.addView(saveDeletedRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        privacyCard.addView(relayCallsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        privacyCard.addView(disableLinkPreviewRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        privacyCard.addView(blockSponsoredRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        privacyCard.addView(disableLocalStatsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        privacyCard.addView(unlockPremiumRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        settingsContentView.addView(privacyCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        settingsContentView.addView(createSpacer(context, 14));

        settingsContentView.addView(createSectionHeader(context, R.string.MassgramSettingsUpdates));
        LinearLayout updatesCard = createSectionCard(context);
        updateRow = new MassgramActionRow(context, R.drawable.msg_download_settings);
        updatesCard.addView(updateRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (isCurrentAccountBetaTester()) {
            betaUpdateRow = new MassgramActionRow(context, R.drawable.msg_download_settings);
            updatesCard.addView(betaUpdateRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        if (isOwnerAccount()) {
            ownerStatsRow = new MassgramActionRow(context, R.drawable.msg_stats);
            updatesCard.addView(ownerStatsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        settingsContentView.addView(updatesCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        settingsContentView.addView(createSpacer(context, 14));

        settingsContentView.addView(createSectionHeader(context, R.string.MassgramSettingsSupport));
        LinearLayout supportCard = createSectionCard(context);
        developerCard = new MassgramDeveloperCard(context);
        supportCard.addView(developerCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        settingsContentView.addView(supportCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void bindSettingsContent() {
        if (settingsContentView == null) {
            return;
        }
        ensureLanguagePill(getContext());
        actionBar.setTitle(getTitle());
        updateLanguagePill();
        if (languagePillView != null) {
            languagePillView.refreshBackground();
        }

        GhostModeManager ghostModeManager = GhostModeManager.getInstance();
        MassgramConfigManager configManager = MassgramConfigManager.getInstance();
        final boolean glassSupported = LiteMode.supportsLiquidGlass();

        bindRow(
            liquidGlassRow,
            BUTTON_LIQUID_GLASS,
            LocaleController.getString(R.string.MassgramLiquidGlass),
            LocaleController.getString(glassSupported ? R.string.MassgramLiquidGlassInfo : R.string.MassgramLiquidGlassUnsupported),
            LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS),
            glassSupported,
            true,
            this::toggleLiquidGlass
        );
        bindRow(
            ghostModeRow,
            BUTTON_GHOST_MODE,
            LocaleController.getString(R.string.MassgramGhostMode),
            LocaleController.getString(R.string.MassgramGhostModeInfo),
            ghostModeManager.isGhostModeEnabled(),
            true,
            false,
            () -> {
                ghostModeManager.setGhostModeEnabled(!ghostModeManager.isGhostModeEnabled());
                refreshSettingsState();
            }
        );
        bindRow(
            saveDeletedRow,
            BUTTON_SAVE_DELETED,
            LocaleController.getString(R.string.MassgramSaveDeletedMessages),
            LocaleController.getString(R.string.MassgramSaveDeletedMessagesInfo),
            ghostModeManager.isSaveDeletedMessagesEnabled(),
            true,
            false,
            () -> {
                ghostModeManager.setSaveDeletedMessagesEnabled(!ghostModeManager.isSaveDeletedMessagesEnabled());
                refreshSettingsState();
            }
        );
        bindRow(
            relayCallsRow,
            BUTTON_FORCE_RELAY_CALLS,
            LocaleController.getString(R.string.MassgramRelayOnlyCalls),
            LocaleController.getString(R.string.MassgramRelayOnlyCallsInfo),
            ghostModeManager.isForceRelayCallsEnabled(),
            true,
            false,
            () -> {
                ghostModeManager.setForceRelayCallsEnabled(!ghostModeManager.isForceRelayCallsEnabled());
                refreshSettingsState();
            }
        );
        bindRow(
            disableLinkPreviewRow,
            BUTTON_DISABLE_LINK_PREVIEWS,
            LocaleController.getString(R.string.MassgramDisableLinkPreviews),
            LocaleController.getString(R.string.MassgramDisableLinkPreviewsInfo),
            ghostModeManager.isDisableLinkPreviewsEnabled(),
            true,
            false,
            () -> {
                ghostModeManager.setDisableLinkPreviewsEnabled(!ghostModeManager.isDisableLinkPreviewsEnabled());
                refreshSettingsState();
            }
        );
        bindRow(
            blockSponsoredRow,
            BUTTON_BLOCK_SPONSORED,
            LocaleController.getString(R.string.MassgramBlockSponsored),
            LocaleController.getString(R.string.MassgramBlockSponsoredInfo),
            configManager.isSponsoredMessagesBlocked(),
            true,
            false,
            () -> {
                configManager.setSponsoredMessagesBlocked(!configManager.isSponsoredMessagesBlocked());
                refreshSettingsState();
            }
        );
        bindRow(
            disableLocalStatsRow,
            BUTTON_DISABLE_LOCAL_STATS,
            LocaleController.getString(R.string.MassgramDisableLocalStats),
            LocaleController.getString(R.string.MassgramDisableLocalStatsInfo),
            configManager.isLocalStatsDisabled(),
            true,
            true,
            () -> {
                configManager.setLocalStatsDisabled(!configManager.isLocalStatsDisabled());
                refreshSettingsState();
            }
        );
        bindRow(
            unlockPremiumRow,
            BUTTON_UNLOCK_PREMIUM,
            LocaleController.getString(R.string.MassgramUnlockPremium),
            LocaleController.getString(R.string.MassgramUnlockPremiumInfo),
            configManager.isPremiumUnlockEnabled(),
            true,
            true,
            () -> {
                boolean newValue = !configManager.isPremiumUnlockEnabled();
                configManager.setPremiumUnlockEnabled(newValue);
                refreshSettingsState();
            }
        );
        if (updateRow != null) {
            updateRow.bind(
                BUTTON_CHECK_UPDATES,
                LocaleController.getString(R.string.MassgramCheckUpdates),
                LocaleController.formatString("MassgramCurrentVersion", R.string.MassgramCurrentVersion, BuildVars.BUILD_VERSION_STRING),
                LocaleController.getString(R.string.Update),
                LocaleController.getString(R.string.MassgramChangelogAction),
                betaUpdateRow == null && ownerStatsRow == null,
                this::checkForUpdates,
                this::showStableChangelog
            );
        }
        if (betaUpdateRow != null) {
            betaUpdateRow.bind(
                BUTTON_CHECK_BETA_UPDATES,
                LocaleController.getString(R.string.MassgramBetaCheckUpdates),
                LocaleController.formatString("MassgramBetaCurrentVersion", R.string.MassgramBetaCurrentVersion, BuildVars.BUILD_VERSION_STRING),
                LocaleController.getString(R.string.Update),
                true,
                this::checkForBetaUpdates
            );
        }
        if (ownerStatsRow != null) {
            ownerStatsRow.bind(
                BUTTON_OWNER_STATS,
                LocaleController.getString(R.string.MassgramOwnerStats),
                LocaleController.getString(R.string.MassgramOwnerStatsInfo),
                LocaleController.getString(R.string.Open),
                true,
                () -> presentFragment(new MassgramOwnerStatsActivity())
            );
        }
        if (developerCard != null) {
            developerCard.bind(
                LocaleController.getString(R.string.MassgramDeveloperTitle),
                LocaleController.getString(R.string.MassgramDeveloperHandle),
                LocaleController.getString(R.string.MassgramDeveloperInfo),
                LocaleController.getString(R.string.MassgramContributorTitle),
                LocaleController.getString(R.string.MassgramContributorHandle),
                LocaleController.getString(R.string.MassgramContributorInfo)
            );
        }
    }

    private void bindRow(MassgramSettingRow row, int id, CharSequence title, CharSequence description, boolean checked, boolean enabled, boolean last, Runnable onToggle) {
        if (row == null) {
            return;
        }
        row.bind(id, title, description, checked, enabled, expandedInfoRows.contains(id), last, onToggle);
        row.setOnHelpClickListener(() -> {
            boolean expanded = !expandedInfoRows.contains(id);
            if (expanded) {
                expandedInfoRows.add(id);
            } else {
                expandedInfoRows.remove(id);
            }
            row.setInfoExpanded(expanded, true);
        });
    }

    private void refreshSettingsState() {
        bindSettingsContent();
        if (listView != null) {
            listView.invalidate();
        }
        if (settingsContentView != null) {
            settingsContentView.invalidate();
        }
    }

    private void toggleLiquidGlass() {
        if (!LiteMode.supportsLiquidGlass()) {
            return;
        }
        LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, !LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS));
        Theme.reloadWallpaper(true);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
        refreshSettingsState();
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.rebuildAllFragments(true);
        }
    }

    private void checkForUpdates() {
        if (getParentActivity() == null) {
            return;
        }
        if (!ApplicationLoader.applicationLoaderInstance.hasCustomUpdateConfig()) {
            showDialog(AlertsCreator.createSimpleAlert(
                getParentActivity(),
                LocaleController.getString(R.string.AppUpdate),
                LocaleController.getString(R.string.MassgramUpdateSourceNotConfigured)
            ).create());
            return;
        }
        ApplicationLoader.applicationLoaderInstance.checkUpdate(true, () -> {
            if (getParentActivity() == null) {
                return;
            }
            String error = ApplicationLoader.applicationLoaderInstance.getLastUpdateError();
            if (!TextUtils.isEmpty(error)) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
                return;
            }
            BetaUpdate update = ApplicationLoader.applicationLoaderInstance.getUpdate();
            if (update != null) {
                ApplicationLoader.applicationLoaderInstance.showCustomUpdateAppPopup(getParentActivity(), update, currentAccount);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.YourVersionIsLatest)).show();
            }
        });
    }

    private void checkForBetaUpdates() {
        if (getParentActivity() == null) {
            return;
        }
        if (!isCurrentAccountBetaTester()) {
            return;
        }
        if (!ApplicationLoader.applicationLoaderInstance.hasCustomBetaUpdateConfig()) {
            showDialog(AlertsCreator.createSimpleAlert(
                getParentActivity(),
                LocaleController.getString(R.string.MassgramBetaCheckUpdates),
                LocaleController.getString(R.string.MassgramBetaUpdateSourceNotConfigured)
            ).create());
            return;
        }
        ApplicationLoader.applicationLoaderInstance.checkBetaUpdate(true, () -> {
            if (getParentActivity() == null) {
                return;
            }
            String error = ApplicationLoader.applicationLoaderInstance.getLastBetaUpdateError();
            if (!TextUtils.isEmpty(error)) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
                return;
            }
            BetaUpdate update = ApplicationLoader.applicationLoaderInstance.getBetaUpdate();
            if (update != null) {
                ApplicationLoader.applicationLoaderInstance.showCustomBetaUpdateAppPopup(getParentActivity(), update, currentAccount);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.MassgramBetaYourVersionIsLatest)).show();
            }
        });
    }

    private void showStableChangelog() {
        if (getParentActivity() == null) {
            return;
        }
        if (!ApplicationLoader.applicationLoaderInstance.hasStableChangelogConfig()) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.MassgramChangelogSourceNotConfigured)).show();
            return;
        }
        ApplicationLoader.applicationLoaderInstance.checkStableChangelog(true, () -> {
            if (getParentActivity() == null) {
                return;
            }
            ArrayList<ApplicationLoader.ChangelogEntry> entries = ApplicationLoader.applicationLoaderInstance.getStableChangelogEntries();
            String error = ApplicationLoader.applicationLoaderInstance.getLastStableChangelogError();
            if (!TextUtils.isEmpty(error) && (entries == null || entries.isEmpty())) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
                return;
            }
            if (entries == null || entries.isEmpty()) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.MassgramChangelogEmpty)).show();
                return;
            }
            showStableChangelogSheet(entries);
        });
    }

    private void showStableChangelogSheet(ArrayList<ApplicationLoader.ChangelogEntry> entries) {
        if (getParentActivity() == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, getResourceProvider());
        builder.setApplyTopPadding(false);
        builder.setApplyBottomPadding(false);

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);

        HeaderCell headerCell = new HeaderCell(getParentActivity(), getResourceProvider());
        headerCell.setText(LocaleController.getString(R.string.MassgramChangelogTitle));
        container.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView infoView = new TextView(getParentActivity());
        infoView.setText(LocaleController.getString(R.string.MassgramChangelogInfo));
        infoView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        infoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoView.setPadding(dp(18), 0, dp(18), dp(10));
        container.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(getParentActivity());
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout entriesLayout = new LinearLayout(getParentActivity());
        entriesLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(entriesLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        for (int i = 0; i < entries.size(); i++) {
            ApplicationLoader.ChangelogEntry entry = entries.get(i);
            entriesLayout.addView(createChangelogEntryView(entry, i == entries.size() - 1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        container.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        builder.setCustomView(container);

        BottomSheet sheet = builder.create();
        sheet.fixNavigationBar();
        sheet.show();
    }

    private View createChangelogEntryView(ApplicationLoader.ChangelogEntry entry, boolean last) {
        Context context = getParentActivity();
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(10), dp(18), dp(last ? 16 : 10));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView versionView = new TextView(context);
        versionView.setText(entry.versionName);
        versionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        versionView.setTypeface(AndroidUtilities.bold());
        topRow.addView(versionView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView codeView = new TextView(context);
        codeView.setText("#" + entry.versionCode);
        codeView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        topRow.addView(codeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        String publishedAt = formatChangelogDate(entry.publishedAt);
        if (!TextUtils.isEmpty(publishedAt)) {
            TextView dateView = new TextView(context);
            dateView.setText(publishedAt);
            dateView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            dateView.setPadding(0, dp(4), 0, 0);
            container.addView(dateView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        TextView changelogView = new TextView(context);
        changelogView.setText(!TextUtils.isEmpty(entry.changelog) ? entry.changelog : LocaleController.getString(R.string.MassgramChangelogEmptyEntry));
        changelogView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        changelogView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        changelogView.setLineSpacing(dp(1.5f), 1f);
        changelogView.setPadding(0, dp(8), 0, 0);
        container.addView(changelogView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!last) {
            View divider = new View(context);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_divider), 140));
            container.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, dp(14), 0, 0));
        }
        return container;
    }

    private String formatChangelogDate(String publishedAt) {
        if (TextUtils.isEmpty(publishedAt)) {
            return null;
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(publishedAt);
            if (date == null) {
                return publishedAt;
            }
            SimpleDateFormat formatter = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
            return formatter.format(date);
        } catch (Exception ignore) {
            return publishedAt;
        }
    }

    private boolean isCurrentAccountBetaTester() {
        return ApplicationLoader.applicationLoaderInstance.isBetaTester(UserConfig.getInstance(currentAccount).getClientUserId());
    }

    private boolean isOwnerAccount() {
        return MassgramConfigManager.getInstance().isOwner(UserConfig.getInstance(currentAccount).getClientUserId());
    }

    private void updateLanguagePill() {
        if (languagePillView == null) {
            return;
        }
        boolean russianSelected = LocaleController.getLocaleStringIso639().toLowerCase().startsWith("ru");
        languagePillView.setSelectedLanguage(russianSelected ? "ru" : "en");
    }

    private void applyLanguage(String langKey) {
        boolean currentRussian = LocaleController.getLocaleStringIso639().toLowerCase().startsWith("ru");
        boolean targetRussian = "ru".equals(langKey);
        if (currentRussian == targetRussian) {
            return;
        }
        LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().getLanguageFromDict(langKey);
        if (localeInfo == null) {
            return;
        }
        LocaleController.getInstance().applyLanguage(localeInfo, true, false, currentAccount);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
        bindSettingsContent();
        if (listView != null) {
            listView.invalidate();
        }
        if (LaunchActivity.instance != null) {
            AndroidUtilities.runOnUIThread(() -> {
                if (LaunchActivity.instance != null) {
                    LaunchActivity.instance.rebuildAllFragments(true);
                }
            }, 16);
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (languagePillView != null && languagePillView.getParent() instanceof ViewGroup) {
            ((ViewGroup) languagePillView.getParent()).removeView(languagePillView);
        }
        super.onFragmentDestroy();
    }

    private void openTelegramContact(String username) {
        getMessagesController().openByUserName(username, this, 1);
    }

    private TextView createSectionHeader(Context context, int textRes) {
        TextView header = new TextView(context);
        header.setText(LocaleController.getString(textRes));
        header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        header.setTypeface(AndroidUtilities.bold());
        header.setPadding(dp(6), 0, dp(6), dp(8));
        return header;
    }

    private LinearLayout createSectionCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int normal = getThemedColor(Theme.key_windowBackgroundWhite);
        int pressed = ColorUtils.blendARGB(normal, getThemedColor(Theme.key_listSelector), 0.25f);
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22), normal, pressed));
        card.setClipToOutline(true);
        return card;
    }

    private View createSpacer(Context context, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, heightDp));
        return spacer;
    }

    private final class LanguagePillView extends FrameLayout {

        private final FrameLayout backgroundView;
        private final TextView enView;
        private final TextView ruView;
        private final View enSelection;
        private final View ruSelection;

        private LanguagePillView(Context context) {
            super(context);
            setPadding(dp(4), dp(4), dp(4), dp(4));

            backgroundView = new FrameLayout(context);
            backgroundView.setBackground(createGlassPillBackground());
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 34));

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            backgroundView.addView(container, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            enSelection = createSelectedLanguageBackground(context);
            ruSelection = createSelectedLanguageBackground(context);
            enView = createLanguageText(context, "EN");
            ruView = createLanguageText(context, "RU");

            FrameLayout enContainer = new FrameLayout(context);
            enContainer.addView(enSelection, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            enContainer.addView(enView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            enContainer.setOnClickListener(v -> applyLanguage("en"));
            container.addView(enContainer, LayoutHelper.createLinear(42, 26, Gravity.CENTER_VERTICAL));

            FrameLayout ruContainer = new FrameLayout(context);
            ruContainer.addView(ruSelection, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            ruContainer.addView(ruView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            ruContainer.setOnClickListener(v -> applyLanguage("ru"));
            container.addView(ruContainer, LayoutHelper.createLinear(42, 26, Gravity.CENTER_VERTICAL));
        }

        private Drawable createGlassPillBackground() {
            int fill = ColorUtils.setAlphaComponent(Color.WHITE, LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS) ? 110 : 230);
            int pressed = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_listSelector), 90);
            Drawable drawable = Theme.createSimpleSelectorRoundRectDrawable(dp(17), fill, pressed);
            drawable.mutate().setColorFilter(new PorterDuffColorFilter(fill, PorterDuff.Mode.SRC_ATOP));
            return drawable;
        }

        private void refreshBackground() {
            backgroundView.setBackground(createGlassPillBackground());
        }

        private View createSelectedLanguageBackground(Context context) {
            View view = new View(context);
            view.setBackground(Theme.createRoundRectDrawable(dp(13), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), 34)));
            view.setVisibility(View.INVISIBLE);
            return view;
        }

        private TextView createLanguageText(Context context, String text) {
            TextView view = new TextView(context);
            view.setText(text);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            view.setTypeface(AndroidUtilities.bold());
            return view;
        }

        private void setSelectedLanguage(String lang) {
            boolean enSelected = "en".equals(lang);
            enSelection.setVisibility(enSelected ? View.VISIBLE : View.INVISIBLE);
            ruSelection.setVisibility(enSelected ? View.INVISIBLE : View.VISIBLE);
            enView.setTextColor(getLanguageTextColor(enSelected));
            ruView.setTextColor(getLanguageTextColor(!enSelected));
            setBackground(null);
        }

        private int getLanguageTextColor(boolean selected) {
            if (selected) {
                return getThemedColor(Theme.key_windowBackgroundWhiteBlueText);
            }
            return ColorUtils.setAlphaComponent(getThemedColor(Theme.key_actionBarDefaultIcon), 180);
        }
    }

    private final class MassgramSettingRow extends FrameLayout {

        private final ImageView iconView;
        private final TextView titleView;
        private final ImageView helpButton;
        private final Switch switchView;
        private final TextView infoView;
        private final View divider;
        private Runnable onToggleClick;
        private Runnable onHelpClick;

        private MassgramSettingRow(Context context, int iconRes) {
            super(context);
            setClipChildren(false);
            setWillNotDraw(false);

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setPadding(dp(18), dp(12), dp(16), dp(12));
            content.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            FrameLayout iconWrap = new FrameLayout(context);
            iconWrap.setBackground(Theme.createRoundRectDrawable(dp(16), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundGray), 200)));
            topRow.addView(iconWrap, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            iconWrap.addView(iconView, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

            LinearLayout titleWrap = new LinearLayout(context);
            titleWrap.setOrientation(LinearLayout.HORIZONTAL);
            titleWrap.setGravity(Gravity.CENTER_VERTICAL);
            topRow.addView(titleWrap, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 10, 0));

            titleView = new TextView(context);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleWrap.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            helpButton = new ImageView(context);
            helpButton.setImageResource(R.drawable.outline_question_mark);
            helpButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
            helpButton.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(24), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3), 22), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3), 42)));
            titleWrap.addView(helpButton, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            switchView = new Switch(context);
            switchView.setDrawIconType(1);
            topRow.addView(switchView, LayoutHelper.createLinear(37, 40, Gravity.CENTER_VERTICAL));

            infoView = new TextView(context);
            infoView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            infoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoView.setLineSpacing(dp(1.5f), 1f);
            infoView.setVisibility(View.GONE);
            content.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 0, 18, 14));

            divider = new View(context);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_divider), 140));
            content.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 62, 0, 0, 0));

            setOnClickListener(v -> {
                if (onToggleClick != null && isEnabled()) {
                    onToggleClick.run();
                }
            });
            switchView.setOnClickListener(v -> {
                if (onToggleClick != null && isEnabled()) {
                    onToggleClick.run();
                }
            });
            helpButton.setOnClickListener(v -> {
                if (onHelpClick != null) {
                    onHelpClick.run();
                }
            });
        }

        private void bind(int id, CharSequence title, CharSequence info, boolean checked, boolean enabled, boolean expanded, boolean last, Runnable onToggleClick) {
            setTag(id);
            titleView.setText(title);
            infoView.setText(info);
            switchView.setChecked(checked, false);
            setEnabled(enabled);
            switchView.setEnabled(enabled);
            divider.setVisibility(last ? View.GONE : View.VISIBLE);
            this.onToggleClick = onToggleClick;
            setInfoExpanded(expanded, false);
            float alpha = enabled ? 1f : 0.5f;
            titleView.setAlpha(alpha);
            iconView.setAlpha(alpha);
            helpButton.setAlpha(enabled ? 0.9f : 0.45f);
            infoView.setAlpha(1f);
        }

        private void setOnHelpClickListener(Runnable listener) {
            onHelpClick = listener;
        }

        private void setInfoExpanded(boolean expanded, boolean animated) {
            AndroidUtilities.updateViewVisibilityAnimated(infoView, expanded, 1f, true, animated);
            helpButton.animate().rotation(expanded ? 90f : 0f).setDuration(animated ? 220 : 0).start();
            requestLayout();
        }
    }

    private final class MassgramActionRow extends FrameLayout {

        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView secondaryActionView;
        private final TextView actionView;
        private final View divider;
        private Runnable onActionClick;
        private Runnable onSecondaryActionClick;

        private MassgramActionRow(Context context, int iconRes) {
            super(context);

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(12), dp(16), dp(12));
            content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            FrameLayout iconWrap = new FrameLayout(context);
            iconWrap.setBackground(Theme.createRoundRectDrawable(dp(16), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundGray), 200)));
            row.addView(iconWrap, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            iconWrap.addView(iconView, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

            LinearLayout textWrap = new LinearLayout(context);
            textWrap.setOrientation(LinearLayout.VERTICAL);
            row.addView(textWrap, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 10, 0));

            titleView = new TextView(context);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            textWrap.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setPadding(0, dp(2), 0, 0);
            textWrap.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout actionWrap = new LinearLayout(context);
            actionWrap.setOrientation(LinearLayout.HORIZONTAL);
            actionWrap.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(actionWrap, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            secondaryActionView = new TextView(context);
            secondaryActionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            secondaryActionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            secondaryActionView.setTypeface(AndroidUtilities.bold());
            secondaryActionView.setPadding(dp(10), dp(6), dp(10), dp(6));
            secondaryActionView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(15), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), 20), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), 34)));
            secondaryActionView.setVisibility(GONE);
            actionWrap.addView(secondaryActionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            actionView = new TextView(context);
            actionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            actionView.setTypeface(AndroidUtilities.bold());
            actionWrap.addView(actionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            ImageView arrowView = new ImageView(context);
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
            actionWrap.addView(arrowView, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

            divider = new View(context);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_divider), 140));
            content.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 62, 0, 0, 0));

            setOnClickListener(v -> {
                if (onActionClick != null && isEnabled()) {
                    onActionClick.run();
                }
            });
            secondaryActionView.setOnClickListener(v -> {
                if (onSecondaryActionClick != null && isEnabled()) {
                    onSecondaryActionClick.run();
                }
            });
        }

        private void bind(int id, CharSequence title, CharSequence subtitle, CharSequence action, boolean last, Runnable onClick) {
            bind(id, title, subtitle, action, null, last, onClick, null);
        }

        private void bind(int id, CharSequence title, CharSequence subtitle, CharSequence action, CharSequence secondaryAction, boolean last, Runnable onClick, Runnable onSecondaryClick) {
            setTag(id);
            titleView.setText(title);
            subtitleView.setText(subtitle);
            actionView.setText(action);
            onSecondaryActionClick = onSecondaryClick;
            if (!TextUtils.isEmpty(secondaryAction) && onSecondaryClick != null) {
                secondaryActionView.setText(secondaryAction);
                secondaryActionView.setVisibility(VISIBLE);
            } else {
                secondaryActionView.setVisibility(GONE);
            }
            divider.setVisibility(last ? View.GONE : View.VISIBLE);
            onActionClick = onClick;
        }
    }

    private final class MassgramDeveloperCard extends FrameLayout {

        private final ProfileLine developerLine;
        private final ProfileLine contributorLine;

        private MassgramDeveloperCard(Context context) {
            super(context);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(18), dp(16), dp(18), dp(16));
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            developerLine = new ProfileLine(context, R.drawable.msg_openprofile, true);
            container.addView(developerLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            View divider = new View(context);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_divider), 140));
            container.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 48, 12, 0, 12));

            contributorLine = new ProfileLine(context, R.drawable.msg_message, false);
            container.addView(contributorLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(getThemedColor(Theme.key_listSelector), 32)));
        }

        private void bind(CharSequence developerTitle, CharSequence developerHandle, CharSequence developerDescription, CharSequence contributorTitle, CharSequence contributorHandle, CharSequence contributorDescription) {
            developerLine.bind(developerTitle, developerHandle, developerDescription, "awenqo");
            contributorLine.bind(contributorTitle, contributorHandle, contributorDescription, "kakadkl");
        }

        private final class ProfileLine extends FrameLayout {

            private final TextView titleView;
            private final TextView usernameView;
            private final TextView descriptionView;
            private String username;

            private ProfileLine(Context context, int iconRes, boolean primary) {
                super(context);

                LinearLayout topRow = new LinearLayout(context);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                addView(topRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                FrameLayout iconWrap = new FrameLayout(context);
                iconWrap.setBackground(Theme.createRoundRectDrawable(dp(18), ColorUtils.setAlphaComponent(getThemedColor(primary ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayIcon), primary ? 24 : 20)));
                topRow.addView(iconWrap, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));

                ImageView iconView = new ImageView(context);
                iconView.setImageResource(iconRes);
                iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(primary ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
                iconWrap.addView(iconView, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

                LinearLayout textWrap = new LinearLayout(context);
                textWrap.setOrientation(LinearLayout.VERTICAL);
                topRow.addView(textWrap, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 10, 0));

                titleView = new TextView(context);
                titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                titleView.setTypeface(AndroidUtilities.bold());
                textWrap.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                usernameView = new TextView(context);
                usernameView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                usernameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                usernameView.setTypeface(AndroidUtilities.bold());
                textWrap.addView(usernameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

                ImageView arrowView = new ImageView(context);
                arrowView.setImageResource(R.drawable.msg_arrowright);
                arrowView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
                topRow.addView(arrowView, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL));

                descriptionView = new TextView(context);
                descriptionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                descriptionView.setLineSpacing(dp(1.5f), 1f);
                descriptionView.setPadding(0, dp(12), 0, 0);
                addView(descriptionView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 48, 38, 0, 0));

                setOnClickListener(v -> {
                    if (!TextUtils.isEmpty(username)) {
                        openTelegramContact(username);
                    }
                });
            }

            private void bind(CharSequence title, CharSequence handle, CharSequence description, String username) {
                titleView.setText(title);
                usernameView.setText(handle);
                descriptionView.setText(description);
                this.username = username;
            }
        }
    }
}
