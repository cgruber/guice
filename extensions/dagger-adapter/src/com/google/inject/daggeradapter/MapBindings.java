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

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.multibindings.MapBinder;
import dagger.MapKey;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Utility methods to support MapBindings in the {@link DaggerMethodScanner}
 *
 * @author cgruber@google.com (Christian Gruber)
 */
final class MapBindings {
  @SuppressWarnings({"rawtypes", "unchecked"})
  static <T> Key<T> processMapBinding(Binder binder, TypeLiteral<T> valueType, Method method) {
    Key<T> newKey = Key.get(valueType, UniqueAnnotations.create());
    try {
      AdaptedMapKey mapKey = findMapKey(method);
      MapBinder mapBinder = MapBinder.newMapBinder(binder, mapKey.type, valueType);
      mapBinder.addBinding(mapKey.content).to(newKey);
    } catch (InvalidMapKeyException e) {
      binder.addError(e);
    }
    return newKey;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static AdaptedMapKey<?> findMapKey(Method method) throws InvalidMapKeyException {
    List<Annotation> mapKeys =
        FluentIterable.of(method.getAnnotations())
            .filter(
                new Predicate<Annotation>() {
                  @Override
                  public boolean apply(Annotation input) {
                    return input.annotationType().isAnnotationPresent(MapKey.class);
                  }
                })
            .toList();
    Annotation annotation = null;
    switch (mapKeys.size()) {
      case 0:
        throw new InvalidMapKeyException("No MapKey annotation found on method " + method);
      case 1:
        annotation = mapKeys.get(0);
        break;
      default:
        throw new InvalidMapKeyException(
            "More than one MapKey annotation found on method " + method);
    }
    // Should have one mapkey annotation here
    if (annotation.annotationType().getAnnotation(MapKey.class).unwrapValue()) {
      List<Method> properties =
          ImmutableList.copyOf(annotation.annotationType().getDeclaredMethods());
      Method property = null;
      try {
        property = Iterables.getOnlyElement(properties);
      } catch (NoSuchElementException nsee) {
        new InvalidMapKeyException("Could not find a key within the annotation " + annotation);
      } catch (IllegalArgumentException iae) {
        new InvalidMapKeyException(
            "Too many possible key values within the annotation " + annotation);
      }
      try {
        Object content = property.invoke(annotation);
        return new AdaptedMapKey(TypeLiteral.get(property.getGenericReturnType()), content);
      } catch (IllegalAccessException e) {
        throw new InvalidMapKeyException(
            "Could not access MapKey value on annotation " + annotation);
      } catch (IllegalArgumentException e) {
        throw new InvalidMapKeyException(
            "Could not access MapKey value on annotation " + annotation);
      } catch (InvocationTargetException e) {
        throw new InvalidMapKeyException(
            "Could not access MapKey value on annotation " + annotation);
      }
    } else {
      throw new UnsupportedOperationException("Wrapped map keys not supported.");
    }
  }

  static class InvalidMapKeyException extends Exception {
    private static final long serialVersionUID = 1;

    private InvalidMapKeyException(String message) {
      super(message);
    }
  }

  private static class AdaptedMapKey<T> {
    final TypeLiteral<T> type;
    final T content;

    AdaptedMapKey(TypeLiteral<T> type, T content) {
      this.type = type;
      this.content = content;
    }
  }

  private MapBindings() {}
}
