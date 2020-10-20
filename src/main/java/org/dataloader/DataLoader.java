/*
 * Copyright (c) 2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.dataloader;

import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * Data loader is a utility class that allows batch loading of data that is identified by a set of unique keys.
 * For each key that is loaded a separate {@link CompletableFuture} is returned, that completes as the batch function completes.
 *
 * fixme
 *      使用有唯一标志的key、批量加载下游数据。
 *
 * <p>
 * With batching enabled the execution will start after calling {@link DataLoader#dispatch()},
 * causing the queue of loaded keys to be sent to the batch function, clears the queue, and returns a promise to the values.
 *
 * fixme
 *      入口方法 {@link DataLoader#dispatch()}
 *
 * <p>
 * As {@link BatchLoader} batch functions are executed the resulting futures are cached
 * using a cache implementation of choice, so they will only execute once.
 *
 * Individual cache keys can be cleared, so they will be re-fetched when referred to again.
 *
 *
 * <p>
 * It is also possible to clear the cache entirely, and prime it with values before they are used.
 *
 * <p>
 * Both caching and batching can be disabled. Configuration of the data loader is done by providing a
 * {@link DataLoaderOptions} instance on creation.
 *
 * fixme 是否缓存和批量加载都可以通过 DataLoaderOptions 配置。
 *
 *
 * <p>
 * A call to the batch loader might result in individual exception failures for item with the returned list.
 * if you want to capture these specific item failures then use {@link Try} as a return value
 * and create the data loader with {@link #newDataLoaderWithTry(BatchLoader)} form.
 *
 * The Try values will be interpreted as either success values or cause the {@link #load(Object)} promise to complete exceptionally.
 *
 * fixme 如果想要同时获取 正常结果 和 异常信息 作为返回结果元素，则可通过 newDataLoaderWithTry、将Try指定为结果类型。
 *
 * @param <K> type parameter indicating the type of the data load keys
 * @param <V> type parameter indicating the type of the data that is returned
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@PublicApi
public class DataLoader<K, V> {

    // 协助类
    private final DataLoaderHelper<K, V> helper;

    // 缓存类
    private final CacheMap<Object, CompletableFuture<V>> cacheMap;

    // 收集Dataloader操作的统计数据，有多个实现类
    private final StatisticsCollector stats;

    /**
     * fixme ==================================================== 创建实例的静态方法  ======================================================
     */

    /**
     * 使用指定的batch loader创建dataloaser，默认使用批量的、缓存的、不限制大小
     *
     * Creates new DataLoader with the specified batch loader function and default options (batching, caching and unlimited(没有限制的) batch size).
     *
     * @param batchLoadFunction the batch load function to use
     *                          要使用的批量加载函数: CompletionStage<List<V>> load(List<K> keys)
     *
     * @param <K>               the key type key类型
     * @param <V>               the value type value类型
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction) {
        // dataLoaderOptions 默认配置为null：是否允许批加载、是否使用缓存、是否缓存异常情况下的值、缓存配置等
        return newDataLoader(batchLoadFunction, null);
    }

    /**
     * 使用指定的batch loader和option创建dataloaser
     *
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If its important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction) {
        return newDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    @SuppressWarnings("unchecked")
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>((BatchLoader<K, V>) batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction) {
        return newDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If its important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction) {
        return newDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction) {
        return newMappedDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If its important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     * <p>
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction) {
        return newMappedDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified mapped batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction) {
        return newMappedDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If its important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction) {
        return newMappedDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * fixme ==================================================== end of 创建实例的静态方法  ======================================================
     */


    /**
     * fixme ==================================================== 构造函数  ======================================================
     */
    /**
     * Creates a new data loader with the provided batch load function, and default options.
     *
     * @param batchLoadFunction the batch load function to use
     */
    public DataLoader(BatchLoader<K, V> batchLoadFunction) {
        this(batchLoadFunction, null);
    }

    /**
     * Creates a new data loader with the provided batch load function and options.
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the batch load options
     */
    public DataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        this((Object) batchLoadFunction, options);
    }

    private DataLoader(Object batchLoadFunction, DataLoaderOptions options) {
        // 默认配置
        DataLoaderOptions loaderOptions = options == null ? new DataLoaderOptions() : options;

        // fixme cache数据在 DataLoaderOptions 类中指定
        this.cacheMap = determineCacheMap(loaderOptions);

        // order of keys matter in data loader
        this.stats = nonNull(loaderOptions.getStatisticsCollector());

        // fixme helper 使用当前对象作为 help 的属性
        this.helper = new DataLoaderHelper<>(this, batchLoadFunction, loaderOptions, this.cacheMap, this.stats);
    }

    /**
     * fixme ==================================================== end of 构造函数  ======================================================
     */

    /**
     * 使用配置信息构造 CacheMap
     */
    @SuppressWarnings("unchecked")
    private CacheMap<Object, CompletableFuture<V>> determineCacheMap(DataLoaderOptions loaderOptions) {
        return loaderOptions.cacheMap().isPresent() ?
                // 获取配置中的 CacheMap
                (CacheMap<Object, CompletableFuture<V>>) loaderOptions.cacheMap().get() :
                // 使用默认的 CacheMap：使用HashMap保存数据
                CacheMap.simpleMap();
    }

    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param key the key to load
     * @return the future of the value
     */
    public CompletableFuture<V> load(K key) {
        return load(key, null);
    }

    /**
     * 返回之前调用 call() 缓存的值，如果之前没有调用、则返回空
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call or empty if not call has been made for that key.
     * <p>
     * If you do get a present CompletableFuture it does not mean it has been dispatched and completed yet.  It just means
     * its at least pending and in cache.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen.  You must called {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     * @return an Optional to the future of the value
     */
    public Optional<CompletableFuture<V>> getIfPresent(K key) {
        return helper.getIfPresent(key);
    }

    /**
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call that has in fact been completed or empty
     * if no call has been made for that key or the promise has not completed yet.
     * <p>
     * If you do get a present CompletableFuture it means it has been dispatched and completed.  Completed is defined as
     * {@link java.util.concurrent.CompletableFuture#isDone()} returning true.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen.  You must called {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     * @return an Optional to the future of the value
     */
    public Optional<CompletableFuture<V>> getIfCompleted(K key) {
        return helper.getIfCompleted(key);
    }


    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param key        the key to load
     * @param keyContext a context object that is specific to this key
     * @return the future of the value
     */
    public CompletableFuture<V> load(K key, Object keyContext) {
        return helper.load(key, keyContext);
    }

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param keys the list of keys to load
     * @return the composite future of the list of values
     */
    public CompletableFuture<List<V>> loadMany(List<K> keys) {
        return loadMany(keys, Collections.emptyList());
    }

    /**fixme 重要：If you forget this call the future will never be completed。
     *
     * Requests to load the list of data provided by the specified keys asynchronously,
     * and returns a composite future of the resulting values.
     * fixme
     *      使用指定的key集合、异步请求加载，并且返回 结果future。
     *      如果允许批量请求，你必须在下一阶段调用 dispatch 来开始批量执行、如果忘了调用此方法、则任务经永远不会执行。
     *
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext}
     * or {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param keys        the list of keys to load
     *                    要加载的key参数
     *
     * @param keyContexts the list of key calling context objects
     *                    key上下文
     *
     * @return the composite future of the list of values
     *         结果
     */
    public CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts) {
        nonNull(keys);
        nonNull(keyContexts);

        // fixme dataLoader 使用本身加锁
        synchronized (this) {
            List<CompletableFuture<V>> collect = new ArrayList<>();

            // 遍历key
            for (int i = 0; i < keys.size(); i++) {

                //获取当前key
                K key = keys.get(i);

                // 获取当前key的上下文
                Object keyContext = null;
                if (i < keyContexts.size()) {
                    keyContext = keyContexts.get(i);
                }

                // 当前key的结果
                CompletableFuture<V> futureByKey = load(key, keyContext);

                // 将结果添加到结果集中
                collect.add(futureByKey);
            }
            // 返回任务结果列表
            return CompletableFutureKit.allOf(collect);
        }
    }

    /**
     * fixme: 入口方法。
     *
     * 将入队的加载请求派遣到执行函数、获取活到异步结果。
     * Dispatches the queued load requests to the batch execution function and returns a promise of the result.
     *
     * <p>
     * 如果不允许批量加载、或者没有入队的请求，则获取单独的结果
     * If batching is disabled, or there are no queued requests, then a succeeded promise is returned.
     *
     * @return the promise of the queued load requests
     */
    public CompletableFuture<List<V>> dispatch() {
        return helper.dispatch().getPromisedResults();
    }

    /**
     * Dispatches the queued load requests to the batch execution function and returns both the promise of the result
     * and the number of entries that were dispatched.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded promise with no entries dispatched is
     * returned.
     *
     * @return the promise of the queued load requests and the number of keys dispatched.
     */
    public DispatchResult<V> dispatchWithCounts() {
        return helper.dispatch();
    }

    /**
     * Normally {@link #dispatch()} is an asynchronous operation but this version will 'join' on the
     * results if dispatch and wait for them to complete.  If the {@link CompletableFuture} callbacks make more
     * calls to this data loader then the {@link #dispatchDepth()} will be &gt; 0 and this method will loop
     * around and wait for any other extra batch loads to occur.
     *
     * @return the list of all results when the {@link #dispatchDepth()} reached 0
     */
    public List<V> dispatchAndJoin() {
        List<V> joinedResults = dispatch().join();
        List<V> results = new ArrayList<>(joinedResults);
        while (this.dispatchDepth() > 0) {
            joinedResults = dispatch().join();
            results.addAll(joinedResults);
        }
        return results;
    }


    /**
     * @return the depth of the batched key loads that need to be dispatched
     */
    public int dispatchDepth() {
        return helper.dispatchDepth();
    }


    /**
     * Clears the future with the specified key from the cache,
     * if caching is enabled, so it will be re-fetched on the next load request.
     *
     * fixme
     *      清除指定key的缓存数据。
     *
     * @param key the key to remove
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clear(K key) {
        // 获取缓存key
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            cacheMap.delete(cacheKey);
        }
        return this;
    }

    /**
     * Clears the entire cache map of the loader.
     * fixme 清除dataLoader中所有的缓存数据。
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clearAll() {
        synchronized (this) {
            cacheMap.clear();
        }
        return this;
    }

    /**
     * Primes(是准备好) the cache with the given key and value.
     * fixme 使用指定的 k-v 构造缓存
     *
     * @param key   the key
     * @param value the value
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> prime(K key, V value) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            if (!cacheMap.containsKey(cacheKey)) {
                cacheMap.set(cacheKey, CompletableFuture.completedFuture(value));
            }
        }
        return this;
    }

    /**
     * Primes the cache with the given key and error.
     * fixme 将指定的key和错误信息保存在缓存中。
     *
     * @param key   the key
     * @param error the exception to prime instead of a value
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> prime(K key, Exception error) {
        Object cacheKey = getCacheKey(key);
        if (!cacheMap.containsKey(cacheKey)) {
            cacheMap.set(cacheKey, CompletableFutureKit.failedFuture(error));
        }
        return this;
    }

    /**
     * Gets the object that is used in the internal cache map as key,
     * by applying the cache key function to the provided key.
     * fixme 获取请求key对应的缓存key。
     *
     * <p>
     * If no cache key function is present in {@link DataLoaderOptions}, then the returned value equals the input key.
     *
     * @param key the input key
     * @return the cache key after the input is transformed with the cache key function
     */
    public Object getCacheKey(K key) {
        return helper.getCacheKey(key);
    }

    /**
     * Gets the statistics associated with this data loader.
     * These will have been gather via the {@link org.dataloader.stats.StatisticsCollector}
     * passed in via {@link DataLoaderOptions#getStatisticsCollector()}
     *
     * @return statistics for this data loader
     *         fixme 该 dataLoader 相关的统计数据
     */
    public Statistics getStatistics() {
        return stats.getStatistics();
    }

}
