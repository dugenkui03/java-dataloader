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

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 *
 * A function that is invoked for batch loading a list of data values indicated by the provided list of keys.
 * The function returns a promise of a list of results of individual load requests.
 *
 * <p>
 * There are a few constraints that must be upheld(支持、支撑):
 * <ul>
 * <li>The list of values must be the same size as the list of keys.</li>
 * <li>Each index in the list of values must correspond to the same index in the list of keys.</li>
 * </ul>
 *
 * fixme
 *      指定参数列表、返回结果列表；
 *      结果列表的个数必须和参数列表的数量一致、顺序一致。
 *
 *
 * <p>
 * For example, if your batch function was provided the list of keys:
 *
 * <pre>
 *  [
 *      2, 9, 6, 1
 *  ]
 * </pre>
 *
 * and loading from a back-end service returned this list of  values:
 *
 * <pre>
 *     错误示例，因为数量与入参列表不一致。
 *  [
 *      { id: 9, name: 'Chicago' },
 *      { id: 1, name: 'New York' },
 *      { id: 2, name: 'San Francisco' },
 *  ]
 * </pre>
 * then the batch loader function contract has been broken.
 *
 * <p>
 * The back-end service returned results in a different order than we requested,
 * likely because it was more efficient for it to do so.
 * Also, it omitted(忽略) a result for key 6, which we may interpret as no value existing for that key.
 *
 * <p> 个数和顺序都要对齐。
 * To uphold(遵守) the constraints of the batch function, it must return an List of values the same length as
 * the List of keys, and re-order them to ensure each index aligns with the original keys [ 2, 9, 6, 1 ]:
 *
 * <pre>
 *     正确返回结果。
 *  [
 *      { id: 2, name: 'San Francisco' },
 *      { id: 9, name: 'Chicago' },
 *      null,
 *      { id: 1, name: 'New York' }
 * ]
 * </pre>
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 *           请求key的类型
 * @param <V> type parameter indicating the type of values returned
 *           响应的结果的类型
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@PublicSpi
@FunctionalInterface
public interface BatchLoader<K, V> {

    /**
     * fixme 批量调用对应的key集合、然后返回对应的value集合
     *
     * Called to batch load the provided keys and return a promise to a list of values.
     * <p>
     * If you need calling context then implement {@link org.dataloader.BatchLoaderWithContext} fixme 上下文类BatchLoaderWithContext
     *
     * @param keys the collection of keys to load
     *             请求参数集合
     *
     * @return a promise of the values for those keys
     *         key对应的value集合：数量和顺序与请求key一致
     */
    CompletionStage<List<V>> load(List<K> keys);
}

