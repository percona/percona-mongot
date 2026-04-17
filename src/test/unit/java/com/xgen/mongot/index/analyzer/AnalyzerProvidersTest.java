package com.xgen.mongot.index.analyzer;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamType;
import com.xgen.mongot.index.analyzer.definition.OverriddenBaseAnalyzerDefinition;
import com.xgen.mongot.index.analyzer.definition.StockAnalyzerNames;
import com.xgen.testing.mongot.index.analyzer.definition.OverriddenBaseAnalyzerDefinitionBuilder;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.bn.BengaliAnalyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.ca.CatalanAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.ckb.SoraniAnalyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.eu.BasqueAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ga.IrishAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.hy.ArmenianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.lt.LithuanianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;
import org.apache.lucene.analysis.uk.UkrainianMorfologikAnalyzer;
import org.junit.Assert;
import org.junit.Test;

public class AnalyzerProvidersTest {

  public static class ProviderTest<A extends Analyzer, P extends AnalyzerProvider.OverriddenBase> {

    /**
     * Run the provider test. Checks for the ability to instantiate the correct Analyzer and with
     * all available options *
     */
    public void run(
        Class<A> analyzerClass, P provider, String stockName, TokenStreamType expectedType)
        throws Exception {
      testNoOptions(analyzerClass, provider, stockName);
      testMaxToken(analyzerClass, provider, stockName);
      testStopWords(analyzerClass, provider, stockName);
      testStemExclusion(analyzerClass, provider, stockName);
      testTokenStreamType(provider, expectedType);
    }

    private void testNoOptions(Class<A> analyzerClass, P provider, String stockName)
        throws Exception {
      // Hacky, but StopAnalyzer requires stop words to be provided, so skip it for the no options
      // test.
      if (analyzerClass.equals(StopAnalyzer.class)) {
        return;
      }

      OverriddenBaseAnalyzerDefinition noOptionsDefinition =
          OverriddenBaseAnalyzerDefinitionBuilder.builder()
              .name("noOptions")
              .baseAnalyzerName(stockName)
              .build();

      // Check register works.
      provider.getAnalyzer(noOptionsDefinition);
    }

    private void testMaxToken(Class<A> analyzerClass, P provider, String stockName)
        throws Exception {
      OverriddenBaseAnalyzerDefinition maxTokenDefinition =
          OverriddenBaseAnalyzerDefinitionBuilder.builder()
              .name("maxToken")
              .baseAnalyzerName(stockName)
              .maxTokenLength(48)
              .build();

      if (supportsMaxTokenLength(analyzerClass)) {
        Analyzer analyzer = provider.getAnalyzer(maxTokenDefinition);
        Assert.assertEquals(48, ((StandardAnalyzer) analyzer).getMaxTokenLength());
      } else {
        try {
          provider.getAnalyzer(maxTokenDefinition);
          Assert.fail(
              String.format(
                  "%s should have thrown with invalid MaxTokenLength arg",
                  analyzerClass.getName()));
        } catch (InvalidAnalyzerDefinitionException e) {
          // expected
        }
      }
    }

    private void testStopWords(Class<A> analyzerClass, P provider, String stockName)
        throws Exception {
      OverriddenBaseAnalyzerDefinition stopwordsDefinition =
          OverriddenBaseAnalyzerDefinitionBuilder.builder()
              .name("stopAnalyzer")
              .baseAnalyzerName(stockName)
              .stopword("one")
              .stopword("two")
              .build();

      boolean isDutchAnalyzer = analyzerClass.equals(DutchAnalyzer.class);
      if (extendsStopwordAnalyzer(analyzerClass) || isDutchAnalyzer) {
        Analyzer analyzer = provider.getAnalyzer(stopwordsDefinition);

        // For some reason, DutchAnalyzer support stopwords but does not subclass StopwordAnalyzer,
        // so just ensure that we were able to register it without checking the stopwords were
        // properly set.
        if (isDutchAnalyzer) {
          return;
        }

        Assert.assertTrue(((StopwordAnalyzerBase) analyzer).getStopwordSet().contains("one"));
        Assert.assertTrue(((StopwordAnalyzerBase) analyzer).getStopwordSet().contains("two"));
      } else {
        try {
          provider.getAnalyzer(stopwordsDefinition);
          Assert.fail(
              String.format(
                  "%s should have thrown with invalid stopwords arg", analyzerClass.getName()));
        } catch (InvalidAnalyzerDefinitionException e) {
          // expected
        }
      }
    }

