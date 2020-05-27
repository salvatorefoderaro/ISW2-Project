package org.d2m2.src;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.utils.D2Utils;
import weka.core.Instances;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.converters.ConverterUtils.DataSource;
import weka.classifiers.lazy.IBk;

public class Deliverable2Milestone2 {

	private static final String TRAINING = "_training.arff";
	private static final String TESTING = "_testing.arff";

	public static void main(String[] args) throws Exception{

		// Declare the list of the dataset names
		String[] projects = {"AVRO", "BOOKKEEPER"};

		// Declare the number of revision for each dataset
		Integer[] limits = {15, 7};

		// For each project...
		for (int j = 0; j < projects.length; j++) {

			// Open the FileWriter for the output file
			try (FileWriter csvWriter = new FileWriter("output/outputD2M2_" + projects[j]+ ".csv")) {

				// Append the first line of the result file
				csvWriter.append("Dataset,#TrainingRelease,Classifier,Precision,Recall,AUC,Kappa\n");

				// Iterate over the single version for the WalkForward technique...
				for (int i = 1; i < limits[j]; i++) {

					// Create the ARFF file for the training, till the i-th version
					D2Utils.walkForwardTraining(projects[j], i);

					// Create the ARFF file for testing, with the i+1 version
					D2Utils.walkForwardTesting(projects[j] ,i+1);

					// Read the Datasource created before and get each dataset
					DataSource source1 = new DataSource(projects[j] + TRAINING);
					Instances training = source1.getDataSet();
					DataSource source2 = new DataSource(projects[j] + TESTING);
					Instances testing = source2.getDataSet();

					// Get the number of attributes
					int numAttr = training.numAttributes();

					/* Set the number of attributes for each dataset,
					 * remembering that the last attribute is the one that we want to predict
					 * */
					training.setClassIndex(numAttr - 1);
					testing.setClassIndex(numAttr - 1);

					// Get the three classifier
					IBk classifierIBk = new IBk();
					RandomForest classifierRF = new RandomForest();
					NaiveBayes classifierNB = new NaiveBayes();

					// Build the classifier
					classifierNB.buildClassifier(training);
					classifierRF.buildClassifier(training);
					classifierIBk.buildClassifier(training);

					// Get an evaluation object
					Evaluation eval = new Evaluation(training);	

					// Evaluate each model and add the result to the output file
					eval.evaluateModel(classifierNB, testing); 
					csvWriter.append(projects[j] + "," + i + ",NaiveBayes," + eval.precision(0) + "," + eval.recall(0) +  "," + eval.areaUnderROC(0) + "," + eval.kappa() + "\n");

					eval.evaluateModel(classifierRF, testing); 
					csvWriter.append(projects[j] + "," + i + ",RandomForest," + eval.precision(0) + "," + eval.recall(0) +  "," + eval.areaUnderROC(0) + "," + eval.kappa() + "\n");

					eval.evaluateModel(classifierIBk, testing); 
					csvWriter.append(projects[j] + "," + i + ",IBk," + eval.precision(0) + "," + eval.recall(0) +  "," + eval.areaUnderROC(0) + "," + eval.kappa() + "\n");

				}

				// Delete the temp file
				Files.deleteIfExists(Paths.get(projects[j] + TESTING));
				Files.deleteIfExists(Paths.get(projects[j] + TRAINING));
				csvWriter.flush();
			}

			// Flush the output file to disk
		}
	}
}
