// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util;

import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;

public class ContainerUtil extends ContainerUtilRt {

  public static final ImmutableDomainObjectSet<?> EMPTY_DOMAIN_OBJECT_SET = ImmutableDomainObjectSet.of(Collections.emptyList());

  @NotNull
  @Contract(pure = true)
  public static <T> ImmutableDomainObjectSet<T> emptyDomainObjectSet() {
    //noinspection unchecked
    return (ImmutableDomainObjectSet<T>)EMPTY_DOMAIN_OBJECT_SET;
  }

  public static <T> boolean match(@NotNull Iterator<T> iterator1,
                                  @NotNull Iterator<T> iterator2,
                                  @NotNull BooleanBiFunction<? super T, ? super T> condition) {
    while (iterator2.hasNext()) {
      if (!iterator1.hasNext() || !condition.fun(iterator1.next(), iterator2.next())) {
        return false;
      }
    }
    return !iterator1.hasNext();
  }
}
