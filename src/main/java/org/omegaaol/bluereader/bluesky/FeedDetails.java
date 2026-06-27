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

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.activities.HtmlViewActivity;
import org.omegaaol.bluereader.common.HasUniqueId;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.bluesky.things.InvalidFeedNameException;
import org.omegaaol.bluereader.bluesky.things.Feed;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;

import java.util.Locale;

public class FeedDetails implements HasUniqueId {

	@NonNull public final FeedCanonicalId id;
	@NonNull public final String name;
	@NonNull public final String url;
	@Nullable public final String publicDescriptionHtmlEscaped;
	@Nullable public final Integer subscribers;

	public FeedDetails(
			@NonNull final Feed feed) throws InvalidFeedNameException {
		id = feed.getCanonicalId();
		name = feed.display_name;
		url = feed.getUrl();
		publicDescriptionHtmlEscaped = feed.public_description_html;
		subscribers = feed.subscribers;
	}

	public FeedDetails(@NonNull final FeedCanonicalId feed) {
		id = feed;
		name = feed.toString();
		url = feed.toString();
		publicDescriptionHtmlEscaped = null;
		subscribers = null;
	}

	@NonNull
	public static FeedDetails newWithRuntimeException(
			@NonNull final Feed feed) {

		try {
			return new FeedDetails(feed);
		} catch(final InvalidFeedNameException e) {
			throw new RuntimeException(e);
		}
	}

	@NonNull
	@Override
	public String getUniqueId() {
		return id.toString();
	}

	public boolean hasSidebar() {
		return publicDescriptionHtmlEscaped != null && !publicDescriptionHtmlEscaped.isEmpty();
	}

	public void showSidebarActivity(final AppCompatActivity context) {

		final Intent intent = new Intent(context, HtmlViewActivity.class);

		intent.putExtra("html", Feed.getSidebarHtmlStatic(
				PrefsUtility.isNightMode(),
				publicDescriptionHtmlEscaped));

		intent.putExtra("title", String.format(
				Locale.US, "%s: %s",
				context.getString(R.string.sidebar_activity_title),
				url));

		context.startActivityForResult(intent, 1);
	}
}
