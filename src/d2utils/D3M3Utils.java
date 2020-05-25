package d2utils;

import java.util.ArrayList;
import java.util.List;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

public class D3M3Utils {

	private static final String OVER_SAMPLING = "Over sampling";
	private static final String UNDER_SAMPLING = "Under sampling";
	private static final String SMOTE = "Smote";
	private static final String NO_SAMPLING = "No sampling";
	
	public static List<String> applyFeatureSelection(Instances training, Instances testing, double percentageMajorityClass) throws Exception{

		AttributeSelection filter = new AttributeSelection();
		CfsSubsetEval eval = new CfsSubsetEval();
		GreedyStepwise search = new GreedyStepwise();

		search.setSearchBackwards(true);

		filter.setEvaluator(eval);
		filter.setSearch(search);
		filter.setInputFormat(training);

		Instances filteredTraining =  Filter.useFilter(training, filter);
		Instances testingFiltered = Filter.useFilter(testing, filter);

		int numAttrFiltered = filteredTraining.numAttributes();
		filteredTraining.setClassIndex(numAttrFiltered - 1);
		testingFiltered.setClassIndex(numAttrFiltered - 1);

		return applySampling(filteredTraining, testingFiltered, percentageMajorityClass, "True");

	}

	public static List<String> applySampling(Instances training, Instances testing, double percentageMajorityClass, String featureSelection) throws Exception {

		ArrayList<String> result = new ArrayList<>();

		IBk classifierIBk = new IBk();
		RandomForest classifierRF = new RandomForest();
		NaiveBayes classifierNB = new NaiveBayes();

		int numAttrNoFilter = training.numAttributes();
		training.setClassIndex(numAttrNoFilter - 1);
		testing.setClassIndex(numAttrNoFilter - 1);

		// Build the classifier
		classifierNB.buildClassifier(training);
		classifierRF.buildClassifier(training);
		classifierIBk.buildClassifier(training);

		// Get an evaluation object
		Evaluation eval = new Evaluation(training);	

		eval.evaluateModel(classifierRF, testing);
		result.add(getMetrics(eval,"RF", NO_SAMPLING, featureSelection));

		eval.evaluateModel(classifierIBk, testing);
		result.add(getMetrics(eval,"IBk", NO_SAMPLING, featureSelection));

		eval.evaluateModel(classifierNB, testing);
		result.add(getMetrics(eval,"NB", NO_SAMPLING, featureSelection));

		// Sampling


		FilteredClassifier fc = new FilteredClassifier();

		SpreadSubsample  spreadSubsample = new SpreadSubsample();
		spreadSubsample.setInputFormat(training);
		String[] opts = new String[]{ "-M", "1.0"};
		spreadSubsample.setOptions(opts);
		fc.setFilter(spreadSubsample);

		fc.setClassifier(classifierRF);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"RF", UNDER_SAMPLING, featureSelection));

		fc.setClassifier(classifierIBk);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"IBk", UNDER_SAMPLING, featureSelection));

		fc.setClassifier(classifierNB);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"NB", UNDER_SAMPLING, featureSelection));

		Resample  spreadOverSample = new Resample();
		spreadOverSample.setInputFormat(training);
		String[] optsOverSampling = new String[]{"-B", "1.0", "-Z", String.valueOf(2*percentageMajorityClass*100)};
		spreadOverSample.setOptions(optsOverSampling);
		fc.setFilter(spreadOverSample);

		fc.setClassifier(classifierRF);
		fc.buildClassifier(training);

		eval = new Evaluation(testing);	
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"RF",OVER_SAMPLING , featureSelection));

		fc.setClassifier(classifierIBk);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"IBk", OVER_SAMPLING, featureSelection));

		fc.setClassifier(classifierNB);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"NB", OVER_SAMPLING, featureSelection));

		SMOTE smote = new SMOTE();
		smote.setInputFormat(training);
		fc.setFilter(smote);

		fc.setClassifier(classifierRF);
		fc.buildClassifier(training);
		eval = new Evaluation(testing);	
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"RF", SMOTE, featureSelection));

		fc.setClassifier(classifierIBk);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"IBk", SMOTE, featureSelection));

		fc.setClassifier(classifierNB);
		fc.buildClassifier(training);
		eval.evaluateModel(fc, testing);
		result.add(getMetrics(eval,"NB", SMOTE, featureSelection));

		return result;

	}

	public static String getMetrics(Evaluation eval, String classifier, String balancing, String featureSelection) {

		return classifier + "," + balancing + "," + featureSelection + "," + eval.truePositiveRate(1)  + "," + eval.falsePositiveRate(1)  + "," + eval.trueNegativeRate(1)  + "," + eval.falseNegativeRate(1)  + "," + eval.precision(1)  + "," + eval.recall(1)  + "," + eval.areaUnderROC(1)  + "," + eval.kappa() + "\n";
	}


}
