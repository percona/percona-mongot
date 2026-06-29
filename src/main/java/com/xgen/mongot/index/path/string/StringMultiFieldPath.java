package com.xgen.mongot.index.path.string;

import com.xgen.mongot.util.FieldPath;
import java.util.Objects;

public class StringMultiFieldPath extends StringPath {

  private final FieldPath path;
  private final String multi;

  public StringMultiFieldPath(FieldPath path, String multi) {
    this.path = path;
    this.multi = multi;
  }

  public FieldPath getFieldPath() {
    return this.path;
  }

  public String getMulti() {
    return this.multi;
  }

  @Override
  public Type getType() {
    return Type.MULTI_FIELD;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof StringMultiFieldPath otherMultiFieldPath)) {
      return false;
    }
    return Objects.equals(this.path, otherMultiFieldPath.getFieldPath())
        && Objects.equals(this.multi, otherMultiFieldPath.multi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.path, this.multi);
  }

  @Override
  public String toString() {
    return String.format("%s (multi: %s)", this.path.toString(), this.multi);
  }
}
