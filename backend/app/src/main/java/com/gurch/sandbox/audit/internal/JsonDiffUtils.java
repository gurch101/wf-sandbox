package com.gurch.sandbox.audit.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Utility for computing before/after JSON diffs used by audit logging. */
public final class JsonDiffUtils {

  private JsonDiffUtils() {}

  /**
   * Computes a structural diff between two JSON values.
   *
   * <p>Returns {@code null} when values are equivalent. For object values, only changed fields are
   * retained in the returned pair.
   */
  public static DiffPair diff(JsonNode before, JsonNode after) {
    if (Objects.equals(before, after)) {
      return null;
    }
    if (before == null || after == null) {
      return new DiffPair(before, after);
    }
    if (before.isObject() && after.isObject()) {
      ObjectNode beforeDiff = JsonNodeFactory.instance.objectNode();
      ObjectNode afterDiff = JsonNodeFactory.instance.objectNode();
      Set<String> fields = collectFieldNames(before, after);
      for (String field : fields) {
        DiffPair nested = diff(before.get(field), after.get(field));
        if (nested == null) {
          continue;
        }
        beforeDiff.set(field, nested.before());
        afterDiff.set(field, nested.after());
      }
      if (beforeDiff.isEmpty()) {
        return null;
      }
      return new DiffPair(beforeDiff, afterDiff);
    }
    return new DiffPair(before, after);
  }

  private static Set<String> collectFieldNames(JsonNode before, JsonNode after) {
    Set<String> names = new LinkedHashSet<>();
    Iterator<String> beforeIterator = before.fieldNames();
    while (beforeIterator.hasNext()) {
      names.add(beforeIterator.next());
    }
    Iterator<String> afterIterator = after.fieldNames();
    while (afterIterator.hasNext()) {
      names.add(afterIterator.next());
    }
    return names;
  }

  /**
   * Holds the before/after projections for changed fields.
   *
   * @param before diff projection from the previous state
   * @param after diff projection from the current state
   */
  public record DiffPair(JsonNode before, JsonNode after) {}
}
