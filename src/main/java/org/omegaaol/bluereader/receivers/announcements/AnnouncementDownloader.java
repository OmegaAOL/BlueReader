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

package org.omegaaol.bluereader.receivers.announcements;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestJSONParser;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyAlways;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.HexUtils;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.SharedPrefsWrapper;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.jsonwrap.JsonValue;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

public final class AnnouncementDownloader {

	private static final String TAG = "AnnouncementDownloader";

	private static final String PUBLIC_KEY = "3059301306072A8648CE3D020106082A8648CE3D0301070342000"
			+ "4F74D436746282E6080F0EE9FB80DCDCA06667F701A0266F2F14C15C204B6E48414444BD9D0C1170E6B0"
			+ "C257B3DE1AE23F4BA965D8CEB055A3C374DA927415C5D";

	public static final String PREF_KEY_PAYLOAD_STORAGE_HEX = "AnnouncementDownloaderPayload";
	public static final String PREF_KEY_LAST_READ_ID = "AnnouncementDownloaderLastReadId";

	public static void performDownload(@NonNull final Context context) {

		final boolean announcementsEnabled = PrefsUtility.pref_menus_mainmenu_dev_announcements();

		if(!announcementsEnabled) {
			return;
		}

		CacheManager.getInstance(context).makeRequest(new CacheRequest(
				URI.create("https://ceruleanweb.neocities.org/bluereader/new.json?limit=1"), // TODO announcements
				RedditAccountManager.getAnon(),
				null,
				new Priority(Constants.Priority.DEV_ANNOUNCEMENTS),
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.POST_LIST,
				CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
				false,
				context,
				new CacheRequestJSONParser(context, new CacheRequestJSONParser.Listener() {
					@Override
					public void onJsonParsed(
							@NonNull final JsonValue result,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache) {

						onJsonRetrieved(context, result);
					}

					@Override
					public void onFailure(@NonNull final RRError error) {

						Log.e(
								TAG,
								"Error downloading announcements: " + error,
								error.t);
					}
				})));
	}

	private static void onJsonRetrieved(
			@NonNull final Context context,
			@NonNull final JsonValue root) {

		try {

			final Optional<String> selfText = root.getStringAtPath(
					"data",
					"children",
					0,
					"data",
					"selftext");

			if(selfText.isEmpty()) {
				throw new IOException("Couldn't find self text in response");
			}

			// This verifies the signature
			final byte[] payloadData = SignedDataSerializer.deserialize(
					SignatureHandler.stringToPublicKey(PUBLIC_KEY),
					selfText.get());

			General.getSharedPrefs(context).edit()
					.putString(PREF_KEY_PAYLOAD_STORAGE_HEX, HexUtils.toHex(payloadData))
					.apply();

			Log.i(TAG, "Announcement stored in shared prefs");

		} catch(final Throwable t) {
			Log.e(TAG, "Error parsing announcements", t);
		}
	}

	@NonNull
	public static Optional<Announcement> getMostRecentUnreadAnnouncement(
			@NonNull final SharedPrefsWrapper prefs) {

		try {

			final String hex = prefs.getString(PREF_KEY_PAYLOAD_STORAGE_HEX, "");

			if(hex == null || hex.isEmpty()) {
				Log.i(TAG, "No announcement found in shared prefs");
				return Optional.empty();
			}

			final Announcement announcement
					= Announcement.fromPayload(Payload.fromBytes(HexUtils.fromHex(hex)));

			if(announcement.isExpired()) {
				Log.i(TAG, "Announcement is expired: " + announcement.id);
				return Optional.empty();
			}

			final String lastReadId = prefs.getString(PREF_KEY_LAST_READ_ID, "");

			if(announcement.id.equals(lastReadId)) {
				Log.i(TAG, "Announcement is already read: " + announcement.id);
				return Optional.empty();
			}

			Log.i(TAG, "Got unread announcement: " + announcement.id);

			return Optional.of(announcement);

		} catch(final Throwable t) {
			Log.e(TAG, "Failed to parse stored announcement", t);
			return Optional.empty();
		}
	}

	public static void markAsRead(
			@NonNull final Context context,
			@NonNull final Announcement announcement) {

		General.getSharedPrefs(context).edit()
				.putString(PREF_KEY_LAST_READ_ID, announcement.id)
				.apply();

		Log.i(TAG, "Marked announcement as read: " + announcement.id);
	}
}
