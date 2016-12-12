/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.inject.daggeradapter;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import dagger.Binds;
import dagger.Provides;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * A scanner to process provider methods on Dagger modules.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
final class DaggerMethodScanner extends ModuleAnnotatedMethodScanner {
  static final DaggerMethodScanner INSTANCE = new DaggerMethodScanner();

  @SuppressWarnings("unchecked")
  private static final ImmutableSet<Class<? extends Annotation>> ANNOTATIONS =
      ImmutableSet.of(
          dagger.Binds.class,
          dagger.Provides.class,
          // The following are consumed for validation, to ensure that dagger modules
          // get annotated by non-dagger annotations.
          com.google.inject.Provides.class,
          com.google.inject.multibindings.ProvidesIntoMap.class,
          com.google.inject.multibindings.ProvidesIntoSet.class,
          com.google.inject.multibindings.ProvidesIntoOptional.class);

  @Override
  public Set<? extends Class<? extends Annotation>> annotationClasses() {
    return ANNOTATIONS;
  }

  @Override
  public <T> Key<T> prepareMethod(
      Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
    Method method = (Method) injectionPoint.getMember();
    if (annotation instanceof Binds) {
      return BindsHandler.process(binder, key, method);
    } else if (annotation instanceof Provides) {
      return ProvidesHandler.process(binder, (Provides) annotation, key, method);
    } else {
      binder.addError(
          "Method %s annotated with non-dagger annotation %s.",
          method, annotation.annotationType().getName());
      return Key.get(
          key.getTypeLiteral(), UniqueAnnotations.create()); // don't bind from bad method.
    }
  }

  private DaggerMethodScanner() {}
}
