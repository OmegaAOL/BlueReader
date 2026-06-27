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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.adapters.GroupedRecyclerViewAdapter;
import org.omegaaol.bluereader.adapters.GroupedRecyclerViewItemLoadingSpinner;
import org.omegaaol.bluereader.adapters.GroupedRecyclerViewItemRRError;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.common.AndroidCommon;
import org.omegaaol.bluereader.common.EventListenerSet;
import org.omegaaol.bluereader.common.FunctionOneArgNoReturn;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.GenerationalCache;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.StringUtils;
import org.omegaaol.bluereader.common.ThreadCheckedVar;
import org.omegaaol.bluereader.common.collections.CollectionStream;
import org.omegaaol.bluereader.bluesky.APIResponseHandler;
import org.omegaaol.bluereader.bluesky.RedditAPI;
import org.omegaaol.bluereader.bluesky.FeedDetails;
import org.omegaaol.bluereader.bluesky.api.FeedSubscriptionManager;
import org.omegaaol.bluereader.bluesky.things.Feed;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;
import org.omegaaol.bluereader.viewholders.FeedItemViewHolder;
import org.omegaaol.bluereader.views.FeedSearchQuickLinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class FeedSearchActivity extends BaseActivity implements
		FeedSubscriptionManager.FeedSubscriptionStateChangeListener {

	private static final String TAG = "FeedSearchActivity";

	private FeedSubscriptionManager mFeedSubscriptionManager;

	@NonNull private final ThreadCheckedVar<SearchView> mSearchView
			= new ThreadCheckedVar<>(null);

	@NonNull private final ThreadCheckedVar<Optional<ArrayList<FeedDetails>>>
			mSubscriptions = new ThreadCheckedVar<>(Optional.empty());

	@NonNull private final ThreadCheckedVar<HashSet<String>> mQueriesPending
			= new ThreadCheckedVar<>(new HashSet<>());

	@NonNull private final ThreadCheckedVar<Boolean> mSubscriptionListPending
			= new ThreadCheckedVar<>(false);

	@NonNull private final ThreadCheckedVar<HashMap<String, ArrayList<FeedDetails>>>
			mQueryResults = new ThreadCheckedVar<>(new HashMap<>());

	@NonNull private final GenerationalCache<FeedDetails, FeedItem>
			mFeedItemCache = new GenerationalCache<>(FeedItem::new);

	@NonNull private Optional<FeedSubscriptionManager.ListenerContext>
			mFeedSubscriptionListenerContext = Optional.empty();

	private LinearLayoutManager mRecyclerViewLayout;
	private GroupedRecyclerViewItemLoadingSpinner mLoadingItem;

	@NonNull private final ThreadCheckedVar<Optional<GroupedRecyclerViewItemRRError>>
			mSubscriptionsErrorItem = new ThreadCheckedVar<>(Optional.empty());

	@NonNull private final ThreadCheckedVar<Optional<GroupedRecyclerViewItemRRError>>
			mQueryErrorItem = new ThreadCheckedVar<>(Optional.empty());

	private GroupedRecyclerViewAdapter mRecyclerViewAdapter;
	private static final int GROUP_QUICK_LINKS = 0;
	private static final int GROUP_FEEDS = 1;
	private static final int GROUP_LOADING_SPINNER = 2;

	@Override
	public void onFeedSubscriptionListUpdated(
			final FeedSubscriptionManager feedSubscriptionManager) {

		AndroidCommon.runOnUiThread(() -> mSubscriptions.set(Optional.empty()));
	}

	@Override
	public void onFeedSubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {
		// Ignore
	}

	@Override
	public void onFeedUnsubscriptionAttempted(
			final FeedSubscriptionManager feedSubscriptionManager) {
		// Ignore
	}

	private class FeedItem
			extends GroupedRecyclerViewAdapter.Item<FeedItemViewHolder> {

		@NonNull private final FeedDetails mFeed;

		private FeedItem(@NonNull final FeedDetails feed) {
			mFeed = feed;
		}

		@Override
		public Class<Feed> getViewType() {
			return Feed.class;
		}

		@Override
		public FeedItemViewHolder onCreateViewHolder(final ViewGroup viewGroup) {
			return new FeedItemViewHolder(viewGroup, FeedSearchActivity.this);
		}

		@Override
		public void onBindViewHolder(final FeedItemViewHolder holder) {
			holder.bind(mFeed);
		}

		@Override
		public boolean isHidden() {
			return false;
		}
	}

	@Override
	protected boolean baseActivityIsToolbarSearchBarEnabled() {
		return true;
	}

	@SuppressLint("NotifyDataSetChanged")
	private void updateList() {

		General.checkThisIsUIThread();

		Log.i(TAG, "Updating list");

		if(mSubscriptionsErrorItem.get().isPresent()) {

			mRecyclerViewAdapter.removeAllFromGroup(GROUP_FEEDS);
			mRecyclerViewAdapter.appendToGroup(
					GROUP_FEEDS,
					mSubscriptionsErrorItem.get().get());

			mLoadingItem.setHidden(true);
			mRecyclerViewAdapter.updateHiddenStatus();

			return;
		}

		final String currentQuery = mSearchView.get().getQuery().toString();

		mRecyclerViewAdapter.removeAllFromGroup(GROUP_FEEDS);

		if(mSubscriptions.get().isEmpty()) {

			Log.i(TAG, "Subscriptions not downloaded yet");

			mLoadingItem.setHidden(false);
			mRecyclerViewAdapter.updateHiddenStatus();

			if(mSubscriptionListPending.get() != Boolean.TRUE) {
				requestSubscriptions();
			}

			mLoadingItem.setHidden(false);
			mRecyclerViewAdapter.updateHiddenStatus();

		} else {

			final HashSet<String> shownFeeds = new HashSet<>(256);

			final ArrayList<FeedDetails> possibleSuggestions
					= new ArrayList<>(mSubscriptions.get().get());

			Collections.sort(possibleSuggestions, (o1, o2) -> o1.name.compareTo(o2.name));

			final String asciiLowercaseQuery = StringUtils.asciiLowercase(currentQuery);

			{
				final Iterator<FeedDetails> it = possibleSuggestions.iterator();

				while(it.hasNext()) {
					final FeedDetails entry = it.next();

					final String lowercaseName
							= StringUtils.asciiLowercase(entry.name);

					if(lowercaseName.startsWith(asciiLowercaseQuery)
							&& shownFeeds.add(lowercaseName)) {
						mRecyclerViewAdapter.appendToGroup(
								GROUP_FEEDS,
								mFeedItemCache.get(entry));
						it.remove();
					}
				}
			}

			{
				final Iterator<FeedDetails> it = possibleSuggestions.iterator();

				while(it.hasNext()) {
					final FeedDetails entry = it.next();

					final String lowercaseName
							= StringUtils.asciiLowercase(entry.name);

					if(lowercaseName.contains(asciiLowercaseQuery)
							&& shownFeeds.add(lowercaseName)) {
						mRecyclerViewAdapter.appendToGroup(
								GROUP_FEEDS,
								mFeedItemCache.get(entry));
						it.remove();
					}
				}
			}

			final ArrayList<FeedDetails> currentQueryResults
					= mQueryResults.get().get(currentQuery);

			if(currentQueryResults != null) {
				for(final FeedDetails feed : currentQueryResults) {
					final String name = feed.name;
					if(shownFeeds.add(name)) {
						mRecyclerViewAdapter.appendToGroup(
								GROUP_FEEDS,
								mFeedItemCache.get(feed));
					}
				}

				mLoadingItem.setHidden(false);
				mRecyclerViewAdapter.updateHiddenStatus();

			} else if(!currentQuery.trim().isEmpty()) {

				if(mQueryErrorItem.get().isPresent()) {
					mRecyclerViewAdapter.appendToGroup(
							GROUP_FEEDS,
							mQueryErrorItem.get().get());

					mLoadingItem.setHidden(true);
					mRecyclerViewAdapter.updateHiddenStatus();

				} else {
					mLoadingItem.setHidden(false);
					mRecyclerViewAdapter.updateHiddenStatus();
				}
			}

			mRecyclerViewAdapter.notifyDataSetChanged();
			mFeedItemCache.nextGeneration();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		PrefsUtility.applyThemeAccent(this);

		super.onCreate(savedInstanceState);

		mFeedSubscriptionManager = FeedSubscriptionManager.getSingleton(
				this,
				RedditAccountManager.getInstance(this).getDefaultAccount());

		final EventListenerSet<String> queryEventListeners = new EventListenerSet<>();

		mLoadingItem = new GroupedRecyclerViewItemLoadingSpinner(this);

		final SearchView searchView = findViewById(R.id.actionbar_search_view);
		mSearchView.set(searchView);
		searchView.setQueryHint(getString(R.string.search_content));
		searchView.requestFocus();

		setBaseActivityListing(R.layout.feed_search_listing);

		final RecyclerView recyclerView = findViewById(R.id.feed_search_recyclerview);

		mRecyclerViewLayout = new LinearLayoutManager(
				this,
				RecyclerView.VERTICAL,
				false);

		mRecyclerViewAdapter = new GroupedRecyclerViewAdapter(3);
		mRecyclerViewAdapter.appendToGroup(GROUP_LOADING_SPINNER, mLoadingItem);

		recyclerView.setLayoutManager(mRecyclerViewLayout);
		recyclerView.setAdapter(mRecyclerViewAdapter);

		mRecyclerViewAdapter.appendToGroup(
				GROUP_QUICK_LINKS,
				new GroupedRecyclerViewAdapter.Item<RecyclerView.ViewHolder>() {
			@Override
			public Class<?> getViewType() {
				return this.getClass();
			}

			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup viewGroup) {

				final FeedSearchQuickLinks quickLinks
						= (FeedSearchQuickLinks)LayoutInflater.from(viewGroup.getContext())
								.inflate(
										R.layout.feed_search_quick_links,
										viewGroup,
										false);

				quickLinks.bind(FeedSearchActivity.this, queryEventListeners);

				return new RecyclerView.ViewHolder(quickLinks) {};
			}

			@Override
			public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder) {}

			@Override
			public boolean isHidden() {
				return false;
			}
		});

		mFeedSubscriptionListenerContext
				= Optional.of(mFeedSubscriptionManager.addListener(this));

		requestSubscriptions();

		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(final String query) {
				handleQueryChanged(query);
				queryEventListeners.send(query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(final String newText) {
				handleQueryChanged(newText);
				queryEventListeners.send(newText);
				return true;
			}
		});

		updateList();
	}

	private void handleQueryChanged(@NonNull final String text) {

		mSubscriptionsErrorItem.set(Optional.empty());
		mQueryErrorItem.set(Optional.empty());

		updateList();
		mRecyclerViewLayout.scrollToPosition(0);

		if(text.isEmpty()) {
			return;
		}

		if(mQueriesPending.get().contains(text)) {
			// Do nothing, let's just wait for now.
			return;
		}

		if(!mQueryResults.get().containsKey(text)) {

			mQueriesPending.get().add(text);

			// Wait 1 second to avoid sending requests too fast
			AndroidCommon.UI_THREAD_HANDLER.postDelayed(
					() -> {
						if(text.contentEquals(mSearchView.get().getQuery())) {
							doSearchRequest(text);
						} else {
							mQueriesPending.get().remove(text);
						}
					},
					1000
			);
		}
	}

	private void doSearchRequest(@NonNull final String text) {

		Log.i(TAG, "Running search");

		final CacheManager cacheManager = CacheManager.getInstance(this);
		final RedditAccount user
				= RedditAccountManager.getInstance(this).getDefaultAccount();

		RedditAPI.searchFeeds(
				cacheManager,
				user,
				text,
				this,
				new APIResponseHandler.ValueResponseHandler<
						RedditAPI.FeedListResponse>(this) {

					@Override
					protected void onSuccess(
							@NonNull final RedditAPI.FeedListResponse value) {

						Log.i(TAG, "Search results received");

						final ArrayList<FeedDetails> results
								= new CollectionStream<>(value.feeds)
										.map(FeedDetails::newWithRuntimeException)
										.collect(new ArrayList<>());

						AndroidCommon.runOnUiThread(() -> {
							mQueryResults.get().put(text, results);
							mQueriesPending.get().remove(text);
							updateList();
						});
					}

					@Override
					protected void onCallbackException(final Throwable t) {

						BugReportActivity.handleGlobalError(
								FeedSearchActivity.this,
								t);

						AndroidCommon.runOnUiThread(() -> mQueriesPending.get().remove(text));
					}

					@Override
					protected void onFailure(@NonNull final RRError error) {

						Log.i(TAG, "Got error receiving search results: " + error);

						AndroidCommon.runOnUiThread(() -> {
							mQueriesPending.get().remove(text);
							mQueryErrorItem.set(Optional.of(
									new GroupedRecyclerViewItemRRError(
											FeedSearchActivity.this,
											error)));
							updateList();
						});
					}
				},
				Optional.empty());
	}

	private void requestSubscriptions() {

		if(mSubscriptionListPending.get() == Boolean.TRUE) {
			Log.i(TAG, "Subscription list already pending");
			return;
		}

		mSubscriptionListPending.set(true);

		final FunctionOneArgNoReturn<ArrayList<FeedCanonicalId>> onSuccess
				= list -> AndroidCommon.runOnUiThread(() -> {

			if(mSubscriptionListPending.get() && list != null) {

				mSubscriptionListPending.set(false);

				final ArrayList<FeedDetails> subscriptions = new CollectionStream<>(list)
						.map(FeedDetails::new)
						.collect(new ArrayList<>());

				mSubscriptions.set(Optional.of(subscriptions));
			}
		});

		mFeedSubscriptionManager.triggerUpdateIfNotReady(
				error -> AndroidCommon.runOnUiThread(() -> {

			mQueryErrorItem.set(Optional.of(
					new GroupedRecyclerViewItemRRError(this, error)));
			updateList();
		}));

		mFeedSubscriptionManager.addListener(
				new FeedSubscriptionManager.FeedSubscriptionStateChangeListener() {

			@Override
			public void onFeedSubscriptionListUpdated(
					final FeedSubscriptionManager feedSubscriptionManager) {

				onSuccess.apply(feedSubscriptionManager.getSubscriptionList());
			}

			@Override
			public void onFeedSubscriptionAttempted(
					final FeedSubscriptionManager feedSubscriptionManager) {}

			@Override
			public void onFeedUnsubscriptionAttempted(
					final FeedSubscriptionManager feedSubscriptionManager) {}
		});

		onSuccess.apply(mFeedSubscriptionManager.getSubscriptionList());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mFeedSubscriptionListenerContext.apply(
				FeedSubscriptionManager.ListenerContext::removeListener);
	}
}
