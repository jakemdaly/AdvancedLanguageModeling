package edu.berkeley.nlp.assignments.assign1;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.assignments.assign1.student.LmFactory;
import edu.berkeley.nlp.io.SentenceCollection;
import edu.berkeley.nlp.langmodel.EmpiricalUnigramLanguageModel.EmpiricalUnigramLanguageModelFactory;
import edu.berkeley.nlp.langmodel.EnglishWordIndexer;
import edu.berkeley.nlp.langmodel.LanguageModelFactory;
import edu.berkeley.nlp.langmodel.NgramLanguageModel;
import edu.berkeley.nlp.langmodel.StubLanguageModel.StubLanguageModelFactory;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.mt.BleuScore;
import edu.berkeley.nlp.mt.Weights;
import edu.berkeley.nlp.mt.decoder.Decoder;
import edu.berkeley.nlp.mt.decoder.Logger;
import edu.berkeley.nlp.mt.decoder.internal.BeamDecoder;
import edu.berkeley.nlp.mt.phrasetable.PhraseTable;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.CommandLineUtils;
import edu.berkeley.nlp.util.MemoryUsageUtils;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.StrUtils;
import edu.berkeley.nlp.util.StringIndexer;

/**
 * This is the main harness for assignment 1. To run this harness, use
 * <p/>
 * java edu.berkeley.nlp.assignments.assign1.LanguageModelTester -path
 * ASSIGNMENT_DATA_PATH -lmType LM_TYPE_DESCRIPTOR_STRING
 * <p/>
 * First verify that the data can be read on your system. Second, find the point
 * in the main method (near the bottom) where an EmpiricalUnigramLanguageModel
 * is constructed. You will be writing new implementations of the LanguageModel
 * interface and constructing them there.
 * 
 * @author Adam Pauls
 */
public class LanguageModelTester
{

	enum LmType
	{
		STUB
		{
			@Override
			public LanguageModelFactory getFactory() {
				return new StubLanguageModelFactory();
			}
		},
		UNIGRAM
		{
			@Override
			public LanguageModelFactory getFactory() {
				return new EmpiricalUnigramLanguageModelFactory();
			}
		},
		TRIGRAM
		{
			@Override
			public LanguageModelFactory getFactory() {
				return new LmFactory();
			}
		};

		public abstract LanguageModelFactory getFactory();
	}

	public static void main(String[] args) {
		// Parse command line flags and arguments
		Map<String, String> argMap = CommandLineUtils.simpleCommandLineParser(args);

		// Set up default parameters and settings
		String basePath = ".";
		LmType lmType = LmType.UNIGRAM;
		// You can use this to make decoding runs run in less time, but remember that we will
		// evaluate you on all test sentences.
		int maxNumTest = Integer.MAX_VALUE;
		boolean sanityCheck = false;
		boolean printTranslations = true;

		// Update defaults using command line specifications

		// The path to the assignment data
		if (argMap.containsKey("-path")) {
			basePath = argMap.get("-path");
		}
		System.out.println("Using base path: " + basePath);

		// A string descriptor of the model to use
		if (argMap.containsKey("-lmType")) {
			lmType = LmType.valueOf(argMap.get("-lmType"));
		}
		System.out.println("Using lmType: " + lmType);

		if (argMap.containsKey("-maxNumTest")) {
			maxNumTest = Integer.parseInt(argMap.get("-maxNumTest"));
		}
		System.out.println("Decoding " + (maxNumTest == Integer.MAX_VALUE ? "all" : ("" + maxNumTest)) + " sentences.");

		if (argMap.containsKey("-noprint")) {
			printTranslations = false;
		}

		if (argMap.containsKey("-sanityCheck")) {
			sanityCheck = true;
		}
		if (sanityCheck) System.out.println("Only doing sanity check.");

		String prefix = sanityCheck ? "sanity_" : "";

		// Read in all the assignment data
		File trainingSentencesFile = new File(basePath, prefix + "training.en.gz");
//		File trainingSentencesFile = new File("/home/jakemdaly/Downloads/code1/code1/src/edu/berkeley/nlp/assignments/assign1/student/LRRH.txt");
//		File trainingSentencesFile = new File("/home/jakemdaly/IdeaProjects/KNTrigram/src/code1/src/edu/berkeley/nlp/assignments/assign1/student/test_sentence.txt");
		File phraseTableFile = new File(basePath, prefix + "phrasetable.txt.gz");

		File testFrench = new File(basePath, prefix + "test.fr");
		File testEnglish = new File(basePath, prefix + "test.en");
		File weightsFile = new File(basePath, "weights.txt");
		Iterable<List<String>> trainingSentenceCollection = SentenceCollection.Reader.readSentenceCollection(trainingSentencesFile.getPath());

		// Build the language model
		LanguageModelFactory languageModelFactory = lmType.getFactory();
		NgramLanguageModel languageModel = languageModelFactory.newLanguageModel(trainingSentenceCollection);
		
//		if (lmType == LmType.TRIGRAM) {
//	    spotCheckLanguageModel(languageModel, sanityCheck);
//		}
		evaluateLanguageModel(phraseTableFile, testFrench, testEnglish, weightsFile, languageModel, maxNumTest, printTranslations);
	}
	
