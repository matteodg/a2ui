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

package com.google.a2ui.swing.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects the log records of the adapter during a test, and keeps them out of the build output.
 * Close the object at the end of the test.
 */
public final class LogCapture implements AutoCloseable {
  /** The messages of the records, in the sequence of the log. */
  public final List<String> messages = new ArrayList<>();

  private final Logger logger = Logger.getLogger("com.google.a2ui.swing");
  private final boolean useParentHandlers = logger.getUseParentHandlers();
  private final Handler handler =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          messages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  /** Starts to collect the records. */
  public LogCapture() {
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
  }

  @Override
  public void close() {
    logger.removeHandler(handler);
    logger.setUseParentHandlers(useParentHandlers);
  }
}
