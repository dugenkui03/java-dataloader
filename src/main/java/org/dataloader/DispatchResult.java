package org.dataloader;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * When a DataLoader is dispatched this object holds the promised results
 * and also the count of key asked for via methods like {@link DataLoader#load(Object)} or {@link DataLoader#loadMany(List)}
 *
 * fixme 对load结果的一个封装，包括结果个数。
 *
 * @param <T> for two
 */
@PublicApi
public class DispatchResult<T> {
    //调度结果list
    private final CompletableFuture<List<T>> futureList;
    // 结果总数
    private final int keysCount;

    public DispatchResult(CompletableFuture<List<T>> futureList, int keysCount) {
        this.futureList = futureList;
        this.keysCount = keysCount;
    }

    public CompletableFuture<List<T>> getPromisedResults() {
        return futureList;
    }

    public int getKeysCount() {
        return keysCount;
    }
}
