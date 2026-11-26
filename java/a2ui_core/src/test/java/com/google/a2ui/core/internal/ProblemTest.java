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

package com.google.a2ui.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProblemTest {
  private final Problem mismatch = new Problem("/text", Problem.TYPE_MISMATCH, "not a string");
  private final Problem unused = new Problem("/a~1b", Problem.INVALID_VALUE, "not used", true);

  @Test
  void theTextOfAProblemStartsWithItsPath() {
    assertEquals("/text: not a string", mismatch.toString());
    assertEquals("no type", new Problem("", Problem.INVALID_VALUE, "no type").toString());
  }

  @Test
  void aContainerSeesAProblemBelowItsPrefix() {
    Problem moved = unused.under("/action");

    assertEquals("/action/a~1b", moved.path());
    assertEquals(unused.code(), moved.code());
    assertEquals(unused.message(), moved.message());
    assertTrue(moved.unevaluated());
    assertSame(mismatch, mismatch.under(""));
  }

  @Test
  void anUnusedPropertyFollowsFromADifferentProblemIfTheSchemaDeclaresIt() {
    List<Problem> both = List.of(mismatch, unused);

    assertTrue(unused.followsFrom(both, name -> name.equals("a/b")));
    assertFalse(unused.followsFrom(both, name -> false));
    assertFalse(unused.followsFrom(List.of(unused), name -> true));
    assertFalse(mismatch.followsFrom(both, name -> true));
  }

  @Test
  void aProblemBecomesADetailOfAnError() {
    assertEquals(
        new A2uiErrorDetail("/text", Problem.TYPE_MISMATCH, "not a string"), mismatch.toDetail());
  }
}
