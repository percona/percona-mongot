package com.xgen.mongot.index.lucene.explain.information.creator;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.index.lucene.explain.information.TermInSetQuerySpec;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.FieldPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.util.BytesRef;

public class TermInSetQuerySpecCreator {
  static TermInSetQuerySpec fromQuery(TermInSetQuery q) {
    return new TermInSetQuerySpec(
        FieldPath.parse(FieldName.stripAnyPrefixFromLuceneFieldName(q.getField())),
        getTermListFromQuery(q));
  }

  private static List<String> getTermListFromQuery(TermInSetQuery q) {
    List<String> termList = new ArrayList<>();
    try {
      var termIter = q.getBytesRefIterator();
      @Var BytesRef term = termIter.next();
      while (term != null) {
        termList.add(Term.toString(term));
        term = termIter.next();
      }
    } catch (IOException e) {
      Check.unreachable("IOException from in-memory TermInSetQuery iterator");
    }
    return termList;
  }
}
