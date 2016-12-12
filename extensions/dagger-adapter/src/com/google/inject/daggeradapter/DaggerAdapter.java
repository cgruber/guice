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

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static java.lang.reflect.Modifier.isStatic;

/**
 * A utility to adapt classes annotated with {@link @dagger.Module} such that their
 * {@link @dagger.Provides} methods can be properly invoked by Guice to perform their provision
 * operations.
 *
 * <p>Simple example:
 *
 * <pre>{@code
 * Guice.createInjector(...other modules..., DaggerAdapter.from(new SomeDaggerAdapter()));
 * }</pre>
 *
 * <p>Some notes on usage and compatibility.
 *
 * <ul>
 *   <li>Dagger provider methods have a "SET_VALUES" provision mode not supported by Guice.
 *   <li>MapBindings are not yet implemented (pending).
 *   <li>Be careful about stateful modules. In contrast to Dagger (where components are expected to
 *       be recreated on-demand with new Module instances), Guice typically has a single injector
 *       with a long lifetime, so your module instance will be used throughout the lifetime of the
 *       entire app.
 *   <li>Dagger 1.x uses {@link @Singleton} for all scopes, including shorter-lived scopes like
 *       per-request or per-activity. Using modules written with Dagger 1.x usage in mind may result
 *       in mis-scoped objects.
 *   <li>Dagger 2.x supports custom scope annotations, but for use in Guice, a custom scope
 *       implementation must be registered in order to support the custom lifetime of that
 *       annotation.
 * </ul>
 *
 * @author cgruber@google.com (Christian Gruber)
 */
public final class DaggerAdapter {
  private static final Function<Object, Class<?>> TO_CLASS =
      new Function<Object, Class<?>>() {
        @Override
        public Class<?> apply(Object input) {
          return (input instanceof Class) ? (Class<?>) input : input.getClass();
        }
      };
      private static final Predicate<Class<?>> IS_ABSTRACT =
              new Predicate<Class<?>>() {
                @Override
                public boolean apply(Class<?> type) {
                  return type.isInterface() || Modifier.isAbstract(type.getModifiers());
                }
              };

  /**
   * Returns a guice module from a dagger module.
   *
   * <p>Note: At present, it does not honor {@code @Module(includes=...)} directives.
   */
  public static Module from(Object... daggerModuleObjects) {
    return new DaggerCompatibilityModule(ImmutableList.copyOf(daggerModuleObjects));
  }

  /**
   * A Module that adapts Dagger {@code @Module}-annotated types to contribute configuration to an
   * {@link com.google.inject.Injector} using a dagger-specific {@link
   * ModuleAnnotatedMethodScanner}.
   */
  private static final class DaggerCompatibilityModule implements Module {
    private final ImmutableList<Object> daggerModuleObjects;

    private DaggerCompatibilityModule(ImmutableList<Object> daggerModuleObjects) {
      this.daggerModuleObjects = daggerModuleObjects;
    }

    @Override
    public void configure(Binder binder) {
      List<Object> givenInstances = extractPreInstantiatedModules(binder, daggerModuleObjects);
      Set<Class<?>> preInstantiatedModules = extractTypes(givenInstances);
      ImmutableSet<Class<?>> knownTypes = knownTypes(binder, extractTypes(daggerModuleObjects));

      // register any static @Provides methods, which may occur on any module type.
      registerStaticProvides(knownTypes);

      Set<Class<?>> uninstantiatedTypes = Sets.difference(knownTypes, preInstantiatedModules);

      // register bindings from abstract types
      registerBinds(Sets.filter(uninstantiatedTypes, IS_ABSTRACT));

      // instantiate remaining concrete types
      Set<Class<?>> toInstantiate = Sets.filter(uninstantiatedTypes, not(IS_ABSTRACT));
      Iterable<Object> instantiatedModules = instantiateModuleTypes(binder, toInstantiate);
      for (Object module : Iterables.concat(givenInstances, instantiatedModules)) {
        binder.install(ProviderMethodsModule.forModule(module, DaggerMethodScanner.INSTANCE));
      }
    }

