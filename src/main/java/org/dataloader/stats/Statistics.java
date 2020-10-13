package org.dataloader.stats;

import org.dataloader.PublicApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This holds statistics on how a {@link org.dataloader.DataLoader} has performed
 *
 * 对于 DataLoader 的统计数据。
 */
@PublicApi
public class Statistics {

    /**
     * load次数、
     * load错误次数、
     * 批量调用次数、
     * 批量load次数、
     * 批量load异常次数、
     * 缓存命中次数
     */
    private final long loadCount;
    private final long loadErrorCount;
    private final long batchInvokeCount;
    private final long batchLoadCount;
    private final long batchLoadExceptionCount;
    private final long cacheHitCount;

    /**
     * Zero statistics
     */
    public Statistics() {
        this(0, 0, 0, 0, 0, 0);
    }

    public Statistics(long loadCount, long loadErrorCount, long batchInvokeCount, long batchLoadCount, long batchLoadExceptionCount, long cacheHitCount) {
        this.loadCount = loadCount;
        this.batchInvokeCount = batchInvokeCount;
        this.batchLoadCount = batchLoadCount;
        this.cacheHitCount = cacheHitCount;
        this.batchLoadExceptionCount = batchLoadExceptionCount;
        this.loadErrorCount = loadErrorCount;
    }

    /**
     * A helper to divide two numbers and handle zero
     *
     * @param numerator   the top bit 分子
     * @param denominator the bottom bit 分母
     *
     * @return numerator / denominator returning zero when denominator is zero
     */
    public double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0f : ((double) numerator) / ((double) denominator);
    }

    /**
     * loader的数量
     *
     * @return the number of objects {@link org.dataloader.DataLoader#load(Object)} has been asked to load
     */
    public long getLoadCount() {
        return loadCount;
    }

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function return an specific object that was in error
     */
    public long getLoadErrorCount() {
        return loadErrorCount;
    }

    /**
     * @return loadErrorCount / loadCount
     */
    public double getLoadErrorRatio() {
        return ratio(loadErrorCount, loadCount);
    }

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function has been called
     */
    public long getBatchInvokeCount() {
        return batchInvokeCount;
    }

    /**
     * @return the number of objects that the {@link org.dataloader.DataLoader} batch loader function has been asked to load
     */
    public long getBatchLoadCount() {
        return batchLoadCount;
    }

    /**
     * @return batchLoadCount / loadCount
     */
    public double getBatchLoadRatio() {
        return ratio(batchLoadCount, loadCount);
    }

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function throw an exception when trying to get any values
     */
    public long getBatchLoadExceptionCount() {
        return batchLoadExceptionCount;
    }

    /**
     * @return batchLoadExceptionCount / loadCount
     */
    public double getBatchLoadExceptionRatio() {
        return ratio(batchLoadExceptionCount, loadCount);
    }

    /**
     * @return the number of times  {@link org.dataloader.DataLoader#load(Object)} resulted in a cache hit
     */
    public long getCacheHitCount() {
        return cacheHitCount;
    }

    /**
     * @return then number of times we missed the cache during {@link org.dataloader.DataLoader#load(Object)}
     */
    public long getCacheMissCount() {
        return loadCount - cacheHitCount;
    }

    /**
     * @return cacheHits / loadCount
     */
    public double getCacheHitRatio() {
        return ratio(cacheHitCount, loadCount);
    }


    /**
     * This will combine this set of statistics with another set of statistics so that they become the combined count of each。
     *
     * fixme 将当前的数据和目标对象数据做累加，返回新对象。
     *
     * @param other the other statistics to combine
     *
     * @return a new statistics object of the combined counts
     */
    public Statistics combine(Statistics other) {
        return new Statistics(
                this.loadCount + other.getLoadCount(),
                this.loadErrorCount + other.getLoadErrorCount(),
                this.batchInvokeCount + other.getBatchInvokeCount(),
                this.batchLoadCount + other.getBatchLoadCount(),
                this.batchLoadExceptionCount + other.getBatchLoadExceptionCount(),
                this.cacheHitCount + other.getCacheHitCount()
        );
    }

    /**
     * @return a map representation of the statistics, perhaps to send over JSON or some such
     */
    public Map<String, Number> toMap() {
        Map<String, Number> stats = new LinkedHashMap<>();
        stats.put("loadCount", getLoadCount());
        stats.put("loadErrorCount", getLoadErrorCount());
        stats.put("loadErrorRatio", getLoadErrorRatio());

        stats.put("batchInvokeCount", getBatchInvokeCount());
        stats.put("batchLoadCount", getBatchLoadCount());
        stats.put("batchLoadRatio", getBatchLoadRatio());
        stats.put("batchLoadExceptionCount", getBatchLoadExceptionCount());
        stats.put("batchLoadExceptionRatio", getBatchLoadExceptionRatio());

        stats.put("cacheHitCount", getCacheHitCount());
        stats.put("cacheHitRatio", getCacheHitRatio());
        return stats;
    }

    @Override
    public String toString() {
        return "Statistics{" +
                "loadCount=" + loadCount +
                ", loadErrorCount=" + loadErrorCount +
                ", batchLoadCount=" + batchLoadCount +
                ", batchLoadExceptionCount=" + batchLoadExceptionCount +
                ", cacheHitCount=" + cacheHitCount +
                '}';
    }
}
