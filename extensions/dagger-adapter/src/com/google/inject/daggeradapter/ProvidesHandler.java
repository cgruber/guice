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

import static com.google.inject.daggeradapter.MapBindings.processMapBinding;
import static com.google.inject.daggeradapter.SetBindings.processSetBinding;
import static com.google.inject.daggeradapter.SetBindings.processSetValuesBinding;

import com.google.inject.Binder;
import com.google.inject.Key;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import java.lang.reflect.Method;

/**
 * A handler for Provides methods.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
final class ProvidesHandler {
  public static <T> Key<T> process(Binder binder, Provides annotation, Key<T> key, Method method) {
    if (method.isAnnotationPresent(IntoSet.class)) {
      return processSetBinding(binder, key.getTypeLiteral());
    } else if (method.isAnnotationPresent(ElementsIntoSet.class)) {
      return processSetValuesBinding(binder, key.getTypeLiteral(), method);
    } else if (method.isAnnotationPresent(IntoMap.class)) {
      return processMapBinding(binder, key.getTypeLiteral(), method);
    }
    try {
      return processLegacyProvides(binder, annotation, key, method);
    } catch (NoSuchMethodError ignore) {
      // If we're here, Dagger has removed the deprecated type() property.
      // TODO(cgruber) Log this?
    }
    // If no known decoration is found, it should be a simple unique binding.
    return key;
  }

  private static <T> Key<T> processLegacyProvides(
      Binder binder, Provides annotation, Key<T> key, Method method) {
    switch (annotation.type()) {
      case UNIQUE:
        return key; // Default is equivalent to no property
      case MAP:
        return processMapBinding(binder, key.getTypeLiteral(), method);
      case SET:
        return processSetBinding(binder, key.getTypeLiteral());
      case SET_VALUES:
        return processSetValuesBinding(binder, key.getTypeLiteral(), method);
      default:
        binder.addError("Unknown @Provides type %s on %s.", annotation.type(), method);
        return key;
    }
  }

  private ProvidesHandler() {}
}
