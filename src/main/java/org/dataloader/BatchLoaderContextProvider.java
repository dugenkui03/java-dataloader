package org.dataloader;

/**
 * This class is used by the {@link DataLoader} code
 * to provide overall calling context to the {@link BatchLoader} call.
 *
 * A common use case is for propagating(传播) user security credentials(凭证)
 * or database connection parameters for example.
 *
 *
 */
@PublicSpi
public interface BatchLoaderContextProvider {
    /**
     * 返回批加载中的上下文，fixme 总的上下文
     * @return a context object that may be needed in batch load calls
     */
    Object getContext();
}