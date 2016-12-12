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
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Providers;
import dagger.multibindings.IntoSet;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link DaggerAdapter}.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
public class DaggerAdapterSetBindingsTest extends TestCase {
  @dagger.Module
  static class SetBindingDaggerModule1 {
    @dagger.Provides
    @IntoSet
    Integer anInteger() {
      return 5;
    }
  }

  @dagger.Module
  static class SetBindingDaggerModule2 {
    @dagger.Provides
    @IntoSet
    Integer anInteger() {
      return 3;
    }
  }

  public void testSetBindings() {
    Injector i =
        Guice.createInjector(
            DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertEquals(ImmutableSet.of(3, 5), i.getInstance(new Key<Set<Integer>>() {}));
  }

  static class MultibindingGuiceModule implements Module {
    @Override
    public void configure(Binder binder) {
      Multibinder<Integer> mb = Multibinder.newSetBinder(binder, Integer.class);
      mb.addBinding().toInstance(13);
      mb.addBinding().toProvider(Providers.of(8)); // mix'n'match.
    }
  }

  public void testSetBindingsWithGuiceModule() {
    Injector i =
        Guice.createInjector(
            new MultibindingGuiceModule(),
            DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertEquals(ImmutableSet.of(13, 3, 5, 8), i.getInstance(new Key<Set<Integer>>() {}));
  }
}
