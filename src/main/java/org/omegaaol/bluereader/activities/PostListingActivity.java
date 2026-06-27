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

package org.omegaaol.bluereader.activities;


import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountChangeListener;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.common.DialogUtils;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.fragments.PostListingFragment;
import org.omegaaol.bluereader.fragments.SessionListDialog;
import org.omegaaol.bluereader.listingcontrollers.PostListingController;
import org.omegaaol.bluereader.bluesky.PostSort;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionManager;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionState;
import org.omegaaol.bluereader.bluesky.prepared.RedditPreparedPost;
import org.omegaaol.bluereader.bluesky.things.InvalidFeedNameException;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;
import org.omegaaol.bluereader.bluesky.url.PostCommentListingURL;
import org.omegaaol.bluereader.bluesky.url.PostListingURL;
import org.omegaaol.bluereader.bluesky.url.RedditURLParser;
import org.omegaaol.bluereader.bluesky.url.SearchPostListURL;
import org.omegaaol.bluereader.bluesky.url.FeedPostListURL;
import org.omegaaol.bluereader.views.RedditPostView;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class PostListingActivity extends RefreshableActivity
		implements RedditAccountChangeListener,
		RedditPostView.PostSelectionListener,
		OptionsMenuUtility.OptionsMenuPostsListener,
		SessionChangeListener,
		FeedSubscriptionManager.FeedSubscriptionStateChangeListener {

	private static final String SAVEDSTATE_SESSION = "pla_session";
	private static final String SAVEDSTATE_SORT = "pla_sort";
	private static final String SAVEDSTATE_FRAGMENT = "pla_fragment";

	private PostListingFragment fragment;
	private PostListingController controller;

	private final AtomicReference<FeedSubscriptionManager.ListenerContext>
			mFeedSubscriptionListenerContext = new AtomicReference<>(null);

	private long mDoubleTapBack_lastTapMs = -1;

	@Override
	public void onCreate(final Bundle savedInstanceState) {

		PrefsUtility.applyThemeAccent(this);


		super.onCreate(savedInstanceState);

		final TypedArray typedArray
				= obtainStyledAttributes(new int[] {R.attr.rrListBackgroundCol});

		try {
			getWindow().setBackgroundDrawable(
					new ColorDrawable(typedArray.getColor(0, 0)));

		} finally {
			typedArray.recycle();
		}

		RedditAccountManager.getInstance(this).addUpdateListener(this);

		if(getIntent() != null) {

			final Intent intent = getIntent();

			final RedditURLParser.RedditURL url
					= RedditURLParser.parseProbablePostListing(intent.getData());

			if(!(url instanceof PostListingURL)) {
				throw new RuntimeException(String.format(
						Locale.US,
						"'%s' is not a post listing URL!",
						url.generateJsonUri()));
			}

			controller = new PostListingController((PostListingURL)url, this);

			Bundle fragmentSavedInstanceState = null;

			if(savedInstanceState != null) {

				if(savedInstanceState.containsKey(SAVEDSTATE_SESSION)) {
					controller.setSession(UUID.fromString(savedInstanceState.getString(
							SAVEDSTATE_SESSION)));
				}

				if(savedInstanceState.containsKey(SAVEDSTATE_SORT)) {
					controller.setSort(PostSort.valueOf(
							savedInstanceState.getString(SAVEDSTATE_SORT)));
				}

				if(savedInstanceState.containsKey(SAVEDSTATE_FRAGMENT)) {
					fragmentSavedInstanceState = savedInstanceState.getBundle(
							SAVEDSTATE_FRAGMENT);
				}
			}

			setTitle(url.humanReadableName(this, false));

			setBaseActivityListing(R.layout.main_single);
			doRefresh(RefreshableFragment.POSTS, false, fragmentSavedInstanceState);

		} else {
			throw new RuntimeException("Nothing to show!");
		}

		recreateSubscriptionListener();
	}

	@Override
	protected void onSaveInstanceState(@NonNull final Bundle outState) {
		super.onSaveInstanceState(outState);

		final UUID session = controller.getSession();
		if(session != null) {
			outState.putString(SAVEDSTATE_SESSION, session.toString());
		}

		final PostSort sort = controller.getSort();
		if(sort != null) {
			outState.putString(SAVEDSTATE_SORT, sort.name());
		}

		if(fragment != null) {
			outState.putBundle(SAVEDSTATE_FRAGMENT, fragment.onSaveInstanceState());
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		final FeedSubscriptionManager.ListenerContext listenerContext
				= mFeedSubscriptionListenerContext.get();

		if(listenerContext != null) {
			listenerContext.removeListener();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {

		final RedditAccount user = RedditAccountManager.getInstance(this)
				.getDefaultAccount();
		final FeedSubscriptionState
				feedSubscriptionState;
		final FeedSubscriptionManager feedSubscriptionManager
				= FeedSubscriptionManager.getSingleton(this, user);

		if(fragment != null
				&& controller.isRandomFeed()
				&& fragment.getFeed() != null) {
			FeedPostListURL url = FeedPostListURL.parse(controller.getUri());
			if(url != null && url.type == FeedPostListURL.Type.RANDOM) {

					final String newFeed = fragment.getFeed().url;
					url = url.changeFeed(newFeed);
					controller = new PostListingController(url, this);

			}
		}

		if(!user.isAnonymous()
				&& (controller.isFeed() || controller.isRandomFeed())
				&& feedSubscriptionManager.areSubscriptionsReady()
				&& fragment != null
				&& fragment.getFeed() != null) {

			feedSubscriptionState = feedSubscriptionManager.getSubscriptionState(
					controller.feedCanonicalName());

		} else {
			feedSubscriptionState = null;
		}

		final String feedDescription = fragment != null
				&& fragment.getFeed() != null
				? fragment.getFeed().description_html
				: null;

		Boolean feedPinState = null;
		Boolean feedBlockedState = null;

		if((controller.isFeed() || controller.isRandomFeed())
				&& fragment != null
				&& fragment.getFeed() != null) {

			try {
				feedPinState = PrefsUtility.pref_pinned_feeds_check(
						fragment.getFeed().getCanonicalId());

				feedBlockedState = PrefsUtility.pref_blocked_feeds_check(
						fragment.getFeed().getCanonicalId());

			} catch(final InvalidFeedNameException e) {
				feedPinState = null;
				feedBlockedState = null;
			}
		}

		OptionsMenuUtility.prepare(
				this,
				menu,
				false,
				true,
				false,
				controller.isSearchResults(),
				controller.isUserPostListing(),
				false,
				controller.isSortable(),
				true,
				controller.isFrontPage(),
				feedSubscriptionState,
				feedDescription != null && !feedDescription.isEmpty(),
				false,
				feedPinState,
				feedBlockedState);

		return true;
	}

	private void recreateSubscriptionListener() {

		final FeedSubscriptionManager.ListenerContext oldContext
				= mFeedSubscriptionListenerContext.getAndSet(
				FeedSubscriptionManager
						.getSingleton(
								this,
								RedditAccountManager.getInstance(this)
										.getDefaultAccount())
						.addListener(this));

		if(oldContext != null) {
			oldContext.removeListener();
		}
	}

	@Override
	public void onRedditAccountChanged() {
		recreateSubscriptionListener();
		postInvalidateOptionsMenu();
		requestRefresh(RefreshableFragment.ALL, false);
	}

	@Override
	protected void doRefresh(
			final RefreshableFragment which,
			final boolean force,
			final Bundle savedInstanceState) {

		if(fragment != null) {
			fragment.cancel();
		}

		fragment = controller.get(this, force, savedInstanceState);
		fragment.setBaseActivityContent(this);
	}

	@Override
	public void onPostSelected(final RedditPreparedPost post) {
		LinkHandler.onLinkClicked(this, post.src.getUrl(), false, post.src.getSrc());
	}

	@Override
	public void onPostCommentsSelected(final RedditPreparedPost post) {
		LinkHandler.onLinkClicked(
				this,
				PostCommentListingURL.forPostId(post.src.getIdAlone()).toString(),
				false);
	}

	@Override
	public void onRefreshPosts() {
		controller.setSession(null);
		requestRefresh(RefreshableFragment.POSTS, true);
	}

	@Override
	public void onPastPosts() {
		final SessionListDialog sessionListDialog = SessionListDialog.newInstance(
				controller.getUri(),
				controller.getSession(),
				SessionChangeType.POSTS);
		sessionListDialog.show(getSupportFragmentManager(), "SessionListDialog");
	}

	@Override
	public void onSubmitPost() {

		final Intent intent = new Intent(this, PostSubmitActivity.class);

		if(controller.isFeed()) {
			intent.putExtra("feed", controller.feedCanonicalName().toString());
		}

		startActivity(intent);
	}

	@Override
	public void onSortSelected(final PostSort order) {
		controller.setSort(order);
		requestRefresh(RefreshableFragment.POSTS, false);
		invalidateOptionsMenu();
	}

	@Override
	public void onSearchPosts() {
		onSearchPosts(controller, this);
	}

	public static void onSearchPosts(
			final PostListingController controller,
			final AppCompatActivity activity) {

		DialogUtils.showSearchDialog(activity, query -> {
			if(query == null) {
				return;
			}

			final SearchPostListURL url;

			if(controller != null && (controller.isFeed()
					|| controller.isFeedCombination()
					|| controller.isFeedSearchResults())) {

				final FeedCanonicalId feedCanonicalId
						= controller.feedCanonicalName();

				if(feedCanonicalId == null) {
					BugReportActivity.handleGlobalError(
							activity,
							new RuntimeException("Can't search post listing "
									+ controller.getUri()));
					return;
				}

				url = SearchPostListURL.build(
						feedCanonicalId.toString(),
						query);
			} else if(controller != null && controller.isList()) {

				final String multiName = controller.listName();
				final String multiUsername = controller.listUsername();

				url = SearchPostListURL.build(multiUsername, multiName, query);
			} else {
				url = SearchPostListURL.build(null, query);
			}

			final Intent intent = new Intent(activity, PostListingActivity.class);
			intent.setData(url.generateJsonUri());
			activity.startActivity(intent);
		});
	}

	@Override
	public void onSubscribe() {
		fragment.onSubscribe();
	}

	@Override
	public void onUnsubscribe() {
		fragment.onUnsubscribe();
	}

	@Override
	public void onSidebar() {
		if(fragment.getFeed() != null) {
			final Intent intent = new Intent(this, HtmlViewActivity.class);
			intent.putExtra(
					"html",
					fragment.getFeed()
							.getSidebarHtml(PrefsUtility.isNightMode()));
			intent.putExtra("title", String.format(Locale.US, "%s: %s",
					getString(R.string.sidebar_activity_title),
					fragment.getFeed().url));
			startActivityForResult(intent, 1);
		}
	}

	@Override
	public void onPin() {

		if(fragment == null) {
			return;
		}

		if(fragment.getFeed() == null) {
			BugReportActivity.handleGlobalError(
					this,
					new RuntimeException("Can't pin post listing "
							+ fragment.getPostListingURL()));
			return;
		}

		try {
			PrefsUtility.pref_pinned_feeds_add(
					this,
					fragment.getFeed().getCanonicalId());

		} catch(final InvalidFeedNameException e) {
			throw new RuntimeException(e);
		}

		invalidateOptionsMenu();
	}

	@Override
	public void onUnpin() {

		if(fragment == null) {
			return;
		}

		if(fragment.getFeed() == null) {
			BugReportActivity.handleGlobalError(
					this,
					new RuntimeException("Can't unpin post listing "
							+ fragment.getPostListingURL()));
			return;
		}

		try {
			PrefsUtility.pref_pinned_feeds_remove(
					this,
					fragment.getFeed().getCanonicalId());

		} catch(final InvalidFeedNameException e) {
			throw new RuntimeException(e);
		}

		invalidateOptionsMenu();
	}

	@Override
	public void onBlock() {
		if(fragment == null) {
			return;
		}

		if(fragment.getFeed() == null) {
			BugReportActivity.handleGlobalError(
					this,
					new RuntimeException("Can't block post listing "
							+ fragment.getPostListingURL()));
			return;
		}

		try {
			PrefsUtility.pref_blocked_feeds_add(
					this,
					fragment.getFeed().getCanonicalId());

		} catch(final InvalidFeedNameException e) {
			throw new RuntimeException(e);
		}

		invalidateOptionsMenu();
	}

	@Override
	public void onUnblock() {
		if(fragment == null) {
			return;
		}

		if(fragment.getFeed() == null) {
			BugReportActivity.handleGlobalError(
					this,
					new RuntimeException("Can't unblock post listing "
							+ fragment.getPostListingURL()));
			return;
		}

		try {
			PrefsUtility.pref_blocked_feeds_remove(
					this,
					fragment.getFeed().getCanonicalId());

		} catch(final InvalidFeedNameException e) {
			throw new RuntimeException(e);
		}

		invalidateOptionsMenu();
	}

	@Override
	public void onSessionSelected(final UUID session, final SessionChangeType type) {
		controller.setSession(session);
		requestRefresh(RefreshableFragment.POSTS, false);
	}

	@Override
	public void onSessionRefreshSelected(final SessionChangeType type) {
		onRefreshPosts();
	}

	@Override
	public void onSessionChanged(
			final UUID session,
			final SessionChangeType type,
			final TimestampUTC timestamp) {
		controller.setSession(session);
	}

	@Override
	public void onBackPressed() {

		if(PrefsUtility.pref_behaviour_back_again()
				&& (mDoubleTapBack_lastTapMs < SystemClock.uptimeMillis() - 5000)) {

			mDoubleTapBack_lastTapMs = SystemClock.uptimeMillis();
			Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show();

		} else if(General.onBackPressed()) {
			super.onBackPressed();
		}
	}

	@Override
	public void onFeedSubscriptionListUpdated(
			final FeedSubscriptionManager feedSubscriptionManager) {
		postInvalidateOptionsMenu();
	}

	@Override
	public void onFeedSubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {
		postInvalidateOptionsMenu();
	}

	@Override
	public void onFeedUnsubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {
		postInvalidateOptionsMenu();
	}

	private void postInvalidateOptionsMenu() {
		runOnUiThread(this::invalidateOptionsMenu);
	}

	@Override
	protected boolean baseActivityAllowToolbarHideOnScroll() {
		return true;
	}

	@Override
	public PostSort getPostSort() {
		return controller.getSort();
	}
}
