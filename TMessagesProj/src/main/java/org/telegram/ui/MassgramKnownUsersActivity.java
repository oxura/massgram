package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchField;

import java.util.ArrayList;
import java.util.Locale;

public class MassgramKnownUsersActivity extends BaseFragment {

    private final ArrayList<MassgramTelemetryManager.OwnerDashboardUser> users = new ArrayList<>();

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
        actionBar.setTitle(LocaleController.getString(R.string.MassgramKnownUsers));
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
        searchField.setHint(LocaleController.getString(R.string.MassgramKnownUsersSearchHint));
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
        emptyView.setText(LocaleController.getString(R.string.MassgramKnownUsersEmpty));
        listContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position < 0 || position >= users.size()) {
                return;
            }
            openUser(users.get(position));
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
        if (System.currentTimeMillis() - lastLoadedAt > 30_000L || users.isEmpty()) {
            loadUsers(true, currentQuery);
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
        searchRunnable = () -> loadUsers(true, currentQuery);
        AndroidUtilities.runOnUIThread(searchRunnable, 220L);
    }

    private void loadUsers(boolean force, String query) {
        emptyView.showProgress();
        MassgramTelemetryManager.getInstance().loadOwnerDashboard(getUserConfig().getClientUserId(), query, force, (data, error) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (!TextUtils.isEmpty(error)) {
                emptyView.setText(error);
                emptyView.showTextView();
                BulletinFactory.of(this).createErrorBulletin(error).show();
                return;
            }
            users.clear();
            if (data != null) {
                users.addAll(data.users);
                lastLoadedAt = data.loadedAt;
            }
            adapter.notifyDataSetChanged();
            updateResultsLabel();
            if (users.isEmpty()) {
                emptyView.setText(TextUtils.isEmpty(query) ? LocaleController.getString(R.string.MassgramKnownUsersEmpty) : LocaleController.getString(R.string.NoResult));
                emptyView.showTextView();
            } else {
                emptyView.showTextView();
            }
        });
    }

    private void updateResultsLabel() {
        if (resultsLabel == null) {
            return;
        }
        if (TextUtils.isEmpty(currentQuery)) {
            resultsLabel.setText(LocaleController.formatString("MassgramKnownUsersCount", R.string.MassgramKnownUsersCount, users.size()));
        } else {
            resultsLabel.setText(LocaleController.formatString("MassgramKnownUsersFiltered", R.string.MassgramKnownUsersFiltered, users.size()));
        }
    }

    private void openUser(MassgramTelemetryManager.OwnerDashboardUser entry) {
        TLRPC.User user = getMessagesController().getUser(entry.userId);
        if (user != null) {
            Bundle args = new Bundle();
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

    private CharSequence buildStatus(MassgramTelemetryManager.OwnerDashboardUser entry) {
        String state = LocaleController.getString(entry.online ? R.string.MassgramOwnerStatusOnline : R.string.MassgramOwnerStatusOffline);
        if (!entry.online && entry.lastSeenAt > 0) {
            state += " · " + LocaleController.getInstance().getFormatterStats().format(entry.lastSeenAt);
        }
        if (!TextUtils.isEmpty(entry.appVersion)) {
            state += " · " + entry.appVersion;
        }
        return state;
    }

    private TLRPC.User ensureDisplayUser(MassgramTelemetryManager.OwnerDashboardUser entry) {
        TLRPC.User user = getMessagesController().getUser(entry.userId);
        if (user != null) {
            return user;
        }
        TLRPC.TL_user placeholder = new TLRPC.TL_user();
        placeholder.id = entry.userId;
        placeholder.username = entry.username;
        placeholder.first_name = !TextUtils.isEmpty(entry.firstName) ? entry.firstName : entry.displayName;
        placeholder.last_name = entry.lastName;
        return placeholder;
    }

    private final class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            UserCell cell = new UserCell(context, 6, 0, false, false, resourceProvider);
            cell.setStatusColors(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            UserCell cell = (UserCell) holder.itemView;
            MassgramTelemetryManager.OwnerDashboardUser entry = users.get(position);
            TLRPC.User user = ensureDisplayUser(entry);
            String name = !TextUtils.isEmpty(user.username)
                ? "@" + user.username
                : String.format(Locale.US, "%d", entry.userId);
            if (!TextUtils.isEmpty(entry.displayName) && TextUtils.isEmpty(user.username)) {
                name = entry.displayName;
            }
            cell.setData(user, name, buildStatus(entry), 0, position != users.size() - 1);
        }
    }
}
