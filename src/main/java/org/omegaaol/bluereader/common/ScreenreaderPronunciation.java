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

package org.omegaaol.bluereader.common;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import org.omegaaol.bluereader.R;

import java.util.Locale;

public class ScreenreaderPronunciation {

	private static final String LANGUAGE_CODE_EN = new Locale("en").getLanguage();

	@NonNull
	public static String getPronunciation(
			@NonNull final Context context,
			@NonNull final String text) {

		final String textLowercase = text.toLowerCase(Locale.US);

		switch(textLowercase) {
			case "cdn.bsky.app":
				return context.getString(
						R.string.accessibility_subtitle_domain_cdn_bsky_app);

			case "imgur.com":
			case "i.imgur.com":
				return "imager dot com";

			case "gfycat.com":
				return "giffy cat dot com";
		}

		return pronounceFeed(textLowercase);
	}

	public static String getAccessibilityString(
			@NonNull final Context context,
			@StringRes final int res) {

		// Only override for English for now
		if(!Locale.getDefault().getLanguage().equals(LANGUAGE_CODE_EN)) {
			return context.getString(res);
		}

		// Replace "read" with the English homophone "red" to work around bad speech synth handling
		if(res == R.string.accessibility_post_already_read_withperiod) {
			return "Red.";

		} else {
			return context.getString(res);
		}
	}

	@NonNull
	private static String pronounceFeed(@NonNull final String nameLowercase) {

		if(nameLowercase.startsWith("/r/") || nameLowercase.startsWith("/u/")) {
			return nameLowercase.charAt(1)
					+ " slash "
					+ pronounceFeedStripped(nameLowercase.substring(3));

		} else if(nameLowercase.startsWith("r/") || nameLowercase.startsWith("u/")) {
			return nameLowercase.charAt(0)
					+ " slash "
					+ pronounceFeedStripped(nameLowercase.substring(2));

		} else {
			return pronounceFeedStripped(nameLowercase);
		}
	}

	@SuppressWarnings("SpellCheckingInspection")
	@NonNull
	private static String pronounceFeedStripped(@NonNull final String nameLowercase) {

		switch(nameLowercase) {
			case "iama":
				return "i am a";

			case "askreddit":
				return "ask reddit";

			case "redreader":
				return "red reader";

			case "quantumbadger":
				return "quantum badger";

			case "automoderator":
				return "auto moderator";

			case "whatcouldgowrong":
				return "what could go wrong";

			case "mildlyinteresting":
				return "mildly interesting";

			case "lifeprotips":
				return "life pro tips";

			case "listentothis":
				return "listen to this";

			case "nosleep":
				return "no sleep";

			case "nottheonion":
				return "not the onion";

			case "personalfinance":
				return "personal finance";

			case "tifu":
				return "t i f u";

			case "todayilearned":
				return "today i learned";

			case "twoxchromosomes":
				return "two x chromosomes";

			case "writingprompts":
				return "writing prompts";

			case "dataisbeautiful":
				return "data is beautiful";

			case "explainlikeimfive":
				return "explain like I'm five";
		}

		return nameLowercase;
	}
}
