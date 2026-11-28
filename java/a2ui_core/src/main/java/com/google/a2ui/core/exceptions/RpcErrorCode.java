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

package com.google.a2ui.core.exceptions;

/** The cause of a failed remote procedure call between the renderer and the agent. */
public enum RpcErrorCode {
  /** The function is unknown, the caller is not permitted, or the arguments are not valid. */
  INVALID_FUNCTION_CALL,
  /** The function started and then failed. */
  EXECUTION_ERROR,
  /** No response with the same call ID came in the permitted time. */
  TIMEOUT,
  /** The call was cancelled. For example, its surface was deleted. */
  CANCELLED,
  /** The call started after the disposal of the RPC handler. */
  DISPOSED,
  /** A call with the same call ID is already pending. */
  DUPLICATE,
  /** No outbound listener is registered. Thus the call cannot go to the agent. */
  NO_LISTENER,
}
