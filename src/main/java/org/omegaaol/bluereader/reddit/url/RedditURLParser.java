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

package org.omegaaol.bluereader.reddit.url;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.StringUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class RedditURLParser {

	public static final int SUBREDDIT_POST_LISTING_URL = 0;
	public static final int USER_POST_LISTING_URL = 1;
	public static final int SEARCH_POST_LISTING_URL = 2;
	public static final int UNKNOWN_POST_LISTING_URL = 3;
	public static final int USER_PROFILE_URL = 4;
	public static final int USER_COMMENT_LISTING_URL = 5;
	public static final int UNKNOWN_COMMENT_LISTING_URL = 6;
	public static final int POST_COMMENT_LISTING_URL = 7;
	public static final int MULTIREDDIT_POST_LISTING_URL = 8;
	public static final int COMPOSE_MESSAGE_URL = 9;

	@IntDef({
			SUBREDDIT_POST_LISTING_URL,
			USER_POST_LISTING_URL,
			SEARCH_POST_LISTING_URL,
			UNKNOWN_POST_LISTING_URL,
			USER_PROFILE_URL,
			USER_COMMENT_LISTING_URL,
			UNKNOWN_COMMENT_LISTING_URL,
			POST_COMMENT_LISTING_URL,
			MULTIREDDIT_POST_LISTING_URL,
			COMPOSE_MESSAGE_URL})
	@Retention(RetentionPolicy.SOURCE)
	public @interface PathType {
	}

	private static Optional<Uri> tryGetRedditUri(final Uri uri) {

		if(uri == null || uri.getHost() == null || uri.getPath() == null) {
			return Optional.empty();
		}

		if("bluesky".equals(uri.getScheme()) && "bluesky".equals(uri.getHost())) {
			return Optional.of(uri.buildUpon()
					.scheme("https")
					.authority("bsky.social")
					.build());
		}

		if("bluesky.app.link".equals(uri.getHost())) {
			final String redirect = uri.getQueryParameter("$og_redirect");

			if(redirect != null) {
				return Optional.ofNullable(Uri.parse(redirect));
			}
		}

		final String ampPrefix = "/amp/s/amp.bsky.social";

		if((("google.com".equals(uri.getHost())
				|| uri.getHost().endsWith(".google.com"))
						&& uri.getPath().startsWith(ampPrefix))) {

			return Optional.ofNullable(Uri.parse(
					"https://bsky.social" + uri.getPath().substring(ampPrefix.length())));
		}

		final String[] hostSegments = StringUtils.asciiLowercase(uri.getHost()).split("\\.");

		if(hostSegments.length < 2) {
			return Optional.empty();
		}

		if(hostSegments[hostSegments.length - 1].equals("social")
				&& hostSegments[hostSegments.length - 2].equals("bsky")) {
			return Optional.of(uri);
		}

		if(hostSegments[hostSegments.length - 1].equals("app")
				&& hostSegments[hostSegments.length - 2].equals("bsky")) {
			return Optional.of(uri);
		}

		return Optional.empty();
	}

	@Nullable
	public static RedditURL parse(final Uri rawUri) {

		if(rawUri == null) {
			return null;
		}

		final Optional<Uri> optionalUri = tryGetRedditUri(rawUri);

		if(optionalUri.isEmpty()) {
			return null;
		}

		final Uri uri = optionalUri.get();

		{
			final SubredditPostListURL subredditPostListURL = SubredditPostListURL.parse(uri);
			if(subredditPostListURL != null) {
				return subredditPostListURL;
			}
		}

		{
			final MultiredditPostListURL multiredditPostListURL
					= MultiredditPostListURL.parse(uri);
			if(multiredditPostListURL != null) {
				return multiredditPostListURL;
			}
		}

		{
			final SearchPostListURL searchPostListURL = SearchPostListURL.parse(uri);
			if(searchPostListURL != null) {
				return searchPostListURL;
			}
		}

		{
			final UserPostListingURL userPostListURL = UserPostListingURL.parse(uri);
			if(userPostListURL != null) {
				return userPostListURL;
			}
		}

		{
			final UserCommentListingURL userCommentListURL = UserCommentListingURL.parse(
					uri);
			if(userCommentListURL != null) {
				return userCommentListURL;
			}
		}

		{
			final PostCommentListingURL commentListingURL = PostCommentListingURL.parse(
					uri);
			if(commentListingURL != null) {
				return commentListingURL;
			}
		}

		{
			final UserProfileURL userProfileURL = UserProfileURL.parse(uri);
			if(userProfileURL != null) {
				return userProfileURL;
			}
		}

		{
			final ComposeMessageURL composeMessageURL = ComposeMessageURL.parse(uri);
			//noinspection RedundantIfStatement
			if(composeMessageURL != null) {
				return composeMessageURL;
			}
		}

		return null;
	}

	public static RedditURL parseProbableCommentListing(final Uri uri) {

		final RedditURL matchURL = parse(uri);
		if(matchURL != null) {
			return matchURL;
		}

		return new UnknownCommentListURL(uri);
	}

	@NonNull
	public static RedditURL parseProbablePostListing(final Uri uri) {

		final RedditURL matchURL = parse(uri);
		if(matchURL != null) {
			return matchURL;
		}

		return new UnknownPostListURL(uri);
	}

	public static abstract class RedditURL {
		public abstract Uri generateJsonUri();

		public abstract @PathType
		int pathType();

		public final SubredditPostListURL asSubredditPostListURL() {
			return (SubredditPostListURL)this;
		}

		public final MultiredditPostListURL asMultiredditPostListURL() {
			return (MultiredditPostListURL)this;
		}

		public final SearchPostListURL asSearchPostListURL() {
			return (SearchPostListURL)this;
		}

		public final UserPostListingURL asUserPostListURL() {
			return (UserPostListingURL)this;
		}

		public UserProfileURL asUserProfileURL() {
			return (UserProfileURL)this;
		}

		public PostCommentListingURL asPostCommentListURL() {
			return (PostCommentListingURL)this;
		}

		public UserCommentListingURL asUserCommentListURL() {
			return (UserCommentListingURL)this;
		}

		public ComposeMessageURL asComposeMessageURL() {
			return (ComposeMessageURL)this;
		}

		public String humanReadableName(final Context context, final boolean shorter) {
			return humanReadablePath();
		}

		public String humanReadableUrl() {
			return "bsky.social" + humanReadablePath();
		}

		public String humanReadablePath() {

			final Uri src = generateJsonUri();

			final StringBuilder builder = new StringBuilder();

			for(final String pathElement : src.getPathSegments()) { //omegabm8
				if(!pathElement.equals(".json")) {
					builder.append("/");
					builder.append(pathElement);
				}
			}

			return builder.toString();
		}

		public String browserUrl() {
			return Constants.Bluesky.getScheme() + "://" + humanReadableUrl();
		}

		@Override
		public String toString() {
			return generateJsonUri().toString();
		}
	}
}
