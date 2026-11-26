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

package com.google.a2ui.core.processing;

import java.util.Map;

/** Receives the messages that the renderer sends to the agent. */
@FunctionalInterface
public interface OutboundMessageListener {
  /**
   * Receives one renderer-to-agent message.
   *
   * @param message the message as plain JSON values. It is read-only, and a transport can write it
   *     as JSON and send it.
   */
  void onMessage(Map<String, Object> message);
}
