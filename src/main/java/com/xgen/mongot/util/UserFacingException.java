package com.xgen.mongot.util;

import java.util.Set;
import org.apache.lucene.search.FuzzyTermsEnum.FuzzyTermsException;
import org.apache.lucene.search.IndexSearcher.TooManyClauses;

/**
 * UserFacingException is an interface tag for exception types where the contents of getMessage()
 * can be rendered directly to users.
 */
public interface UserFacingException {
  Set<Class<? extends Throwable>> KNOWN_USER_FACING =
      Set.of(
          FuzzyTermsException.class,
          TooManyClauses.class,
          IllegalArgumentException.class,
          ClassCastException.class,
          NumberFormatException.class);

  static boolean isUserFacing(Throwable t) {
    // Check for marker interface first
    if (t instanceof UserFacingException) {
      return true;
    }
    // Check if it's a known Lucene or core Java user-facing exception
    for (Class<? extends Throwable> cls : KNOWN_USER_FACING) {
      if (cls.isAssignableFrom(t.getClass())) {
        return true;
      }
    }
    return false;
  }
}
