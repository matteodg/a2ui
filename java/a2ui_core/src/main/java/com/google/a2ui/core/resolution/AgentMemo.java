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

package com.google.a2ui.core.resolution;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.internal.AgentRequests;
import com.google.a2ui.core.internal.AgentRequests.Outcome;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The calls to the agent that the bindings and the checks of one node made: the answers and the
 * failures that the node saw, and the requests that the node waits for.
 *
 * <p>The node keeps its own copy of each answer. Thus the node does not send a call again because
 * the surface removed the answer. The node also remembers a failure. Thus it does not send a failed
 * call again at each resolution. It sends the call again only when the call changes.
 *
 * <p>An instance is not thread-safe. Use it on the thread that owns its surface.
 */
final class AgentMemo {
  private final Runnable resolveAgain;
  private final Map<String, Outcome> seen = new HashMap<>();
  private final Map<String, Subscription> waiting = new HashMap<>();
  private final Set<String> used = new HashSet<>();
  private boolean disposed;

  /**
   * Makes the memory of one node.
   *
   * @param resolveAgain resolves the node again. The memory calls it when a call that the node
   *     waits for has an answer or failed.
   */
  AgentMemo(Runnable resolveAgain) {
    this.resolveAgain = resolveAgain;
  }

  /**
   * Gets the value of a call. If no identical call waits and the surface keeps no answer, the
   * method sends the call.
   *
   * @param requests the requests of the surface
   * @param key the key of the call
   * @param call the call to send
   * @return the answer of the agent, or {@link Later#PENDING} while there is none
   * @throws A2uiError if the agent failed the call
   */
  Object valueOf(AgentRequests requests, String key, Map<String, Object> call) {
    used.add(key);
    Outcome known = seen.get(key);
    if (known != null) {
      return read(known);
    }
    if (waiting.containsKey(key)) {
      return Later.PENDING;
    }
    Outcome.Value kept = requests.answer(key);
    if (kept != null) {
      seen.put(key, kept);
      return kept.value();
    }
    // The caller can complete the call before `share` returns.
    boolean[] returned = {false};
    Outcome[] early = {null};
    Subscription subscription =
        requests.share(
            key,
            call,
            outcome -> {
              waiting.remove(key);
              if (disposed) {
                return;
              }
              // A cancelled call is not a failure to remember. The subsequent resolution sends
              // the call again.
              boolean cancelled = outcome instanceof Outcome.Cancelled;
              if (!cancelled) {
                seen.put(key, outcome);
              }
              if (!returned[0]) {
                early[0] = outcome;
              } else if (!cancelled) {
                resolveAgain.run();
              }
            });
    returned[0] = true;
    if (early[0] != null) {
      return read(early[0]);
    }
    waiting.put(key, subscription);
    return Later.PENDING;
  }

  private static Object read(Outcome outcome) {
    return switch (outcome) {
      case Outcome.Value answer -> answer.value();
      case Outcome.Failed failed -> throw failed.error();
      case Outcome.Cancelled _ -> Later.PENDING;
    };
  }

  /** Forgets each call that the resolution that ended did not make. */
  void endOfResolution() {
    seen.keySet().retainAll(used);
    Iterator<Map.Entry<String, Subscription>> entries = waiting.entrySet().iterator();
    while (entries.hasNext()) {
      Map.Entry<String, Subscription> entry = entries.next();
      if (!used.contains(entry.getKey())) {
        entry.getValue().cancel();
        entries.remove();
      }
    }
    used.clear();
  }

  /** Forgets each call. The node receives no more answers. */
  void dispose() {
    disposed = true;
    waiting.values().forEach(Subscription::cancel);
    waiting.clear();
    seen.clear();
  }
}