    private void registerBinds(Set<Class<?>> filter) {

    }

    private void registerStaticProvides(Set<Class<?>> moduleTypes) {
      // TODO(cgruber) Confirm behavior with Dagger re: static methods from parent types.
      for (Class<?> type : moduleTypes) {
        for (Method method : type.getDeclaredMethods()) {
          if (isStatic(method.getModifiers())) {
            if (method.isAnnotationPresent(dagger.Provides.class)) {

            }
          }
        }
      }
    }

    private ImmutableSet<Class<?>> extractTypes(List<Object> givenInstances) {
      return FluentIterable.from(givenInstances).transform(TO_CLASS).toSet();
    }

    private LinkedHashSet<Object> instantiateModuleTypes(
        Binder binder, Iterable<Class<?>> toInstantiate) {
      LinkedHashSet<Object> modules = new LinkedHashSet<Object>();
      for (Class<?> moduleType : toInstantiate) {
        try {
          Object module = null;
          for (Constructor<?> constructor : moduleType.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
              constructor.setAccessible(true);
              module = constructor.newInstance();
            }
          }
          if (module == null) { // try default constructor.
            module = moduleType.newInstance();
          }
          modules.add(module);
        } catch (Exception e) {
          binder.addError(e);
        }
      }
      return modules;
    }

    /**
     * Scans the graph of module inclusions for all known module times, validating that the types
     * found are actually dagger {@code @}{@link dagger.Module} types.
     *
     * @param binder the Guice binder to which errors can be reported.
     * @param initialModuleTypes the initial set of types to scan
     * @return the set of all module types referenced from the given types
     */
    static ImmutableSet<Class<?>> knownTypes(Binder binder, Set<Class<?>> initialModuleTypes) {
      Deque<Class<?>> toScan = new ArrayDeque<Class<?>>(initialModuleTypes);
      LinkedHashSet<Class<?>> seen = new LinkedHashSet<Class<?>>();
      while (!toScan.isEmpty()) {
        Class<?> type = toScan.poll();
        if (seen.add(type)) {
          if (com.google.inject.Module.class.isAssignableFrom(type)) {
            binder.addError("%s is a Guice module. The adapter is only for Dagger modules.", type);
          }
          dagger.Module annotation = type.getAnnotation(dagger.Module.class);
          if (annotation == null) {
            // TODO(cgruber) Make this scan track inclusion path, and report.
            // That probably means switching to a recursive function with a
            // stack to track inclusion paths.
            binder.addError("%s is not annotated with @dagger.Module", type);
            continue;
          }
          toScan.addAll(ImmutableList.copyOf(annotation.includes()));
        }
      }
      return ImmutableSet.copyOf(seen);
    }

    /**
     * Filters the set of pre-instantiated module objects from a set of given module objects,
     * reporting errors if the instances given are duplicated (more than one instance for each
     * type).
     */
    private static List<Object> extractPreInstantiatedModules(
        Binder binder, Iterable<Object> givenObjects) {
      ImmutableListMultimap<Class<?>, Object> typesIndex =
          FluentIterable.from(givenObjects).filter(not(instanceOf(Class.class))).index(TO_CLASS);
      ImmutableList.Builder<Object> instances = ImmutableList.builder();
      for (Map.Entry<Class<?>, Collection<Object>> entry : typesIndex.asMap().entrySet()) {
        if (entry.getValue().size() > 1) {
          binder.addError(
              "Module %s was manually instantiated %s times. Multiple instances are not supported",
              entry.getKey(), entry.getValue().size());
        }
        // TODO(cgruber) should this throw a guice creation exception?
        instances.addAll(entry.getValue()); // let Guice report the duplicate bindings
      }
      return instances.build();
    }

    @Override // For debugging
    public String toString() {
      return MoreObjects.toStringHelper(this).add("modules", daggerModuleObjects).toString();
    }
  }

  private DaggerAdapter() {}
}
