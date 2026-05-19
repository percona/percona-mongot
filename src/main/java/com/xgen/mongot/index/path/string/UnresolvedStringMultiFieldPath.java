package com.xgen.mongot.index.path.string;

import com.xgen.mongot.util.FieldPath;
import java.util.Objects;

public final class UnresolvedStringMultiFieldPath extends UnresolvedStringPath {
  private final FieldPath path;
  private final String multi;

  public UnresolvedStringMultiFieldPath(FieldPath path, String multi) {
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
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof UnresolvedStringMultiFieldPath otherMultiFieldPath)) {
      return false;
    }
    return Objects.equals(this.path, otherMultiFieldPath.getFieldPath())
        && Objects.equals(this.multi, otherMultiFieldPath.multi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.path, this.multi);
  }
}
