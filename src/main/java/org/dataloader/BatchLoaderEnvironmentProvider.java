package org.dataloader;

/**
 * A BatchLoaderEnvironmentProvider is used by the {@link DataLoader} code
 * to provide {@link BatchLoaderEnvironment} calling context to the {@link BatchLoader} call.
 *
 * A common use case is for propagating user security credentials or database connection parameters.
 */
@PublicSpi
public interface BatchLoaderEnvironmentProvider {
    /**
     * @return a {@link BatchLoaderEnvironment} that may be needed in batch calls
     */
    BatchLoaderEnvironment get();
}