package com.xgen.mongot.index.lucene.analyzer;

import com.xgen.mongot.index.analyzer.custom.SnowballStemmingTokenFilterDefinition;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.ArabicStemmer;
import org.tartarus.snowball.ext.ArmenianStemmer;
import org.tartarus.snowball.ext.BasqueStemmer;
import org.tartarus.snowball.ext.CatalanStemmer;
import org.tartarus.snowball.ext.DanishStemmer;
import org.tartarus.snowball.ext.DutchStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.EstonianStemmer;
import org.tartarus.snowball.ext.FinnishStemmer;
import org.tartarus.snowball.ext.FrenchStemmer;
import org.tartarus.snowball.ext.GermanStemmer;
import org.tartarus.snowball.ext.HungarianStemmer;
import org.tartarus.snowball.ext.IrishStemmer;
import org.tartarus.snowball.ext.ItalianStemmer;
import org.tartarus.snowball.ext.LithuanianStemmer;
import org.tartarus.snowball.ext.NorwegianStemmer;
import org.tartarus.snowball.ext.PorterStemmer;
import org.tartarus.snowball.ext.PortugueseStemmer;
import org.tartarus.snowball.ext.RomanianStemmer;
import org.tartarus.snowball.ext.RussianStemmer;
import org.tartarus.snowball.ext.SpanishStemmer;
import org.tartarus.snowball.ext.SwedishStemmer;
import org.tartarus.snowball.ext.TurkishStemmer;

public class SnowballTokenFilterFactory {
  private static final Map<
          SnowballStemmingTokenFilterDefinition.StemmerName, Supplier<SnowballStemmer>>
      SNOWBALL_STEMMER_SUPPLIER =
          Map.ofEntries(
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.ARABIC, ArabicStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.ARMENIAN, ArmenianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.BASQUE, BasqueStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.CATALAN, CatalanStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.DANISH, DanishStemmer::new),
              Map.entry(SnowballStemmingTokenFilterDefinition.StemmerName.DUTCH, DutchStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.ENGLISH, EnglishStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.ESTONIAN, EstonianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.FINNISH, FinnishStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.FRENCH, FrenchStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.GERMAN, GermanStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.GERMAN2, GermanStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.HUNGARIAN,
                  HungarianStemmer::new),
              Map.entry(SnowballStemmingTokenFilterDefinition.StemmerName.IRISH, IrishStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.ITALIAN, ItalianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.LITHUANIAN,
                  LithuanianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.NORWEGIAN,
                  NorwegianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.PORTER, PorterStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.PORTUGUESE,
                  PortugueseStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.ROMANIAN, RomanianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.RUSSIAN, RussianStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.SPANISH, SpanishStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.SWEDISH, SwedishStemmer::new),
              Map.entry(
                  SnowballStemmingTokenFilterDefinition.StemmerName.TURKISH, TurkishStemmer::new));

  static SnowballFilter build(SnowballStemmingTokenFilterDefinition definition, TokenStream input) {
    return new SnowballFilter(input, SNOWBALL_STEMMER_SUPPLIER.get(definition.stemmerName).get());
  }
}