    private void testStemExclusion(Class<A> analyzerClass, P provider, String stockName)
        throws Exception {
      OverriddenBaseAnalyzerDefinition stemDefinition =
          OverriddenBaseAnalyzerDefinitionBuilder.builder()
              .name("stemExclusion")
              .baseAnalyzerName(stockName)
              .stopword("one")
              .stopword("two")
              .excludeStem("exclude")
              .excludeStem("me")
              .build();

      if (supportsStemExclusion(analyzerClass)) {
        provider.getAnalyzer(stemDefinition);

        // Can't do a more thorough generic StemExclusionSet test through reflection because
        // the way that Analyzers name and handle StemExclusionSet varies considerably.
        // What we can know is that registering with StemExclusionSet arguments does not throw.
      } else {
        try {
          provider.getAnalyzer(stemDefinition);
          Assert.fail(
              String.format(
                  "%s should have thrown with invalid stemExclusionSet arg",
                  analyzerClass.getName()));
        } catch (InvalidAnalyzerDefinitionException e) {
          // expected
        }
      }
    }

    private void testTokenStreamType(P provider, TokenStreamType expected) {
      Assert.assertEquals(
          "provider produced tokens of unexpected type,", expected, provider.getTokenStreamType());
    }

    private boolean supportsMaxTokenLength(Class<A> analyzerClass) {
      try {
        Class[] parameters = {int.class};
        analyzerClass.getMethod("setMaxTokenLength", parameters);
        return true;
      } catch (NoSuchMethodException e) {
        return false;
      }
    }

    private boolean extendsStopwordAnalyzer(Class<A> analyzerClass) {
      // do not want to support overridden functionality for newly added analyzers.
      if (analyzerClass.equals(UkrainianMorfologikAnalyzer.class)
          || analyzerClass.equals(JapaneseAnalyzer.class)) {
        return false;
      }

      return analyzerClass.getSuperclass().equals(StopwordAnalyzerBase.class);
    }

    private boolean supportsStemExclusion(Class<A> analyzerClass) {
      // do not want to support overridden functionality for newly added analyzers.
      Set<Class<? extends Analyzer>> unsupportedAnalyzers =
          Set.of(UkrainianMorfologikAnalyzer.class, PersianAnalyzer.class);
      if (unsupportedAnalyzers.contains(analyzerClass)) {
        return false;
      }

      try {
        analyzerClass.getConstructor(CharArraySet.class, CharArraySet.class);
        return true;
      } catch (NoSuchMethodException e) {
        return false;
      }
    }
  }

