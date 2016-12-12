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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import java.lang.reflect.Method;

/**
 * A handler for Binds methods. Currently unsupported.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
final class BindsHandler {
  public static <T> Key<T> process(Binder binder, Key<T> key, Method method) {
    binder.addError("@Binds not supported by the Dagger Adapter.");
    return Key.get(key.getTypeLiteral(), UniqueAnnotations.create()); // don't bind via unsupported.
  }

  private BindsHandler() {}
}
