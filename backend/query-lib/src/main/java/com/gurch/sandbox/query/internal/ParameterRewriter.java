package com.gurch.sandbox.query.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParameterRewriter {
  private static final Pattern PARAM_PLACEHOLDER_PATTERN =
      Pattern.compile(":([a-zA-Z][a-zA-Z0-9_]*)");

  private ParameterRewriter() {
    // Utility class.
  }

  public static String rewrite(
      String sqlFragment, Map<String, Object> sourceParams, Map<String, Object> targetParams) {
    Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(sqlFragment);
    StringBuffer rewritten = new StringBuffer();
    Map<String, String> replacements = new LinkedHashMap<>();
    while (matcher.find()) {
      String sourceName = matcher.group(1);
      String targetName = replacements.get(sourceName);
      if (targetName == null) {
        targetName = nextParamName(targetParams);
        replacements.put(sourceName, targetName);
        targetParams.put(targetName, sourceParams.get(sourceName));
      }
      matcher.appendReplacement(rewritten, ":" + targetName);
    }
    matcher.appendTail(rewritten);
    return rewritten.toString();
  }

  private static String nextParamName(Map<String, Object> paramMap) {
    return "p" + (paramMap.size() + 1);
  }
}
