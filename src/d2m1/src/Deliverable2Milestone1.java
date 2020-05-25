package d2m1.src;



import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import utils.D2M1Utils;
import utils.JSONUtils;

import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.json.JSONArray;

public class Deliverable2Milestone1 {

	// Get a new istance of JiraUtils object
	private static D2M1Utils jiraUtilsIstance;

	// MultiKeyMap<FileVersion, FilePath, MetricsList>
	private static MultiKeyMap fileMapDataset = MultiKeyMap.multiKeyMap(new LinkedMap());

	// Map<ticketID, (IV, FV)>
	private static Map<Integer, List<Integer>> ticketWithBuggyIndex = new HashMap<>();

	// Index of the last version (first half of the version released)
	private static int lastVersion;

	public static final String USER_DIR = "user.dir";
	public static final String RELEASE_DATE = "releaseDate";
	public static final String FILE_EXTENSION = ".java";




	/** This function return the list of released version of a given project
	 * 
	 * @param projectName, the name of the project
	 * @return versionList, the list of the version with release date
	 *
	 */ 
	public static Multimap<LocalDate, String> getVersionWithReleaseDate(String projectName)
			throws IOException, JSONException {

		Multimap<LocalDate, String> versionList = MultimapBuilder.treeKeys().linkedListValues().build();
		String releaseName = null;
		Integer i;

		// Url for the GET request to get information associated to Jira project
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;

		JSONObject json = JSONUtils.readJsonFromUrl(url);

		// Get the JSONArray associated to project version
		JSONArray versions = json.getJSONArray("versions");	

		// For each version...
		for (i = 0; i < versions.length(); i++) {
			// ... check if verion has release date and name, and add it to relative list
			if (versions.getJSONObject(i).has(RELEASE_DATE) && versions.getJSONObject(i).has("name")) {
				releaseName = versions.getJSONObject(i).get("name").toString();
				versionList.put(LocalDate.parse(versions.getJSONObject(i).get(RELEASE_DATE).toString()), releaseName);
			}
		}

		// Give an index to each release in the list
		int counterVersion = 1;
		for (LocalDate k : versionList.keySet()) {
			versionList.put(k, String.valueOf(counterVersion));
			counterVersion++;
		}

		return versionList;
	}


	/** This function calculate the AV bound [IV, FV) for the ticket witk valid AV from Jira
	 * 
	 * @param projectName, the name of the project
	 *
	 */ 
	public static void getBuggyVersionAVTicket(String projectName) throws IOException, JSONException {

		Integer j = 0;
		Integer i = 0;
		Integer total = 1;
		String key = null;

		// Get JSON API for closed bugs w/ AV in the project
		do {
			// Only gets a max of 1000 at a time, so must do this multiple times if bugs
			// >1000
			j = i + 1000;
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projectName
					+ "%22AND(%22status%22=%22closed%22OR"
					+ "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,versions,resolutiondate,created,fixVersions&startAt="
					+ i.toString() + "&maxResults=1000";
			JSONObject json = JSONUtils.readJsonFromUrl(url);
			JSONArray issues = json.getJSONArray("issues");
			total = json.getInt("total");

			// For each closed ticket...
			for (; i < total && i < j; i++) {

				JSONObject singleJsonObject = (JSONObject) issues.getJSONObject(i % 1000).get("fields");

				// ... get the key of the ticket,
				key = issues.getJSONObject(i % 1000).get("key").toString();

				// , get JSONArray associated to the affected versions,
				JSONArray affectedVersionArray = singleJsonObject.getJSONArray("versions");

				// Get a Java List from the JSONArray
				List<String> affectedVersionList = jiraUtilsIstance.getJsonAffectedVersionList(affectedVersionArray);

				// Calculate the AV index of the ticket [IV, FV)
				jiraUtilsIstance.getBuggyVersionListAV(affectedVersionList, singleJsonObject.getString("resolutiondate").split("T")[0],
						singleJsonObject.getString("created").split("T")[0], Integer.parseInt(key.split("-")[1]));

			}
		} while (i < total);

	}


