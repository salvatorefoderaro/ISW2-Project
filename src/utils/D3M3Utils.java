package utils;

import java.util.ArrayList;
import java.util.List;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.AbstractClassifier;
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

	private D3M3Utils() {}

	private static final String OVER_SAMPLING = "Over sampling";
	private static final String UNDER_SAMPLING = "Under sampling";
	private static final String SMOTE = "Smote";
	private static final String NO_SAMPLING = "No sampling";
	private static final String CUSTOM_ERROR = "Unable to do this operation";

	public static List<String> applyFeatureSelection(Instances training, Instances testing, double percentageMajorityClass) throws Exception{

		AttributeSelection filter = new AttributeSelection();
		CfsSubsetEval eval = new CfsSubsetEval();
		GreedyStepwise search = new GreedyStepwise();

		search.setSearchBackwards(true);

		filter.setEvaluator(eval);
		filter.setSearch(search);
		filter.setInputFormat(training);

		try {
		Instances filteredTraining =  Filter.useFilter(training, filter);
		Instances testingFiltered = Filter.useFilter(testing, filter);
		int numAttrFiltered = filteredTraining.numAttributes();
		filteredTraining.setClassIndex(numAttrFiltered - 1);
		testingFiltered.setClassIndex(numAttrFiltered - 1);

		return applySampling(filteredTraining, testingFiltered, percentageMajorityClass, "True");
		} catch (Exception e) {
			throw new CustomException(CUSTOM_ERROR);

		}
		



	}
	
	public static Evaluation applyFilter(FilteredClassifier fc, Evaluation eval, Instances training, Instances testing, AbstractClassifier classifierName) throws Exception {
		
		if (fc != null) {
			fc.setClassifier(classifierName);
			fc.buildClassifier(training);
			eval.evaluateModel(fc, testing);

		} else {
			eval.evaluateModel(classifierName, testing);
		}
		return eval;
	}

	public static void addResult(Evaluation eval, List<String> result, String classifierAbb, String sampling, String featureSelection) {

		result.add(getMetrics(eval,classifierAbb, sampling, featureSelection));
		
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

		applyFilter(null, eval, training, testing, classifierRF);
		addResult(eval, result, "RF", NO_SAMPLING, featureSelection);
		
		applyFilter(null, eval, training, testing, classifierIBk);
		addResult(eval, result, "IBk", NO_SAMPLING, featureSelection);

		applyFilter(null, eval, training, testing, classifierNB);
		addResult(eval, result, "NB", NO_SAMPLING, featureSelection);

		// Sampling

		FilteredClassifier fc = new FilteredClassifier();

		SpreadSubsample  spreadSubsample = new SpreadSubsample();
		spreadSubsample.setInputFormat(training);
		String[] opts = new String[]{ "-M", "1.0"};
		spreadSubsample.setOptions(opts);
		fc.setFilter(spreadSubsample);

		applyFilter(null, eval, training, testing, classifierRF);
		addResult(eval, result, "RF", UNDER_SAMPLING, featureSelection);

		
		applyFilter(null, eval, training, testing, classifierIBk);
		addResult(eval, result, "IBk", UNDER_SAMPLING, featureSelection);

		applyFilter(null, eval, training, testing, classifierNB);
		addResult(eval, result, "NB", UNDER_SAMPLING, featureSelection);

		Resample  spreadOverSample = new Resample();
		spreadOverSample.setInputFormat(training);
		String[] optsOverSampling = new String[]{"-B", "1.0", "-Z", String.valueOf(2*percentageMajorityClass*100)};
		spreadOverSample.setOptions(optsOverSampling);
		fc.setFilter(spreadOverSample);

		eval = new Evaluation(testing);	

		applyFilter(null, eval, training, testing, classifierRF);
		addResult(eval, result, "RF", OVER_SAMPLING, featureSelection);

		applyFilter(null, eval, training, testing, classifierIBk);
		addResult(eval, result, "IBk", OVER_SAMPLING, featureSelection);
		
		applyFilter(null, eval, training, testing, classifierNB);
		addResult(eval, result, "NB", OVER_SAMPLING, featureSelection);

		SMOTE smote = new SMOTE();
		smote.setInputFormat(training);
		fc.setFilter(smote);

		eval = new Evaluation(testing);	

		
		applyFilter(null, eval, training, testing, classifierRF);
		addResult(eval, result, "RF", SMOTE, featureSelection);
		
		applyFilter(null, eval, training, testing, classifierIBk);
		addResult(eval, result, "IBk", SMOTE, featureSelection);
		
		applyFilter(null, eval, training, testing, classifierNB);
		addResult(eval, result, "NB", SMOTE, featureSelection);
		
		return result;

	}

	public static String getMetrics(Evaluation eval, String classifier, String balancing, String featureSelection) {

		return classifier + "," + balancing + "," + featureSelection + "," + eval.truePositiveRate(1)  + "," + eval.falsePositiveRate(1)  + "," + eval.trueNegativeRate(1)  + "," + eval.falseNegativeRate(1)  + "," + eval.precision(1)  + "," + eval.recall(1)  + "," + eval.areaUnderROC(1)  + "," + eval.kappa() + "\n";
	}


}
