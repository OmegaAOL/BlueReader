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

package org.omegaaol.bluereader.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.activities.BugReportActivity;
import org.omegaaol.bluereader.activities.PMSendActivity;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestCallbacks;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyAlways;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyIfNotCached;
import org.omegaaol.bluereader.common.AndroidCommon;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.GenericFactory;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.datastream.SeekableInputStream;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.bluesky.APIResponseHandler;
import org.omegaaol.bluereader.bluesky.RedditAPI;
import org.omegaaol.bluereader.bluesky.things.RedditUser;
import org.omegaaol.bluereader.bluesky.url.UserPostListingURL;
import org.omegaaol.bluereader.views.liststatus.ErrorView;
import org.omegaaol.bluereader.views.liststatus.LoadingView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.UUID;

public class UserProfileDialog extends PropertiesDialog {

	private String username;
	private boolean active = true;

	public static UserProfileDialog newInstance(final String user) {

		final UserProfileDialog dialog = new UserProfileDialog();

		final Bundle args = new Bundle();
		args.putString("user", user);
		dialog.setArguments(args);

		return dialog;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		active = false;
	}

	@Override
	protected String getTitle(final Context context) {
		return username;
	}

	@Override
	protected void prepare(
			@NonNull final BaseActivity context,
			@NonNull final LinearLayout items) {

		final LoadingView loadingView = new LoadingView(
				context,
				R.string.download_waiting,
				true,
				true);
		items.addView(loadingView);

		username = getArguments().getString("user");
		final CacheManager cm = CacheManager.getInstance(context);

		RedditAPI.getUser(
				cm,
				username,
				new APIResponseHandler.UserResponseHandler(context) {
					@Override
					protected void onDownloadStarted() {
						if(!active) {
							return;
						}
						loadingView.setIndeterminate(R.string.download_connecting);
					}

					@Override
					protected void onSuccess(final RedditUser user, final TimestampUTC timestamp) {
						AndroidCommon.UI_THREAD_HANDLER.post(() -> {

							if(!active) {
								return;
							}

							loadingView.setDone(R.string.download_done);

							if(PrefsUtility.appearance_user_show_avatars()) {
								final String iconUrl = user.getIconUrl();

								if (iconUrl != null && !iconUrl.equals("")) {
									final LinearLayout avatarLayout
											= (LinearLayout) context.getLayoutInflater()
											.inflate(R.layout.avatar, null);
									items.addView(avatarLayout);

									final ImageView avatarImage
											= avatarLayout
											.findViewById(R.id.layout_avatar_image);

									try {
										assignUserAvatar(iconUrl, avatarImage, context);
									} catch (final URISyntaxException e) {
										Log.d("UserProfileDialog", "Error decoding uri: " + e);
									}
								} else {
									Log.d(
											"UserProfileDialog",
											"Unknown icon url: " + user.icon_img);
								}
							}

							final LinearLayout karmaLayout
									= (LinearLayout)context.getLayoutInflater()
											.inflate(R.layout.karma, null);
							items.addView(karmaLayout);

							final LinearLayout linkKarmaLayout
									= karmaLayout.findViewById(R.id.layout_karma_link);
							final LinearLayout commentKarmaLayout
									= karmaLayout.findViewById(R.id.layout_karma_comment);
							final TextView linkKarma
									= karmaLayout.findViewById(R.id.layout_karma_text_link);
							final TextView commentKarma
									= karmaLayout.findViewById(R.id.layout_karma_text_comment);

							linkKarma.setText(String.valueOf(user.link_karma));
							commentKarma.setText(String.valueOf(user.comment_karma));

							linkKarmaLayout.setOnLongClickListener(v -> {
								final ClipboardManager clipboardManager
										= (ClipboardManager)context.getSystemService(
										Context.CLIPBOARD_SERVICE);
								if(clipboardManager != null) {
									final ClipData data = ClipData.newPlainText(
											context.getString(R.string.karma_link),
											linkKarma.getText());
									clipboardManager.setPrimaryClip(data);

									General.quickToast(
											context,
											R.string.copied_to_clipboard);
								}
								return true;
							});
							commentKarmaLayout.setOnLongClickListener(v -> {
								final ClipboardManager clipboardManager
										= (ClipboardManager)context.getSystemService(
										Context.CLIPBOARD_SERVICE);
								if(clipboardManager != null) {
									final ClipData data = ClipData.newPlainText(
											context.getString(R.string.karma_comment),
											commentKarma.getText());
									clipboardManager.setPrimaryClip(data);

									General.quickToast(
											context,
											R.string.copied_to_clipboard);
								}
								return true;
							});

							items.addView(propView(
									context,
									R.string.userprofile_created,
									TimestampUTC.fromUtcSecs(user.created_utc).format(),
									false));
							items.getChildAt(items.getChildCount() - 1)
									.setNextFocusUpId(R.id.layout_karma_link);

							if(user.is_friend) {
								items.addView(propView(
										context,
										R.string.userprofile_isfriend,
										R.string.general_true,
										false));
							}

							if(user.is_gold) {
								items.addView(propView(
										context,
										R.string.userprofile_isgold,
										R.string.general_true,
										false));
							}

							if(user.is_mod) {
								items.addView(propView(
										context,
										R.string.userprofile_moderator,
										R.string.general_true,
										false));
							}

							final Button commentsButton = new Button(context);
							commentsButton.setText(R.string.userprofile_viewcomments);
							commentsButton.setOnClickListener(v -> LinkHandler.onLinkClicked(
									context,
									Constants.Bluesky.getUri("/user/"
											+ username
											+ "/comments.json")
											.toString(),
									false));
							items.addView(commentsButton);
							// TODO use margin? or framelayout? scale padding dp
							// TODO change button color
							commentsButton.setPadding(20, 20, 20, 20);

							final Button postsButton = new Button(context);
							postsButton.setText(R.string.userprofile_viewposts);
							postsButton.setOnClickListener(v -> LinkHandler.onLinkClicked(
									context,
									UserPostListingURL.getSubmitted(username)
											.generateJsonUri()
											.toString(),
									false));
							items.addView(postsButton);
							// TODO use margin? or framelayout? scale padding dp
							postsButton.setPadding(20, 20, 20, 20);

							if(!RedditAccountManager.getInstance(context)
									.getDefaultAccount()
									.isAnonymous()) {
								final Button pmButton = new Button(context);
								pmButton.setText(R.string.userprofile_pm);
								pmButton.setOnClickListener(v -> {
									final Intent intent = new Intent(
											context,
											PMSendActivity.class);
									intent.putExtra(
											PMSendActivity.EXTRA_RECIPIENT,
											username);
									startActivity(intent);
								});
								items.addView(pmButton);
								pmButton.setPadding(20, 20, 20, 20);
							}
						});
					}

					@Override
					protected void onCallbackException(final Throwable t) {
						BugReportActivity.handleGlobalError(context, t);
					}

					@Override
					protected void onFailure(@NonNull final RRError error) {

						AndroidCommon.UI_THREAD_HANDLER.post(() -> {

							if(!active) {
								return;
							}

							loadingView.setDone(R.string.download_failed);

							items.addView(new ErrorView(context, error));
						});
					}
				},
				RedditAccountManager.getInstance(context).getDefaultAccount(),
				DownloadStrategyAlways.INSTANCE,
				context);
	}

