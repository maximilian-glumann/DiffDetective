package org.variantsync.diffdetective.metadata;

import org.tinylog.Logger;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.diffdetective.util.IO;
import org.variantsync.functjonal.Cast;
import org.variantsync.functjonal.category.InplaceSemigroup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Generic interface to model composable and printable metadata.
 * @param <T> The type of metadata. Should be the subclasses type.
 */
public interface Metadata<T> {
    /**
     * Create a key-value store of the metadata that can be used for serialization.
     * @return A LinkedHashMap that stores all relevant properties to export.
     *         The return type has to be a LinkedHashMap to obtain insertion-order iteration.
     */
    LinkedHashMap<String, ?> snapshot();

    void setFromSnapshot(LinkedHashMap<String, String> snapshot);

    /**
     * Metadata should be composable.
     * Composition should be inplace to optimize performance.
     */
    InplaceSemigroup<T> semigroup();

    /**
     * Append the other metadata's values to this metadata.
     * The default implementation uses the {@link #semigroup()} for this purpose.
     * @param other The metadata to append to this metadata. Remains unchanged (if the semigroup leaves it unchanged).
     */
    default void append(T other) {
        semigroup().appendToFirst(Cast.unchecked(this), other);
    }

    /**
     * Composes two equal values by returning that value unmodified.
     * This method is intended to be used to implement a semigroup for objects which can't be merged
     * but should always be the same anyway. If {@code !a.equals(b)} then an {@code AssertionError}
     * is thrown.
     *
     * <p>The value {@code null} is treated as the neutral element in the sense that no exception is
     * thrown if an element is {@code null}. In this case return value is defined by {@code
     * mergeEqual(a, null) == a} and {@code mergeEqual(b, null) == b}.
     *
     * @param a the first element to merge
     * @param b the second element to merge
     * @param <T> the type of the objects to be merged
     * @return {@code a} or {@code b}
     */
    static <T> T mergeEqual(T a, T b) {
        if (b == null) {
            return a;
        }

        if (a != null) {
            Assert.assertTrue(a.equals(b));
        }

        return b;
    }

    /**
     * Same as {@link #mergeEqual(Object, Object)} but does not crash when the two values are unequal.
     * Instead, both values are merged using the supplied function.
     * The supplied function is called only if the two given values are unequal (according to {@link Object#equals(Object)}).
     *
     * <p>The value {@code null} is treated as the neutral element in the sense that no exception is
     * thrown if an element is {@code null}. In this case return value is defined by {@code
     * mergeIfEqualElse(a, null, f) == a} and {@code mergeIfEqualElse(b, null, f) == b}.
     *
     * @param a the first element to merge
     * @param b the second element to merge
     * @param ifUnequal merge operator called when the two values are unequal
     * @param <T> the type of the objects to be merged
     * @return {@code a} if both given values are equal, otherwise the result of {@code ifUnequal.apply(a, b)}
     */
    static <T> T mergeIfEqualElse(T a, T b, BiFunction<T, T, T> ifUnequal) {
        if (b == null) {
            return a;
        }

        if (a == null) {
            return b;
        }

        if (a.equals(b)) {
            return a;
        } else {
            return ifUnequal.apply(a, b);
        }
    }

    /**
     * Prints all key-value pairs to a single string.
     * Falls back to {@link #show(String, Object)} on each entry.
     * @param properties The key-value store to print.
     * @return A string showing all key-value pairs.
     */
    public static String show(final Map<String, ?> properties) {
        StringBuilder result = new StringBuilder();
        for (final Map.Entry<String, ?> property : properties.entrySet()) {
            result.append(show(property.getKey(), property.getValue()));
        }
        return result.toString();
    }

    /**
     * Prints the given key, value pair to text.
     * @param name Name of the metadata entry.
     * @param value Value of the metadata entry.
     * @return A String <code>name: value\n</code>.
     * @param <T> The type of the metadata value.
     */
    static <T> String show(final String name, T value) {
        return name + ": " + value.toString() + "\n";
    }

    /**
     * Export this metadata to the given file.
     * @param file File to write.
     * @return The exported file's content.
     * @see IO#write
     */
    default String exportTo(final Path file) {
        try {
            final String result = show(snapshot());
            IO.write(file, result);
            return result;
        } catch (IOException e) {
            Logger.error(e);
            System.exit(1);
            return "";
        }
    }
}
