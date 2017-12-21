package org.christiangalsterer.stash.filehooks.plugin.hook;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Implements an in-memory cache for values that are expensive to get (like external process calls).
 *
 * The cache is backed with a map of consumer defined types. CachingResolver will resolve values based on
 * requested keys and get values either from cache or by invoking resolve function (and caching results).
 * Actual way to get values is to be provided by consumer of the class upon resolving. Only missing entries
 * (i.e. those not already cached) will be resolved using provided function.
 *
 * @param <K> type of keys to construct cache by
 * @param <V> type of value elements to be stored in cache
 */
public class CachingResolver<K, V> {
    private Map<K, V> cache = new HashMap<>();

    /**
     * Resolves requested key to a previously cached value.
     *
     * @param key requested key
     * @return value if already cached or null if there is no cached value for the key
     */
    public V resolve(K key) {
        return cache.get(key);
    }

    /**
     * Resolves requested key using provided resolveFunction to get values not present in the cache.
     *
     * Value resolved by the resolveFunction will be stored in cache for future use.
     *
     * @param key requested key
     * @param resolveFunction function to resolve key to value when the key is not in the cache
     * @return resolved value
     */
    public V resolve(K key, Function<K, V> resolveFunction) {
        return cache.computeIfAbsent(key, resolveFunction);
    }

    /**
     * Resolves requested keys using provided resolveFunction to get values not present in the cache.
     *
     * resolveFunction will be called once for every key not yet present in cache. Resolved values
     * will be stored in cache for future use.
     *
     * @param keys an iterable of keys to resolve
     * @param resolveFunction function to resolve single key to a value when the key is not in cache
     * @return a map representing requested keys and their corresponding values
     */
    public Map<K, V> resolve(Iterable<K> keys, Function<K, V> resolveFunction) {
        Map<K, V> result = new HashMap<>();
        for (K key : keys) {
            result.put(key, resolve(key, resolveFunction));
        }
        return result;
    }

    /**
     * Resolves requested keys using provided resolveFunction to get values not present in cache.
     *
     * resolveFunction will be called once for keys that are missing from the cache. This allows to get
     * all missing values in one call, as this may be more efficient than getting missing values one by one.
     * Resolved values will be stored in cache for future use.
     *
     * @param keys an iterable of keys to resolve
     * @param resolveFunction function to resolve all keys missing from cache to corresponding values
     * @return a map representing requested keys and their corresponding values
     */
    public Map<K, V> batchResolve(Iterable<K> keys,
                                  Function<Iterable<K>, Map<K, V>> resolveFunction) {
        Set<K> keysToResolve = StreamSupport.stream(keys.spliterator(), false)
                .filter(key -> !cache.containsKey(key))
                .collect(Collectors.toSet());

        if (!keysToResolve.isEmpty()) {
            cache.putAll(resolveFunction.apply(keysToResolve));
        }

        return StreamSupport.stream(keys.spliterator(), false)
                .collect(Collectors.toMap(key -> key, key -> cache.get(key)));
    }
}