	/** This function write the dataset to CSV file
	 * 
	 * @param projectName, the name of the project
	 *
	 */ 
	public static void writeToCSV(String projectName) throws IOException {

		// Set the name of the file
		try (FileWriter csvWriter = new FileWriter("output/" + projectName + "_dataset.csv")) {

			/*	
			 * Metrics Data Structure
			 *  0 - LOC_Touched
			 *  1 - NumberRevisions
			 *  2 - NumberBugFix
			 *  3 - LOC_Added
			 *  4 - MAX_LOC_Added
			 *  5 - Chg_Set_Size
			 *  6 - Max_Chg_Set
			 *  7 - Avg_Chg_Set
			 *  8 - Avg_LOC_Added
			 * 	9 - Buggyness
			 * 
			 * */

			// Append the first line
			csvWriter.append("Version Number");
			csvWriter.append(",");
			csvWriter.append("File Name");
			csvWriter.append(",");
			csvWriter.append("LOC_Touched");
			csvWriter.append(",");
			csvWriter.append("NumberRevisions");
			csvWriter.append(",");
			csvWriter.append("NumberBugFix");
			csvWriter.append(",");
			csvWriter.append("LOC_Added");
			csvWriter.append(",");
			csvWriter.append("MAX_LOC_Added");
			csvWriter.append(",");
			csvWriter.append("Chg_Set_Size");
			csvWriter.append(",");
			csvWriter.append("Max_Chg_Set");
			csvWriter.append(",");
			csvWriter.append("AVG_Chg_Set");
			csvWriter.append(",");
			csvWriter.append("Avg_LOC_Added");
			csvWriter.append(",");
			csvWriter.append("Buggy");
			csvWriter.append("\n");

			Map<String, List<Integer>> monthMap = new TreeMap<>();
			String buggy;
			int avgLOCAdded;
			int avgChgSet;
			MapIterator dataSetIterator = fileMapDataset.mapIterator();

			// Iterate over the dataset
			while (dataSetIterator.hasNext()) {
				dataSetIterator.next();
				MultiKey key = (MultiKey) dataSetIterator.getKey();

				// Get the metrics list associated to the multikey
				ArrayList<Integer> fileMetrics = (ArrayList<Integer>) fileMapDataset.get(key.getKey(0), key.getKey(1));

				monthMap.put(String.valueOf(key.getKey(0)) + "," + (String)key.getKey(1), fileMetrics);
			}

			for (Map.Entry<String, List<Integer>> entry : monthMap.entrySet()) {

				ArrayList<Integer> fileMetrics = (ArrayList<Integer>) entry.getValue();
				// Check that the version index is contained in the first half of the releases
				if (Integer.valueOf(entry.getKey().split(",")[0]) <= (lastVersion) + 1) {
					if (fileMetrics.get(9).equals(0))
						buggy = "No";
					else
						buggy = "Yes";

					if (fileMetrics.get(1).equals(0)) {
						avgLOCAdded = 0;
						avgChgSet = 0;
					} else {
						avgLOCAdded = fileMetrics.get(5)/fileMetrics.get(1);
						avgChgSet = fileMetrics.get(3)/fileMetrics.get(1);
					}

					// Append the data to CSV file
					csvWriter.append(entry.getKey().split(",")[0] + "," + entry.getKey().split(",")[1] + "," + fileMetrics.get(0) + "," + fileMetrics.get(1) + ","
							+ fileMetrics.get(2) + "," + fileMetrics.get(3) + "," + fileMetrics.get(4) + "," + fileMetrics.get(5) + ","
							+ fileMetrics.get(6) + "," + avgLOCAdded + "," + avgChgSet + "," + buggy);

					csvWriter.append("\n");
				}
			}

			// Flish the data to the file
			csvWriter.flush();
		}
	}


