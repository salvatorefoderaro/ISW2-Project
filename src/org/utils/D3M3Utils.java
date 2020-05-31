package org.utils;

import java.lang.System.Logger;
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
	
	/** This apply feature selection, apply different sampling technique and evaluate the model
	 * 
	 * @param training, the Evaluation object
	 * @param testing, the name of the classifier
	 * @param percentageMajorityClass, the percentage in the training set of the majority class
	 * @return string with the list of metrics separated with ','
	 */ 
	public static List<String> applyFeatureSelection(Instances training, Instances testing, double percentageMajorityClass) throws CustomException{

		// Build the filter
		AttributeSelection filter = new AttributeSelection();
		CfsSubsetEval eval = new CfsSubsetEval();
		GreedyStepwise search = new GreedyStepwise();
		search.setSearchBackwards(true);
		filter.setEvaluator(eval);
		filter.setSearch(search);

		try {
			// Apply the filter to the training and testing set
			filter.setInputFormat(training);
			Instances filteredTraining =  Filter.useFilter(training, filter);
			Instances testingFiltered = Filter.useFilter(testing, filter);
			int numAttrFiltered = filteredTraining.numAttributes();
			filteredTraining.setClassIndex(numAttrFiltered - 1);
			testingFiltered.setClassIndex(numAttrFiltered - 1);

			// Apply sampling to evaluate the model with datasets filtered
			return applySampling(filteredTraining, testingFiltered, percentageMajorityClass, "True");
		} catch (Exception e) {
			throw new CustomException("Error applyin filter.");
		}


	}

	/** This apply different sampling technique and evaluate the model
	 * 
	 * @param training, the Evaluation object
	 * @param testing, the name of the classifier
	 * @param percentageMajorityClass, the percentage in the training set of the majority class
	 * @return result, list string with the list of metrics separated with ',' of the various run
	 */
	public static List<String> applySampling(Instances training, Instances testing, double percentageMajorityClass, String featureSelection) throws Exception {

		ArrayList<String> result = new ArrayList<>();

		IBk classifierIBk = new IBk();
		RandomForest classifierRF = new RandomForest();
		NaiveBayes classifierNB = new NaiveBayes();

		int numAttrNoFilter = training.numAttributes();
		training.setClassIndex(numAttrNoFilter - 1);
		testing.setClassIndex(numAttrNoFilter - 1);

		// Build the classifier
		try {
			classifierNB.buildClassifier(training);
			classifierRF.buildClassifier(training);
			classifierIBk.buildClassifier(training);
		} catch (Exception e) {
			throw new CustomException("Error building the classifier.");
		}
		// Get an evaluation object

			// Evaluate with no sampling e no feature selection
			Evaluation eval = new Evaluation(training);	

			applyFilterForSampling(null, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", NO_SAMPLING, featureSelection);

			applyFilterForSampling(null, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", NO_SAMPLING, featureSelection);

			applyFilterForSampling(null, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", NO_SAMPLING, featureSelection);

			// Apply under sampling
			FilteredClassifier fc = new FilteredClassifier();
			SpreadSubsample  underSampling = new SpreadSubsample();
			underSampling.setInputFormat(training);
			String[] opts = new String[]{ "-M", "1.0"};
			underSampling.setOptions(opts);
			fc.setFilter(underSampling);

			// Evaluate the three classifiers
			eval = new Evaluation(training);

			applyFilterForSampling(fc, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", UNDER_SAMPLING, featureSelection);

			applyFilterForSampling(fc, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", UNDER_SAMPLING, featureSelection);

			applyFilterForSampling(fc, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", UNDER_SAMPLING, featureSelection);

			// Apply over sampling
			fc = new FilteredClassifier();
			Resample  overSampling = new Resample();
			overSampling.setInputFormat(training);
			String[] optsOverSampling = new String[]{"-B", "1.0", "-Z", String.valueOf(2*percentageMajorityClass*100)};
			overSampling.setOptions(optsOverSampling);
			fc.setFilter(overSampling);

			// Evaluate the three classifiers
			eval = new Evaluation(testing);	

			applyFilterForSampling(fc, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", OVER_SAMPLING, featureSelection);

			applyFilterForSampling(fc, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", OVER_SAMPLING, featureSelection);

			applyFilterForSampling(fc, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", OVER_SAMPLING, featureSelection);

			// Apply SMOTE
			SMOTE smote = new SMOTE();
			fc = new FilteredClassifier();
			smote.setInputFormat(training);
			fc.setFilter(smote);

			// Evaluate the three classifiers
			eval = new Evaluation(testing);	

			applyFilterForSampling(fc, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", SMOTE, featureSelection);

			applyFilterForSampling(fc, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", SMOTE, featureSelection);

			applyFilterForSampling(fc, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", SMOTE, featureSelection);

			return result;
	}


	/** This function build apply the specified filter with the sampling technique to the evaluator
	 * 
	 * @param fc, the FilteredClassifier object, with the filter technique 
	 * @param eval, the Evaluation object
	 * @param training, the training instance
	 * @param testing, the testing instance
	 * @param classifierName, the name of the classifier
	 * @return eval, return the Evaluation object with filter applied
	 */ 
	public static Evaluation applyFilterForSampling(FilteredClassifier fc, Evaluation eval, Instances training, Instances testing, AbstractClassifier classifierName) throws CustomException {

		// In filter needed, applyt it and evaluate the model 
		if (fc != null) {
			fc.setClassifier(classifierName);
			try {
				fc.buildClassifier(training);
				eval.evaluateModel(fc, testing);
			} catch (Exception e) { }

		// If not... Just evaluate the model
		} else {
			try {
				eval.evaluateModel(classifierName, testing);
			} catch (Exception e) {
				throw new CustomException("Errore nella valutazione del modello.");
			}
		}
		return eval;
	}

	/** This function build the ARFF file for the specific project relative to the testing set
	 * 
	 * @param eval, the Evaluation object
	 * @param result, the list needed to append the results
	 * @param classifierAbb, the abbreviation of the classifier
	 * @param sampling, the name of sampling technique
	 * @param featureSelection, the name of feature selection technique
	 */ 
	public static void addResult(Evaluation eval, List<String> result, String classifierAbb, String sampling, String featureSelection) {

		// Add the result to the List of instances metrics
		result.add(getMetrics(eval,classifierAbb, sampling, featureSelection));

	}

	
	/** This function build the ARFF file for the specific project relative to the testing set
	 * 
	 * @param projectName, the Evaluation object
	 * @param testing, the name of the classifier
	 * @param balancing, the name of the balancing technique
	 * @param featureSelection, the name of feature selection technique
	 * @return a string with the list of metrics separated with ','
	 */ 
	public static String getMetrics(Evaluation eval, String classifier, String balancing, String featureSelection) {
		return classifier + "," + balancing + "," + featureSelection + "," + eval.numTruePositives(1)  + "," + eval.numFalsePositives(1)  + "," + eval.numTrueNegatives(1)  + "," + eval.numFalseNegatives(1)  + "," + eval.precision(1)  + "," + eval.recall(1)  + "," + eval.areaUnderROC(1)  + "," + eval.kappa() + "\n";
	}

}
