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

package org.omegaaol.bluereader.bluesky.prepared;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.omegaaol.bluereader.bluesky.kthings.RedditComment;
import org.omegaaol.bluereader.bluesky.kthings.RedditIdAndType;
import org.omegaaol.bluereader.bluesky.kthings.UrlEncodedString;
import org.omegaaol.bluereader.bluesky.prepared.bodytext.BodyElement;
import org.omegaaol.bluereader.bluesky.prepared.html.HtmlReader;
import org.omegaaol.bluereader.bluesky.things.RedditThingWithIdAndType;

public class RedditParsedComment implements RedditThingWithIdAndType {

	private final RedditComment mSrc;

	@NonNull private final BodyElement mBody;

	public RedditParsedComment(
			final RedditComment comment,
			final AppCompatActivity activity) {

		mSrc = comment;

		mBody = HtmlReader.parse(
				comment.getBody_html().getDecoded(), // TODO nullable?
				activity);
	}

	@NonNull
	public BodyElement getBody() {
		return mBody;
	}

	public UrlEncodedString getFlair() {
		return mSrc.getAuthor_flair_text();
	}

	@Override
	public String getIdAlone() {
		return mSrc.getIdAlone();
	}

	@Override
	public RedditIdAndType getIdAndType() {
		return mSrc.getIdAndType();
	}

	public RedditComment getRawComment() {
		return mSrc;
	}
}