	public void assignUserAvatar(
			final String url,
			final ImageView imageOutput,
			final AppCompatActivity context)
			throws URISyntaxException {
		CacheManager.getInstance(context).makeRequest(new CacheRequest(
			General.uriFromString(url),
			RedditAccountManager.getAnon(),
			null,
			new Priority(Constants.Priority.INLINE_IMAGE_PREVIEW),
			DownloadStrategyIfNotCached.INSTANCE,
			Constants.FileType.INLINE_IMAGE_PREVIEW,
			CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
			context,
			new CacheRequestCallbacks() {
				@Override
				public void onDataStreamComplete(
						final GenericFactory<SeekableInputStream, IOException> streamFactory,
						final TimestampUTC timestamp,
						final UUID session,
						final boolean fromCache,
						final  String mimetype) {
					try(InputStream is = streamFactory.create()) {
						final Bitmap data = BitmapFactory.decodeStream(is);
						if(data == null) {
							throw new IOException("Failed to decode bitmap");
						}
						AndroidCommon.runOnUiThread(() -> {
							imageOutput.setImageBitmap(data);
						});

					} catch(final Throwable t) {
						onFailure(General.getGeneralErrorForFailure(
								context,
								CacheRequest.REQUEST_FAILURE_CONNECTION,
								t,
								null,
								url,
								Optional.empty()));
					}
				}

				@Override
				public void onFailure(@NonNull final RRError error) {
					Log.d(
							"UserProfileDialog",
							"Failed to download user avatar: " + error);
				}
			}
		));
	}
}
