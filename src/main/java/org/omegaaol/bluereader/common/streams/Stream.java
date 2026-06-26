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

package org.omegaaol.bluereader.common.streams;

import androidx.annotation.NonNull;
import org.omegaaol.bluereader.common.Consumer;

import java.util.Iterator;

public interface Stream<E> {

	boolean hasNext();

	E next();

	@NonNull
	default Stream<E> filter(@NonNull final Predicate<E> predicate) {
		return new FilterStream<>(this, predicate);
	}

	default void forEach(@NonNull final Consumer<E> consumer) {
		while(hasNext()) {
			consumer.consume(next());
		}
	}

	@NonNull
	static <E> Stream<E> from(@NonNull final Iterator<E> iterator) {
		return new IteratorStream<>(iterator);
	}

	@NonNull
	static <E> Stream<E> from(@NonNull final Iterable<E> iterable) {
		return new IteratorStream<>(iterable.iterator());
	}
}
