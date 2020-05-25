package d2utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

public class D2Utils {

	private static final String TRAINING = "_training.arff";
	private static final String TESTING = "_testing.arff";
	private static final String OVER_SAMPLING = "Over sampling";
	private static final String UNDER_SAMPLING = "Under sampling";
	private static final String SMOTE = "Smote";
	private static final String NO_SAMPLING = "No sampling";
	
	/** This function build the ARFF file for the specific project relative to the training set
	 * 
	 * @param projectName, the name of the project
	 * @param trainingLimit, the index of the last version to be included in the training set
	 *
	 */ 
	public static List<Integer> writeTrainingToCSV(String projectName, int trainingLimit) throws IOException {

		int counterElement = 0;
		int counterDefective = 0;

		ArrayList<Integer> counterList = new ArrayList<>();

		// Create the output ARFF file
		try (FileWriter csvWriter = new FileWriter(projectName + TRAINING)) {

			// Append the static line of the ARFF file
			csvWriter.append("@relation " + projectName + "\n\n");
			csvWriter.append("@attribute LOC_Touched real\n");
			csvWriter.append("@attribute NumberRevisions real\n");
			csvWriter.append("@attribute NumberBugFix real\n");
			csvWriter.append("@attribute LOC_Addedr real\n");
			csvWriter.append("@attribute MAX_LOC_Added real\n");
			csvWriter.append("@attribute Chg_Set_Size real\n");
			csvWriter.append("@attribute Max_Chg_Set real\n");
			csvWriter.append("@attribute AVG_Chg_Set real\n");
			csvWriter.append("@attribute Avg_LOC_Added real\n");
			csvWriter.append("@attribute Buggy {Yes, No}\n\n");
			csvWriter.append("@data\n");

			// Read the project dataset
			try (BufferedReader br = new BufferedReader(new FileReader("output/" + projectName + "_dataset.csv"))){ 

				// Skip the first line (contains just column name)
				String line = br.readLine();

				// Read till the last row 
				while ((line = br.readLine()) != null){  

					// Check if the version number is contained in the limit index
					if (Integer.parseInt(line.split(",")[0]) <= trainingLimit ) {

						counterElement = counterElement + 1;

						// Append the row readed from the CSV file, but without the first 2 column
						String[] array = line.split(",");
						for (int i = 2; i < array.length; i++) {
							if (i == array.length - 1) {
								if(array[i].equals("Yes"))
									counterDefective = counterDefective + 1;

								csvWriter.append(array[i] + "\n");
							} else {
								csvWriter.append(array[i] + ",");
							}

						}
					}
				}

				// Flush the file to the disk
				csvWriter.flush();

				counterList.add(counterElement);
				counterList.add(counterDefective);

				return counterList;

			}
		}
	}

	/** This function build the ARFF file for the specific project relative to the testing set
	 * 
	 * @param projectName, the name of the project
	 * @param testing, the index of the version to be included in the testing set
	 *
	 */ 
	public static List<Integer> writeTestingToCSV(String projectName, int testing) throws IOException {

		int counterElement = 0;
		int counterDefective = 0;
		ArrayList<Integer> counterList = new ArrayList<>();
		// Create the output ARFF file
		try (FileWriter csvWriter = new FileWriter(projectName + TESTING)) {

			// Append the static line of the ARFF file
			csvWriter.append("@relation " + projectName + "\n\n");
			csvWriter.append("@attribute LOC_Touched real\n");
			csvWriter.append("@attribute NumberRevisions real\n");
			csvWriter.append("@attribute NumberBugFix real\n");
			csvWriter.append("@attribute LOC_Addedr real\n");
			csvWriter.append("@attribute MAX_LOC_Added real\n");
			csvWriter.append("@attribute Chg_Set_Size real\n");
			csvWriter.append("@attribute Max_Chg_Set real\n");
			csvWriter.append("@attribute AVG_Chg_Set real\n");
			csvWriter.append("@attribute Avg_LOC_Added real\n");
			csvWriter.append("@attribute Buggy {Yes, No}\n\n");
			csvWriter.append("@data\n");

			// Read the project dataset
			try (BufferedReader br = new BufferedReader(new FileReader("output/" + projectName + "_dataset.csv"))){  

				// Skip the first line (contains just column name)
				String line = br.readLine();

				// Read till the last row 
				while ((line = br.readLine()) != null){  

					// Check if the version number is equal to the one equal to the test index
					if (Integer.parseInt(line.split(",")[0]) == testing ) {

						counterElement = counterElement + 1;

						// Append the row readed from the CSV file, but without the first 2 column
						String[] array = line.split(",");
						for (int i = 2; i < array.length; i++) {
							if (i == array.length - 1) {
								if(array[i].equals("Yes"))
									counterDefective = counterDefective + 1;

								csvWriter.append(array[i] + "\n");
							} else {
								csvWriter.append(array[i] + ",");
							}
						}
					}
				}

				// Flush the file to the disk
				csvWriter.flush();
				counterList.add(counterElement);
				counterList.add(counterDefective);

			}
		}
		return counterList;
	}

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