	private static void spotCheckLanguageModel(NgramLanguageModel languageModel, boolean sanityCheck) {
	  System.out.println("Performing spot checks...");
	  if (!sanityCheck) {
	    spotCheckCount(languageModel, new String[] { "the" }, 19880264L);
	    spotCheckCount(languageModel, new String[] { "in", "terms", "of" }, 31257L);
      spotCheckCount(languageModel, new String[] { "romanian", "independent", "society" }, 30L);
      spotCheckCount(languageModel, new String[] { "XXXtotally", "XXXunseen", "XXXtrigram" }, 0L);

	  }
    spotCheckContextNormalizes(languageModel, new String[] { "in", "terms" });
    spotCheckContextNormalizes(languageModel, new String[] { "romanian", "independent" });
    spotCheckContextNormalizes(languageModel, new String[] { "the" });

    System.out.println("Spot checks completed");
	}
	
	private static void spotCheckCount(NgramLanguageModel languageModel, String[] arr, long expectedCount) {
    long count = languageModel.getCount(index(arr));
    if (count != expectedCount) {
      System.out.println("ERROR: Count does not match expected count " + count + " != " + expectedCount + " for " + Arrays.toString(arr));
    } else {
      System.out.println("Count matches expected count " + count + " = " + expectedCount + " for " + Arrays.toString(arr));
    }
	}
  
  private static void spotCheckContextNormalizes(NgramLanguageModel languageModel, String[] rawContext) {
    StringIndexer indexer = EnglishWordIndexer.getIndexer();
    int[] context = index(rawContext);
    int[] ngram = new int[context.length + 1];
    for (int i = 0; i < context.length; i++) {
      ngram[i] = context[i];
    }
    double totalLogProb = Double.NEGATIVE_INFINITY;
    for (int wordIdx = 0; wordIdx < indexer.size(); wordIdx++) {
      // Don't include the START symbol since it is only observed in contexts
      if (wordIdx != indexer.indexOf(NgramLanguageModel.START)) {
        ngram[ngram.length - 1] = wordIdx;
        totalLogProb = SloppyMath.logAdd(totalLogProb, languageModel.getNgramLogProbability(ngram, 0, ngram.length));
      }
    }
    if (Math.abs(totalLogProb) > 0.001) {
      System.out.println("WARNING: Distribution for context " + Arrays.toString(rawContext) + " does not normalize correctly, sums to " + Math.exp(totalLogProb));
    } else {
      System.out.println("Distribution for context " + Arrays.toString(rawContext) + " normalizes correctly, sums to " + Math.exp(totalLogProb));
    }
  }
	
	private static int[] index(String[] arr) {
	  int[] indexedArr = new int[arr.length];
	  for (int i = 0; i < indexedArr.length; i++) {
	    indexedArr[i] = EnglishWordIndexer.getIndexer().addAndGetIndex(arr[i]);
	  }
	  return indexedArr;
	}

	/**
	 * @param useTestSet
	 * @param phraseTableFile
	 * @param devFrench
	 * @param devEnglish
	 * @param testFrench
	 * @param testEnglish
	 * @param weightsFile
	 * @param languageModel
	 */
	private static void evaluateLanguageModel(File phraseTableFile, File testFrench, File testEnglish, File weightsFile, NgramLanguageModel languageModel,
		int maxNumTest, boolean printTranslations) {
		PhraseTable phraseTable = new PhraseTable(5, 30);
		phraseTable.readFromFile(phraseTableFile.getPath(), Weights.readWeightsFile(weightsFile));

		Decoder decoder = new BeamDecoder(languageModel, phraseTable, EnglishWordIndexer.getIndexer());
		MemoryUsageUtils.printMemoryUsage();
		final String frenchData = (testFrench).getPath();
		Iterable<List<String>> frenchSentences = SentenceCollection.Reader.readSentenceCollection(frenchData);
		final String englishData = (testEnglish).getPath();
		Iterable<List<String>> englishSentences = SentenceCollection.Reader.readSentenceCollection(englishData);
		List<BleuScore> scores = new ArrayList<BleuScore>();
		doDecoding(decoder, frenchSentences, englishSentences, scores, maxNumTest, printTranslations);
		String bleuString = new BleuScore(scores).toString();
		System.out.println("BLEU score on " + ("test") + " data was " + bleuString);

	}

	/**
	 * @param decoder
	 * @param frenchSentences
	 * @param englishSentences
	 * @param scores
	 */
	private static void doDecoding(Decoder decoder, Iterable<List<String>> frenchSentences, Iterable<List<String>> englishSentences, List<BleuScore> scores,
		int maxNumTest, boolean printTranslations) {
		long startTime = System.nanoTime();
		int sent = 0;
		System.out.println("Decoding " + (maxNumTest == Integer.MAX_VALUE ? "all" : ("" + maxNumTest)) + " test sentences");
		for (Pair<List<String>, List<String>> input : CollectionUtils.zip(Pair.makePair(frenchSentences, englishSentences))) {
			if (sent >= maxNumTest) break;
			sent++;

			if (sent % 100 == 0) Logger.logs("On sentence " + sent);
			List<String> hypothesis = Decoder.StaticMethods.extractEnglish(decoder.decode(input.getFirst()));
			List<String> reference = input.getSecond();
			if (printTranslations) {
				System.out.println("Input:\t\t" + StrUtils.join(input.getFirst()));
				System.out.println("Hypothesis\t" + StrUtils.join(hypothesis));
				System.out.println("Reference:\t" + StrUtils.join(reference));
			}
			BleuScore bleuScore = new BleuScore(hypothesis, reference);
			scores.add(bleuScore);
		}
		long endTime = System.nanoTime();

		System.out.println("Decoding took " + BleuScore.formatDouble((endTime - startTime) / 1e9) + "s");
	}

}
