package org.dataloader;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This represents code that the java-dataloader project considers public SPI
 * and has an imperative to be stable within major releases.
 *
 * The guarantee is for callers of code with this annotation as well as derivations(推导) that inherit / implement this code.
 *
 * New methods will not be added (without using default methods say)
 * that would nominally breaks SPI implementations within a major release.
 *
 * fixme 不会添加新方法。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD, TYPE})
@Documented
public @interface PublicSpi {
}
