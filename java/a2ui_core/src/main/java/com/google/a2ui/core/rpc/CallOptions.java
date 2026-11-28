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

package com.google.a2ui.core.rpc;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The options for one call to the agent.
 *
 * @param timeout the time to wait for the response of the agent. The result is empty if the call
 *     uses the default timeout of the handler. A call with a timeout of zero or less waits with no
 *     limit.
 * @param functionCallId the ID that connects the call to its response. The result is empty if the
 *     handler makes a new ID.
 */
public record CallOptions(Optional<Duration> timeout, Optional<String> functionCallId) {
  /** The options of a call that uses the default timeout and gets a new ID. */
  public static final CallOptions DEFAULT = new CallOptions(Optional.empty(), Optional.empty());

  /** Makes the options. */
  public CallOptions {
    Objects.requireNonNull(timeout, "timeout");
    Objects.requireNonNull(functionCallId, "functionCallId");
  }

  /**
   * Makes the options of a call that has its own timeout and gets a new ID.
   *
   * @param timeout the time to wait for the response of the agent
   * @return the options
   */
  public static CallOptions withTimeout(Duration timeout) {
    return new CallOptions(Optional.of(timeout), Optional.empty());
  }

  /**
   * Makes the options of a call that has its own ID and uses the default timeout.
   *
   * @param functionCallId the ID that connects the call to its response
   * @return the options
   */
  public static CallOptions withFunctionCallId(String functionCallId) {
    return new CallOptions(Optional.empty(), Optional.of(functionCallId));
  }
}
