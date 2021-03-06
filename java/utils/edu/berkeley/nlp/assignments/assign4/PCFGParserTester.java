package edu.berkeley.nlp.assignments.assign4;

import edu.berkeley.nlp.assignments.assign4.solutions.ChartParser;
import edu.berkeley.nlp.assignments.assign4.student.DiscriminativeParserFactory;
import edu.berkeley.nlp.assignments.assign4.student.AwesomeParserFactory;
import edu.berkeley.nlp.assignments.assign4.student.GenerativeParserFactory;
import edu.berkeley.nlp.io.PennTreebankReader;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.ling.Trees;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.util.*;

import java.util.*;

/**
 * Harness for PCFG Parser project.
 * 
 * @author Dan Klein
 */
public class PCFGParserTester
{

	public enum ParserType
	{
		BASELINE
		{
			@Override
			public ParserFactory getParserFactory() {
				return new BaselineParser.BaselineParserFactory();
			}
		},
		GENERATIVE
		{
			@Override
			public ParserFactory getParserFactory() {
				return new GenerativeParserFactory();
			}
		},
		DISCRIMINATIVE
		{
			@Override
			public ParserFactory getParserFactory() {
				return new DiscriminativeParserFactory();
			}
		},
		AWESOME
		{
			@Override
			public ParserFactory getParserFactory() {
				return new AwesomeParserFactory();
			}
		};

		public abstract ParserFactory getParserFactory();
	}

	public static void main(String[] args) {
		// Parse command line flags and arguments
		Map<String, String> argMap = CommandLineUtils.simpleCommandLineParser(args);

		// Set up default parameters and settings
		String basePath = ".";
		boolean verbose = true;
		boolean sanity = false;
		String testMode = "validate";
		int maxTrainLength = 1000;
		int maxTestLength = 40;

		if (argMap.containsKey("-sanityCheck")) {
			sanity = true;
		}

		// Update defaults using command line specifications
		if (argMap.containsKey("-path")) {
			basePath = argMap.get("-path");
			System.out.println("Using base path: " + basePath);
		}
		if (argMap.containsKey("-test")) {
			testMode = "test";
			System.out.println("Testing on final test data.");
		} else {
			System.out.println("Testing on validation data.");
		}
		if (argMap.containsKey("-maxTrainLength")) {
			maxTrainLength = Integer.parseInt(argMap.get("-maxTrainLength"));
		}
		System.out.println("Maximum length for training sentences: " + maxTrainLength);
		if (argMap.containsKey("-maxTestLength")) {
			maxTestLength = Integer.parseInt(argMap.get("-maxTestLength"));
		}
		System.out.println("Maximum length for test sentences: " + maxTestLength);
		if (argMap.containsKey("-verbose")) {
			verbose = true;
		}
		if (argMap.containsKey("-quiet")) {
			verbose = false;
		}
		ParserType parserType = ParserType.BASELINE;

		if (argMap.containsKey("-parserType")) {
			parserType = ParserType.valueOf(argMap.get("-parserType"));
		}

		int trainTreesEnd = 2199;
		if (sanity) {
			maxTrainLength = 3;
			maxTestLength = 3;
			trainTreesEnd = 299;
		}
		System.out.print("Loading training trees (sections 2-21) ... ");
		List<Tree<String>> trainTrees = readTrees(basePath, 200, trainTreesEnd, maxTrainLength);
		System.out.println("done. (" + trainTrees.size() + " trees)");
		List<Tree<String>> testTrees = null;
		if (testMode.equalsIgnoreCase("validate")) {
			System.out.print("Loading validation trees (section 22) ... ");
			testTrees = readTrees(basePath, 2200, 2299, maxTestLength);
		} else {
			System.out.print("Loading test trees (section 23) ... ");
			testTrees = readTrees(basePath, 2300, 2399, maxTestLength);
		}
		System.out.println("done. (" + testTrees.size() + " trees)");

		Indexer<FeatureIndexable> featureIndexer = new Indexer<FeatureIndexable>();
		Weights weights = new Weights();
		featureIndexer.addObserver(weights); // the weights grow as objects are added to the featureIndexer
		ParserFactory factory = parserType.getParserFactory();
		Parser parser = factory.getParser(trainTrees, featureIndexer, weights);

		testParser(parser, testTrees, verbose);
	}

	private static void testParser(Parser parser, List<Tree<String>> testTrees, boolean verbose) {
		EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(
			Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] { "''", "``", ".", ":", "," })));
		for (Tree<String> testTree : testTrees) {
			List<String> testSentence = testTree.getYield();
			Tree<String> guessedTree = parser.getBestParse(testSentence);
			if (verbose) {
				System.out.println("Guess:\n" + Trees.PennTreeRenderer.render(guessedTree));
				System.out.println("Gold:\n" + Trees.PennTreeRenderer.render(testTree));
			}
			eval.evaluate(guessedTree, testTree);
		}
		eval.display(true);
	}

	private static List<Tree<String>> readTrees(String basePath, int low, int high, int maxLength) {
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath, low, high);
		// normalize trees
		Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
		List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			if (normalizedTree.getYield().size() > maxLength) continue;
			//      System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
			normalizedTreeList.add(normalizedTree);
		}
		return normalizedTreeList;
	}
}
