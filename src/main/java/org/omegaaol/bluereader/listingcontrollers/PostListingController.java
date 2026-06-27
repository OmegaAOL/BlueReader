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

package org.omegaaol.bluereader.listingcontrollers;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.fragments.PostListingFragment;
import org.omegaaol.bluereader.bluesky.PostSort;
import org.omegaaol.bluereader.bluesky.things.InvalidFeedNameException;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;
import org.omegaaol.bluereader.bluesky.url.PostListingURL;
import org.omegaaol.bluereader.bluesky.url.RedditURLParser;
import org.omegaaol.bluereader.bluesky.url.FeedPostListURL;
import org.omegaaol.bluereader.bluesky.url.UserPostListingURL;

import java.util.UUID;

// TODO add notification/header for abnormal sort order
public class PostListingController {

	private UUID session = null;
	private PostListingURL url;

	public void setSession(final UUID session) {
		this.session = session;
	}

	public UUID getSession() {
		return session;
	}

	public PostListingController(PostListingURL url, final Context context) {

		if(url.pathType() == RedditURLParser.FEED_POST_LISTING_URL) {
			if(url.asFeedPostListURL().order == null) {

				PostSort order = PrefsUtility.pref_behaviour_postsort();

				if(order == PostSort.BEST
						&& url.asFeedPostListURL().type
						!= FeedPostListURL.Type.FEED_DISCOVER) {

					order = PostSort.HOT;
				}

				//url = url.asFeedPostListURL().sort(order); // TODO sort
			}
		} else if(url.pathType() == RedditURLParser.USER_POST_LISTING_URL) {
			if(url.asUserPostListURL().order == null) {
				url = url.asUserPostListURL().sort(PrefsUtility.pref_behaviour_user_postsort());
			}
		} else if(url.pathType() == RedditURLParser.LIST_POST_LISTING_URL) {
			if(url.asListPostListURL().order == null) {
				url = url.asListPostListURL()
						.sort(PrefsUtility.pref_behaviour_multi_postsort());
			}
		}

		this.url = url;
	}

	public boolean isSortable() {
		if(url.pathType() == RedditURLParser.USER_POST_LISTING_URL) {
			return (url.asUserPostListURL().type == UserPostListingURL.Type.SUBMITTED);
		}
		return (url.pathType() == RedditURLParser.FEED_POST_LISTING_URL)
				|| (url.pathType() == RedditURLParser.LIST_POST_LISTING_URL)
				|| (url.pathType() == RedditURLParser.SEARCH_POST_LISTING_URL);
	}

	public boolean isFrontPage() {
		return url.pathType() == RedditURLParser.FEED_POST_LISTING_URL
				&& url.asFeedPostListURL().type
				== FeedPostListURL.Type.FEED_DISCOVER;
	}

	public final void setSort(final PostSort order) {
		if(url.pathType() == RedditURLParser.FEED_POST_LISTING_URL) {
			// url = url.asFeedPostListURL().sort(order); // TODO sort

		} else if(url.pathType() == RedditURLParser.LIST_POST_LISTING_URL) {
			url = url.asListPostListURL().sort(order);

		} else if(url.pathType() == RedditURLParser.SEARCH_POST_LISTING_URL) {
			url = url.asSearchPostListURL().sort(order);

		} else if(url.pathType() == RedditURLParser.USER_POST_LISTING_URL) {
			url = url.asUserPostListURL().sort(order);

		} else {
			throw new RuntimeException("Cannot set sort for this URL");
		}
	}

	public final PostSort getSort() {

		if(url.pathType() == RedditURLParser.FEED_POST_LISTING_URL) {
			return url.asFeedPostListURL().order;
		}

		if(url.pathType() == RedditURLParser.LIST_POST_LISTING_URL) {
			return url.asListPostListURL().order;
		}

		if(url.pathType() == RedditURLParser.SEARCH_POST_LISTING_URL) {
			return url.asSearchPostListURL().order;
		}

		if(url.pathType() == RedditURLParser.USER_POST_LISTING_URL) {
			return url.asUserPostListURL().order;
		}

		return null;
	}

	public Uri getUri() {
		return url.generateJsonUri();
	}

	public final PostListingFragment get(
			final AppCompatActivity parent,
			final boolean force,
			final Bundle savedInstanceState) {
		if(force) {
			session = null;
		}
		return new PostListingFragment(
				parent,
				savedInstanceState,
				getUri(),
				session,
				force);
	}

	public final boolean isFeed() {
		return url.pathType() == RedditURLParser.FEED_POST_LISTING_URL
				&& url.asFeedPostListURL().type
				== FeedPostListURL.Type.FEED;
	}

	public final boolean isFeedCombination() {
		return url.pathType() == RedditURLParser.FEED_POST_LISTING_URL
				&& url.asFeedPostListURL().type
				== FeedPostListURL.Type.FEED_COMBINATION;
	}

	public final boolean isRandomFeed() {
		return url.pathType() == RedditURLParser.FEED_POST_LISTING_URL
				&& url.asFeedPostListURL().type == FeedPostListURL.Type.RANDOM;
	}

	public final boolean isList() {
		return url.pathType() == RedditURLParser.LIST_POST_LISTING_URL;
	}

	public final boolean isSearchResults() {
		return url.pathType() == RedditURLParser.SEARCH_POST_LISTING_URL;
	}

	public final boolean isFeedSearchResults() {
		return isSearchResults() && url.asSearchPostListURL().feed != null;
	}

	public final boolean isUserPostListing() {
		return url.pathType() == RedditURLParser.USER_POST_LISTING_URL;
	}

	public final FeedCanonicalId feedCanonicalName() {

		if(url.pathType() == RedditURLParser.FEED_POST_LISTING_URL
				&& (url.asFeedPostListURL().type
				== FeedPostListURL.Type.FEED
				|| url.asFeedPostListURL().type
				== FeedPostListURL.Type.RANDOM
				|| url.asFeedPostListURL().type
				== FeedPostListURL.Type.FEED_COMBINATION)) {
			try {
				return new FeedCanonicalId(url.asFeedPostListURL().feed);
			} catch(final InvalidFeedNameException e) {
				throw new RuntimeException(e);
			}
		} else if(url.pathType() == RedditURLParser.SEARCH_POST_LISTING_URL
				&& url.asSearchPostListURL().feed != null) {
			try {
				return new FeedCanonicalId(url.asSearchPostListURL().feed);
			} catch(final InvalidFeedNameException e) {
				throw new RuntimeException(e);
			}
		}

		return null;
	}

	public final String listName() {
		if(url.pathType() == RedditURLParser.LIST_POST_LISTING_URL) {
			return url.asListPostListURL().name;
		}

		return null;
	}

	public final String listUsername() {
		if(url.pathType() == RedditURLParser.LIST_POST_LISTING_URL) {
			return url.asListPostListURL().username;
		}

		return null;
	}
}
