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

package org.omegaaol.bluereader.bluesky;

import android.content.Context;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.TimestampBound;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.io.RawObjectDB;
import org.omegaaol.bluereader.io.RequestResponseHandler;
import org.omegaaol.bluereader.io.ThreadedRawObjectDB;
import org.omegaaol.bluereader.io.UpdatedVersionListener;
import org.omegaaol.bluereader.io.WeakCache;
import org.omegaaol.bluereader.bluesky.api.IndividualFeedDataRequester;
import org.omegaaol.bluereader.bluesky.things.Feed;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;

import java.util.Collection;
import java.util.HashMap;

public class FeedManager {

	public void offerRawFeedData(
			final Collection<Feed> toWrite,
			final TimestampUTC timestamp) {
		feedCache.performWrite(toWrite);
	}

	// TODO need way to cancel web update and start again?
	// TODO anonymous user

	// TODO Ability to temporarily flag feeds as subscribed/unsubscribed
	// TODO Ability to temporarily add/remove feeds from lists

	// TODO store favourites in preference

	public enum FeedListType {
		SUBSCRIBED,
		MODERATED,
		LISTS,
		MOST_POPULAR,
		DEFAULTS
	}

	private static FeedManager singleton;
	private static RedditAccount singletonUser;

	private final WeakCache<FeedCanonicalId, Feed, RRError>
			feedCache;

	public static synchronized FeedManager getInstance(
			final Context context,
			final RedditAccount user) {

		if(singleton == null || !user.equals(singletonUser)) {
			singletonUser = user;
			singleton = new FeedManager(context, user);
		}

		return singleton;
	}

	private FeedManager(final Context context, final RedditAccount user) {

		// Feed cache

		final RawObjectDB<FeedCanonicalId, Feed> feedDb
				= new RawObjectDB<>(
				context,
				getDbFilename("feeds", user),
				Feed.class);

		final ThreadedRawObjectDB<FeedCanonicalId, Feed, RRError>
				feedDbWrapper
				= new ThreadedRawObjectDB<>(
				feedDb,
				new IndividualFeedDataRequester(context, user));

		feedCache = new WeakCache<>(feedDbWrapper);
	}

	private static String getDbFilename(final String type, final RedditAccount user) {
		return General.sha1(user.username.getBytes()) + "_" + type + "_feeds.db";
	}

	public void getFeed(
			final FeedCanonicalId feedCanonicalId,
			final TimestampBound timestampBound,
			final RequestResponseHandler<Feed, RRError> handler,
			final UpdatedVersionListener<
					FeedCanonicalId, Feed> updatedVersionListener) {

		feedCache.performRequest(
				feedCanonicalId,
				timestampBound,
				handler,
				updatedVersionListener);
	}

	public void getFeeds(
			final Collection<FeedCanonicalId> ids,
			final TimestampBound timestampBound,
			final RequestResponseHandler<
					HashMap<FeedCanonicalId, Feed>,
					RRError> handler) {

		feedCache.performRequest(ids, timestampBound, handler);
	}
}
