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

import android.content.Context;
import android.net.Uri;
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
import org.omegaaol.bluereader.common.UnexpectedInternalStateException;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.http.FailedRequestBody;
import org.omegaaol.bluereader.io.CacheDataSource;
import org.omegaaol.bluereader.io.RequestResponseHandler;
import org.omegaaol.bluereader.io.WritableHashSet;
import org.omegaaol.bluereader.jsonwrap.JsonArray;
import org.omegaaol.bluereader.jsonwrap.JsonObject;
import org.omegaaol.bluereader.jsonwrap.JsonValue;
import org.omegaaol.bluereader.bluesky.FeedManager;
import org.omegaaol.bluereader.bluesky.things.InvalidFeedNameException;
import org.omegaaol.bluereader.bluesky.things.Feed;
import org.omegaaol.bluereader.bluesky.things.RedditThing;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class IndividualFeedListRequester implements CacheDataSource<
		FeedManager.FeedListType, WritableHashSet, RRError> {

	private final Context context;
	private final RedditAccount user;

	public IndividualFeedListRequester(
			final Context context,
			final RedditAccount user) {
		this.context = context;
		this.user = user;
	}

	@Override
	public void performRequest(
			final FeedManager.FeedListType type,
			final TimestampBound timestampBound,
			final RequestResponseHandler<WritableHashSet, RRError> handler) {

		if(type == FeedManager.FeedListType.DEFAULTS) {

			final TimestampUTC now = TimestampUTC.now();

			final HashSet<String> data =
					new HashSet<>(Constants.Bluesky.DEFAULT_FEEDS.size() + 1);

			for(final FeedCanonicalId id : Constants.Bluesky.DEFAULT_FEEDS) {
				data.add(id.toString());
			}

			final WritableHashSet result = new WritableHashSet(data, now, "DEFAULTS");
			handler.onRequestSuccess(result, now);

			return;
		}

		if(type == FeedManager.FeedListType.MOST_POPULAR) {
			doFeedListRequest(
					FeedManager.FeedListType.MOST_POPULAR,
					handler,
					null);

		} else if(user.isAnonymous()) {
			switch(type) {

				case SUBSCRIBED:
					performRequest(
							FeedManager.FeedListType.DEFAULTS,
							timestampBound,
							handler);
					return;

				case MODERATED: {
					final TimestampUTC curTime = TimestampUTC.now();
					handler.onRequestSuccess(
							new WritableHashSet(
									new HashSet<>(),
									curTime,
									FeedManager.FeedListType.MODERATED.name()),
							curTime);
					return;
				}

				case LISTS: {
					final TimestampUTC curTime = TimestampUTC.now();
					handler.onRequestSuccess(
							new WritableHashSet(
									new HashSet<>(),
									curTime,
									FeedManager.FeedListType.LISTS.name()),
							curTime);
					return;
				}

				default:
					throw new RuntimeException(
							"Internal error: unknown feed list type '"
									+ type.name()
									+ "'");
			}

		} else {
			doFeedListRequest(type, handler, null);
		}
	}

	private void doFeedListRequest(
			final FeedManager.FeedListType type,
			final RequestResponseHandler<WritableHashSet, RRError> handler,
			final String after) {

		final URI uri;

		{
			final URI baseUri;

			switch(type) {
				case SUBSCRIBED:
					baseUri = Constants.Bluesky.getUri(
							Constants.Bluesky.PATH_FEEDS_SUGGESTED);
					break;
				case MODERATED:
					baseUri = Constants.Bluesky.getUri(
							Constants.Bluesky.PATH_FEEDS_MINE_MODERATOR);
					break;
				case MOST_POPULAR:
					baseUri = Constants.Bluesky.getUri(
							Constants.Bluesky.PATH_FEEDS_POPULAR);
					break;
				default:
					throw new UnexpectedInternalStateException(type.name());
			}

			if(after == null) {
				uri = baseUri;

			} else {
				final Uri.Builder builder = Uri.parse(baseUri.toString()).buildUpon();
				builder.appendQueryParameter("after", after);
				uri = General.uriFromString(builder.toString());
			}
		}

		final CacheRequest aboutFeedCacheRequest = new CacheRequest(
				uri,
				user,
				null,
				new Priority(Constants.Priority.API_FEED_INVIDIVUAL),
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.FEED_LIST,
				CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session, final boolean fromCache) {

						try {

							final HashSet<String> output = new HashSet<>();
							final ArrayList<Feed> toWrite = new ArrayList<>();

							final JsonObject redditListing =
									result.asObject().getObject("data");

							final JsonArray feeds =
									redditListing.getArray("children");

							if(type == FeedManager.FeedListType.SUBSCRIBED
									&& feeds.size() == 0
									&& after == null) {
								performRequest(
										FeedManager.FeedListType.DEFAULTS,
										TimestampBound.ANY,
										handler);
								return;
							}

							for(final JsonValue v : feeds) {
								final RedditThing thing = v.asObject(RedditThing.class);
								final Feed feed = thing.asFeed();

								feed.downloadTime = timestamp.toUtcMs();

								try {
									output.add(feed.getCanonicalId().toString());
									toWrite.add(feed);
								} catch(final InvalidFeedNameException e) {
									Log.e(
											"FeedListRequester",
											"Ignoring invalid feed",
											e);
								}

							}

							FeedManager.getInstance(context, user)
									.offerRawFeedData(toWrite, timestamp);
							final String receivedAfter = redditListing.getString("after");
							if(receivedAfter != null && type !=
									FeedManager.FeedListType.MOST_POPULAR) {

								doFeedListRequest(
										type,
										new RequestResponseHandler<
												WritableHashSet,
												RRError>() {
											@Override
											public void onRequestFailed(
													final RRError failureReason) {
												handler.onRequestFailed(failureReason);
											}

											@Override
											public void onRequestSuccess(
													final WritableHashSet result,
													final TimestampUTC timeCached) {
												output.addAll(result.toHashset());
												handler.onRequestSuccess(new WritableHashSet(
														output,
														timeCached,
														type.name()), timeCached);

												if(after == null) {
													Log.i("FeedListRequester", "Got "
															+ output.size()
															+ " feeds in multiple requests");
												}
											}
										},
										receivedAfter);

							} else {
								handler.onRequestSuccess(new WritableHashSet(
										output,
										timestamp,
										type.name()), timestamp);

								if(after == null) {
									Log.i("FeedListRequester", "Got "
											+ output.size() + " feeds in 1 request");
								}
							}

						} catch(final Exception e) {
							handler.onRequestFailed(General.getGeneralErrorForFailure(
									context,
									CacheRequest.REQUEST_FAILURE_PARSE,
									e,
									null,
									uri != null ? uri.toString() : null,
									Optional.of(new FailedRequestBody(result))));
						}
					}

					@Override
					public void onFailure(@NonNull final RRError error) {
						handler.onRequestFailed(error);
					}
				}));

		CacheManager.getInstance(context).makeRequest(aboutFeedCacheRequest);
	}

	@Override
	public void performRequest(
			final Collection<FeedManager.FeedListType> keys,
			final TimestampBound timestampBound,
			final RequestResponseHandler<
					HashMap<FeedManager.FeedListType, WritableHashSet>,
					RRError> handler) {
		// TODO batch API? or just make lots of requests and build up a hash map?
		throw new UnsupportedOperationException();
	}

	@Override
	public void performWrite(final WritableHashSet value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void performWrite(final Collection<WritableHashSet> values) {
		throw new UnsupportedOperationException();
	}
}
