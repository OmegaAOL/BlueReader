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

package org.omegaaol.bluereader.bluesky.url;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.StringUtils;
import org.omegaaol.bluereader.bluesky.PostSort;
import org.omegaaol.bluereader.bluesky.kthings.RedditIdAndType;
import org.omegaaol.bluereader.bluesky.things.InvalidFeedNameException;
import org.omegaaol.bluereader.bluesky.things.FeedCanonicalId;

import java.util.ArrayList;
import java.util.List;

public class FeedPostListURL extends PostListingURL {

	public static FeedPostListURL getFeedDiscover() {
		return new FeedPostListURL(Type.FEED_DISCOVER, null, null, null, null, null);
	}

	public static FeedPostListURL getPopular() {
		return new FeedPostListURL(Type.POPULAR, null, null, null, null, null);
	}

	public static FeedPostListURL getRandom() {
		return new FeedPostListURL(Type.RANDOM, "random", null, null, null, null);
	}

	public static FeedPostListURL getRandomNsfw() {
		return new FeedPostListURL(Type.RANDOM, "randnsfw", null, null, null, null);
	}

	public static FeedPostListURL getAll() {
		return new FeedPostListURL(Type.TIMELINE, null, null, null, null, null);
	}

	public static RedditURLParser.RedditURL getFeed(final String feed) throws
			InvalidFeedNameException {
		return getFeed(new FeedCanonicalId(feed));
	}

	public static RedditURLParser.RedditURL getFeed(final FeedCanonicalId feed) {

		return RedditURLParser.parse(new Uri.Builder()
				.scheme(Constants.Bluesky.getScheme())
				.authority(Constants.Bluesky.getPDSHost())
				.encodedPath(feed.toString()).build());
	}

	public enum Type {
		FEED_DISCOVER, TIMELINE, FEED, FEED_COMBINATION, ALL_SUBTRACTION, POPULAR, RANDOM
	}

	@NonNull public final Type type;
	@Nullable public final String feed;

	@Nullable public final PostSort order;
	@Nullable public final Integer limit;
	@Nullable public final String before;
	@Nullable public final RedditIdAndType after;

	private FeedPostListURL(
			@NonNull final Type type,
			@Nullable final String feed,
			@Nullable final PostSort order,
			@Nullable final Integer limit,
			@Nullable final String before,
			@Nullable final RedditIdAndType after) {

		this.type = type;
		this.feed = feed;
		this.order = order;
		this.limit = limit;
		this.before = before;
		this.after = after;
	}

	@Override
	public FeedPostListURL after(final RedditIdAndType newAfter) {
		return new FeedPostListURL(type, feed, order, limit, before, newAfter);
	}

	@Override
	public FeedPostListURL limit(final Integer newLimit) {
		return new FeedPostListURL(type, feed, order, newLimit, before, after);
	}

	@Nullable
	@Override
	public PostSort getOrder() {
		return order;
	}

	@Override
	public Uri generateJsonUri() {

		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Constants.Bluesky.getScheme())
				.authority(Constants.Bluesky.getPDSHost());

		switch(type) {

			case FEED_DISCOVER:
				builder.encodedPath("/");
				break;

			case TIMELINE:
				builder.encodedPath(Constants.Bluesky.PATH_TIMELINE);//omegabm
				break;

			case FEED:
			case FEED_COMBINATION:
			case ALL_SUBTRACTION:
			case RANDOM:
				builder.encodedPath("/r/");
				builder.appendPath(feed);
				break;

			case POPULAR:
				builder.encodedPath("/r/popular");
				break;
		}

		if(order != null) {
			order.addToFeedListingUri(builder);
		}

		if(before != null) {
			builder.appendQueryParameter("before", before);
		}

		if(after != null) {
			builder.appendQueryParameter("after", after.getValue());
		}

		if(limit != null) {
			builder.appendQueryParameter("limit", String.valueOf(limit));
		}

