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

package org.omegaaol.bluereader.image;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestJSONParser;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyAlways;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyIfTimestampOutsideBounds;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.StringUtils;
import org.omegaaol.bluereader.common.TimestampBound;
import org.omegaaol.bluereader.common.time.TimeDuration;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.http.FailedRequestBody;
import org.omegaaol.bluereader.http.PostField;
import org.omegaaol.bluereader.http.body.HTTPRequestBodyPostFields;
import org.omegaaol.bluereader.jsonwrap.JsonValue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class RedgifsAPIV2 {

	private static final String TAG = "RedgifsAPIV2";

	private static final AtomicReference<AuthToken> TOKEN = new AtomicReference<>(
			new AuthToken("", 0));

	private static final class AuthToken {
		@NonNull public final String token;
		private final long expireAt;

		private AuthToken(@NonNull final String token, final long expireAt) {
			this.token = token;
			this.expireAt = expireAt;
		}

		public static AuthToken expireIn10Mins(@NonNull final String token) {
			return new AuthToken(token, SystemClock.uptimeMillis() + 10L * 60 * 1000);
		}

		public boolean isValid() {
			return !token.isEmpty() && expireAt > SystemClock.uptimeMillis();
		}
	}

	public static String getLatestToken() {
		return TOKEN.get().token;
	}

	private static void requestMetadata(
			final Context context,
			final String imageId,
			@NonNull final Priority priority,
			final GetImageInfoListener listener) {

		final String apiUrl = "https://api.redgifs.com/v2/gifs/"
				+ StringUtils.asciiLowercase(imageId);

		CacheManager.getInstance(context).makeRequest(new CacheRequest(
				General.uriFromString(apiUrl),
				RedditAccountManager.getAnon(),
				null,
				priority,
				// RedGifs V2 links expire after an undocumented period of time
				new DownloadStrategyIfTimestampOutsideBounds(
						TimestampBound.notOlderThan(TimeDuration.minutes(10))),
				Constants.FileType.IMAGE_INFO,
				CacheRequest.DOWNLOAD_QUEUE_REDGIFS_API_V2,
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache) {

						try {
							listener.onSuccess(ImageInfo.parseRedgifsV2(result
									.getObjectAtPath("gif")
									.orThrow(() -> new RuntimeException("No element 'gif'"))));

							Log.i(TAG, "Got RedGifs v2 metadata");

						} catch(final Throwable t) {
							listener.onFailure(General.getGeneralErrorForFailure(
									context,
									CacheRequest.REQUEST_FAILURE_PARSE,
									t,
									null,
									apiUrl,
									Optional.of(new FailedRequestBody(result))));
						}
					}

					@Override
					public void onFailure(@NonNull final RRError error) {
						listener.onFailure(error);
					}
				})));

	}

	public static void getImageInfo(
			final Context context,
			final String imageId,
			@NonNull final Priority priority,
			final GetImageInfoListener listener) {

		if(TOKEN.get().isValid()) {
			Log.i(TAG, "Existing token still valid");
			requestMetadata(context, imageId, priority, listener);
			return;
		}

		Log.i(TAG, "Retrieving new token");

		final String apiUrl = "https://api.redgifs.com/v2/oauth/client";

		CacheManager.getInstance(context).makeRequest(new CacheRequest(
				General.uriFromString(apiUrl),
				RedditAccountManager.getAnon(),
				null,
				priority,
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.IMAGE_INFO,
				CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
				new HTTPRequestBodyPostFields(
						new PostField("grant_type", "client_credentials"),
						new PostField(
								Constants.OA_CI,
								"1828d09da4e-1011-a880-0005-d2ecbe8daab3"),
						new PostField(
								Constants.OA_CS,
								"yCarP8TUpIr6J2W8YW+vgSRb8HuBd9koW/nkPtsQaP8=")
				),
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache) {

						final Optional<String> accessToken
								= result.getStringAtPath("access_token");

						if(accessToken.isEmpty()) {
							Log.i(TAG, "Failed to get RedGifs v2 token: result not present");
							listener.onFailure(General.getGeneralErrorForFailure(
									context,
									CacheRequest.REQUEST_FAILURE_REQUEST,
									null,
									null,
									apiUrl,
									Optional.of(new FailedRequestBody(result))));
							return;
						}

						Log.i(TAG, "Got RedGifs v2 token");

						TOKEN.set(AuthToken.expireIn10Mins(accessToken.get()));

						requestMetadata(context, imageId, priority, listener);
					}

					@Override
					public void onFailure(@NonNull final RRError error) {

						Log.i(TAG, "Failed to get RedGifs v2 token");
						listener.onFailure(error);
					}
				})

		));
	}


}
