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

package org.omegaaol.bluereader.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.omegaaol.bluereader.common.GenericFactory;
import org.omegaaol.bluereader.common.RRError;
import org.omegaaol.bluereader.common.datastream.SeekableInputStream;
import org.omegaaol.bluereader.common.time.TimestampUTC;

import java.io.IOException;
import java.util.UUID;

public interface CacheRequestCallbacks {

	default void onDownloadNecessary() {}

	default void onDownloadStarted() {}

	default void onDataStreamAvailable(
			@NonNull final GenericFactory<SeekableInputStream, IOException> streamFactory,
			final TimestampUTC timestamp,
			@NonNull final UUID session,
			final boolean fromCache,
			@Nullable final String mimetype) {}

	default void onDataStreamComplete(
			@NonNull final GenericFactory<SeekableInputStream, IOException> streamFactory,
			final TimestampUTC timestamp,
			@NonNull final UUID session,
			final boolean fromCache,
			@Nullable final String mimetype) {}

	default void onProgress(
			final boolean authorizationInProgress,
			final long bytesRead,
			final long totalBytes) {}

	void onFailure(@NonNull RRError error);

	default void onCacheFileWritten(
			@NonNull final CacheManager.ReadableCacheFile cacheFile,
			final TimestampUTC timestamp,
			@NonNull final UUID session,
			final boolean fromCache,
			@Nullable final String mimetype) {}
}
