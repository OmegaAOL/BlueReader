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

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestJSONParser;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyAlways;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.TimestampBound;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.http.FailedRequestBody;
import org.omegaaol.bluereader.io.CacheDataSource;
import org.omegaaol.bluereader.io.RequestResponseHandler;
import org.omegaaol.bluereader.jsonwrap.JsonValue;
import org.omegaaol.bluereader.reddit.RedditSubredditHistory;
import org.omegaaol.bluereader.reddit.things.InvalidSubredditNameException;
import org.omegaaol.bluereader.reddit.things.RedditSubreddit;
import org.omegaaol.bluereader.reddit.things.RedditThing;
import org.omegaaol.bluereader.reddit.things.SubredditCanonicalId;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RedditAPIIndividualSubredditDataRequester implements
		CacheDataSource<SubredditCanonicalId, RedditSubreddit, RRError> {

	private static final String TAG = "IndividualSRDataReq";

	private final Context context;
	private final RedditAccount user;

	public RedditAPIIndividualSubredditDataRequester(
			final Context context,
			final RedditAccount user) {
		this.context = context;
		this.user = user;
	}

	@Override
	public void performRequest(
			final SubredditCanonicalId subredditCanonicalId,
			final TimestampBound timestampBound,
			final RequestResponseHandler<RedditSubreddit, RRError> handler) {

		final URI url = Constants.Bluesky.getUri(subredditCanonicalId.toString() + "/about.json");

		final CacheRequest aboutSubredditCacheRequest = new CacheRequest(
				url,
				user,
				null,
				new Priority(Constants.Priority.API_SUBREDDIT_INVIDIVUAL),
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.SUBREDDIT_ABOUT,
				CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache) {

						try {
							final RedditThing subredditThing = result.asObject(RedditThing.class);
							final RedditSubreddit subreddit = subredditThing.asSubreddit();
							subreddit.downloadTime = timestamp.toUtcMs();
							handler.onRequestSuccess(subreddit, timestamp);

							RedditSubredditHistory.addSubreddit(user, subredditCanonicalId);

						} catch(final Exception e) {
							handler.onRequestFailed(General.getGeneralErrorForFailure(
									context,
									CacheRequest.REQUEST_FAILURE_PARSE,
									e,
									null,
									url.toString(),
									Optional.of(new FailedRequestBody(result))));
						}
					}

					@Override
					public void onFailure(@NonNull final RRError error) {
						handler.onRequestFailed(error);
					}
				}));

		CacheManager.getInstance(context).makeRequest(aboutSubredditCacheRequest);
	}

	@Override
	public void performRequest(
			final Collection<SubredditCanonicalId> subredditCanonicalIds,
			final TimestampBound timestampBound,
			final RequestResponseHandler<
					HashMap<SubredditCanonicalId, RedditSubreddit>,
					RRError> handler) {

		// TODO if there's a bulk API to do this, that would be good... :)

		final HashMap<SubredditCanonicalId, RedditSubreddit> result = new HashMap<>();
		final AtomicBoolean stillOkay = new AtomicBoolean(true);
		final AtomicInteger requestsToGo
				= new AtomicInteger(subredditCanonicalIds.size());
		final AtomicReference<TimestampUTC> oldestResult = new AtomicReference<>(null);

		final RequestResponseHandler<RedditSubreddit, RRError>
				innerHandler
				= new RequestResponseHandler<RedditSubreddit, RRError>() {
			@Override
			public void onRequestFailed(final RRError failureReason) {
				synchronized(result) {
					if(stillOkay.get()) {
						stillOkay.set(false);
						handler.onRequestFailed(failureReason);
					}
				}
			}

			@Override
			public void onRequestSuccess(
					final RedditSubreddit innerResult,
					final TimestampUTC timeCached) {

				synchronized(result) {
					if(stillOkay.get()) {

						try {
							final SubredditCanonicalId canonicalId
									= innerResult.getCanonicalId();

							result.put(canonicalId, innerResult);

							synchronized (oldestResult) {
								if(oldestResult.get() == null) {
									oldestResult.set(timeCached);
								} else {
									oldestResult.set(TimestampUTC.oldest(
											oldestResult.get(),
											timeCached));
								}
							}

							RedditSubredditHistory.addSubreddit(user, canonicalId);
						} catch(final InvalidSubredditNameException e) {
							Log.e(TAG, "Invalid subreddit name " + innerResult.name, e);
						}

						if(requestsToGo.decrementAndGet() == 0) {
							handler.onRequestSuccess(result, oldestResult.get());
						}
					}
				}
			}
		};

		for(final SubredditCanonicalId subredditCanonicalId : subredditCanonicalIds) {
			performRequest(subredditCanonicalId, timestampBound, innerHandler);
		}
	}

	@Override
	public void performWrite(final RedditSubreddit value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void performWrite(final Collection<RedditSubreddit> values) {
		throw new UnsupportedOperationException();
	}
}
