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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Providers;
import dagger.MapKey;
import dagger.multibindings.IntoMap;
import java.lang.annotation.Retention;
import java.util.Map;
import junit.framework.TestCase;

/**
 * Tests for {@link DaggerAdapter}.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
public class DaggerAdapterMapBindingsTest extends TestCase {
  @dagger.Module
  private static class MapBindingDaggerModuleOne {
    @dagger.Provides
    @IntoMap
    @StringKey("One")
    Integer anInteger() {
      return 1;
    }
  }

  @dagger.Module
  private static class MapBindingDaggerModuleTwo {
    @dagger.Provides
    @IntoMap
    @StringKey("Two")
    Integer anInteger() {
      return 2;
    }
  }

  @dagger.Module
  private static class MApBindingDaggerModuleTwoDuplicate {
    @dagger.Provides
    @IntoMap
    @StringKey("Two")
    Integer anInteger() {
      return 3; // different value
    }
  }

  public void testMapBindings() {
    Injector i =
        Guice.createInjector(
            DaggerAdapter.from(new MapBindingDaggerModuleOne(), new MapBindingDaggerModuleTwo()));
    assertEquals(
        ImmutableMap.of("One", 1, "Two", 2), i.getInstance(new Key<Map<String, Integer>>() {}));
  }

  static class MultibindingGuiceModule implements Module {
    @Override
    public void configure(Binder binder) {
      MapBinder<String, Integer> mb = MapBinder.newMapBinder(binder, String.class, Integer.class);

      mb.addBinding("Three").toInstance(3);
      mb.addBinding("Four").toProvider(Providers.of(4)); // mix'n'match.
    }
  }

  public void testMapBindingsWithGuiceModule() {
    Injector i =
        Guice.createInjector(
            new MultibindingGuiceModule(),
            DaggerAdapter.from(new MapBindingDaggerModuleOne(), new MapBindingDaggerModuleTwo()));
    assertEquals(
        ImmutableMap.of("One", 1, "Two", 2, "Three", 3, "Four", 4),
        i.getInstance(new Key<Map<String, Integer>>() {}));
  }

  /** An alternative MapKey to the normal dagger pre-fab which doesn't have RUNTIME retention. */
  @MapKey
  @Retention(RUNTIME)
  @interface StringKey {
    String value();
  }
}
