package org.dataloader.impl;

import org.dataloader.Internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;

/**
 * Some really basic helpers when working with CompletableFutures
 *
 * CompletableFutures 协助类
 */
@Internal
public class CompletableFutureKit {

    /**
     * CompletableFuture 以 e 完成
     */
    public static <V> CompletableFuture<V> failedFuture(Exception e) {
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

    public static <V> Throwable cause(CompletableFuture<V> completableFuture) {

        // 如果不是以异常方式结束、则返回null
        if (!completableFuture.isCompletedExceptionally()) {
            return null;
        }

        try {
            completableFuture.get();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                return cause;
            }
            return e;
        }
    }

    // 是否结束执行、并且没有异常。
    public static <V> boolean succeeded(CompletableFuture<V> future) {
        return future.isDone() && !future.isCompletedExceptionally();
    }

    // 是否结束执行、但是遇到了异常。
    public static <V> boolean failed(CompletableFuture<V> future) {
        return future.isDone() && future.isCompletedExceptionally();
    }


    /**
     * @param cfs 任务列表
     * @param <T> 结果类型
     *
     * @return 结果列表。
     */
    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> cfs) {
        // list 2 array
        CompletableFuture[] completableFutures = cfs.toArray(new CompletableFuture[0]);

        /**
         * allOf: 当所有的CompletableFuture都执行完后执行计算
         *
         * fixme 其中一个出现问题，整个结果都会有问题。
         */
        return CompletableFuture.allOf(cfs.toArray(completableFutures))
                /**
                 *  Function: R apply(T t): 搜集计算完成的 参数 future 的结果
                 */
                .thenApply(v -> cfs.stream().map(CompletableFuture::join).collect(toList()));
    }
}
