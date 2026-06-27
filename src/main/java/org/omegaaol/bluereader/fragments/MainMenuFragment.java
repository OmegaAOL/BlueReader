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

package org.omegaaol.bluereader.fragments;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.IntDef;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.activities.OptionsMenuUtility;
import org.omegaaol.bluereader.adapters.MainMenuListingManager;
import org.omegaaol.bluereader.adapters.MainMenuSelectionListener;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.TimestampBound;
import org.omegaaol.bluereader.common.time.TimeDuration;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.io.RequestResponseHandler;
import org.omegaaol.bluereader.bluesky.api.BlueskyListSubscriptionManager;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionManager;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;
import org.omegaaol.bluereader.bluesky.url.PostListingURL;
import org.omegaaol.bluereader.views.ScrollbarRecyclerViewManager;
import org.omegaaol.bluereader.views.liststatus.ErrorView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.HashSet;

public class MainMenuFragment extends RRFragment implements
		MainMenuSelectionListener,
		FeedSubscriptionManager.FeedSubscriptionStateChangeListener,
		BlueskyListSubscriptionManager.BlueskyListChangeListener {

	public static final int MENU_MENU_ACTION_FEED_DISCOVER = 0;
	public static final int MENU_MENU_ACTION_PROFILE = 1;
	public static final int MENU_MENU_ACTION_INBOX = 2;
	public static final int MENU_MENU_ACTION_SUBMITTED = 3;
	public static final int MENU_MENU_ACTION_LIKED = 4;
	public static final int MENU_MENU_ACTION_DOWNVOTED = 5;
	public static final int MENU_MENU_ACTION_SAVED = 6;
	public static final int MENU_MENU_ACTION_MODMAIL = 7;
	public static final int MENU_MENU_ACTION_HIDDEN = 8;
	public static final int MENU_MENU_ACTION_CUSTOM = 9;
	public static final int MENU_MENU_ACTION_TIMELINE = 10;
	public static final int MENU_MENU_ACTION_POPULAR = 11;
	public static final int MENU_MENU_ACTION_RANDOM = 12;
	public static final int MENU_MENU_ACTION_RANDOM_NSFW = 13;
	public static final int MENU_MENU_ACTION_SENT_MESSAGES = 14;
	public static final int MENU_MENU_ACTION_FIND_FEED = 15;

	@IntDef({
			MENU_MENU_ACTION_FEED_DISCOVER,
			MENU_MENU_ACTION_PROFILE,
			MENU_MENU_ACTION_INBOX,
			MENU_MENU_ACTION_SUBMITTED,
			MENU_MENU_ACTION_LIKED,
			MENU_MENU_ACTION_DOWNVOTED,
			MENU_MENU_ACTION_SAVED,
			MENU_MENU_ACTION_MODMAIL,
			MENU_MENU_ACTION_HIDDEN,
			MENU_MENU_ACTION_CUSTOM,
			MENU_MENU_ACTION_TIMELINE,
			MENU_MENU_ACTION_POPULAR,
			MENU_MENU_ACTION_RANDOM,
			MENU_MENU_ACTION_RANDOM_NSFW,
			MENU_MENU_ACTION_SENT_MESSAGES,
			MENU_MENU_ACTION_FIND_FEED})
	@Retention(RetentionPolicy.SOURCE)
	public @interface MainMenuAction {
	}

	private final MainMenuListingManager mManager;

	private final View mOuter;

	public MainMenuFragment(
			final AppCompatActivity parent,
			final Bundle savedInstanceState,
			final boolean force) {

		super(parent, savedInstanceState);
		final Context context = getActivity();

		final RedditAccount user = RedditAccountManager.getInstance(context)
				.getDefaultAccount();

		final ScrollbarRecyclerViewManager recyclerViewManager
				= new ScrollbarRecyclerViewManager(parent, null, false);

		mOuter = recyclerViewManager.getOuterView();
		final RecyclerView recyclerView = recyclerViewManager.getRecyclerView();

		if(parent instanceof OptionsMenuUtility.OptionsMenuFeedsListener
				&& PrefsUtility.pref_behaviour_enable_swipe_refresh()) {

			recyclerViewManager.enablePullToRefresh(
					((OptionsMenuUtility.OptionsMenuFeedsListener)parent)
							::onRefreshFeeds);
		}

		mManager = new MainMenuListingManager(getActivity(), this, user);

		recyclerView.setAdapter(mManager.getAdapter());

		final int paddingPx = General.dpToPixels(context, 8);
		recyclerView.setPadding(paddingPx, 0, paddingPx, 0);
		recyclerView.setClipToPadding(false);

		{
			final TypedArray appearance = context.obtainStyledAttributes(new int[] {
					R.attr.rrListItemBackgroundCol});

			getActivity().getWindow().setBackgroundDrawable(
					new ColorDrawable(appearance.getColor(0, General.COLOR_INVALID)));

			appearance.recycle();
		}

		final BlueskyListSubscriptionManager listSubscriptionManager
				= BlueskyListSubscriptionManager.getSingleton(context, user);

		final FeedSubscriptionManager feedSubscriptionManager
				= FeedSubscriptionManager.getSingleton(context, user);

		if(force) {
			listSubscriptionManager.triggerUpdate(
					new RequestResponseHandler<HashSet<String>, RRError>() {

						@Override
						public void onRequestFailed(final RRError failureReason) {
							onListError(failureReason);
						}

						@Override
						public void onRequestSuccess(
								final HashSet<String> result,
								final TimestampUTC timeCached) {

							listSubscriptionManager.addListener(MainMenuFragment.this);
							onListSubscriptionsChanged(result);
						}
					}, TimestampBound.NONE);

			feedSubscriptionManager.triggerUpdate(
					new RequestResponseHandler<
							HashSet<FeedCanonicalId>,
							RRError>() {
						@Override
						public void onRequestFailed(final RRError failureReason) {
							onFeedError(failureReason);
						}

						@Override
						public void onRequestSuccess(
								final HashSet<FeedCanonicalId> result,
								final TimestampUTC timeCached) {
							feedSubscriptionManager.addListener(MainMenuFragment.this);
							onFeedSubscriptionsChanged(result);
						}
					}, TimestampBound.NONE);

		} else {

			listSubscriptionManager.addListener(this);
			feedSubscriptionManager.addListener(this);

			if(listSubscriptionManager.areSubscriptionsReady()) {
				onListSubscriptionsChanged(
						listSubscriptionManager.getSubscriptionList());
			}

			if(feedSubscriptionManager.areSubscriptionsReady()) {
				onFeedSubscriptionsChanged(
						feedSubscriptionManager.getSubscriptionList());
			}

			final TimestampBound.MoreRecentThanBound oneHour
					= TimestampBound.notOlderThan(TimeDuration.hours(1));
			listSubscriptionManager.triggerUpdate(null, oneHour);
			feedSubscriptionManager.triggerUpdate(null, oneHour);
		}
	}

	public enum MainMenuUserItems {
		PROFILE, INBOX, SUBMITTED, SAVED, HIDDEN, LIKED, DOWNVOTED, MODMAIL, SENT_MESSAGES
	}

	public enum MainMenuShortcutItems {
		FRONTPAGE, POPULAR, ALL, FEED_SEARCH, CUSTOM, RANDOM, RANDOM_NSFW
	}

	@Override
	public View getListingView() {
		return mOuter;
	}

	@Override
	public Bundle onSaveInstanceState() {
		return null;
	}

	public void onFeedSubscriptionsChanged(
			final Collection<FeedCanonicalId> subscriptions) {
		mManager.setFeeds(subscriptions);
	}

	public void onListSubscriptionsChanged(final Collection<String> subscriptions) {
		mManager.setLists(subscriptions);
	}

	private void onFeedError(final RRError error) {
		mManager.setFeedsError(new ErrorView(getActivity(), error));
	}

	private void onListError(final RRError error) {
		mManager.setListsError(new ErrorView(getActivity(), error));
	}

	@Override
	public void onSelected(final @MainMenuAction int type) {
		((MainMenuSelectionListener)getActivity()).onSelected(type);
	}

	@Override
	public void onSelected(final PostListingURL postListingURL) {
		((MainMenuSelectionListener)getActivity()).onSelected(postListingURL);
	}

	@Override
	public void onFeedSubscriptionListUpdated(
			final FeedSubscriptionManager feedSubscriptionManager) {
		onFeedSubscriptionsChanged(feedSubscriptionManager.getSubscriptionList());
	}

	@Override
	public void onBlueskyListUpdated(
			final BlueskyListSubscriptionManager listSubscriptionManager) {
		onListSubscriptionsChanged(listSubscriptionManager.getSubscriptionList());
	}

	@Override
	public void onFeedSubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {
	}

	@Override
	public void onFeedUnsubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {
	}

	public void onUpdateAnnouncement() {
		mManager.onUpdateAnnouncement();
	}
}
