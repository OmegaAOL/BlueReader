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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.TimestampBound;
import org.omegaaol.bluereader.common.collections.WeakReferenceListManager;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.io.RawObjectDB;
import org.omegaaol.bluereader.io.RequestResponseHandler;
import org.omegaaol.bluereader.io.WritableHashSet;

import java.util.ArrayList;
import java.util.HashSet;

public class BlueskyListSubscriptionManager {

	private final BlueskyListChangeNotifier notifier
			= new BlueskyListChangeNotifier();
	private final WeakReferenceListManager<BlueskyListChangeListener> listeners
			= new WeakReferenceListManager<>();

	@SuppressLint("StaticFieldLeak") private static BlueskyListSubscriptionManager singleton;
	private static RedditAccount singletonAccount;

	@NonNull private final RedditAccount mUser;
	@NonNull private final Context mContext;

	private static RawObjectDB<String, WritableHashSet> db = null;

	private WritableHashSet mLists;

	public static synchronized BlueskyListSubscriptionManager getSingleton(
			@NonNull final Context context,
			@NonNull final RedditAccount account) {

		if(db == null) {
			db = new RawObjectDB<>(
					context.getApplicationContext(),
					"rr_list_subscriptions.db",
					WritableHashSet.class);
		}

		if(singleton == null
				|| !account.equals(BlueskyListSubscriptionManager.singletonAccount)) {

			singleton = new BlueskyListSubscriptionManager(
					account,
					context.getApplicationContext());

			BlueskyListSubscriptionManager.singletonAccount = account;
		}

		return singleton;
	}

	private BlueskyListSubscriptionManager(
			@NonNull final RedditAccount user,
			@NonNull final Context context) {

		this.mUser = user;
		this.mContext = context;

		mLists = db.getById(user.getCanonicalUsername());
	}

	public void addListener(@NonNull final BlueskyListChangeListener listener) {
		listeners.add(listener);
	}

	public synchronized boolean areSubscriptionsReady() {
		return mLists != null;
	}

	private synchronized void onNewSubscriptionListReceived(
			final HashSet<String> newSubscriptions,
			final TimestampUTC timestamp) {

		mLists = new WritableHashSet(
				newSubscriptions,
				timestamp,
				mUser.getCanonicalUsername());

		listeners.map(notifier);

		// TODO threaded? or already threaded due to cache manager
		db.put(mLists);
	}

	public synchronized ArrayList<String> getSubscriptionList() {
		return new ArrayList<>(mLists.toHashset());
	}

	public void triggerUpdate(
			@Nullable final RequestResponseHandler<
					HashSet<String>,
					RRError> handler,
			@NonNull final TimestampBound timestampBound) {

		if(mLists != null
				&& timestampBound.verifyTimestamp(mLists.getTimestamp())) {
			return;
		}

		new BlueskyListRequester(mContext, mUser).performRequest(
				BlueskyListRequester.Key.INSTANCE,
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
						final HashSet<String> newSubscriptions = result.toHashset();
						onNewSubscriptionListReceived(newSubscriptions, timeCached);
						if(handler != null) {
							handler.onRequestSuccess(newSubscriptions, timeCached);
						}
					}
				}
		);
	}

	public interface BlueskyListChangeListener {
		void onBlueskyListUpdated(
				BlueskyListSubscriptionManager listSubscriptionManager);
	}

	private class BlueskyListChangeNotifier
			implements WeakReferenceListManager.Operator<BlueskyListChangeListener> {

		@Override
		public void operate(final BlueskyListChangeListener listener) {
			listener.onBlueskyListUpdated(
					BlueskyListSubscriptionManager.this);
		}
	}
}
