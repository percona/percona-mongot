package com.xgen.mongot.util.bson.parser;

import com.google.common.base.Strings;
import com.xgen.mongot.util.FieldPath;
import com.xgen.mongot.util.LoggableException;
import com.xgen.mongot.util.UserFacingException;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public class BsonParseException extends LoggableException implements UserFacingException {

  public BsonParseException(@Nullable String description, Optional<FieldPath> path) {
    this(description, path, null);
  }

  public BsonParseException(
      @Nullable String description, Optional<FieldPath> path, @Nullable Throwable cause) {
    super(getMessage(description, path), cause);
  }

  public BsonParseException(Throwable cause) {
    super(Objects.toString(cause), cause);
  }

  private static String getMessage(@Nullable String description, Optional<FieldPath> optionalPath) {
    if (optionalPath.isEmpty()) {
      return Strings.nullToEmpty(description);
    }

    FieldPath path = optionalPath.get();
    return String.format("\"%s\" %s", path, description);
  }
}
