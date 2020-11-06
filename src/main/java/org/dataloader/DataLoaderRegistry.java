package org.dataloader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.dataloader.stats.Statistics;

/**
 * This allows data loaders to be registered together into a single place so they can be dispatched as one.
 * It also allows you to retrieve data loaders by name from a central place.
 *
 * 允许将多个dataLoader作为一个dataLoader使用；也可以根据dataLoader名称、获取指定的dataLoader。
 */
@PublicApi
public class DataLoaderRegistry {
    // dataLoader注册map
    private final Map<String, DataLoader<?, ?>> dataLoaders = new ConcurrentHashMap<>();

    /**
     * This will register a new dataloader.
     * 注册dataLoader
     *
     * @param key        the key to put the data loader under dataLoader对应的key
     * @param dataLoader the data loader to register 要注册的dataLoader
     * @return this registry
     */
    public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
        dataLoaders.put(key, dataLoader);
        return this;
    }

    /**
     * Computes a data loader if absent or return it if it was
     * already registered at that key.
     * <p>
     * Note: The entire method invocation is performed atomically,
     * so the function is applied at most once per key.
     *
     * @param key             the key of the data loader
     * @param mappingFunction the function to compute a data loader
     *                        如果指定的key没有对应的dataLoader，则根据key创建一个dataLoader
     *
     * @param <K>             the type of keys
     * @param <V>             the type of values
     * @return a data loader
     */
    @SuppressWarnings("unchecked")
    public <K, V> DataLoader<K, V> computeIfAbsent(final String key,
                                                   final Function<String, DataLoader<?, ?>> mappingFunction) {
        return (DataLoader<K, V>) dataLoaders.computeIfAbsent(key, mappingFunction);
    }

    /**
     * This will combine all the current data loaders in this registry
     * and all the data loaders from the specified registry
     * and return a new combined registry
     *
     * fixme 将当前对象的dataLoader和指定的dataLoader合并成一个dataLoader返回。
     *
     * @param registry the registry to combine into this registry
     * @return a new combined registry
     */
    public DataLoaderRegistry combine(DataLoaderRegistry registry) {
        DataLoaderRegistry combined = new DataLoaderRegistry();

        this.dataLoaders.forEach(combined::register);
        registry.dataLoaders.forEach(combined::register);
        return combined;
    }

    /**
     * @return the currently registered data loaders
     *         fixme 返回当前注册器中的所有dataLoader.
     */
    public List<DataLoader<?, ?>> getDataLoaders() {
        return new ArrayList<>(dataLoaders.values());
    }

    /**
     * This will unregister a new data loader
     * fixme 取消对某个data loader的注册
     *
     * @param key the key of the data loader to unregister
     * @return this registry
     */
    public DataLoaderRegistry unregister(String key) {
        dataLoaders.remove(key);
        return this;
    }

    /**
     * Returns the data loader that was registered under the specified key
     * fixme 获取指定key的dataLoader。
     *
     * @param key the key of the data loader
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a data loader or null if its not present
     */
    @SuppressWarnings("unchecked")
    public <K, V> DataLoader<K, V> getDataLoader(String key) {
        return (DataLoader<K, V>) dataLoaders.get(key);
    }

    /**
     * @return the keys of the data loaders in this registry
     *         fixme 该注册器中所有 dataLoader 的key
     */
    public Set<String> getKeys() {
        return new HashSet<>(dataLoaders.keySet());
    }

    /**
     * fixme 重中之重：
     *      调度该注册器中所有的 data loader，但是并不获取结果
     *
     * This will called {@link DataLoader#dispatch()} on each of the registered
     *
     * {@link org.dataloader.DataLoader}s
     */
    public void dispatchAll() {
        getDataLoaders().forEach(DataLoader::dispatch);
    }

    /**
     * Similar to {@link DataLoaderRegistry#dispatchAll()}, this calls {@link DataLoader#dispatch()} on
     * each of the registered {@link DataLoader}s, but returns the number of dispatches.
     *
     * @return total number of entries that were dispatched from registered {@link DataLoader}s.
     *         所有data loader结果总数。
     */
    public int dispatchAllWithCount() {
        int sum = 0;
        for (DataLoader<?,?> dataLoader : getDataLoaders()) {
            sum += dataLoader.dispatchWithCounts().getKeysCount();
        }
        return sum;
    }

    /**
     * @return The sum of all batched key loads
     *         that need to be dispatched from all registered {@link DataLoader}s
     *         fixme 所有注册的dataLoader的 key的 数量。
     */
    public int dispatchDepth() {
        int totalDispatchDepth = 0;
        for (DataLoader dataLoader : getDataLoaders()) {
            totalDispatchDepth += dataLoader.dispatchDepth();
        }
        return totalDispatchDepth;
    }

    /**
     * @return a combined set of statistics for all data loaders
     *         in this registry presented as the sum of all their statistics
     *         将该注册器中所有 dataLoader 的统计数据、合并成一个
     */
    public Statistics getStatistics() {
        Statistics stats = new Statistics();
        for (DataLoader<?, ?> dataLoader : dataLoaders.values()) {
            // 将当前的数据和目标对象数据做累加
            stats = stats.combine(dataLoader.getStatistics());
        }
        return stats;
    }
}
