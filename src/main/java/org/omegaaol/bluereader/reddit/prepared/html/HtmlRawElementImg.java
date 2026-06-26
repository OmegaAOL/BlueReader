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

package org.omegaaol.bluereader.reddit.prepared.html;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.account.RedditAccountManager;
import org.omegaaol.bluereader.cache.CacheManager;
import org.omegaaol.bluereader.cache.CacheRequest;
import org.omegaaol.bluereader.cache.CacheRequestCallbacks;
import org.omegaaol.bluereader.cache.downloadstrategy.DownloadStrategyIfNotCached;
import org.omegaaol.bluereader.common.Constants;
import org.omegaaol.bluereader.common.General;
import org.omegaaol.bluereader.common.GenericFactory;
import org.omegaaol.bluereader.common.Optional;
import org.omegaaol.bluereader.common.Priority;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.datastream.SeekableInputStream;
import org.omegaaol.bluereader.common.time.TimestampUTC;
import org.omegaaol.bluereader.reddit.prepared.bodytext.BodyElement;
import org.omegaaol.bluereader.reddit.prepared.bodytext.DynamicSpanned;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

public class HtmlRawElementImg extends HtmlRawElement{
	@NonNull private final ArrayList<HtmlRawElement> mChildren;
	@NonNull private final String mTitle;
	@NonNull private final String mSrc;

	public HtmlRawElementImg(
			@NonNull final ArrayList<HtmlRawElement> children,
			@NonNull final String title,
			@NonNull final String src) {
		mChildren = children;
		mTitle = title;
		mSrc = src;
	}

	@Override
	public void getPlainText(@NonNull final StringBuilder stringBuilder) {
		for(final HtmlRawElement element : mChildren) {
			element.getPlainText(stringBuilder);
		}
	}

	public final synchronized void writeTo(
			@NonNull final SpannableStringBuilder ssb,
			@NonNull final AppCompatActivity activity,
			@NonNull final DynamicSpanned dynamicSpanned) {
		final int emoteLocationStart = ssb.length();

		ssb.append(mTitle);

		CacheManager.getInstance(activity).makeRequest(new CacheRequest(
				General.uriFromString(mSrc),
				RedditAccountManager.getAnon(),
				null,
				new Priority(Constants.Priority.API_COMMENT_LIST),
				DownloadStrategyIfNotCached.INSTANCE,
				Constants.FileType.IMAGE,
				CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
				activity,
				new CacheRequestCallbacks() {
					Bitmap image = null;

					@Override
					public void onDataStreamComplete(
							@NonNull final GenericFactory<SeekableInputStream, IOException> stream,
							final TimestampUTC timestamp,
							@NonNull final UUID session,
							final boolean fromCache,
							@Nullable final String mimetype) {
						try(InputStream is = stream.create()) {

							image = BitmapFactory.decodeStream(is);

							if (image == null) {
								throw new IOException("Failed to decode bitmap");
							}

							final ImageSpan span = new ImageSpan(
									activity.getApplicationContext(),
									image);

							dynamicSpanned.addSpanDynamic(
									span,
									emoteLocationStart,
									emoteLocationStart + mTitle.length(),
									Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

						} catch (final Throwable t) {
							onFailure(General.getGeneralErrorForFailure(
									activity,
									CacheRequest.REQUEST_FAILURE_CONNECTION,
									t,
									null,
									mSrc,
									Optional.empty()));
						}
					}
					@Override
					public void onFailure(@NonNull final RRError error) {
					}
				}
		));
	}

	@Override
	public void reduce(
			@NonNull final HtmlTextAttributes activeAttributes,
			@NonNull final AppCompatActivity activity,
			@NonNull final ArrayList<HtmlRawElement> destination,
			@NonNull final ArrayList<LinkButtonDetails> linkButtons) {
		destination.add(this);
	}

	@Override
	public void generate(
			@NonNull final AppCompatActivity activity,
			@NonNull final ArrayList<BodyElement> destination) {
		throw new RuntimeException(
				"Attempt to call generate() on inline image: should be inside a block");
	}
}
