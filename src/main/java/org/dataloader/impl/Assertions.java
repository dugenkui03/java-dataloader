package org.dataloader.impl;

import org.dataloader.Internal;

import java.util.Objects;

@Internal
public class Assertions {

    // 如果为false、则抛出断言异常、并指定异常信息
    public static void assertState(boolean state, String message) {
        if (!state) {
            throw new AssertionException(message);
        }
    }

    // 确定非null
    public static <T> T nonNull(T t) {
        return Objects.requireNonNull(t, "nonNull object required");
    }

    // 确定非null、指定异常信息
    public static <T> T nonNull(T t, String message) {
        return Objects.requireNonNull(t, message);
    }

    private static class AssertionException extends IllegalStateException {
        public AssertionException(String message) {
            super(message);
        }
    }
}
