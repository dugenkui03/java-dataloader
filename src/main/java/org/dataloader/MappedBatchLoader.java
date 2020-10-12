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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * A function that is invoked for batch loading a map of of data values indicated by the provided set of keys.
 * The function returns a promise of a map of results of individual load requests.
 *
 * fixme
 *      入参是Set<K>，出参是对应的 Map<K,V>。
 *      这样就不要求数量和顺序了。
 *      但是当结果map被 DataLoader 处理后，数量仍然有要求。
 * fixme
 *      限制：
 *          1. 作为key的参数必须可以作为HashMap的key、重写equals() 和 hashCode()方法；
 *          2. 调用者必须可以处理value为null的情况。？？
 *
 * <p>
 * There are a few constraints that must be upheld:
 * <ul>
 *      <li>The keys MUST be able to be first class keys in a Java map.  Get your equals() and hashCode() methods in order
 *      <li>The caller of the {@link DataLoader} that uses this batch loader function MUST be able to cope with(处理、应对) null values coming back as results
 * </ul>
 *
 *
 * <p>
 * This form is useful when you don't have a 1:1 mapping of keys to values or when null is an acceptable value for a missing value.
 *
 *
 * fixme demo
 *      请求指定 userIds 的用户信息，某些userId可能不存在。
 * <p>
 * For example, let's assume you want to load users from a database, you could probably use a query that looks like this:
 * <pre>
 *    SELECT * FROM User WHERE id IN (keys)
 * </pre>
 *
 * <p>
 * Given say 10 user id keys you might only get 7 results back.
 * This can be more naturally represented in a map than in an ordered list of values from the batch loader function.
 *
 * <p>
 * When the map is processed by the {@link DataLoader} code,
 * any keys that are missing in the map will be replaced with null values.
 *
 * The semantic that the number of {@link DataLoader#load(Object)} requests are matched with and equal number of values is kept.
 *
 * <p>
 * This means that if 10 keys are asked for then {@link DataLoader#dispatch()} will return a promise of 10 value results
 * and each of the {@link DataLoader#load(Object)} will complete with a value, null or an exception.
 *
 * @param <K> type parameter indicating the type of element in keys to use for data load requests.
 * @param <V> type parameter indicating the type of element in values returned
 *
 */
public interface MappedBatchLoader<K, V> {

    /**
     * Called to batch load the provided keys and return a promise to a map of values.
     *
     * @param keys        the set of keys to load
     *
     * @return a promise to a map of values for those keys
     */
    @SuppressWarnings("unused")
    CompletionStage<Map<K, V>> load(Set<K> keys);
}
