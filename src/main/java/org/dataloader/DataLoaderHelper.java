package org.dataloader;

import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.dataloader.impl.Assertions.assertState;
import static org.dataloader.impl.Assertions.nonNull;

/**
 * 有足浴拆解DataLoader类的功能、包含派遣请求的逻辑。
 * This helps break up the large DataLoader class functionality and it contains the logic to dispatch the
 * promises on behalf(利益) of its peer dataloader
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
@Internal
class DataLoaderHelper<K, V> {

    /**
     * 内部类：任务元素
     */
    static class LoaderQueueEntry<K, V> {

        //k-v
        final K key;
        final V value;

        // 请求上下文
        final Object callContext;

        public LoaderQueueEntry(K key, V value, Object callContext) {
            this.key = key;
            this.value = value;
            this.callContext = callContext;
        }

        K getKey() {
            return key;
        }

        V getValue() {
            return value;
        }

        Object getCallContext() {
            return callContext;
        }
    }

    /**
     * 1. 加锁操作；
     * 2.
     */
    private final DataLoader<K, V> dataLoader;

    //批量加载函数
    private final Object batchLoadFunction;

    //dataloader配置类：是否允许批加载、缓存、是否缓存异常情况下的值、缓存key、缓存Map、最大批处理size
    private final DataLoaderOptions loaderOptions;

    //数据缓存
    private final CacheMap<Object, CompletableFuture<V>> futureCache;

    //任务加载队列
    private final List<LoaderQueueEntry<K, CompletableFuture<V>>> loaderQueue;

    //收集Dataloader操作的统计数据
    private final StatisticsCollector stats;

    DataLoaderHelper(DataLoader<K, V> dataLoader,
                     Object batchLoadFunction,
                     DataLoaderOptions loaderOptions,
                     CacheMap<Object, CompletableFuture<V>> futureCache,
                     StatisticsCollector stats) {
        this.dataLoader = dataLoader;
        this.batchLoadFunction = batchLoadFunction;
        this.loaderOptions = loaderOptions;
        this.futureCache = futureCache;
        this.loaderQueue = new ArrayList<>();
        this.stats = stats;
    }

    //从缓存获取数据，如果不允许缓存或者不包含则直接返回空
    Optional<CompletableFuture<V>> getIfPresent(K key) {
        synchronized (dataLoader) {
            //如果是允许使用缓存的
            boolean cachingEnabled = loaderOptions.cachingEnabled();
            if (cachingEnabled) {
                //非null检测、返回原值：获取缓存key
                Object cacheKey = getCacheKey(nonNull(key));
                if (futureCache.containsKey(cacheKey)) {
                    //获取缓存计数器
                    stats.incrementCacheHitCount();
                    return Optional.of(futureCache.get(cacheKey));
                }
            }
        }
        return Optional.empty();
    }

    //等结果执行结束的时候在返回，futureCache的value是异步任务
    Optional<CompletableFuture<V>> getIfCompleted(K key) {
        synchronized (dataLoader) {
            Optional<CompletableFuture<V>> cachedPromise = getIfPresent(key);
            if (cachedPromise.isPresent()) {
                CompletableFuture<V> promise = cachedPromise.get();
                if (promise.isDone()) {
                    return cachedPromise;
                }
            }
        }
        return Optional.empty();
    }


    /**
     * 使用指定的上下文，加载请求key的数据。
     *
     * @param key 请求key
     * @param loadContext 数据加载上下文
     *
     * @return 数据加载异步结果
     */
    CompletableFuture<V> load(K key, Object loadContext) {

        /**
         * fixme
         *      使用 dataLoader 加锁，说明这个操作有对dataLoader装的的修改；
         */
        synchronized (dataLoader) {
            // 是否允许批量加载
            boolean batchingEnabled = loaderOptions.batchingEnabled();
            // 是否允许缓存
            boolean cachingEnabled = loaderOptions.cachingEnabled();

            //允许缓存、则获取缓存key，否则key设置为null
            Object cacheKey = cachingEnabled ? getCacheKey(nonNull(key)) : null;

            //load次数 +1
            stats.incrementLoadCount();

            //如果允许缓存
            if (cachingEnabled) {
                // 如果缓存了数据，则返回
                if (futureCache.containsKey(cacheKey)) {
                    // 命中缓存统计数 +1
                    stats.incrementCacheHitCount();
                    // fixme 返回缓存数据
                    return futureCache.get(cacheKey);
                }
            }


            //将请求入队
            CompletableFuture<V> future = new CompletableFuture<>();
            if (batchingEnabled) {
                // fixme 积攒起来，使用生产者-消费者模式
                loaderQueue.add(new LoaderQueueEntry<>(key, future, loadContext));
            }
            //如果不允许批处理，则立即请求并返回异步结果
            else {
                stats.incrementBatchLoadCountBy(1);
                // immediate execution of batch function
                future = invokeLoaderImmediately(key, loadContext);
            }
            //是否允许缓存
            if (cachingEnabled) {
                futureCache.set(cacheKey, future);
            }
            return future;
        }
    }

    /**
     * 获取请求key、对应的缓存key
     */
    Object getCacheKey(K key) {
        if (loaderOptions.cacheKeyFunction().isPresent()) {
            // 对输入的key进行了一次包装、然后转换为缓存key.
            CacheKey cacheKey = loaderOptions.cacheKeyFunction().get();
            return cacheKey.getKey(key);
        } else {
            return key;
        }
    }

    // 入口方法
    DispatchResult<V> dispatch() {
        //是否允许批量加载
        boolean batchingEnabled = loaderOptions.batchingEnabled();

        /**
         * we copy the pre-loaded set of futures ready for dispatch
         *
         * dataLoader的key、value和调用上下文
         */
        List<K> keys = new ArrayList<>();
        List<Object> callContexts = new ArrayList<>();
        List<CompletableFuture<V>> queuedFutures = new ArrayList<>();
        synchronized (dataLoader) {
            for (LoaderQueueEntry<K, CompletableFuture<V>> entry : loaderQueue) {
                //加载key
                keys.add(entry.getKey());
                //对应的异步value结果
                queuedFutures.add(entry.getValue());
                //上下文
                callContexts.add(entry.getCallContext());
            }
            //清空任务队列
            loaderQueue.clear();
        }

        //如果不允许批量加载、或者当前key是空的，则直接返回空值
        if (!batchingEnabled || keys.isEmpty()) {
            return new DispatchResult<>(CompletableFuture.completedFuture(emptyList()), 0);
        }
        final int totalEntriesHandled = keys.size();

        // order of keys -> values matter in data loader hence the use of linked hash map
        // See https://github.com/facebook/dataloader/blob/master/README.md for more details
        //
        // fixme key的顺序关系重大、所以使用 LinkedHashMap

        //
        // 最大的批处理容量
        // when the promised list of values completes, we transfer the values into
        // the previously cached future objects that the client already has been given
        // via calls to load("foo") and loadMany(["foo","bar"])
        int maxBatchSize = loaderOptions.maxBatchSize();
        CompletableFuture<List<V>> futureList;

        //如果设置了 最大批处理大小，而且当前的keys的数量大于 批.size()，则进行分批处理
        if (maxBatchSize > 0 && maxBatchSize < keys.size()) {
            futureList = sliceIntoBatchesOfBatches(keys, queuedFutures, callContexts, maxBatchSize);
        }
        // 如果没有设置分批处理、或者请求的keys小于 批.size()，则直接调用
        else {
            futureList = dispatchQueueBatch(keys, callContexts, queuedFutures);
        }
        return new DispatchResult<>(futureList, totalEntriesHandled);
    }

    /**
     * slice Into Batches Of Batches 切成批处理的片
     * @param keys 要被处理的任务key
     * @param queuedFutures 入队任务
     * @param callContexts 调用上下文
     * @param maxBatchSize 最大批处理大小
     *
     * @return 结果
     */
    private CompletableFuture<List<V>> sliceIntoBatchesOfBatches(List<K> keys,
                                                                 List<CompletableFuture<V>> queuedFutures,
                                                                 List<Object> callContexts,
                                                                 int maxBatchSize) {
        // the number of keys is > than what the batch loader function can accept
        // so make multiple calls to the loader
        List<CompletableFuture<List<V>>> allBatches = new ArrayList<>();
        int len = keys.size();
        int batchCount = (int) Math.ceil(len / (double) maxBatchSize);
        for (int i = 0; i < batchCount; i++) {

            int fromIndex = i * maxBatchSize;
            int toIndex = Math.min((i + 1) * maxBatchSize, len);

            List<K> subKeys = keys.subList(fromIndex, toIndex);
            List<CompletableFuture<V>> subFutures = queuedFutures.subList(fromIndex, toIndex);
            List<Object> subCallContexts = callContexts.subList(fromIndex, toIndex);

            allBatches.add(dispatchQueueBatch(subKeys, subCallContexts, subFutures));
        }
        //
        // now reassemble all the futures into one that is the complete set of results
        return CompletableFuture.allOf(allBatches.toArray(new CompletableFuture[0]))
                .thenApply(v -> allBatches.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
    }

    /**
     * fixme 最重要的批处理方法
     *
     *
     * @param keys 调用入参
     * @param callContexts  调用上下文
     * @param queuedFutures
     * @return
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<List<V>> dispatchQueueBatch(List<K> keys,
                                                          List<Object> callContexts,
                                                          List<CompletableFuture<V>> queuedFutures) {
        // 指标统计
        stats.incrementBatchLoadCountBy(keys.size());

        //
        CompletionStage<List<V>> batchLoad = invokeLoader(keys, callContexts);

        return batchLoad
                .toCompletableFuture()
                .thenApply(values -> {
                    assertResultSize(keys, values);

                    List<K> clearCacheKeys = new ArrayList<>();
                    for (int idx = 0; idx < queuedFutures.size(); idx++) {
                        Object value = values.get(idx);
                        CompletableFuture<V> future = queuedFutures.get(idx);
                        if (value instanceof Throwable) {
                            stats.incrementLoadErrorCount();
                            future.completeExceptionally((Throwable) value);
                            clearCacheKeys.add(keys.get(idx));
                        } else if (value instanceof Try) {
                            // we allow the batch loader to return a Try so we can better represent a computation
                            // that might have worked or not.
                            Try<V> tryValue = (Try<V>) value;
                            if (tryValue.isSuccess()) {
                                future.complete(tryValue.get());
                            } else {
                                stats.incrementLoadErrorCount();
                                future.completeExceptionally(tryValue.getThrowable());
                                clearCacheKeys.add(keys.get(idx));
                            }
                        } else {
                            V val = (V) value;
                            future.complete(val);
                        }
                    }
                    possiblyClearCacheEntriesOnExceptions(clearCacheKeys);
                    return values;
                }).exceptionally(ex -> {
                    stats.incrementBatchLoadExceptionCount();
                    for (int idx = 0; idx < queuedFutures.size(); idx++) {
                        K key = keys.get(idx);
                        CompletableFuture<V> future = queuedFutures.get(idx);
                        future.completeExceptionally(ex);
                        // clear any cached view of this key because they all failed
                        dataLoader.clear(key);
                    }
                    return emptyList();
                });
    }


    private void assertResultSize(List<K> keys, List<V> values) {
        assertState(keys.size() == values.size(), "The size of the promised values MUST be the same size as the key list");
    }

    private void possiblyClearCacheEntriesOnExceptions(List<K> keys) {
        if (keys.isEmpty()) {
            return;
        }
        // by default we don't clear the cached view of this entry to avoid
        // frequently loading the same error.  This works for short lived request caches
        // but might work against long lived caches.  Hence we have an option that allows
        // it to be cleared
        if (!loaderOptions.cachingExceptionsEnabled()) {
            keys.forEach(dataLoader::clear);
        }
    }


    CompletableFuture<V> invokeLoaderImmediately(K key, Object keyContext) {
        // 将请求key包装为单个元素的list
        List<K> keys = singletonList(key);

        List<Object> keysContextList = singletonList(keyContext);

        CompletionStage<V> singleLoadCall;
        try {
            //获取批量加载上下文
            Object context = loaderOptions.getBatchLoaderContextProvider().getContext();

            // 批量加载参数
            BatchLoaderEnvironment environment = BatchLoaderEnvironment
                                                    .newBatchLoaderEnvironment()
                                                    // 批量加载上下文
                                                    .context(context)
                                                    // key上下文及其对应的上下文
                                                    .keyContexts(keys, keysContextList)
                                                    .build();
            /**
             * Map 和 List 两种Loader
             */
            if (isMapLoader()) {
                singleLoadCall = invokeMapBatchLoader(keys, environment).thenApply(list -> list.get(0));
            } else {
                singleLoadCall = invokeListBatchLoader(keys, environment).thenApply(list -> list.get(0));
            }
            return singleLoadCall.toCompletableFuture();
        } catch (Exception e) {
            return CompletableFutureKit.failedFuture(e);
        }
    }

    // fixme 进行调用
    CompletionStage<List<V>> invokeLoader(List<K> keys, List<Object> keyContexts) {
        CompletionStage<List<V>> batchLoad;
        try {
            // 获取批量调用上下文
            Object context = loaderOptions.getBatchLoaderContextProvider().getContext();

            // 构造调用参数
            BatchLoaderEnvironment environment = BatchLoaderEnvironment.newBatchLoaderEnvironment()
                    // 批量调用上下文
                    .context(context)
                    // 请求key、和请求上下文
                    .keyContexts(keys, keyContexts)
                    .build();

            if (isMapLoader()) {
                batchLoad = invokeMapBatchLoader(keys, environment);
            } else {
                batchLoad = invokeListBatchLoader(keys, environment);
            }
        } catch (Exception e) {
            batchLoad = CompletableFutureKit.failedFuture(e);
        }
        return batchLoad;
    }

    /**
     * fixme 这里调用到你自定一的的BatchLoader接口了
     *
     * @param keys 请求参数
     * @param environment 请求环境
     *
     * @return 请求结果
     */
    @SuppressWarnings("unchecked")
    private CompletionStage<List<V>> invokeListBatchLoader(List<K> keys, BatchLoaderEnvironment environment) {
        CompletionStage<List<V>> loadResult;
        if (batchLoadFunction instanceof BatchLoaderWithContext) {
            loadResult = ((BatchLoaderWithContext<K, V>) batchLoadFunction).load(keys, environment);
        } else {
            loadResult = ((BatchLoader<K, V>) batchLoadFunction).load(keys);
        }
        // 返回数据不可为空
        return nonNull(loadResult, "Your batch loader function MUST return a non null CompletionStage promise");
    }


    /*
     * Turns a map of results that MAY be smaller than the key list back into a list by mapping null
     * to missing elements.
     */
    @SuppressWarnings("unchecked")
    private CompletionStage<List<V>> invokeMapBatchLoader(List<K> keys, BatchLoaderEnvironment environment) {
        CompletionStage<Map<K, V>> loadResult;
        Set<K> setOfKeys = new LinkedHashSet<>(keys);
        if (batchLoadFunction instanceof MappedBatchLoaderWithContext) {
            loadResult = ((MappedBatchLoaderWithContext<K, V>) batchLoadFunction).load(setOfKeys, environment);
        } else {
            loadResult = ((MappedBatchLoader<K, V>) batchLoadFunction).load(setOfKeys);
        }
        CompletionStage<Map<K, V>> mapBatchLoad = nonNull(loadResult, "Your batch loader function MUST return a non null CompletionStage promise");
        return mapBatchLoad.thenApply(map -> {
            List<V> values = new ArrayList<>();
            for (K key : keys) {
                V value = map.get(key);
                values.add(value);
            }
            return values;
        });
    }

    /**
     * @return 批量加载函数是否是 map批量加载{@link MappedBatchLoader} 或者 带有上下文的map批量加载{@link MappedBatchLoaderWithContext}
     */
    private boolean isMapLoader() {
        return batchLoadFunction instanceof MappedBatchLoader || batchLoadFunction instanceof MappedBatchLoaderWithContext;
    }

    int dispatchDepth() {
        synchronized (dataLoader) {
            return loaderQueue.size();
        }
    }
}
