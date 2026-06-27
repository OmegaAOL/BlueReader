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

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import org.omegaaol.bluereader.account.RedditAccount;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.activities.SessionChangeListener;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestCallbacks;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategy;
import org.omegaaol.bluereader.common.AndroidCommon;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.GenericFactory;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.datastream.SeekableInputStream;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.fragments.CommentListingFragment;
import org.omegaaol.bluereader.http.FailedRequestBody;
import org.omegaaol.bluereader.bluesky.kthings.JsonUtils;
import org.omegaaol.bluereader.bluesky.kthings.MaybeParseError;
import org.omegaaol.bluereader.bluesky.kthings.RedditComment;
import org.omegaaol.bluereader.bluesky.kthings.RedditFieldReplies;
import org.omegaaol.bluereader.bluesky.kthings.RedditListing;
import org.omegaaol.bluereader.bluesky.kthings.RedditPost;
import org.omegaaol.bluereader.bluesky.kthings.RedditThing;
import org.omegaaol.bluereader.bluesky.kthings.RedditThingResponse;
import org.omegaaol.bluereader.bluesky.kthings.UrlEncodedString;
import org.omegaaol.bluereader.bluesky.prepared.RedditChangeDataManager;
import org.omegaaol.bluereader.bluesky.prepared.RedditParsedComment;
import org.omegaaol.bluereader.bluesky.prepared.RedditParsedPost;
import org.omegaaol.bluereader.bluesky.prepared.RedditPreparedPost;
import org.omegaaol.bluereader.bluesky.prepared.RedditRenderableComment;
import org.omegaaol.bluereader.bluesky.url.RedditURLParser;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CommentListingRequest {

	private static final String TAG = "CommentListingRequest";

	private final Context mContext;
	private final CommentListingFragment mFragment;
	private final BaseActivity mActivity;
	private final RedditURLParser.RedditURL mCommentListingURL;

	private final boolean mParsePostSelfText;
	private final CacheManager mCacheManager;
	private final RedditURLParser.RedditURL mUrl;
	private final RedditAccount mUser;
	private final UUID mSession;
	private final DownloadStrategy mDownloadStrategy;

	private final Listener mListener;

	public CommentListingRequest(
			final Context context,
			final CommentListingFragment fragment,
			final BaseActivity activity,
			final RedditURLParser.RedditURL commentListingURL,
			final boolean parsePostSelfText,
			final RedditURLParser.RedditURL url,
			final RedditAccount user,
			final UUID session,
			final DownloadStrategy downloadStrategy,
			final Listener listener) {

		mContext = context;
		mFragment = fragment;
		mActivity = activity;
		mCommentListingURL = commentListingURL;
		mParsePostSelfText = parsePostSelfText;
		mUrl = url;
		mUser = user;
		mSession = session;
		mDownloadStrategy = downloadStrategy;
		mListener = listener;

		mCacheManager = CacheManager.getInstance(context);

		mCacheManager.makeRequest(createCommentListingCacheRequest());
	}

	@UiThread
	public interface Listener {

		void onCommentListingRequestDownloadNecessary();

		void onCommentListingRequestFailure(RRError error);

		void onCommentListingRequestCachedCopy(TimestampUTC timestamp);

		void onCommentListingRequestParseStart();

		void onCommentListingRequestPostDownloaded(RedditPreparedPost post);

		void onCommentListingRequestAllItemsDownloaded(ArrayList<RedditCommentListItem> items);
	}

	private void onThingDownloaded(
			@NonNull final RedditThingResponse thingResponse,
			@NonNull final UUID session,
			final TimestampUTC timestamp,
			final boolean fromCache
	) {
		String parentPostAuthor = null;

		if(mActivity instanceof SessionChangeListener) {
			((SessionChangeListener)mActivity).onSessionChanged(
					session,
					SessionChangeListener.SessionChangeType.COMMENTS,
					timestamp);
		}

		final Integer minimumCommentScore
				= PrefsUtility.pref_behaviour_comment_min();

		if(fromCache) {
			AndroidCommon.runOnUiThread(()
					-> mListener.onCommentListingRequestCachedCopy(timestamp));
		}

		AndroidCommon.runOnUiThread(mListener::onCommentListingRequestParseStart);

		@NonNull final RedditListing commentListing;

		if(thingResponse instanceof RedditThingResponse.Single) {
			commentListing = ((RedditThing.Listing)((RedditThingResponse.Single) thingResponse)
					.getThing()).getData();

		} else {
			final RedditThingResponse.Multiple multiple
					= (RedditThingResponse.Multiple) thingResponse;

			if(multiple.getThings().size() != 2) {
				throw new RuntimeException("Expecting 2 items in array response, got "
						+ multiple.getThings().size());
			}

			final RedditPost post
					= ((RedditThing.Post)((RedditThing.Listing)multiple.getThings().get(0))
							.getData()
							.getChildren()
							.get(0)
							.ok()).getData();

			final RedditParsedPost parsedPost =
					new RedditParsedPost(mActivity, post, mParsePostSelfText);

			final RedditPreparedPost preparedPost = new RedditPreparedPost(
					mContext,
					mCacheManager,
					0,
					parsedPost,
					timestamp,
					true,
					false,
					false,
					false);

			AndroidCommon.runOnUiThread(()
					-> mListener.onCommentListingRequestPostDownloaded(
					preparedPost));

			parentPostAuthor = parsedPost.getAuthor();

			commentListing = ((RedditThing.Listing)((RedditThingResponse.Multiple) thingResponse)
					.getThings().get(1)).getData();
		}

		// Download comments

		final ArrayList<MaybeParseError<RedditThing>> topLevelComments
				= commentListing.getChildren();

		final ArrayList<RedditCommentListItem> items
				= new ArrayList<>(200);

		for(final MaybeParseError<RedditThing> commentThingValue : topLevelComments) {
			buildCommentTree(
					commentThingValue,
					null,
					items,
					minimumCommentScore,
					parentPostAuthor);
		}

		final RedditChangeDataManager changeDataManager
				= RedditChangeDataManager.getInstance(mUser);

		for(final RedditCommentListItem item : items) {
			if(item.isComment()) {
				changeDataManager.update(
						timestamp,
						item.asComment().getParsedComment().getRawComment());
			}
		}

		AndroidCommon.runOnUiThread(()
				-> mListener.onCommentListingRequestAllItemsDownloaded(items));
	}

	@NonNull
	private CacheRequest createCommentListingCacheRequest() {

		final URI url = General.uriFromString(mUrl.generateJsonUri().toString());

		return new CacheRequest(
				url,
				mUser,
				mSession,
				new Priority(Constants.Priority.API_COMMENT_LIST),
				mDownloadStrategy,
				Constants.FileType.COMMENT_LIST,
				CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
				mContext,
				new CacheRequestCallbacks() {
					@Override
					public void onFailure(@NonNull final RRError error) {
						AndroidCommon.runOnUiThread(()
								-> mListener.onCommentListingRequestFailure(error));
					}

					@Override
					public void onDownloadNecessary() {
						AndroidCommon.runOnUiThread(
								mListener::onCommentListingRequestDownloadNecessary);
					}

					@Override
					public void onDataStreamAvailable(
							@NonNull final GenericFactory<SeekableInputStream, IOException>
									streamFactory,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache,
							@Nullable final String mimetype) {

						new Thread(null, () -> {
							try {
								final RedditThingResponse thingResponse
										= JsonUtils.INSTANCE.decodeRedditThingResponseFromStream(
												streamFactory.create());

								onThingDownloaded(thingResponse, session, timestamp, fromCache);

							} catch(final Exception e) {
								onFailure(General.getGeneralErrorForFailure(
										mContext,
										CacheRequest.REQUEST_FAILURE_PARSE,
										e,
										null,
										url.toString(),
										FailedRequestBody.from(streamFactory)));
							}
						}, "Comment parsing", 1_000_000).start();
					}
				});
	}

	private void buildCommentTree(
			final MaybeParseError<RedditThing> maybeThing,
			final RedditCommentListItem parent,
			final ArrayList<RedditCommentListItem> output,
			final Integer minimumCommentScore,
			final String parentPostAuthor) {

		// TODO handle gracefully by showing error message
		final RedditThing thing = maybeThing.ok();

		if(thing instanceof RedditThing.More
				&& mUrl.pathType() == RedditURLParser.POST_COMMENT_LISTING_URL) {

			output.add(new RedditCommentListItem(
					((RedditThing.More)thing).getData(),
					parent,
					mFragment,
					mActivity,
					mCommentListingURL));

		} else if(thing instanceof RedditThing.Comment) {
			RedditComment comment = ((RedditThing.Comment) thing).getData();

			if (comment.getMedia_metadata() != null && comment.getBody_html() != null) {
				try {

					for(final Map.Entry<
									UrlEncodedString,
									MaybeParseError<RedditComment.EmoteMetadata>
							> entry : comment.getMedia_metadata().entrySet()) {

						if(!(entry.getValue() instanceof MaybeParseError.Ok)) {
							continue;
						}

						final RedditComment.EmoteMetadata emoteMetadata
								= ((MaybeParseError.Ok<RedditComment.EmoteMetadata>)
										entry.getValue()).getValue();

						// id is always structured as emote|{feed_id}|{emote_id}
						// for feed emotes
						if (emoteMetadata.getId().split("\\|")[0].equalsIgnoreCase("emote")
								&& emoteMetadata.getS().getU() != null) {
							final String feedId = emoteMetadata.getId().split("\\|")[1];

							// These are default reddit emotes (i think).
							// They already have an img tag in the body html
							// so no processing is required for these
							if (feedId.equals("free_emotes_pack")) {
								continue;
							}

							final String emoteId = emoteMetadata.getId().split("\\|")[2];

							final String emotePlaceholder = String.format(Locale.getDefault(),
									":%s:", emoteId);

							final String imgTag = String.format(Locale.getDefault(),
									"<emote src=\"%s\" title=\"%s\"></emote>",
									emoteMetadata.getS().getU(),
									emotePlaceholder);

							comment = comment.copyWithNewBodyHtml(
									comment.getBody_html().getDecoded()
											.replace(emotePlaceholder, imgTag));
						}
					}

				} catch (final Exception e) {
					// Including this try-catch to cover for edge cases where reddit might send
					// different values under media_metadata
					Log.e(
							TAG,
							"Exception while processing media metadata for "
									+ comment.getIdAndType(),
							e);
				}
			}

			final String currentCanonicalUserName = RedditAccountManager.getInstance(mContext)
					.getDefaultAccount().getCanonicalUsername();
			final boolean showFeedName = !(mCommentListingURL != null
					&& mCommentListingURL.pathType() == RedditURLParser.POST_COMMENT_LISTING_URL);
			final boolean neverAutoCollapse = mCommentListingURL != null
					&& mCommentListingURL.pathType() == RedditURLParser.USER_COMMENT_LISTING_URL;

			final RedditCommentListItem item = new RedditCommentListItem(
					new RedditRenderableComment(
							new RedditParsedComment(comment, mActivity),
							parentPostAuthor,
							minimumCommentScore,
							currentCanonicalUserName,
							true,
							showFeedName,
							neverAutoCollapse),
					parent,
					mFragment,
					mActivity,
					mCommentListingURL);

			output.add(item);

			if(comment.getReplies() instanceof RedditFieldReplies.Some) {

				final RedditListing listing = ((RedditThing.Listing)(
						(RedditFieldReplies.Some)comment.getReplies()).getValue()).getData();

				for(final MaybeParseError<RedditThing> reply : listing.getChildren()) {
					buildCommentTree(
							reply,
							item,
							output,
							minimumCommentScore,
							parentPostAuthor);
				}
			}
		}
	}
}
