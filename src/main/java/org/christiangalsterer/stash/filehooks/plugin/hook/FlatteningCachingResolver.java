package org.christiangalsterer.stash.filehooks.plugin.hook;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A {@link CachingResolver} designed for values that are iterables.
 *
 * @param <K> type of keys to construct cache by
 * @param <V> type of values that will be held as {@link Iterable}s
 */
public class FlatteningCachingResolver<K, V> extends CachingResolver<K, Iterable<V>> {
    /**
     * Resolved selected keys to values, then flattens the results to create single set of values.
     *
     * Value resolution is done with {@link CachingResolver#resolve(Iterable, Function)}.
     *
     * @param keys an iterable of keys to resolve
     * @param resolveFunction function to resolve single key to a value when the key is not in cache
     * @return a set of values resolved for all requested keys
     */
    public Set<V> flatResolve(Iterable<K> keys, Function<K, Iterable<V>> resolveFunction) {
        return flatten(resolve(keys, resolveFunction));
    }

    /**
     * Resolved selected keys to values, then flattens the results to create single set of values.
     *
     * Value resolution is done with {@link CachingResolver#batchResolve(Iterable, Function)}.
     *
     * @param keys an iterable of keys to resolve
     * @param resolveFunction function to resolve single key to a value when the key is not in cache
     * @return a set of values resolved for all requested keys
     */
    public Set<V> flatBatchResolve(Iterable<K> keys,
                                   Function<Iterable<K>, Map<K, Iterable<V>>> resolveFunction) {
        return flatten(batchResolve(keys, resolveFunction));
    }

    private Set<V> flatten(Map<?, Iterable<V>> source) {
        return source.values().stream()
                .flatMap(x -> StreamSupport.stream(x.spliterator(), false))
                .collect(Collectors.toSet());
    }
}
