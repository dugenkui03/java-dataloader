package org.dataloader;

/**
 * A BatchLoaderContextProvider is used by the {@link org.dataloader.DataLoader} code to
 * provide overall calling context to the {@link org.dataloader.BatchLoader} call.  A common use
 * case is for propagating user security credentials or database connection parameters for example.
 */
@PublicSpi
public interface BatchLoaderContextProvider {
    /**
     * 返回批加载中的上下文，fixme 总的上下文
     * @return a context object that may be needed in batch load calls
     */
    Object getContext();
}