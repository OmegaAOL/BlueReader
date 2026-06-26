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

package org.omegaaol.bluereader.reddit.api;

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
import org.omegaaol.bluereader.reddit.APIResponseHandler;
import org.omegaaol.bluereader.reddit.RedditAPI;
import org.omegaaol.bluereader.reddit.RedditSubredditHistory;
import org.omegaaol.bluereader.reddit.RedditSubredditManager;
import org.omegaaol.bluereader.reddit.things.InvalidSubredditNameException;
import org.omegaaol.bluereader.reddit.things.SubredditCanonicalId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class RedditSubredditSubscriptionManager {

	public class ListenerContext {

		private final SubredditSubscriptionStateChangeListener mListener;

		private ListenerContext(final SubredditSubscriptionStateChangeListener listener) {
			mListener = listener;
		}

		public void removeListener() {
			synchronized(RedditSubredditSubscriptionManager.this) {
				listeners.remove(mListener);
			}
		}
	}

	private static final String TAG = "SubscriptionManager";

	private final SubredditSubscriptionStateChangeNotifier notifier =
			new SubredditSubscriptionStateChangeNotifier();
	private final WeakReferenceListManager<SubredditSubscriptionStateChangeListener>
			listeners
			= new WeakReferenceListManager<>();

	@SuppressLint("StaticFieldLeak") private static RedditSubredditSubscriptionManager singleton;
	private static RedditAccount singletonAccount;

	private final RedditAccount user;
	private final Context context;

	private static RawObjectDB<String, WritableHashSet> db = null;

	@Nullable private WritableHashSet subscriptions;
	@NonNull private final HashSet<SubredditCanonicalId> pendingSubscriptions =
			new HashSet<>();
	@NonNull private final HashSet<SubredditCanonicalId> pendingUnsubscriptions =
			new HashSet<>();

	private TimestampUTC mLastUpdateRequestTime = TimestampUTC.ZERO;

	public static synchronized RedditSubredditSubscriptionManager getSingleton(
			final Context context,
			final RedditAccount account) {

		if(db == null) {
			db = new RawObjectDB<>(
					context.getApplicationContext(),
					"rr_subscriptions.db",
					WritableHashSet.class);
		}

		if(singleton == null
				|| !account.equals(RedditSubredditSubscriptionManager.singletonAccount)) {
			singleton = new RedditSubredditSubscriptionManager(
					account,
					context.getApplicationContext());
			RedditSubredditSubscriptionManager.singletonAccount = account;
		}

		singleton.triggerUpdateIfNotReady();

		return singleton;
	}

	private RedditSubredditSubscriptionManager(final RedditAccount user, final Context context) {

		this.user = user;
		this.context = context;

		subscriptions = db.getById(user.getCanonicalUsername());

		if(subscriptions != null) {
			addToHistory(user, getSubscriptionList());
		}
	}

	public synchronized ListenerContext addListener(
			final SubredditSubscriptionStateChangeListener listener) {

		listeners.add(listener);
		return new ListenerContext(listener);
	}

	public synchronized boolean areSubscriptionsReady() {
		return subscriptions != null;
	}

	@Nullable
	public synchronized SubredditSubscriptionState getSubscriptionState(
			final SubredditCanonicalId id) {

		if(subscriptions == null) {
			return null;
		}

		if(pendingSubscriptions.contains(id)) {
			return SubredditSubscriptionState.SUBSCRIBING;
		} else if(pendingUnsubscriptions.contains(id)) {
			return SubredditSubscriptionState.UNSUBSCRIBING;
		} else if(subscriptions.toHashset().contains(id.toString())) {
			return SubredditSubscriptionState.SUBSCRIBED;
		} else {
			return SubredditSubscriptionState.NOT_SUBSCRIBED;
		}
	}

	private synchronized void onSubscriptionAttempt(final SubredditCanonicalId id) {
		pendingSubscriptions.add(id);
		listeners.map(notifier, SubredditSubscriptionChangeType.SUBSCRIPTION_ATTEMPTED);
	}

	private synchronized void onUnsubscriptionAttempt(final SubredditCanonicalId id) {
		pendingUnsubscriptions.add(id);
		listeners.map(notifier, SubredditSubscriptionChangeType.UNSUBSCRIPTION_ATTEMPTED);
	}

	private synchronized void onSubscriptionChangeAttemptFailed(final SubredditCanonicalId id) {
		pendingUnsubscriptions.remove(id);
		pendingSubscriptions.remove(id);
		listeners.map(notifier, SubredditSubscriptionChangeType.LIST_UPDATED);
	}

	private synchronized void onSubscriptionAttemptSuccess(final SubredditCanonicalId id) {

		General.quickToast(context, context.getApplicationContext().getString(
				R.string.subscription_successful,
				id.toString()));

		pendingSubscriptions.remove(id);
		subscriptions.toHashset().add(id.toString());
		listeners.map(notifier, SubredditSubscriptionChangeType.LIST_UPDATED);
	}

	private synchronized void onUnsubscriptionAttemptSuccess(final SubredditCanonicalId id) {

		General.quickToast(context, context.getApplicationContext().getString(
				R.string.unsubscription_successful,
				id.toString()));

		pendingUnsubscriptions.remove(id);
		subscriptions.toHashset().remove(id.toString());
		listeners.map(notifier, SubredditSubscriptionChangeType.LIST_UPDATED);
	}

	private static void addToHistory(
			final RedditAccount account,
			final Collection<SubredditCanonicalId> newSubscriptions) {

		RedditSubredditHistory.addSubreddits(account, newSubscriptions);
	}

	private synchronized void onNewSubscriptionListReceived(
			final HashSet<SubredditCanonicalId> newSubscriptions,
			final TimestampUTC timestamp) {

		pendingSubscriptions.clear();
		pendingUnsubscriptions.clear();

		final HashSet<String> newSubscriptionsStrings =
				new CollectionStream<>(newSubscriptions)
						.map(SubredditCanonicalId::toString).collect(new HashSet<>());

		subscriptions = new WritableHashSet(
				newSubscriptionsStrings,
				timestamp,
				user.getCanonicalUsername());

		// TODO threaded? or already threaded due to cache manager
		db.put(subscriptions);

		addToHistory(user, newSubscriptions);

		listeners.map(notifier, SubredditSubscriptionChangeType.LIST_UPDATED);
	}

	@Nullable
	public synchronized ArrayList<SubredditCanonicalId> getSubscriptionList() {

		if(subscriptions == null) {
			return null;
		}

		return new CollectionStream<>(subscriptions.toHashset())
				.mapRethrowExceptions(SubredditCanonicalId::new)
				.collect(new ArrayList<>());
	}

	public synchronized void triggerUpdateIfNotReady(
			@Nullable final FunctionOneArgNoReturn<RRError> onFailure) {

		final RequestResponseHandler<HashSet<SubredditCanonicalId>, RRError> handler
				= new RequestResponseHandler<
						HashSet<SubredditCanonicalId>,
						RRError>() {

			@Override
			public void onRequestFailed(final RRError failureReason) {
				if(onFailure != null) {
					onFailure.apply(failureReason);
				}
			}

			@Override
			public void onRequestSuccess(
					final HashSet<SubredditCanonicalId> result,
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
					HashSet<SubredditCanonicalId>,
					RRError> handler,
			@NonNull final TimestampBound timestampBound) {

		if(subscriptions != null
				&& timestampBound.verifyTimestamp(subscriptions.getTimestamp())) {
			return;
		}

		mLastUpdateRequestTime = TimestampUTC.now();

		new RedditAPIIndividualSubredditListRequester(context, user).performRequest(
				RedditSubredditManager.SubredditListType.SUBSCRIBED,
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

						final HashSet<SubredditCanonicalId> newSubscriptions =
								new HashSet<>();

						for(final String id : newSubscriptionStrings) {
							try {
								newSubscriptions.add(new SubredditCanonicalId(id));
							} catch(final InvalidSubredditNameException e) {
								Log.e(TAG, "Ignoring invalid subreddit name " + id, e);
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
			final SubredditCanonicalId id,
			final AppCompatActivity activity) {

		RedditAPI.subscriptionAction(
				CacheManager.getInstance(context),
				new SubredditActionResponseHandler(
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
			final SubredditCanonicalId id,
			final AppCompatActivity activity) {

		RedditAPI.subscriptionAction(
				CacheManager.getInstance(context),
				new SubredditActionResponseHandler(
						activity,
						RedditAPI.SUBSCRIPTION_ACTION_UNSUBSCRIBE,
						id),
				user,
				id,
				RedditAPI.SUBSCRIPTION_ACTION_UNSUBSCRIBE,
				context);

		onUnsubscriptionAttempt(id);
	}

	private class SubredditActionResponseHandler
			extends APIResponseHandler.ActionResponseHandler {

		private final @RedditAPI.RedditSubredditAction int action;
		private final AppCompatActivity activity;
		private final SubredditCanonicalId canonicalName;

		protected SubredditActionResponseHandler(
				final AppCompatActivity activity,
				@RedditAPI.RedditSubredditAction final int action,
				final SubredditCanonicalId canonicalName) {
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
				// this subreddit.

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

	public interface SubredditSubscriptionStateChangeListener {
		void onSubredditSubscriptionListUpdated(
				RedditSubredditSubscriptionManager subredditSubscriptionManager);

		void onSubredditSubscriptionAttempted(
				RedditSubredditSubscriptionManager subredditSubscriptionManager);

		void onSubredditUnsubscriptionAttempted(
				RedditSubredditSubscriptionManager subredditSubscriptionManager);
	}

	private enum SubredditSubscriptionChangeType {
		LIST_UPDATED,
		SUBSCRIPTION_ATTEMPTED,
		UNSUBSCRIPTION_ATTEMPTED
	}

	private class SubredditSubscriptionStateChangeNotifier
			implements WeakReferenceListManager.ArgOperator<
			SubredditSubscriptionStateChangeListener,
			SubredditSubscriptionChangeType> {

		@Override
		public void operate(
				final SubredditSubscriptionStateChangeListener listener,
				final SubredditSubscriptionChangeType changeType) {

			switch(changeType) {
				case LIST_UPDATED:
					listener.onSubredditSubscriptionListUpdated(
							RedditSubredditSubscriptionManager.this);
					break;
				case SUBSCRIPTION_ATTEMPTED:
					listener.onSubredditSubscriptionAttempted(
							RedditSubredditSubscriptionManager.this);
					break;
				case UNSUBSCRIPTION_ATTEMPTED:
					listener.onSubredditUnsubscriptionAttempted(
							RedditSubredditSubscriptionManager.this);
					break;
				default:
					throw new UnexpectedInternalStateException(
							"Invalid SubredditSubscriptionChangeType " + changeType);
			}
		}
	}
}
