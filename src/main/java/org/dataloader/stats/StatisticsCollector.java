package org.dataloader.stats;

import org.dataloader.PublicSpi;

/**
 * 收集Datalaoder操作的统计数据，有多个实现类
 *
 * This allows statistics to be collected for {@link org.dataloader.DataLoader} operations
 */
@PublicSpi
public interface StatisticsCollector {

    /**
     * 添加dataloader时调用
     *
     * @return 操作后的dataLoader数量
     */
    long incrementLoadCount();

    /**
     * 返回结果为错误的loader的数量
     * Called to increment the number of loads that resulted in an object deemed in error
     *
     * @return 操作后的dataLoader数量
     */
    long incrementLoadErrorCount();

    /**
     * 调用来增加loader的数量
     *
     * @param delta how much to add to the count
     *
     * @return the current value after increment
     */
    long incrementBatchLoadCountBy(long delta);

    /**
     * 调用来增加返回值为exception的loader的数量
     *
     * Called to increment the number of batch loads exceptions
     *
     * @return the current value after increment
     */
    long incrementBatchLoadExceptionCount();

    /**
     * 递增缓存命中的数量：Called to increment the number of cache hits
     *
     * @return the current value after increment
     */
    long incrementCacheHitCount();

    /**
     * 目前为止收集的统计数据
     *
     * @return the statistics that have been gathered up(收集) to this point in time
     */
    Statistics getStatistics();
}
