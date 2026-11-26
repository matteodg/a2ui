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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The context of the errors of {@link MessageProcessor}. */
class MessageProcessorErrorsTest {
  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(List.of(BasicCatalog.api()), ValidationConfig.STRICT);

  @Test
  void aSchemaProblemOfAComponentHasItsSurfaceItsPathAndItsDetails() {
    processor.processMessages(create("s"));

    A2uiValidationError error = rejected(update(map("id", "root", "component", "Text", "text", 5)));

    assertEquals(Optional.of("s"), error.surfaceId());
    assertEquals(Optional.of("/components/0/text"), error.path());
    assertEquals(Optional.empty(), error.errorCode());
    assertFalse(error.details().isEmpty());
    assertTrue(
        error.details().stream().allMatch(detail -> detail.path().equals("/text")),
        error.details().toString());
    assertTrue(
        error.details().stream().anyMatch(detail -> detail.code().equals("type_mismatch")),
        error.details().toString());
  }

  @Test
  void anAbsentPropertyAndAnUnknownPropertyGiveTheirOwnPath() {
    processor.processMessages(create("s"));

    A2uiValidationError missing = rejected(update(map("id", "root", "component", "Text")));
    A2uiValidationError undeclared =
        rejected(update(map("id", "root", "component", "Text", "text", "x", "bogus", 1)));

    assertEquals(Optional.of("/components/0/text"), missing.path());
    assertEquals(1, missing.details().size(), missing.details().toString());
    assertEquals("missing_field", missing.details().get(0).code());
    assertEquals(Optional.of("/components/0/bogus"), undeclared.path());
  }

  @Test
  void theProblemsOfTwoPropertiesGiveThePathOfTheFirstComponentThatIsNotCorrect() {
    processor.processMessages(create("s"));
    Map<String, Object> root = map("id", "root", "component", "Card", "child", "a");
    Map<String, Object> wrong = map("id", "a", "component", "Text", "text", 5, "bogus", 1);
    Map<String, Object> alsoWrong = map("id", "b", "component", "Text");

    A2uiValidationError error = rejected(update(root, wrong, alsoWrong));

    assertEquals(Optional.of("/components/1"), error.path());
    assertTrue(
        error.details().stream().anyMatch(detail -> detail.path().equals("/bogus")),
        error.details().toString());
    assertEquals("missing_field", error.details().getLast().code(), error.details().toString());
    assertTrue(error.getMessage().contains("component 'a' (Text): /text: "), error.getMessage());
    assertTrue(error.getMessage().contains("; component 'b' (Text): /text: "), error.getMessage());
  }

  @Test
  void anEnvelopeThatIsNotCorrectNamesTheMessageAndTheField() {
    Map<String, Object> noVersion = map("createSurface", map("surfaceId", "t"));
    Map<String, Object> numberAsSurface = message("deleteSurface", "surfaceId", 7);

    A2uiValidationError missing = rejected(List.of(create("s"), noVersion));
    A2uiValidationError mismatch = rejected(numberAsSurface);

    assertEquals(
        List.of(
            new A2uiErrorDetail(
                "messages.1.version",
                "missing_field",
                "Missing required version field in message.")),
        missing.details());
    assertEquals(Optional.of("t"), missing.surfaceId());
    assertTrue(processor.model().getSurface("s").isPresent());
    assertEquals(1, mismatch.details().size());
    assertEquals("messages.0.deleteSurface.surfaceId", mismatch.details().get(0).path());
    assertEquals("type_mismatch", mismatch.details().get(0).code());
  }

  @Test
  void aSchemaProblemOfABodyHasAPathThatIsRelativeToTheBody() {
    A2uiValidationError error = rejected(map("version", "1.0", "deleteSurface", map()));

    assertEquals(Optional.of(""), error.surfaceId());
    assertEquals(Optional.of("/surfaceId"), error.path());
    assertEquals("messages.0.deleteSurface.surfaceId", error.details().get(0).path());
    assertEquals("missing_field", error.details().get(0).code());
  }

  @Test
  void anErrorWithNoActionHasNoSurface() {
    A2uiValidationError error = rejected(map("version", "1.0"));

    assertEquals(Optional.empty(), error.surfaceId());
    assertEquals(Optional.empty(), error.functionCallId());
  }

  @Test
  void anErrorOfAFunctionCallMessageHasItsFunctionCall() {
    A2uiValidationError error =
        rejected(message("callRendererFunction", "functionCallId", "call-1"));

    assertEquals(Optional.of("call-1"), error.functionCallId());
    assertEquals(Optional.empty(), error.surfaceId());
  }

  @Test
  void eachErrorOfASurfaceMessageHasItsSurface() {
    processor.processMessages(create("s"));

    A2uiIntegrityError duplicate =
        assertThrows(A2uiIntegrityError.class, () -> processor.processMessages(create("s")));
    A2uiCatalogError noCatalog =
        assertThrows(
            A2uiCatalogError.class,
            () ->
                processor.processMessages(
                    message("createSurface", "surfaceId", "t", "catalogId", "urn:absent")));
    A2uiDataError data =
        assertThrows(
            A2uiDataError.class,
            () ->
                processor.processMessages(
                    message(
                        "updateDataModel", "surfaceId", "s", "path", "/__proto__/x", "value", 1)));

    assertEquals(Optional.of("s"), duplicate.surfaceId());
    assertEquals(Optional.of("t"), noCatalog.surfaceId());
    assertEquals(Optional.of("s"), data.surfaceId());
  }

  @Test
  void anUpdateDataModelPathThatIsNotCorrectHasThePathOfItsField() {
    processor.processMessages(create("s"));

    A2uiValidationError relative =
        rejected(message("updateDataModel", "surfaceId", "s", "path", "name", "value", 1));
    A2uiValidationError escape =
        rejected(message("updateDataModel", "surfaceId", "s", "path", "/a~2b", "value", 1));

    assertEquals(Optional.of("/path"), relative.path());
    assertEquals(Optional.of("/path"), escape.path());
    assertEquals(Optional.of("s"), escape.surfaceId());
  }

  private A2uiValidationError rejected(Object payload) {
    return assertThrows(A2uiValidationError.class, () -> processor.processMessages(payload));
  }

  /** Makes a map from keys and values. */
  private static Map<String, Object> map(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  private static Map<String, Object> message(String action, Object... body) {
    return map("version", "1.0", action, map(body));
  }

  private static Map<String, Object> create(String id, Object... extra) {
    Map<String, Object> body = map("surfaceId", id, "catalogId", BasicCatalog.ID);
    body.putAll(map(extra));
    return map("version", "1.0", "createSurface", body);
  }

  /** Makes an {@code updateComponents} message for the surface {@code s}. */
  private static Map<String, Object> update(Object... components) {
    return message("updateComponents", "surfaceId", "s", "components", List.of(components));
  }
}
