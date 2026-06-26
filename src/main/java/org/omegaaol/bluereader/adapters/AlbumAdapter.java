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

package org.omegaaol.bluereader.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import org.omegaaol.bluereader.R;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.activities.BaseActivity;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestCallbacks;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyIfNotCached;
import org.omegaaol.bluereader.common.AndroidCommon;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.GenericFactory;
import org.omegaaol.bluereader.common.LinkHandler;
import org.omegaaol.bluereader.common.NeverAlwaysOrWifiOnly;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.PrefsUtility;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.datastream.SeekableInputStream;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.image.AlbumInfo;
import org.omegaaol.bluereader.image.ImageInfo;
import org.omegaaol.bluereader.viewholders.VH3TextIcon;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public class AlbumAdapter extends RecyclerView.Adapter<VH3TextIcon> {

	private final BaseActivity activity;
	private final AlbumInfo albumInfo;

	public AlbumAdapter(final BaseActivity activity, final AlbumInfo albumInfo) {
		this.activity = activity;
		this.albumInfo = albumInfo;
	}

	@NonNull
	@Override
	public VH3TextIcon onCreateViewHolder(final ViewGroup parent, final int viewType) {
		final View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_3_text_icon, parent, false);
		return new VH3TextIcon(v);
	}

	@Override
	public void onBindViewHolder(final VH3TextIcon vh, final int position) {

		final long bindingId = ++vh.bindingId;

		final ImageInfo imageInfo = albumInfo.images.get(position);

		if(imageInfo.title == null || imageInfo.title.trim().isEmpty()) {
			vh.text.setText(activity.getString(
					R.string.album_image_default_text,
					position + 1));
		} else {
			//noinspection SetTextI18n
			vh.text.setText((position + 1) + ". " + imageInfo.title.trim());
		}

		String subtitle = "";

		if(imageInfo.type != null) {
			subtitle += imageInfo.type;
		}

		if(imageInfo.width != null && imageInfo.height != null) {
			if(!subtitle.isEmpty()) {
				subtitle += ", ";
			}
			subtitle += imageInfo.width + "x" + imageInfo.height;
		}

		if(imageInfo.size != null) {
			if(!subtitle.isEmpty()) {
				subtitle += ", ";
			}

			final long size = imageInfo.size;
			if(size < 512 * 1024) {
				subtitle += String.format(Locale.US, "%.1f kB", (float)size / 1024);
			} else {
				subtitle += String.format(
						Locale.US,
						"%.1f MB",
						(float)size / (1024 * 1024));
			}
		}


		vh.text2.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);

		vh.text2.setText(subtitle);

		if(imageInfo.caption != null && !imageInfo.caption.isEmpty()) {
			vh.text3.setText(imageInfo.caption);
			vh.text3.setVisibility(View.VISIBLE);
		} else {
			vh.text3.setVisibility(View.GONE);
		}

		vh.icon.setImageBitmap(null);

		final boolean isConnectionWifi = General.isConnectionWifi(activity);

		final NeverAlwaysOrWifiOnly thumbnailsPref
				= PrefsUtility.appearance_thumbnails_show();

		final boolean downloadThumbnails
				= thumbnailsPref == NeverAlwaysOrWifiOnly.ALWAYS
				|| (thumbnailsPref == NeverAlwaysOrWifiOnly.WIFIONLY
						&& isConnectionWifi);

		if(!downloadThumbnails || imageInfo.urlBigSquare == null) {
			vh.icon.setVisibility(View.GONE);

		} else {
			vh.text2.setVisibility(View.VISIBLE);

			CacheManager.getInstance(activity).makeRequest(new CacheRequest(
					General.uriFromString(imageInfo.urlBigSquare),
					RedditAccountManager.getAnon(),
					null,
					new Priority(Constants.Priority.THUMBNAIL, position),
					DownloadStrategyIfNotCached.INSTANCE,
					Constants.FileType.THUMBNAIL,
					CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
					activity,
					new CacheRequestCallbacks() {
						@Override
						public void onFailure(@NonNull final RRError error) {

							if(General.isSensitiveDebugLoggingEnabled()) {
								Log.e(
										"AlbumAdapter",
										"Failed to fetch thumbnail "
												+ imageInfo.urlBigSquare
												+ ": " + error,
										error.t);
							}
						}

						@Override
						public void onDataStreamComplete(
								@NonNull final GenericFactory<SeekableInputStream, IOException>
										streamFactory,
								final TimestampUTC timestamp,
								@NonNull final UUID session,
								final boolean fromCache,
								@Nullable final String mimetype) {

							try {
								final SeekableInputStream is = streamFactory.create();

								final Bitmap bitmap = BitmapFactory.decodeStream(is);

								AndroidCommon.runOnUiThread(() -> {
									if(vh.bindingId == bindingId) {
										vh.icon.setImageBitmap(bitmap);
									}
								});

							} catch(final IOException e) {
								onFailure(General.getGeneralErrorForFailure(
										activity,
										CacheRequest.REQUEST_FAILURE_CONNECTION,
										e,
										null,
										null,
										Optional.empty()));
							}
						}
					}));
		}

		if(imageInfo.urlOriginal != null) {
			vh.itemView.setOnClickListener(v -> LinkHandler.onLinkClicked(
					activity,
					imageInfo.urlOriginal,
					false,
					null,
					albumInfo,
					vh.getAdapterPosition()));

		} else {
			vh.itemView.setOnClickListener(v -> General.showResultDialog(
					activity,
					new RRError(
							activity.getString(R.string.image_gallery_no_image_present_title),
							activity.getString(R.string.image_gallery_no_image_present_message),
							true,
							new RuntimeException(),
							null,
							albumInfo.url,
							null)));
		}

		vh.itemView.setOnLongClickListener(v -> {
			LinkHandler.onLinkLongClicked(activity, imageInfo.urlOriginal, false);
			return true;
		});

	}

	@Override
	public int getItemCount() {
		return albumInfo.images.size();
	}
}
