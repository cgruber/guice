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
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.spi.Message;
import com.google.inject.util.Providers;
import dagger.Binds;
import dagger.multibindings.IntoSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Qualifier;
import junit.framework.TestCase;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Tests for {@link DaggerAdapter}.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
public class DaggerAdapterTest extends TestCase {
  @dagger.Module
  static class SimpleDaggerModule {
    @dagger.Provides
    @Named("test")
    Integer anInteger() {
      return 1;
    }
  }

  public void testSimpleModule() {
    Injector i = createInjector(DaggerAdapter.from(SimpleDaggerModule.class));
    assertThat(i.getInstance(Integer.class)).isEqualTo(1);
  }

  interface Foo {}

  static class FooA {
    @Inject
    FooA() {}
  }

  static class FooB {
    @Inject
    FooB() {}
  }

  @Qualifier
  @interface A {}

  @Qualifier
  @interface B {}

  @dagger.Module
  abstract static class SimpleAbstractModule {
    @Binds
    abstract @A Foo foo(FooA a);
  }

  @dagger.Module
  interface SimpleInterfaceModule {
    @Binds
    @B
    Foo foo(FooB b);
  }

  public void testAbstractModuleTypes() {
    Injector i = createInjector(DaggerAdapter.from(SimpleInterfaceModule.class));
    assertThat(i.getInstance(Key.get(Foo.class, A.class))).isNotNull();
    assertThat(i.getInstance(Key.get(Foo.class, B.class))).isNotNull();
  }

  static class SimpleGuiceModule extends AbstractModule {
    @com.google.inject.Provides
    String aString(Integer i) {
      return i.toString();
    }

    @Override
    protected void configure() {}
  }

  public void testInteractionWithGuiceModules() {
    Injector i =
        createInjector(new SimpleGuiceModule(), DaggerAdapter.from(new SimpleDaggerModule()));
    assertThat(i.getInstance(String.class)).isEqualTo("1");
  }

  @dagger.Module(includes = SimpleDaggerModule.class)
  static class Includer {
    @dagger.Provides
    String anInteger(Integer integer) {
      return integer.toString();
    }
  }

  public void testSimpleInclusion() {
    Injector i = createInjector(DaggerAdapter.from(new Includer()));
    String actual = i.getInstance(String.class);
    assertThat(actual).isEqualTo("1");
  }

  public void testInclusionWithDuplicateInstances() {
    try {
      createInjector(DaggerAdapter.from(new Includer(), new Includer()));
      fail("Should have thrown.");
    } catch (CreationException e) {
      System.out.println(e);
      assertThat(e.getErrorMessages()).hasSize(2);
      // Unsure if the order of messages will always be consistent.
      boolean hasError = false;
      for (Message m : e.getErrorMessages()) {
        hasError |= m.getMessage().contains("was manually instantiated 2 times");
      }
      assertWithMessage(
              "Creation exception did not contain duplicate instance error: "
                  + e.getErrorMessages())
          .that(hasError)
          .isTrue();
    }
  }

  @dagger.Module
  static class Dupe {
    @dagger.Provides
    Integer integer() {
      return 1;
    }
  }

  @dagger.Module(includes = {Dupe.class, Dupe.class})
  static class DupeIncluder {}

  public void testInclusionDeDuping() {
    Injector i = createInjector(DaggerAdapter.from(DupeIncluder.class));
    assertThat(i.getInstance(Integer.class)).isEqualTo(1);
  }

  @dagger.Module(includes = CycleIncluder.class)
  static class Cycle {
    @dagger.Provides
    Integer integer() {
      return 1;
    }
  }

  @dagger.Module(includes = Cycle.class)
  static class CycleIncluder {}

  public void testInclusionCycleHandling() {
    Injector i = createInjector(DaggerAdapter.from(CycleIncluder.class));
    assertThat(i.getInstance(Integer.class)).isEqualTo(1);
  }

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
        createInjector(
            DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertThat(i.getInstance(new Key<Set<Integer>>() {})).containsExactly(3, 5);
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
        createInjector(
            new MultibindingGuiceModule(),
            DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertThat(i.getInstance(new Key<Set<Integer>>() {})).containsExactly(13, 3, 5, 8);
  }

  @dagger.Module
  static class DaggerModuleWithGuiceProvides {
    @com.google.inject.Provides
    Integer anInteger() {
      return 1;
    }
  }

  public void testGuiceProvidesOnDaggerModule() {
    try {
      createInjector(DaggerAdapter.from(DaggerModuleWithGuiceProvides.class));
      fail("Should have thrown.");
    } catch (CreationException e) {
      assertThat(e.getErrorMessages()).hasSize(1);
      Message m = e.getErrorMessages().iterator().next();
      assertThat(m.getMessage()).contains(" instead of @dagger.Provides");
    }
  }

  private static final Module INJECTOR_CONFIG =
      new Module() {
        @Override
        public void configure(Binder binder) {
          // We want very explicit behavior so we don't error on the wrong thing,
          // or succeed accidentally.
          binder.requireExactBindingAnnotations();
          binder.requireAtInjectOnConstructors();
        }
      };

  private Injector createInjector(Module... modules) {
    return Guice.createInjector(
        ImmutableSet.<Module>builder().add(INJECTOR_CONFIG).add(modules).build());
  }
}