		return builder.build();
	}

	@Override
	public @RedditURLParser.PathType
	int pathType() {
		return RedditURLParser.FEED_POST_LISTING_URL;
	}

	public static FeedPostListURL parse(final Uri uri) {

		Integer limit = null;
		String before = null;
		RedditIdAndType after = null;

		for(final String parameterKey : General.getUriQueryParameterNames(uri)) {

			if(parameterKey.equalsIgnoreCase("after")) {
				after = new RedditIdAndType(uri.getQueryParameter(parameterKey));

			} else if(parameterKey.equalsIgnoreCase("before")) {
				before = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("limit")) {
				try {
					limit = Integer.parseInt(uri.getQueryParameter(parameterKey));
				} catch(final Throwable ignored) {
				}

			}
		}

		final String[] pathSegments;
		{
			final List<String> pathSegmentsList = uri.getPathSegments();

			final ArrayList<String> pathSegmentsFiltered =
					new ArrayList<>(pathSegmentsList.size());
			for(String segment : pathSegmentsList) {

				while(StringUtils.asciiLowercase(segment).endsWith(".json")
						|| StringUtils.asciiLowercase(segment).endsWith(".xml")) {
					segment = segment.substring(0, segment.lastIndexOf('.'));
				}

				if(!segment.isEmpty()) {
					pathSegmentsFiltered.add(segment);
				}
			}

			pathSegments = pathSegmentsFiltered.toArray(new String[0]);
		}

		final PostSort order;
		if(pathSegments.length > 0) {
			order = PostSort.parse(
					pathSegments[pathSegments.length - 1],
					uri.getQueryParameter("t"));
		} else {
			order = null;
		}

		switch(pathSegments.length) {
			case 0:
				return new FeedPostListURL(
						Type.FEED_DISCOVER,
						null,
						null,
						limit,
						before,
						after);

			case 1: {
				if(order != null) {
					return new FeedPostListURL(
							Type.FEED_DISCOVER,
							null,
							order,
							limit,
							before,
							after);
				} else {
					return null;
				}
			}

			case 2:
			case 3: {

				if(!(pathSegments[0].equals("xrpc") && pathSegments[1].startsWith("app.bsky.feed"))) {
					return null;
				}

				final String feed = pathSegments[1];

				if(feed.equals("app.bsky.feed.getTimeline")) {

					if(pathSegments.length == 2) { // omegabm333
						return new FeedPostListURL(
								Type.TIMELINE,
								null,
								null,
								limit,
								before,
								after);

					} else if(order != null) {
						return new FeedPostListURL(
								Type.TIMELINE,
								null,
								order,
								limit,
								before,
								after);

					} else {
						return null;
					}

				} else if(feed.equals("popular")) {

					return new FeedPostListURL(
							Type.POPULAR,
							null,
							order,
							limit,
							before,
							after);

				} else if(feed.equals("random") || feed.equals("randnsfw")) {

					return new FeedPostListURL(
							Type.RANDOM,
							feed,
							order,
							limit,
							before,
							after);

				} else if(feed.matches("all(\\-[\\w\\.]+)+")) {

					if(pathSegments.length == 2) {
						return new FeedPostListURL(
								Type.ALL_SUBTRACTION,
								feed,
								null,
								limit,
								before,
								after);

					} else if(order != null) {
						return new FeedPostListURL(
								Type.ALL_SUBTRACTION,
								feed,
								order,
								limit,
								before,
								after);

					} else {
						return null;
					}

				} else if(feed.matches("\\w+(\\+[\\w\\.]+)+")) {

					if(pathSegments.length == 2) {
						return new FeedPostListURL(
								Type.FEED_COMBINATION,
								feed,
								null,
								limit,
								before,
								after);

					} else if(order != null) {
						return new FeedPostListURL(
								Type.FEED_COMBINATION,
								feed,
								order,
								limit,
								before,
								after);

					} else {
						return null;
					}

				} else if(feed.matches("[\\w\\.]+")) {

					if(pathSegments.length == 2) {
						return new FeedPostListURL(
								Type.FEED,
								feed,
								null,
								limit,
								before,
								after);

					} else if(order != null) {
						return new FeedPostListURL(
								Type.FEED,
								feed,
								order,
								limit,
								before,
								after);

					} else {
						return null;
					}

				} else {
					return null;
				}
			}

			default:
				return null;
		}
	}

	@Override
	public String humanReadablePath() {

		final String path = super.humanReadablePath();

		if(order == null) {
			return path;
		}

		switch(order) {
			case CONTROVERSIAL_HOUR:
			case CONTROVERSIAL_DAY:
			case CONTROVERSIAL_WEEK:
			case CONTROVERSIAL_MONTH:
			case CONTROVERSIAL_YEAR:
			case CONTROVERSIAL_ALL:
			case TOP_HOUR:
			case TOP_DAY:
			case TOP_WEEK:
			case TOP_MONTH:
			case TOP_YEAR:
			case TOP_ALL:
				return path + "?t=" + StringUtils.asciiLowercase(order.name().split("_")[1]);

			default:
				return path;
		}
	}

	@Override
	public String humanReadableName(final Context context, final boolean shorter) {

		switch(type) {

			case FEED_DISCOVER:
				return context.getString(R.string.mainmenu_feed_discover);

			case TIMELINE:
				return context.getString(R.string.mainmenu_feed_following);

			case POPULAR:
				return context.getString(R.string.mainmenu_popular);

			case RANDOM:
				return context.getString("randnsfw".equals(feed)
						? R.string.mainmenu_random_nsfw
						: R.string.mainmenu_random);

			case FEED:
				try {
					return new FeedCanonicalId(feed).toString();
				} catch(final InvalidFeedNameException e) {
					return feed;
				}

			case FEED_COMBINATION:
			case ALL_SUBTRACTION:
				return feed;

			default:
				return super.humanReadableName(context, shorter);
		}
	}

	public FeedPostListURL changeFeed(final String newFeed) {
		return new FeedPostListURL(type, newFeed, order, limit, before, after);
	}
}
