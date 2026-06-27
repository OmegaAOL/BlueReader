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

package org.omegaaol.bluereader.bluesky.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.activities.BugReportActivity;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.common.FunctionOneArgNoReturn;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.TimestampBound;
import org.omegaaol.bluereader.common.UnexpectedInternalStateException;
import org.omegaaol.bluereader.common.collections.CollectionStream;
import org.omegaaol.bluereader.common.collections.WeakReferenceListManager;
import org.omegaaol.bluereader.common.time.TimeDuration;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.io.RawObjectDB;
import org.omegaaol.bluereader.io.RequestResponseHandler;
import org.omegaaol.bluereader.io.WritableHashSet;
import org.omegaaol.bluereader.bluesky.APIResponseHandler;
import org.omegaaol.bluereader.bluesky.RedditAPI;
import org.omegaaol.bluereader.bluesky.FeedHistory;
import org.omegaaol.bluereader.bluesky.FeedManager;
import org.omegaaol.bluereader.bluesky.things.InvalidFeedNameException;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class FeedSubscriptionManager {

	public class ListenerContext {

		private final FeedSubscriptionStateChangeListener mListener;

		private ListenerContext(final FeedSubscriptionStateChangeListener listener) {
			mListener = listener;
		}

		public void removeListener() {
			synchronized(FeedSubscriptionManager.this) {
				listeners.remove(mListener);
			}
		}
	}

	private static final String TAG = "SubscriptionManager";

	private final FeedSubscriptionStateChangeNotifier notifier =
			new FeedSubscriptionStateChangeNotifier();
	private final WeakReferenceListManager<FeedSubscriptionStateChangeListener>
			listeners
			= new WeakReferenceListManager<>();

	@SuppressLint("StaticFieldLeak") private static FeedSubscriptionManager singleton;
	private static RedditAccount singletonAccount;

	private final RedditAccount user;
	private final Context context;

	private static RawObjectDB<String, WritableHashSet> db = null;

	@Nullable private WritableHashSet subscriptions;
	@NonNull private final HashSet<FeedCanonicalId> pendingSubscriptions =
			new HashSet<>();
	@NonNull private final HashSet<FeedCanonicalId> pendingUnsubscriptions =
			new HashSet<>();

	private TimestampUTC mLastUpdateRequestTime = TimestampUTC.ZERO;

	public static synchronized FeedSubscriptionManager getSingleton(
			final Context context,
			final RedditAccount account) {

		if(db == null) {
			db = new RawObjectDB<>(
					context.getApplicationContext(),
					"rr_subscriptions.db",
					WritableHashSet.class);
		}

		if(singleton == null
				|| !account.equals(FeedSubscriptionManager.singletonAccount)) {
			singleton = new FeedSubscriptionManager(
					account,
					context.getApplicationContext());
			FeedSubscriptionManager.singletonAccount = account;
		}

		singleton.triggerUpdateIfNotReady();

		return singleton;
	}

	private FeedSubscriptionManager(final RedditAccount user, final Context context) {

		this.user = user;
		this.context = context;

		subscriptions = db.getById(user.getCanonicalUsername());

		if(subscriptions != null) {
			addToHistory(user, getSubscriptionList());
		}
	}

	public synchronized ListenerContext addListener(
			final FeedSubscriptionStateChangeListener listener) {

		listeners.add(listener);
		return new ListenerContext(listener);
	}

	public synchronized boolean areSubscriptionsReady() {
		return subscriptions != null;
	}

	@Nullable
	public synchronized FeedSubscriptionState getSubscriptionState(
			final FeedCanonicalId id) {

		if(subscriptions == null) {
			return null;
		}

		if(pendingSubscriptions.contains(id)) {
			return FeedSubscriptionState.SUBSCRIBING;
		} else if(pendingUnsubscriptions.contains(id)) {
			return FeedSubscriptionState.UNSUBSCRIBING;
		} else if(subscriptions.toHashset().contains(id.toString())) {
			return FeedSubscriptionState.SUBSCRIBED;
		} else {
			return FeedSubscriptionState.NOT_SUBSCRIBED;
		}
	}

	private synchronized void onSubscriptionAttempt(final FeedCanonicalId id) {
		pendingSubscriptions.add(id);
		listeners.map(notifier, FeedSubscriptionChangeType.SUBSCRIPTION_ATTEMPTED);
	}

	private synchronized void onUnsubscriptionAttempt(final FeedCanonicalId id) {
		pendingUnsubscriptions.add(id);
		listeners.map(notifier, FeedSubscriptionChangeType.UNSUBSCRIPTION_ATTEMPTED);
	}

	private synchronized void onSubscriptionChangeAttemptFailed(final FeedCanonicalId id) {
		pendingUnsubscriptions.remove(id);
		pendingSubscriptions.remove(id);
		listeners.map(notifier, FeedSubscriptionChangeType.LIST_UPDATED);
	}

	private synchronized void onSubscriptionAttemptSuccess(final FeedCanonicalId id) {

		General.quickToast(context, context.getApplicationContext().getString(
				R.string.subscription_successful,
				id.toString()));

		pendingSubscriptions.remove(id);
		subscriptions.toHashset().add(id.toString());
		listeners.map(notifier, FeedSubscriptionChangeType.LIST_UPDATED);
	}

	private synchronized void onUnsubscriptionAttemptSuccess(final FeedCanonicalId id) {

		General.quickToast(context, context.getApplicationContext().getString(
				R.string.unsubscription_successful,
				id.toString()));

		pendingUnsubscriptions.remove(id);
		subscriptions.toHashset().remove(id.toString());
		listeners.map(notifier, FeedSubscriptionChangeType.LIST_UPDATED);
	}

	private static void addToHistory(
			final RedditAccount account,
			final Collection<FeedCanonicalId> newSubscriptions) {

		FeedHistory.addFeeds(account, newSubscriptions);
	}

	private synchronized void onNewSubscriptionListReceived(
			final HashSet<FeedCanonicalId> newSubscriptions,
			final TimestampUTC timestamp) {

		pendingSubscriptions.clear();
		pendingUnsubscriptions.clear();

		final HashSet<String> newSubscriptionsStrings =
				new CollectionStream<>(newSubscriptions)
						.map(FeedCanonicalId::toString).collect(new HashSet<>());

		subscriptions = new WritableHashSet(
				newSubscriptionsStrings,
				timestamp,
				user.getCanonicalUsername());

		// TODO threaded? or already threaded due to cache manager
		db.put(subscriptions);

		addToHistory(user, newSubscriptions);

		listeners.map(notifier, FeedSubscriptionChangeType.LIST_UPDATED);
	}

	@Nullable
	public synchronized ArrayList<FeedCanonicalId> getSubscriptionList() {

		if(subscriptions == null) {
			return null;
		}

		return new CollectionStream<>(subscriptions.toHashset())
				.mapRethrowExceptions(FeedCanonicalId::new)
				.collect(new ArrayList<>());
	}

	public synchronized void triggerUpdateIfNotReady(
			@Nullable final FunctionOneArgNoReturn<RRError> onFailure) {

		final RequestResponseHandler<HashSet<FeedCanonicalId>, RRError> handler
				= new RequestResponseHandler<
						HashSet<FeedCanonicalId>,
						RRError>() {

			@Override
			public void onRequestFailed(final RRError failureReason) {
				if(onFailure != null) {
					onFailure.apply(failureReason);
				}
			}

			@Override
			public void onRequestSuccess(
					final HashSet<FeedCanonicalId> result,
					final TimestampUTC timeCached) {
				// Do nothing
			}
		};

		if(!areSubscriptionsReady()
				&& (mLastUpdateRequestTime == TimestampUTC.ZERO
				|| mLastUpdateRequestTime.elapsed().isGreaterThan(TimeDuration.secs(10)))) {
			triggerUpdate(handler, TimestampBound.notOlderThan(TimeDuration.hours(1)));
		}
	}

	public synchronized void triggerUpdateIfNotReady() {
		triggerUpdateIfNotReady(null);
	}

	public synchronized void triggerUpdate(
			@Nullable final RequestResponseHandler<
					HashSet<FeedCanonicalId>,
					RRError> handler,
			@NonNull final TimestampBound timestampBound) {

		if(subscriptions != null
				&& timestampBound.verifyTimestamp(subscriptions.getTimestamp())) {
			return;
		}

		mLastUpdateRequestTime = TimestampUTC.now();

		new IndividualFeedListRequester(context, user).performRequest(
				FeedManager.FeedListType.SUBSCRIBED,
				timestampBound,
				new RequestResponseHandler<WritableHashSet, RRError>() {

					// TODO handle failed requests properly -- retry? then notify listeners
					@Override
					public void onRequestFailed(final RRError failureReason) {
						if(handler != null) {
							handler.onRequestFailed(failureReason);
						}
					}

					@Override
					public void onRequestSuccess(
							final WritableHashSet result,
							final TimestampUTC timeCached) {
						final HashSet<String> newSubscriptionStrings = result.toHashset();

						final HashSet<FeedCanonicalId> newSubscriptions =
								new HashSet<>();

						for(final String id : newSubscriptionStrings) {
							try {
								newSubscriptions.add(new FeedCanonicalId(id));
							} catch(final InvalidFeedNameException e) {
								Log.e(TAG, "Ignoring invalid feed name " + id, e);
							}
						}

						onNewSubscriptionListReceived(newSubscriptions, timeCached);
						if(handler != null) {
							handler.onRequestSuccess(newSubscriptions, timeCached);
						}
					}
				}
		);

	}

	public void subscribe(
			final FeedCanonicalId id,
			final AppCompatActivity activity) {

		RedditAPI.subscriptionAction(
				CacheManager.getInstance(context),
				new FeedActionResponseHandler(
						activity,
						RedditAPI.SUBSCRIPTION_ACTION_SUBSCRIBE,
						id),
				user,
				id,
				RedditAPI.SUBSCRIPTION_ACTION_SUBSCRIBE,
				context);

		onSubscriptionAttempt(id);
	}

	public void unsubscribe(
			final FeedCanonicalId id,
			final AppCompatActivity activity) {

		RedditAPI.subscriptionAction(
				CacheManager.getInstance(context),
				new FeedActionResponseHandler(
						activity,
						RedditAPI.SUBSCRIPTION_ACTION_UNSUBSCRIBE,
						id),
				user,
				id,
				RedditAPI.SUBSCRIPTION_ACTION_UNSUBSCRIBE,
				context);

		onUnsubscriptionAttempt(id);
	}

	private class FeedActionResponseHandler
			extends APIResponseHandler.ActionResponseHandler {

		private final @RedditAPI.RedditFeedAction int action;
		private final AppCompatActivity activity;
		private final FeedCanonicalId canonicalName;

		protected FeedActionResponseHandler(
				final AppCompatActivity activity,
				@RedditAPI.RedditFeedAction final int action,
				final FeedCanonicalId canonicalName) {
			super(activity);
			this.activity = activity;
			this.action = action;
			this.canonicalName = canonicalName;
		}

		@Override
		protected void onSuccess() {

			switch(action) {
				case RedditAPI.SUBSCRIPTION_ACTION_SUBSCRIBE:
					onSubscriptionAttemptSuccess(canonicalName);
					break;
				case RedditAPI.SUBSCRIPTION_ACTION_UNSUBSCRIBE:
					onUnsubscriptionAttemptSuccess(canonicalName);
					break;
			}
		}

		@Override
		protected void onCallbackException(final Throwable t) {
			BugReportActivity.handleGlobalError(context, t);
		}

		@Override
		protected void onFailure(@NonNull final RRError error) {

			if(error.httpStatus != null && error.httpStatus == 404) {
				// Weirdly, reddit returns a 404 if we were already subscribed/unsubscribed to
				// this feed.

				if(action == RedditAPI.SUBSCRIPTION_ACTION_SUBSCRIBE
						|| action == RedditAPI.SUBSCRIPTION_ACTION_UNSUBSCRIBE) {

					onSuccess();
					return;
				}
			}

			onSubscriptionChangeAttemptFailed(canonicalName);

			General.showResultDialog(activity, error);
		}
	}

	public interface FeedSubscriptionStateChangeListener {
		void onFeedSubscriptionListUpdated(
				FeedSubscriptionManager feedSubscriptionManager);

		void onFeedSubscriptionAttempted(
				FeedSubscriptionManager feedSubscriptionManager);

		void onFeedUnsubscriptionAttempted(
				FeedSubscriptionManager feedSubscriptionManager);
	}

	private enum FeedSubscriptionChangeType {
		LIST_UPDATED,
		SUBSCRIPTION_ATTEMPTED,
		UNSUBSCRIPTION_ATTEMPTED
	}

	private class FeedSubscriptionStateChangeNotifier
			implements WeakReferenceListManager.ArgOperator<
			FeedSubscriptionStateChangeListener,
			FeedSubscriptionChangeType> {

		@Override
		public void operate(
				final FeedSubscriptionStateChangeListener listener,
				final FeedSubscriptionChangeType changeType) {

			switch(changeType) {
				case LIST_UPDATED:
					listener.onFeedSubscriptionListUpdated(
							FeedSubscriptionManager.this);
					break;
				case SUBSCRIPTION_ATTEMPTED:
					listener.onFeedSubscriptionAttempted(
							FeedSubscriptionManager.this);
					break;
				case UNSUBSCRIPTION_ATTEMPTED:
					listener.onFeedUnsubscriptionAttempted(
							FeedSubscriptionManager.this);
					break;
				default:
					throw new UnexpectedInternalStateException(
							"Invalid FeedSubscriptionChangeType " + changeType);
			}
		}
	}
}
