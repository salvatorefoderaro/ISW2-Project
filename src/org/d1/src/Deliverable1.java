package org.d1.src;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.utils.JSONUtils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class Deliverable1 {

	private static final String RELEASE_DATE = "releaseDate";
	private static final String USER_DIR = "user.dir";
	private static final String MONTH_YEAR = "MONTH_YEAR";
	private static List<Integer> checkedTickets = new ArrayList<>();

	/** This function, given the project name, return the list of all ticket with status =("closed" or "resolved") and resolution="fixed"
	 * 
	 * @param montsMap The HashMap with the number of ticket fixed for each month
	 * @param commitDate The date of the single commit
	 * @param incrementValue The value of the increment for the specific key of the HashMap
	 *
	 */
	public static void addDateToMap(Multimap<Date, Integer> monthsMap, Date commitDate, Integer incrementValue) {

		List<Integer> currentValue = new ArrayList(monthsMap.get(commitDate));


		// If exists, update the value of the key using the old value plus incrementValue
		currentValue.set(0,  currentValue.get(0) + incrementValue);

		monthsMap.replaceValues(commitDate, currentValue);

		// Else, if the date key doesn't exists

	}


	/** This function count the number of fixed ticket for each month
	 * 
	 * @param issuesID The list with all the IDs of fixed ticket
	 * @param projectName The name of the project
	 * @return monthMap The HashMap with [Date(Month), Number of fixed ticket]
	 */
	public static Multimap<Date, Integer> getMonthFI(List<Integer> issuesID, String projectName) throws IOException, GitAPIException, ParseException {

		// MultiMap<Month, (NumberOfFixedTickets, NumberOfCommits, NumberOfversionReleased)>
		Multimap<Date, Integer> monthMap = MultimapBuilder.treeKeys().linkedListValues().build();
		String commitDateString;
		Date commitDate;

		// Create new FileRepositoryBuilder
		FileRepositoryBuilder builder = new FileRepositoryBuilder();		

		// Setting the project's folder
		String repoFolder = System.getProperty(USER_DIR) + "/" + projectName + "/.git";

		// Set the project's folder
		Repository repository = builder.setGitDir(new File(repoFolder)).readEnvironment().findGitDir().build();
		Pattern pattern = null; 
		Matcher matcher = null; 

		// Try to open the Git repository
		try (Git git = new Git(repository)) {

			// Get all the commits
			Iterable<RevCommit> commits = null;
			commits = git.log().all().call();

			// Iterate over the single commit
			for (RevCommit commit : commits) {

				// Get the Date of the commit
				LocalDate commitLocalDate = commit.getCommitterIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				commitDateString = commitLocalDate.getMonthValue() + "/" + commitLocalDate.getYear();
				commitDate=new SimpleDateFormat(MONTH_YEAR).parse(commitDateString); 

				List<Integer> currentValue = new ArrayList(monthMap.get(commitDate));

				// Check if the date key exists on the Map
				if(monthMap.containsKey(commitDate)) {

					// If exists, update the value of the key using the old value plus incrementValue
					currentValue.set(1,  currentValue.get(1) + 1);
					monthMap.replaceValues(commitDate, currentValue);

					// Else, if the date key doesn't exists
				} else {

					// Insert in the map the date key with the given value
					
					// Number of fixed tickets
					monthMap.put(commitDate, 0);
					
					// Number of commits
					monthMap.put(commitDate, 1);
					
					// Number of versions
					monthMap.put(commitDate, 0);

				}				

				// Iterate over the single issues
				for (Integer issues : issuesID) {  

					// Use pattern to check if the commit message contains the word " ProjectName-IssuesID "
					pattern = Pattern.compile("\\b"+ projectName + "-" +issues + "\\b", Pattern.CASE_INSENSITIVE);
					matcher = pattern.matcher(commit.getFullMessage());

					// Check if commit message contains the issues ID and the issues is labeled like "not checked"
					if (matcher.find()) {

						// If ticket was not checked before...
						if (!checkedTickets.contains(issues)) {

							// Add the Date to the map, incrementing the pair key-value of 1
							addDateToMap(monthMap, commitDate, 1); 

							// Add the ticket to the checked list
							checkedTickets.add(issues);
						}

					} else {

						// Add the Date to the map, incrementing the pair key-value of 0
						addDateToMap(monthMap, commitDate, 0);       		

					} 
				}

			} 
		}

		return monthMap;
	}


	/** This function returns the list of all ticket with status =("closed" or "resolved") and resolution="fixed"
	 * 
	 * @param projectName The name of the Apache project
	 * @return fixedIssues The list of the IDs of all fixed ticket
	 *
	 */
	public static List<Integer> getFII(String projectName) throws IOException, JSONException{
		ArrayList<Integer> fixedIssues = new ArrayList<>();
		Integer j = 0;
		Integer i = 0;
		Integer total = 1;

		//Get JSON API for closed bugs w/ AV in the project
		do {

			//Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
			j = i + 1000;
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projectName
					+ "%22AND(%22status%22=%22closed%22OR"
					+ "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,versions,resolutiondate,created,fixVersions&startAt="
					+ i.toString() + "&maxResults=1000";
			JSONObject json = JSONUtils.readJsonFromUrl(url);
			JSONArray issues = json.getJSONArray("issues");
			total = json.getInt("total");
			for (; i < total && i < j; i++) {

				//Iterate through each bug
				String key = issues.getJSONObject(i%1000).get("key").toString().split("-")[1];
				fixedIssues.add(Integer.parseInt(key));
			}  
		} while (i < total);
		return fixedIssues;
	}

	/** This function return the list of released version of a given project
	 * 
	 * @param projectName, the name of the project
	 * @return versionList, the list of the version with release date
	 *
	 */ 
	public static Multimap<Date, Integer> getReleasedVersion(Multimap<Date, Integer> monthMap, String projectName)
			throws IOException, JSONException, ParseException {

		Date versionDate;
		String versionDateString;
		Integer i;

		// Url for the GET request to get information associated to Jira project
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;

		JSONObject json = JSONUtils.readJsonFromUrl(url);

		// Get the JSONArray associated to project version
		JSONArray versions = json.getJSONArray("versions");	

		// For each version...
		for (i = 0; i < versions.length(); i++) {
			// ... check if version has release date and name, and add it to relative list
			if (versions.getJSONObject(i).has(RELEASE_DATE) && versions.getJSONObject(i).has("name")) {

				LocalDate test = LocalDate.parse(versions.getJSONObject(i).get(RELEASE_DATE).toString());
				versionDateString = test.getMonthValue() + "/" + test.getYear();
				versionDate =new SimpleDateFormat(MONTH_YEAR).parse(versionDateString);   

				// Get the list of value associated to the month
				List<Integer> currentValue = new ArrayList(monthMap.get(versionDate));

				// Increment the number of the released for the specific month
				currentValue.set(2, currentValue.get(2) + 1);

				monthMap.replaceValues(versionDate, currentValue);

			}
		}
		return monthMap;

	}


	/** This function, given the project name, return the list of all ticket with status =("closed" or "resolved") and resolution="fixed"
	 * 
	 * @param monthList The HashMap with, for each month, the number of fixed ticket
	 *
	 */ 
	public static void writeToCSV(Multimap<Date, Integer> monthList, String projectName) throws IOException {

		// Use a Calendar instance to get month and year from Date
		Calendar cal = Calendar.getInstance();

		// Open the destination csv file
		try (FileWriter csvWriter = new FileWriter("output/outputM1_" + projectName + ".csv")) {

			// Append the first line
			csvWriter.append("Month");
			csvWriter.append(",");
			csvWriter.append("#Fixed tickets");
			csvWriter.append(",");
			csvWriter.append("Number of commits");
			csvWriter.append(",");
			csvWriter.append("Number of versions");
			csvWriter.append("\n");

			// For every entry of the map, write the pair key-value on the csv file
			for (Date entry : monthList.keySet()) {
				cal.setTime(entry);
				csvWriter.append((cal.get(Calendar.MONTH)+1 + "/" + cal.get(Calendar.YEAR)) + "," + Iterables.get(monthList.get(entry), 0).toString() + "," + Iterables.get(monthList.get(entry), 1).toString() + "," + Iterables.get(monthList.get(entry), 2));
				csvWriter.append("\n");
			} 

			// Flush the written data to the csv file
			csvWriter.flush();
		} 
	}


	public static void main(String[] args) throws IOException, JSONException, GitAPIException, ParseException {

		// The name of the project and the link of the Git repo
		String projectName = "FALCON";
		String projectRepo = "https://github.com/apache/falcon.git";

		// Clone the repo in the 'projectName' folder
		Git.cloneRepository()
		.setURI(projectRepo)
		.setDirectory(new File(projectName))
		.call();

		// Get all the commit of the project
		List<Integer> commitID = getFII(projectName);

		// For each month from first to last commit, get the number of fixed
		Multimap<Date, Integer> mapFIMap = getMonthFI(commitID, projectName);

		getReleasedVersion(mapFIMap, projectName);

		// Write to CSV all the pair key-value for each month
		writeToCSV(mapFIMap, projectName);

		// Delete the folder containing the repo
		FileUtils.delete(new File(projectName), 1);
	}
}

