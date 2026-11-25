/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.core.expressions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One part of a <code>${...}</code> template: literal content, a data binding or a function call.
 */
public sealed interface Expression {
  /**
   * Literal content.
   *
   * @param value a {@code String}, a {@code Long}, a {@code Double} or a {@code Boolean}
   */
  record Literal(Object value) implements Expression {
    /** Makes a literal and makes sure that the value is not null. */
    public Literal {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A data binding.
   *
   * @param path a JSON Pointer. It is absolute if it starts with {@code /}.
   */
  record Path(String path) implements Expression {
    /** Makes a path and makes sure that the pointer is not null. */
    public Path {
      Objects.requireNonNull(path, "path");
    }
  }

  /**
   * A function call with named arguments.
   *
   * @param name the name of the function
   * @param args the arguments, in the sequence of the template
   */
  record Call(String name, Map<String, Expression> args) implements Expression {
    /** Makes a call and makes a read-only copy of the arguments. */
    public Call {
      Objects.requireNonNull(name, "name");
      args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
  }
}
