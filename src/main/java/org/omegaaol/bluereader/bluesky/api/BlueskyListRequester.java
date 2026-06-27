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
import androidx.annotation.NonNull;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
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
import org.omegaaol.bluereader.io.WritableHashSet;
import org.omegaaol.bluereader.jsonwrap.JsonArray;
import org.omegaaol.bluereader.jsonwrap.JsonValue;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class BlueskyListRequester implements CacheDataSource<
		BlueskyListRequester.Key, WritableHashSet, RRError> {

	public static class Key {
		public static final Key INSTANCE = new Key();

		private Key() {
		}
	}

	private final Context context;
	private final RedditAccount user;

	public BlueskyListRequester(final Context context, final RedditAccount user) {
		this.context = context;
		this.user = user;
	}

	@Override
	public void performRequest(
			final Key key,
			final TimestampBound timestampBound,
			final RequestResponseHandler<WritableHashSet, RRError> handler) {

		if(user.isAnonymous()) {

			final TimestampUTC now = TimestampUTC.now();

			handler.onRequestSuccess(
					new WritableHashSet(
							new HashSet<>(),
							now,
							user.getCanonicalUsername()),
					now);

		} else {
			doRequest(handler);
		}
	}

	private void doRequest(
			final RequestResponseHandler<WritableHashSet, RRError> handler) {

		final URI uri = URI.create(Constants.Bluesky.getUri(Constants.Bluesky.PATH_LISTS_ACTOR) + RedditAccountManager.getInstance(context).getDefaultAccount().username);

		final CacheRequest request = new CacheRequest(
				uri,
				user,
				null,
				new Priority(Constants.Priority.API_FEED_LIST),
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.LIST_LIST,
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
							final HashSet<String> output = new HashSet<>();

							final JsonArray blueskyList = result.asArray();

							for(final JsonValue list : blueskyList) {
								final String name = list.asObject()
										.getObject("data")
										.getString("name");
								output.add(name);
							}

							handler.onRequestSuccess(new WritableHashSet(
									output,
									timestamp,
									user.getCanonicalUsername()), timestamp);

						} catch(final Exception e) {
							handler.onRequestFailed(General.getGeneralErrorForFailure(
									context,
									CacheRequest.REQUEST_FAILURE_PARSE,
									e,
									null,
									uri.toString(),
									Optional.of(new FailedRequestBody(result))));
						}
					}

					@Override
					public void onFailure(@NonNull final RRError error) {
						handler.onRequestFailed(error);
					}
				}));

		CacheManager.getInstance(context).makeRequest(request);
	}

	@Override
	public void performRequest(
			final Collection<Key> keys, final TimestampBound timestampBound,
			final RequestResponseHandler<HashMap<Key, WritableHashSet>,
					RRError> handler) {
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
