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
import androidx.annotation.NonNull;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestJSONParser;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyIfNotCached;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.http.FailedRequestBody;
import org.omegaaol.bluereader.jsonwrap.JsonObject;
import org.omegaaol.bluereader.jsonwrap.JsonValue;

import java.util.UUID;

public final class ImgurAPIV3 {

	public static void getAlbumInfo(
			final Context context,
			final String albumUrl,
			final String albumId,
			@NonNull final Priority priority,
			final boolean withAuth,
			final GetAlbumInfoListener listener) {

		final String apiUrl = "https://api.imgur.com/3/album/" + albumId;

		CacheManager.getInstance(context).makeRequest(new CacheRequest(
				General.uriFromString(apiUrl),
				RedditAccountManager.getAnon(),
				null,
				priority,
				DownloadStrategyIfNotCached.INSTANCE,
				Constants.FileType.IMAGE_INFO,
				withAuth
						? CacheRequest.DOWNLOAD_QUEUE_IMGUR_API
						: CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache) {

						try {
							final JsonObject outer = result.asObject().getObject("data");
							listener.onSuccess(AlbumInfo.parseImgurV3(albumUrl, outer));

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
			final boolean withAuth,
			final GetImageInfoListener listener) {

		final String apiUrl = "https://api.imgur.com/3/image/" + imageId;

		CacheManager.getInstance(context).makeRequest(new CacheRequest(
				General.uriFromString(apiUrl),
				RedditAccountManager.getAnon(),
				null,
				priority,
				DownloadStrategyIfNotCached.INSTANCE,
				Constants.FileType.IMAGE_INFO,
				withAuth
						? CacheRequest.DOWNLOAD_QUEUE_IMGUR_API
						: CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache) {

						try {
							final JsonObject outer = result.asObject().getObject("data");
							listener.onSuccess(ImageInfo.parseImgurV3(outer));

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
}
