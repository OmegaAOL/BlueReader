/*******************************************************************************
 * This file is part of BlueReader.
 *
 * BlueReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BlueReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BlueReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.omegaaol.bluereader.adapters;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.common.AndroidCommon;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.ScreenreaderPronunciation;
import org.omegaaol.bluereader.common.SharedPrefsWrapper;
import org.omegaaol.bluereader.fragments.MainMenuFragment;
import org.omegaaol.bluereader.receivers.announcements.Announcement;
import org.omegaaol.bluereader.receivers.announcements.AnnouncementDownloader;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionManager;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionState;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;
import org.omegaaol.bluereader.bluesky.url.ListPostURL;
import org.omegaaol.bluereader.bluesky.url.PostListingURL;
import org.omegaaol.bluereader.bluesky.url.FeedPostListURL;
import org.omegaaol.bluereader.views.AnnouncementView;
import org.omegaaol.bluereader.views.LoadingSpinnerView;
import org.omegaaol.bluereader.views.list.GroupedRecyclerViewItemListItemView;
import org.omegaaol.bluereader.views.list.GroupedRecyclerViewItemListSectionHeaderView;
import org.omegaaol.bluereader.views.liststatus.ErrorView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainMenuListingManager {

	@SuppressWarnings("unused")
	private static final int GROUP_MAIN_HEADER = 0;

	private static final int GROUP_MAIN_ITEMS = 1;
	private static final int GROUP_USER_HEADER = 2;
	private static final int GROUP_USER_ITEMS = 3;
	private static final int GROUP_ANNOUNCEMENTS = 4;
	private static final int GROUP_PINNED_FEEDS_HEADER = 5;
	private static final int GROUP_PINNED_FEEDS_ITEMS = 6;
	private static final int GROUP_BLOCKED_FEEDS_HEADER = 7;
	private static final int GROUP_BLOCKED_FEEDS_ITEMS = 8;
	private static final int GROUP_LISTS_HEADER = 9;
	private static final int GROUP_LISTS_ITEMS = 10;
	private static final int GROUP_FEEDS_HEADER = 11;
	private static final int GROUP_FEEDS_ITEMS = 12;

	@NonNull private final GroupedRecyclerViewAdapter mAdapter
			= new GroupedRecyclerViewAdapter(13);
	@NonNull private final Context mContext;
	@NonNull private final AppCompatActivity mActivity;

	@NonNull private final MainMenuSelectionListener mListener;

	@Nullable private GroupedRecyclerViewAdapter.Item mListHeaderItem;

	@Nullable private ArrayList<FeedCanonicalId> mFeedSubscriptions;
	@Nullable private ArrayList<String> mListSubscriptions;

	@NonNull private final FrameLayout mAnnouncementHolder;

	@NonNull
	public GroupedRecyclerViewAdapter getAdapter() {
		return mAdapter;
	}

	public enum FeedAction {
		SHARE(R.string.action_share),
		COPY_URL(R.string.action_copy_link),
		BLOCK(R.string.block_feed),
		UNBLOCK(R.string.unblock_feed),
		PIN(R.string.pin_feed),
		UNPIN(R.string.unpin_feed),
		SUBSCRIBE(R.string.options_subscribe),
		UNSUBSCRIBE(R.string.options_unsubscribe),
		EXTERNAL(R.string.action_external);

		public final int descriptionResId;

		FeedAction(final int descriptionResId) {
			this.descriptionResId = descriptionResId;
		}
	}

	public MainMenuListingManager(
			@NonNull final AppCompatActivity activity,
			@NonNull final MainMenuSelectionListener listener,
			@NonNull final RedditAccount user) {

		General.checkThisIsUIThread();

		mActivity = activity;
		mContext = activity.getApplicationContext();
		mListener = listener;

		mAnnouncementHolder = new FrameLayout(mActivity);
		General.setLayoutMatchWidthWrapHeight(mAnnouncementHolder);

		final Drawable rrIconPerson;
		final Drawable rrIconEnvOpen;
		final Drawable rrIconSentMessages;
		final Drawable rrIconSend;
		final Drawable rrIconStarFilled;
		final Drawable rrIconCross;
		final Drawable rrIconLike;
		final Drawable rrIconDownvote;
		final Drawable rrIconAccountSearch;

		{
			final TypedArray attr = activity.obtainStyledAttributes(new int[] {
					R.attr.rrIconPerson,
					R.attr.rrIconEnvOpen,
					R.attr.rrIconSentMessages,
					R.attr.rrIconSend,
					R.attr.rrIconStarFilled,
					R.attr.rrIconCross,
					R.attr.rrIconArrowUpBold,
					R.attr.rrIconArrowDownBold,
					R.attr.rrIconAccountSearch
			});

			rrIconPerson = ContextCompat.getDrawable(activity, attr.getResourceId(0, 0));
			rrIconEnvOpen = ContextCompat.getDrawable(activity, attr.getResourceId(1, 0));
			rrIconSentMessages = ContextCompat.getDrawable(activity, attr.getResourceId(2,0));
			rrIconSend = ContextCompat.getDrawable(activity, attr.getResourceId(3, 0));
			rrIconStarFilled = ContextCompat.getDrawable(
					activity,
					attr.getResourceId(4, 0));
			rrIconCross = ContextCompat.getDrawable(activity, attr.getResourceId(5, 0));
			rrIconLike = ContextCompat.getDrawable(activity, attr.getResourceId(6, 0));
			rrIconDownvote = ContextCompat.getDrawable(
					activity,
					attr.getResourceId(7, 0));
			rrIconAccountSearch = Objects.requireNonNull(ContextCompat.getDrawable(
					activity,
					attr.getResourceId(8, 0)));

			attr.recycle();
		}

		{
			final EnumSet<MainMenuFragment.MainMenuShortcutItems> mainMenuShortcutItems
					= PrefsUtility.pref_menus_mainmenu_shortcutitems();

			if(mainMenuShortcutItems.contains(MainMenuFragment.MainMenuShortcutItems.FRONTPAGE)) {
				mAdapter.appendToGroup(
						GROUP_MAIN_ITEMS,
						makeItem(
								R.string.mainmenu_feed_discover,
								MainMenuFragment.MENU_MENU_ACTION_FEED_DISCOVER,
								null,
								true));
			}

			if(mainMenuShortcutItems.contains(MainMenuFragment.MainMenuShortcutItems.POPULAR)) {
				mAdapter.appendToGroup(
						GROUP_MAIN_ITEMS,
						makeItem(
								R.string.mainmenu_popular,
								MainMenuFragment.MENU_MENU_ACTION_POPULAR,
								null,
								false));
			}

			if(mainMenuShortcutItems.contains(MainMenuFragment.MainMenuShortcutItems.ALL)) {
				mAdapter.appendToGroup(
						GROUP_MAIN_ITEMS,
						makeItem(
								R.string.mainmenu_feed_following,
								MainMenuFragment.MENU_MENU_ACTION_TIMELINE,
								null,
								false));
			}

			if(mainMenuShortcutItems.contains(
					MainMenuFragment.MainMenuShortcutItems.FEED_SEARCH)) {

				if(mainMenuShortcutItems.contains(
						MainMenuFragment.MainMenuShortcutItems.CUSTOM)) {

					final View.OnClickListener clickListener = view -> mListener.onSelected(
							MainMenuFragment.MENU_MENU_ACTION_FIND_FEED);

					final GroupedRecyclerViewItemListItemView item
							= new GroupedRecyclerViewItemListItemView(
									null,
									activity.getString(R.string.search_content),
									null,
									false,
									clickListener,
									null,
									Optional.of(rrIconAccountSearch),
									Optional.of(view -> mListener.onSelected(
											MainMenuFragment.MENU_MENU_ACTION_CUSTOM)),
									Optional.of(activity.getString(
											R.string.mainmenu_custom_destination)));

					mAdapter.appendToGroup(GROUP_MAIN_ITEMS, item);

				} else {
					mAdapter.appendToGroup(
							GROUP_MAIN_ITEMS,
							makeItem(
									R.string.search_content,
									MainMenuFragment.MENU_MENU_ACTION_FIND_FEED,
									null,
									false));
				}

			} else if(mainMenuShortcutItems.contains(
					MainMenuFragment.MainMenuShortcutItems.CUSTOM)) {

				mAdapter.appendToGroup(
						GROUP_MAIN_ITEMS,
						makeItem(
								R.string.mainmenu_custom_destination,
								MainMenuFragment.MENU_MENU_ACTION_CUSTOM,
								null,
								false));
			}

			if(mainMenuShortcutItems.contains(MainMenuFragment.MainMenuShortcutItems.RANDOM)) {
				mAdapter.appendToGroup(
						GROUP_MAIN_ITEMS,
						makeItem(
								R.string.mainmenu_random,
								MainMenuFragment.MENU_MENU_ACTION_RANDOM,
								null,
								false));
			}

			if(mainMenuShortcutItems.contains(MainMenuFragment.MainMenuShortcutItems.RANDOM_NSFW)) {
				mAdapter.appendToGroup(
						GROUP_MAIN_ITEMS,
						makeItem(
								R.string.mainmenu_random_nsfw,
								MainMenuFragment.MENU_MENU_ACTION_RANDOM_NSFW,
								null,
								false));
			}
		}

		if(PrefsUtility.pref_menus_mainmenu_dev_announcements()) {

			mAdapter.appendToGroup(
					GROUP_ANNOUNCEMENTS,
					new GroupedRecyclerViewItemFrameLayout(mAnnouncementHolder));

			onUpdateAnnouncement();
		}

		if(!user.isAnonymous()) {

			final EnumSet<MainMenuFragment.MainMenuUserItems> mainMenuUserItems
					= PrefsUtility.pref_menus_mainmenu_useritems();

			if(!mainMenuUserItems.isEmpty()) {
				if(PrefsUtility.pref_appearance_hide_username_main_menu()) {

					mAdapter.appendToGroup(
							GROUP_USER_HEADER,
							new GroupedRecyclerViewItemListSectionHeaderView(
									activity.getString(R.string.mainmenu_useritems)));

				} else {
					mAdapter.appendToGroup(
							GROUP_USER_HEADER,
							new GroupedRecyclerViewItemListSectionHeaderView(user.username));
				}

				final AtomicBoolean isFirst = new AtomicBoolean(true);

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.PROFILE)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_profile,
									MainMenuFragment.MENU_MENU_ACTION_PROFILE,
									rrIconPerson,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.INBOX)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_inbox,
									MainMenuFragment.MENU_MENU_ACTION_INBOX,
									rrIconEnvOpen,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.SENT_MESSAGES)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_sent_messages,
									MainMenuFragment.MENU_MENU_ACTION_SENT_MESSAGES,
									rrIconSentMessages,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.SUBMITTED)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_submitted,
									MainMenuFragment.MENU_MENU_ACTION_SUBMITTED,
									rrIconSend,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.SAVED)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_saved,
									MainMenuFragment.MENU_MENU_ACTION_SAVED,
									rrIconStarFilled,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.HIDDEN)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_hidden,
									MainMenuFragment.MENU_MENU_ACTION_HIDDEN,
									rrIconCross,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.LIKED)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_liked,
									MainMenuFragment.MENU_MENU_ACTION_LIKED,
									rrIconLike,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.DOWNVOTED)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_downvoted,
									MainMenuFragment.MENU_MENU_ACTION_DOWNVOTED,
									rrIconDownvote,
									isFirst.getAndSet(false)));
				}

				if(mainMenuUserItems.contains(MainMenuFragment.MainMenuUserItems.MODMAIL)) {
					mAdapter.appendToGroup(
							GROUP_USER_ITEMS,
							makeItem(
									R.string.mainmenu_modmail,
									MainMenuFragment.MENU_MENU_ACTION_MODMAIL,
									rrIconEnvOpen,
									isFirst.getAndSet(false)));
				}
			}
		}

		setPinnedFeeds();

		if(PrefsUtility.pref_appearance_show_blocked_feeds_main_menu()) {

			setBlockedFeeds();
		}

		if(!user.isAnonymous()) {
			if(PrefsUtility.pref_show_list_main_menu()) {

				showListsHeader(activity);

				final LoadingSpinnerView listsLoadingSpinnerView
						= new LoadingSpinnerView(activity);
				final int paddingPx = General.dpToPixels(activity, 30);
				listsLoadingSpinnerView.setPadding(
						paddingPx,
						paddingPx,
						paddingPx,
						paddingPx);

				final GroupedRecyclerViewItemFrameLayout listsLoadingItem
						= new GroupedRecyclerViewItemFrameLayout(
						listsLoadingSpinnerView);
				mAdapter.appendToGroup(GROUP_LISTS_ITEMS, listsLoadingItem);
			}
		}

		if(PrefsUtility.pref_show_subscribed_feeds_main_menu()) {

			mAdapter.appendToGroup(
					GROUP_FEEDS_HEADER,
					new GroupedRecyclerViewItemListSectionHeaderView(
							activity.getString(R.string.mainmenu_header_feeds_subscribed)));

			{
				final LoadingSpinnerView feedsLoadingSpinnerView
						= new LoadingSpinnerView(activity);
				final int paddingPx = General.dpToPixels(activity, 30);
				feedsLoadingSpinnerView.setPadding(
						paddingPx,
						paddingPx,
						paddingPx,
						paddingPx);

				final GroupedRecyclerViewItemFrameLayout feedsLoadingItem
						= new GroupedRecyclerViewItemFrameLayout(
						feedsLoadingSpinnerView);
				mAdapter.appendToGroup(GROUP_FEEDS_ITEMS, feedsLoadingItem);
			}
		}
	}

	private void setPinnedFeeds() {

		final List<FeedCanonicalId> pinnedFeeds
				= PrefsUtility.pref_pinned_feeds();

		mAdapter.removeAllFromGroup(GROUP_PINNED_FEEDS_ITEMS);
		mAdapter.removeAllFromGroup(GROUP_PINNED_FEEDS_HEADER);

		if(!pinnedFeeds.isEmpty()) {

			final PrefsUtility.HomeItemSort pinnedFeedsSort
					= PrefsUtility.pref_behaviour_home_item_sort();

			mAdapter.appendToGroup(
					GROUP_PINNED_FEEDS_HEADER,
					new GroupedRecyclerViewItemListSectionHeaderView(
							mActivity.getString(R.string.mainmenu_header_feeds_pinned)));

			if(pinnedFeedsSort == PrefsUtility.HomeItemSort.NAME) {
				Collections.sort(pinnedFeeds);
			}

			boolean isFirst = true;

			for(final FeedCanonicalId sr : pinnedFeeds) {
				mAdapter.appendToGroup(
						GROUP_PINNED_FEEDS_ITEMS,
						makeFeedItem(sr, isFirst));
				isFirst = false;
			}
		}
	}

	private void setBlockedFeeds() {

		final List<FeedCanonicalId> blockedFeeds
				= PrefsUtility.pref_blocked_feeds();

		mAdapter.removeAllFromGroup(GROUP_BLOCKED_FEEDS_ITEMS);
		mAdapter.removeAllFromGroup(GROUP_BLOCKED_FEEDS_HEADER);

		if(!blockedFeeds.isEmpty()) {

			final PrefsUtility.BlockedFeedSort blockedFeedsSort
					= PrefsUtility.pref_behaviour_blocked_feedsort();

			mAdapter.appendToGroup(
					GROUP_BLOCKED_FEEDS_HEADER,
					new GroupedRecyclerViewItemListSectionHeaderView(
							mActivity.getString(R.string.mainmenu_header_feeds_blocked)));

			if(blockedFeedsSort == PrefsUtility.BlockedFeedSort.NAME) {
				Collections.sort(blockedFeeds);
			}

			boolean isFirst = true;
			for(final FeedCanonicalId sr : blockedFeeds) {
				mAdapter.appendToGroup(
						GROUP_BLOCKED_FEEDS_ITEMS,
						makeFeedItem(sr, isFirst));
				isFirst = false;
			}
		}
	}


	private void showListsHeader(@NonNull final Context context) {

		General.checkThisIsUIThread();

		if(mListHeaderItem == null) {
			mListHeaderItem = new GroupedRecyclerViewItemListSectionHeaderView(
					context.getString(R.string.mainmenu_header_lists));

			mAdapter.appendToGroup(GROUP_LISTS_HEADER, mListHeaderItem);
		}
	}

	private void hideListsHeader() {

		General.checkThisIsUIThread();

		mListHeaderItem = null;
		mAdapter.removeAllFromGroup(GROUP_LISTS_HEADER);
	}

	public void setListsError(final ErrorView errorView) {

		AndroidCommon.UI_THREAD_HANDLER.post(() -> {

			mAdapter.removeAllFromGroup(GROUP_LISTS_ITEMS);
			mAdapter.appendToGroup(
					GROUP_LISTS_ITEMS,
					new GroupedRecyclerViewItemFrameLayout(errorView));
		});
	}

	public void setFeedsError(final ErrorView errorView) {

		AndroidCommon.UI_THREAD_HANDLER.post(() -> {

			mAdapter.removeAllFromGroup(GROUP_FEEDS_ITEMS);
			mAdapter.appendToGroup(
					GROUP_FEEDS_ITEMS,
					new GroupedRecyclerViewItemFrameLayout(errorView));
		});
	}

	public void setFeeds(final Collection<FeedCanonicalId> subscriptions) {

		final ArrayList<FeedCanonicalId> subscriptionsSorted = new ArrayList<>(
				subscriptions);

		if(PrefsUtility.pref_behaviour_home_item_sort() == PrefsUtility.HomeItemSort.NAME) {
			Collections.sort(subscriptionsSorted);
		}

		AndroidCommon.UI_THREAD_HANDLER.post(() -> {

			if(mFeedSubscriptions != null
					&& mFeedSubscriptions.equals(subscriptionsSorted)) {

				return;
			}

			if(!PrefsUtility.pref_show_subscribed_feeds_main_menu()) {
				mAdapter.removeAllFromGroup(GROUP_FEEDS_HEADER);
				mAdapter.removeAllFromGroup(GROUP_FEEDS_ITEMS);
				return;
			}

			mFeedSubscriptions = subscriptionsSorted;

			mAdapter.removeAllFromGroup(GROUP_FEEDS_ITEMS);

			boolean isFirst = true;

			for(final FeedCanonicalId feed : subscriptionsSorted) {

				mAdapter.appendToGroup(
						GROUP_FEEDS_ITEMS,
						makeFeedItem(feed, isFirst));

				isFirst = false;
			}
		});
	}

	public void setLists(final Collection<String> subscriptions) {

		final ArrayList<String> subscriptionsSorted = new ArrayList<>(subscriptions);
		if(PrefsUtility.pref_behaviour_home_item_sort() == PrefsUtility.HomeItemSort.NAME) {
			Collections.sort(subscriptionsSorted);
		}

		AndroidCommon.UI_THREAD_HANDLER.post(() -> {

			if(mListSubscriptions != null
					&& mListSubscriptions.equals(subscriptionsSorted)) {

				return;
			}

			if(!PrefsUtility.pref_show_list_main_menu()) {
				mAdapter.removeAllFromGroup(GROUP_LISTS_HEADER);
				mAdapter.removeAllFromGroup(GROUP_LISTS_ITEMS);
				return;
			}

			mListSubscriptions = subscriptionsSorted;

			mAdapter.removeAllFromGroup(GROUP_LISTS_ITEMS);

			if(subscriptionsSorted.isEmpty()) {
				hideListsHeader();

			} else {

				showListsHeader(mContext);

				boolean isFirst = true;

				for(final String list : subscriptionsSorted) {

					final GroupedRecyclerViewItemListItemView item
							= makeListItem(list, isFirst);
					mAdapter.appendToGroup(GROUP_LISTS_ITEMS, item);

					isFirst = false;
				}
			}
		});
	}

	private GroupedRecyclerViewItemListItemView makeItem(
			final int nameRes,
			final @MainMenuFragment.MainMenuAction int action,
			@Nullable final Drawable icon,
			final boolean hideDivider) {

		return makeItem(mContext.getString(nameRes), action, icon, hideDivider);
	}

	private GroupedRecyclerViewItemListItemView makeItem(
			@NonNull final String name,
			final @MainMenuFragment.MainMenuAction int action,
			@Nullable final Drawable icon,
			final boolean hideDivider) {

		final View.OnClickListener clickListener = view -> mListener.onSelected(action);

		return new GroupedRecyclerViewItemListItemView(
				icon,
				name,
				null,
				hideDivider,
				clickListener,
				null,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
	}

	private GroupedRecyclerViewItemListItemView makeFeedItem(
			final FeedCanonicalId feed,
			final boolean hideDivider) {

		final View.OnClickListener clickListener = view -> {

			if(feed.toString().startsWith("/r/")) {
				mListener.onSelected((PostListingURL)FeedPostListURL.getFeed(
						feed));

			} else {
				LinkHandler.onLinkClicked(mActivity, feed.toString());
			}
		};

		final View.OnLongClickListener longClickListener = view -> {
			showActionMenu(mActivity, feed);
			return true;
		};

		final String displayName = feed.toString();

		return new GroupedRecyclerViewItemListItemView(
				null,
				displayName,
				ScreenreaderPronunciation.getPronunciation(mContext, displayName),
				hideDivider,
				clickListener,
				longClickListener,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
	}

	public static void showActionMenu(
			final AppCompatActivity activity,
			final FeedCanonicalId feed) {
		final EnumSet<FeedAction> itemPref
				= PrefsUtility.pref_menus_feed_context_items();

		if(itemPref.isEmpty()) {
			return;
		}

		final ArrayList<FeedMenuItem> menu = new ArrayList<>();
		if(itemPref.contains(FeedAction.COPY_URL)) {
			menu.add(new FeedMenuItem(
					activity,
					R.string.action_copy_link,
					FeedAction.COPY_URL));
		}
		if(itemPref.contains(FeedAction.EXTERNAL)) {
			menu.add(new FeedMenuItem(
					activity,
					R.string.action_external,
					FeedAction.EXTERNAL));
		}
		if(itemPref.contains(FeedAction.SHARE)) {
			menu.add(new FeedMenuItem(
					activity,
					R.string.action_share,
					FeedAction.SHARE));
		}

		if(itemPref.contains(FeedAction.BLOCK)) {

			final boolean isBlocked = PrefsUtility.pref_blocked_feeds_check(feed);

			if(isBlocked) {
				menu.add(new FeedMenuItem(
						activity,
						R.string.unblock_feed,
						FeedAction.UNBLOCK));
			} else {
				menu.add(new FeedMenuItem(
						activity,
						R.string.block_feed,
						FeedAction.BLOCK));
			}
		}

		if(itemPref.contains(FeedAction.PIN)) {

			final boolean isPinned = PrefsUtility.pref_pinned_feeds_check(feed);

			if(isPinned) {
				menu.add(new FeedMenuItem(
						activity,
						R.string.unpin_feed,
						FeedAction.UNPIN));
			} else {
				menu.add(new FeedMenuItem(
						activity,
						R.string.pin_feed,
						FeedAction.PIN));
			}
		}

		if(!RedditAccountManager.getInstance(activity)
				.getDefaultAccount()
				.isAnonymous()) {

			if(itemPref.contains(FeedAction.SUBSCRIBE)) {

				final FeedSubscriptionManager subscriptionManager
						= FeedSubscriptionManager
						.getSingleton(
								activity,
								RedditAccountManager.getInstance(activity)
										.getDefaultAccount());

				if(subscriptionManager.areSubscriptionsReady()) {
					if(subscriptionManager.getSubscriptionState(feed)
							== FeedSubscriptionState.SUBSCRIBED) {
						menu.add(new FeedMenuItem(
								activity,
								R.string.options_unsubscribe,
								FeedAction.UNSUBSCRIBE));
					} else {
						menu.add(new FeedMenuItem(
								activity,
								R.string.options_subscribe,
								FeedAction.SUBSCRIBE));
					}
				}
			}
		}

		final String[] menuText = new String[menu.size()];

		for(int i = 0; i < menuText.length; i++) {
			menuText[i] = menu.get(i).title;
		}

		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);

		builder.setItems(menuText, (dialog, which) -> onFeedActionMenuItemSelected(
				feed,
				activity,
				menu.get(which).action));

		final AlertDialog alert = builder.create();
		alert.setCanceledOnTouchOutside(true);
		alert.show();
	}

	private static void onFeedActionMenuItemSelected(
			final FeedCanonicalId feedCanonicalId,
			final AppCompatActivity activity,
			final FeedAction action) {

		final String url = Constants.Bluesky.getNonAPIUri(feedCanonicalId.toString())
				.toString();

		final FeedSubscriptionManager subMan = FeedSubscriptionManager
				.getSingleton(
						activity,
						RedditAccountManager.getInstance(
								activity)
								.getDefaultAccount());

		switch(action) {
			case SHARE: {
				LinkHandler.shareText(activity, feedCanonicalId.toString(), url);
				break;
			}

			case COPY_URL: {
				final ClipboardManager clipboardManager
						= (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
				if(clipboardManager != null) {
					final ClipData data = ClipData.newPlainText(null, url);
					clipboardManager.setPrimaryClip(data);

					General.quickToast(
							activity.getApplicationContext(),
							R.string.feed_link_copied_to_clipboard);
				}
				break;
			}

			case EXTERNAL: {
				final Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse(url));
				activity.startActivity(intent);
				break;
			}

			case PIN:
				PrefsUtility.pref_pinned_feeds_add(
						activity,
						feedCanonicalId);
				break;

			case UNPIN:
				PrefsUtility.pref_pinned_feeds_remove(
						activity,
						feedCanonicalId);
				break;

			case BLOCK:
				PrefsUtility.pref_blocked_feeds_add(
						activity,
						feedCanonicalId);
				break;

			case UNBLOCK:
				PrefsUtility.pref_blocked_feeds_remove(
						activity,
						feedCanonicalId);
				break;

			case SUBSCRIBE:

				if(subMan.getSubscriptionState(feedCanonicalId)
						== FeedSubscriptionState.NOT_SUBSCRIBED) {
					subMan.subscribe(feedCanonicalId, activity);
					Toast.makeText(
							activity,
							R.string.options_subscribing,
							Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(
							activity,
							R.string.mainmenu_toast_subscribed,
							Toast.LENGTH_SHORT).show();
				}
				break;

			case UNSUBSCRIBE:

				if(subMan.getSubscriptionState(feedCanonicalId)
						== FeedSubscriptionState.SUBSCRIBED) {
					subMan.unsubscribe(feedCanonicalId, activity);
					Toast.makeText(
							activity,
							R.string.options_unsubscribing,
							Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(
							activity,
							R.string.mainmenu_toast_not_subscribed,
							Toast.LENGTH_SHORT).show();
				}
				break;
		}
	}

	private GroupedRecyclerViewItemListItemView makeListItem(
			final String name,
			final boolean hideDivider) {

		final View.OnClickListener clickListener = view -> mListener.onSelected(
				(PostListingURL) ListPostURL.getList(name));

		return new GroupedRecyclerViewItemListItemView(
				null,
				name,
				ScreenreaderPronunciation.getPronunciation(mContext, name),
				hideDivider,
				clickListener,
				null,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
	}

	private static class FeedMenuItem {
		public final String title;
		public final FeedAction action;

		private FeedMenuItem(
				final Context context,
				final int titleRes,
				final FeedAction action) {
			this.title = context.getString(titleRes);
			this.action = action;
		}
	}

	public void onUpdateAnnouncement() {

		final SharedPrefsWrapper sharedPreferences = General.getSharedPrefs(mContext);

		if(PrefsUtility.pref_menus_mainmenu_dev_announcements()) {

			final Optional<Announcement> announcement
					= AnnouncementDownloader.getMostRecentUnreadAnnouncement(sharedPreferences);

			if(announcement.isPresent()) {
				mAnnouncementHolder.removeAllViews();
				mAnnouncementHolder.addView(new AnnouncementView(mActivity, announcement.get()));
			}
		}
	}
}