  @Test
  public void testLuceneAnalyzerProviders() throws Exception {
    new ProviderTest<KeywordAnalyzer, KeywordAnalyzerProvider>()
        .run(
            KeywordAnalyzer.class,
            new KeywordAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_KEYWORD.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<SimpleAnalyzer, SimpleAnalyzerProvider>()
        .run(
            SimpleAnalyzer.class,
            new SimpleAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_SIMPLE.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<StandardAnalyzer, StandardAnalyzerProvider>()
        .run(
            StandardAnalyzer.class,
            new StandardAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_STANDARD.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<StopAnalyzer, StopAnalyzerProvider>()
        .run(
            StopAnalyzer.class,
            new StopAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_STOP.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<WhitespaceAnalyzer, WhitespaceAnalyzerProvider>()
        .run(
            WhitespaceAnalyzer.class,
            new WhitespaceAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_WHITESPACE.getName(),
            TokenStreamType.STREAM);

    // Language analyzers.
    new ProviderTest<CJKAnalyzer, CjkAnalyzerProvider>()
        .run(
            CJKAnalyzer.class,
            new CjkAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_CJK.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<CJKAnalyzer, CjkAnalyzerProvider>()
        .run(
            CJKAnalyzer.class,
            new CjkAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_CHINESE.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<CJKAnalyzer, CjkAnalyzerProvider>()
        .run(
            CJKAnalyzer.class,
            new CjkAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_JAPANESE.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<CJKAnalyzer, CjkAnalyzerProvider>()
        .run(
            CJKAnalyzer.class,
            new CjkAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_KOREAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<GreekAnalyzer, GreekAnalyzerProvider>()
        .run(
            GreekAnalyzer.class,
            new GreekAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_GREEK.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<PersianAnalyzer, PersianAnalyzerProvider>()
        .run(
            PersianAnalyzer.class,
            new PersianAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_PERSIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<ThaiAnalyzer, ThaiAnalyzerProvider>()
        .run(
            ThaiAnalyzer.class,
            new ThaiAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_THAI.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<MorfologikAnalyzer, MorfologikAnalyzerProvider>()
        .run(
            MorfologikAnalyzer.class,
            new MorfologikAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_MORFOLOGIK.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<JapaneseAnalyzer, KuromojiAnalyzerProvider>()
        .run(
            JapaneseAnalyzer.class,
            new KuromojiAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_KUROMOJI.getName(),
            TokenStreamType.GRAPH);
    new ProviderTest<SmartChineseAnalyzer, SmartCnAnalyzerProvider>()
        .run(
            SmartChineseAnalyzer.class,
            new SmartCnAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_SMARTCN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<KoreanAnalyzer, NoriAnalyzerProvider>()
        .run(
            KoreanAnalyzer.class,
            new NoriAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_NORI.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<UkrainianMorfologikAnalyzer, UkrainianMorfologikAnalyzerProvider>()
        .run(
            UkrainianMorfologikAnalyzer.class,
            new UkrainianMorfologikAnalyzerProvider(),
            StockAnalyzerNames.LUCENE_UKRAINIAN.getName(),
            TokenStreamType.STREAM);

    new ProviderTest<ArabicAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            ArabicAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_ARABIC.getName(),
                ArabicAnalyzer::new,
                ArabicAnalyzer::new,
                ArabicAnalyzer::new),
            StockAnalyzerNames.LUCENE_ARABIC.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<ArmenianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            ArmenianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_ARMENIAN.getName(),
                ArmenianAnalyzer::new,
                ArmenianAnalyzer::new,
                ArmenianAnalyzer::new),
            StockAnalyzerNames.LUCENE_ARMENIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<BasqueAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            BasqueAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_BASQUE.getName(),
                BasqueAnalyzer::new,
                BasqueAnalyzer::new,
                BasqueAnalyzer::new),
            StockAnalyzerNames.LUCENE_BASQUE.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<BengaliAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            BengaliAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_BENGALI.getName(),
                BengaliAnalyzer::new,
                BengaliAnalyzer::new,
                BengaliAnalyzer::new),
            StockAnalyzerNames.LUCENE_BENGALI.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<BrazilianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            BrazilianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_BRAZILIAN.getName(),
                BrazilianAnalyzer::new,
                BrazilianAnalyzer::new,
                BrazilianAnalyzer::new),
            StockAnalyzerNames.LUCENE_BRAZILIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<BulgarianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            BulgarianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_BULGARIAN.getName(),
                BulgarianAnalyzer::new,
                BulgarianAnalyzer::new,
                BulgarianAnalyzer::new),
            StockAnalyzerNames.LUCENE_BULGARIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<CatalanAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            CatalanAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_CATALAN.getName(),
                CatalanAnalyzer::new,
                CatalanAnalyzer::new,
                CatalanAnalyzer::new),
            StockAnalyzerNames.LUCENE_CATALAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<CzechAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            CzechAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_CZECH.getName(),
                CzechAnalyzer::new,
                CzechAnalyzer::new,
                CzechAnalyzer::new),
            StockAnalyzerNames.LUCENE_CZECH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<DanishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            DanishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_DANISH.getName(),
                DanishAnalyzer::new,
                DanishAnalyzer::new,
                DanishAnalyzer::new),
            StockAnalyzerNames.LUCENE_DANISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<DutchAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            DutchAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_DUTCH.getName(),
                DutchAnalyzer::new,
                DutchAnalyzer::new,
                DutchAnalyzer::new),
            StockAnalyzerNames.LUCENE_DUTCH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<EnglishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            EnglishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_ENGLISH.getName(),
                EnglishAnalyzer::new,
                EnglishAnalyzer::new,
                EnglishAnalyzer::new),
            StockAnalyzerNames.LUCENE_ENGLISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<FinnishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            FinnishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_FINNISH.getName(),
                FinnishAnalyzer::new,
                FinnishAnalyzer::new,
                FinnishAnalyzer::new),
            StockAnalyzerNames.LUCENE_FINNISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<FrenchAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            FrenchAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_FRENCH.getName(),
                FrenchAnalyzer::new,
                FrenchAnalyzer::new,
                FrenchAnalyzer::new),
            StockAnalyzerNames.LUCENE_FRENCH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<GalicianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            GalicianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_GALICIAN.getName(),
                GalicianAnalyzer::new,
                GalicianAnalyzer::new,
                GalicianAnalyzer::new),
            StockAnalyzerNames.LUCENE_GALICIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<GermanAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            GermanAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_GERMAN.getName(),
                GermanAnalyzer::new,
                GermanAnalyzer::new,
                GermanAnalyzer::new),
            StockAnalyzerNames.LUCENE_GERMAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<HindiAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            HindiAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_HINDI.getName(),
                HindiAnalyzer::new,
                HindiAnalyzer::new,
                HindiAnalyzer::new),
            StockAnalyzerNames.LUCENE_HINDI.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<HungarianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            HungarianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_HUNGARIAN.getName(),
                HungarianAnalyzer::new,
                HungarianAnalyzer::new,
                HungarianAnalyzer::new),
            StockAnalyzerNames.LUCENE_HUNGARIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<IndonesianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            IndonesianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_INDONESIAN.getName(),
                IndonesianAnalyzer::new,
                IndonesianAnalyzer::new,
                IndonesianAnalyzer::new),
            StockAnalyzerNames.LUCENE_INDONESIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<IrishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            IrishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_IRISH.getName(),
                IrishAnalyzer::new,
                IrishAnalyzer::new,
                IrishAnalyzer::new),
            StockAnalyzerNames.LUCENE_IRISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<ItalianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            ItalianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_ITALIAN.getName(),
                ItalianAnalyzer::new,
                ItalianAnalyzer::new,
                ItalianAnalyzer::new),
            StockAnalyzerNames.LUCENE_ITALIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<LithuanianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            LithuanianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_LITHUANIAN.getName(),
                LithuanianAnalyzer::new,
                LithuanianAnalyzer::new,
                LithuanianAnalyzer::new),
            StockAnalyzerNames.LUCENE_LITHUANIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<LatvianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            LatvianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_LATVIAN.getName(),
                LatvianAnalyzer::new,
                LatvianAnalyzer::new,
                LatvianAnalyzer::new),
            StockAnalyzerNames.LUCENE_LATVIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<NorwegianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            NorwegianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_NORWEGIAN.getName(),
                NorwegianAnalyzer::new,
                NorwegianAnalyzer::new,
                NorwegianAnalyzer::new),
            StockAnalyzerNames.LUCENE_NORWEGIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<PolishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            PolishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_POLISH.getName(),
                PolishAnalyzer::new,
                PolishAnalyzer::new,
                PolishAnalyzer::new),
            StockAnalyzerNames.LUCENE_POLISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<PortugueseAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            PortugueseAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_PORTUGUESE.getName(),
                PortugueseAnalyzer::new,
                PortugueseAnalyzer::new,
                PortugueseAnalyzer::new),
            StockAnalyzerNames.LUCENE_PORTUGUESE.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<RomanianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            RomanianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_ROMANIAN.getName(),
                RomanianAnalyzer::new,
                RomanianAnalyzer::new,
                RomanianAnalyzer::new),
            StockAnalyzerNames.LUCENE_ROMANIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<RussianAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            RussianAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_RUSSIAN.getName(),
                RussianAnalyzer::new,
                RussianAnalyzer::new,
                RussianAnalyzer::new),
            StockAnalyzerNames.LUCENE_RUSSIAN.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<SoraniAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            SoraniAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_SORANI.getName(),
                SoraniAnalyzer::new,
                SoraniAnalyzer::new,
                SoraniAnalyzer::new),
            StockAnalyzerNames.LUCENE_SORANI.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<SpanishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            SpanishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_SPANISH.getName(),
                SpanishAnalyzer::new,
                SpanishAnalyzer::new,
                SpanishAnalyzer::new),
            StockAnalyzerNames.LUCENE_SPANISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<SwedishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            SwedishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_SWEDISH.getName(),
                SwedishAnalyzer::new,
                SwedishAnalyzer::new,
                SwedishAnalyzer::new),
            StockAnalyzerNames.LUCENE_SWEDISH.getName(),
            TokenStreamType.STREAM);
    new ProviderTest<TurkishAnalyzer, AnalyzerProvider.OverriddenBase>()
        .run(
            TurkishAnalyzer.class,
            StopWordBasedAnalyzerProviderFactory.create(
                StockAnalyzerNames.LUCENE_TURKISH.getName(),
                TurkishAnalyzer::new,
                TurkishAnalyzer::new,
                TurkishAnalyzer::new),
            StockAnalyzerNames.LUCENE_TURKISH.getName(),
            TokenStreamType.STREAM);
  }
}
