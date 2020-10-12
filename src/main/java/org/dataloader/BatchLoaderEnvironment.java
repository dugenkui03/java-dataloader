package org.dataloader;

import org.dataloader.impl.Assertions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 这个对象传递给batchLoader作为调用上下文。 他包含了调用者的安全证书、或者数据源参数，允许数据层成功调用下游。
 *
 * This object is passed to a batch loader as calling context.  It could contain security credentials
 * of the calling users for example or database parameters that allow the data layer call to succeed.
 */
@PublicApi
public class BatchLoaderEnvironment {
    // 批量加载上下文
    private final Object context;

    // key上下文
    private final Map<Object, Object> keyContexts;

    // key上下文对象列表
    private final List<Object> keyContextsList;

    private BatchLoaderEnvironment(Object context, List<Object> keyContextsList, Map<Object, Object> keyContexts) {
        this.context = context;
        this.keyContexts = keyContexts;
        this.keyContextsList = keyContextsList;
    }

    /**
     * Returns the overall context object provided by {@link org.dataloader.BatchLoaderContextProvider}
     *
     * @param <T> the type you would like the object to be
     *
     * @return a context object or null if there isn't one
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
    }

    /**
     * Each call to {@link org.dataloader.DataLoader#load(Object, Object)} or
     * {@link org.dataloader.DataLoader#loadMany(java.util.List, java.util.List)} can be given
     * a context object when it is invoked.  A map of them is present by this method.
     *
     * @return a map of key context objects
     */
    public Map<Object, Object> getKeyContexts() {
        return keyContexts;
    }

    /**
     * Each call to {@link org.dataloader.DataLoader#load(Object, Object)} or
     * {@link org.dataloader.DataLoader#loadMany(java.util.List, java.util.List)} can be given
     * a context object when it is invoked.  A list of them is present by this method.
     *
     * @return a list of key context objects in the order they where encountered
     */
    public List<Object> getKeyContextsList() {
        return keyContextsList;
    }

    public static Builder newBatchLoaderEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private Object context;
        private Map<Object, Object> keyContexts = Collections.emptyMap();
        private List<Object> keyContextsList = Collections.emptyList();

        private Builder() {

        }

        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        // fixme 重点方法
        public <K> Builder keyContexts(List<K> keys, List<Object> keyContexts) {
            // todo assert not empty.
            Assertions.nonNull(keys);
            Assertions.nonNull(keyContexts);

            Map<Object, Object> map = new HashMap<>();
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                // fixme 必须都是 ArrayList 啊，要不然 get(i) 操作开销有点大
                K key = keys.get(i);
                Object keyContext = null;
                if (i < keyContexts.size()) {
                    keyContext = keyContexts.get(i);
                }
                if (keyContext != null) {
                    map.put(key, keyContext);
                }
                list.add(keyContext);
            }
            this.keyContexts = map;
            this.keyContextsList = list;
            return this;
        }

        public BatchLoaderEnvironment build() {
            return new BatchLoaderEnvironment(context, keyContextsList, keyContexts);
        }
    }
}