	/** This function build the dataset for the first half of released version
	 * 
	 * @param projectName, the name of the project
	 *
	 */ 
	public static void buildDataset(String projectName)
			throws IOException, GitAPIException {

		ArrayList<Integer> fileMetrics;
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		// Setting the project's folder
		String repoFolder = System.getProperty(USER_DIR) + "/" + projectName + "/.git";
		Repository repository = builder.setGitDir(new File(repoFolder)).readEnvironment().findGitDir().build();

		// Try to open the Git repository
		try (Git git = new Git(repository)) {

			Iterable<RevCommit> commits = null;

			// Get all the commits
			commits = git.log().all().call();

			// Iterate over the single issues
			for (RevCommit commit : commits) {

				// Check if commit has parent commit
				if (commit.getParentCount() != 0) {

					List<DiffEntry> filesChanged;

					// Get the date of the commit
					LocalDate commitLocalDate = commit.getCommitterIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				
					
					// Get the appartain version of the commit
					int appartainVersion = jiraUtilsIstance.getCommitAppartainVersion(commitLocalDate);

					// Check if the version index is in the first half ot the releases
					if (appartainVersion < lastVersion + 1){

						// Get the list of the commit (could be empty) associated to the commit
						List<Integer> ticketInformation = jiraUtilsIstance.getTicketAssociatedToCommit(commit.getFullMessage(), projectName);

						// Create a new DiffFormatter, needed to get the change between the commit and his parent
						try (DiffFormatter differenceBetweenCommits = new DiffFormatter(NullOutputStream.INSTANCE)) {

							differenceBetweenCommits.setRepository(repository);

							// Get the difference between the two commit
							filesChanged = differenceBetweenCommits.scan(commit.getParent(0), commit);

							// For each file changed in the commit
							for (DiffEntry singleFileChanged : filesChanged) {

								if (singleFileChanged.getNewPath().endsWith(FILE_EXTENSION)) {
									// Put (if not present) an empty record in the dataset map for the pair (version, filePath)
									jiraUtilsIstance.putEmptyRecord(appartainVersion, singleFileChanged.getNewPath());

									// Get the update metrics of the file
									fileMetrics = (ArrayList<Integer>) jiraUtilsIstance.getMetrics(singleFileChanged, appartainVersion, differenceBetweenCommits,
											filesChanged, ticketInformation, lastVersion +1);

									// Replace the updated metrics
									fileMapDataset.replace(appartainVersion, singleFileChanged.getNewPath(), fileMetrics);
									
									// Set this and other class contained in [IV, FV) buggy (if ther'are ticket(s) associated to the commit)
									jiraUtilsIstance.setClassBuggy(ticketInformation, singleFileChanged, lastVersion +1 );
								}
							}
						}
					}
				}
			}
		}

	}

	public static void main(String[] args)
			throws IOException, JSONException, GitAPIException {

		//Multimap<ReleaseDate, VersionName, VersionIndex>
		Multimap<LocalDate, String> versionListWithReleaseDate = MultimapBuilder.treeKeys().linkedListValues()
				.build();

		// The name of the project
		String[] projectList = {"AVRO", "BOOKKEEPER"};

		for (String projectName : projectList) {
		// The repo of the project
		String projectRepo = "https://github.com/apache/" + projectName + ".git";

		// Get the list of version with release date
		versionListWithReleaseDate = getVersionWithReleaseDate(projectName);

		jiraUtilsIstance = new D2M1Utils(versionListWithReleaseDate, fileMapDataset, ticketWithBuggyIndex);
		lastVersion = (versionListWithReleaseDate.size() / 2) / 2;

		// Clone the repo in the 'projectName' folder
		Git.cloneRepository()
		.setURI(projectRepo)
		.setDirectory(new File(projectName))
		.call();

		// Get all the file in the repo folder
		try (Stream<File> fileStream = Files.walk(Paths.get(System.getProperty(USER_DIR) + "/" + projectName + "/"))
				.filter(Files::isRegularFile).map(Path::toFile)){

			List<File> filesInFolder = fileStream.collect(Collectors.toList());

			// For each file in the folder that ends with .java...
			for (File i : filesInFolder) {
				if (i.toString().endsWith(FILE_EXTENSION)) {

					// ... put the pair (version, filePath) in the dataset map
					for (int j = 1; j < (lastVersion) + 1; j++) {
						jiraUtilsIstance.putEmptyRecord(j, i.toString().replace(
								Paths.get(System.getProperty(USER_DIR)).toString() + "/" + projectName + "/",""));
					}
				}
			}
		}

		// Find the IV and FV index for tickets with Jira affected version
		getBuggyVersionAVTicket(projectName);

		// Find the IV and FV index for tickets without Jira affected version (proportion method needed)
		jiraUtilsIstance.getBuggyVersionProportionTicket();

		// Build the dataset
		buildDataset(projectName);

		// Write the dataset to CSV file
		writeToCSV(projectName);

		// Delete the project repo folder
		FileUtils.delete(new File(projectName), 1);
		}

	}
}
