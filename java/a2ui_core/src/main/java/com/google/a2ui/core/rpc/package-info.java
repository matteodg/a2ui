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

/**
 * Function calls between the renderer and the agent. {@link com.google.a2ui.core.rpc.RpcHandler}
 * sends a call to the agent and completes its future with the response. {@link
 * com.google.a2ui.core.rpc.RpcScheduler} runs the timeout of a call, and {@link
 * com.google.a2ui.core.rpc.CallOptions} are the options of one call.
 */
package com.google.a2ui.core.rpc;
