package d2utils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class D2M1Utils {

	//Multimap<ReleaseDate, VersionName, VersionIndex>
	private  Multimap<LocalDate, String> versionListWithDateAndIndex;

	// Map<ticketID, (OV, FV)>
	private  Multimap<Integer, Double> ticketWithProportion = MultimapBuilder.treeKeys().linkedListValues().build();

	// Map<ticketID, (OV, FV)>
	private  Multimap<Integer, Integer> ticketWithoutAffectedVersionList = MultimapBuilder.treeKeys().linkedListValues().build();

	// MultiKeyMap<FileVersion, FilePath, MetricsList>
	private  MultiKeyMap fileMapDataset;

	// Map<ticketID, (IV, FV)>
	private  Map<Integer, List<Integer>> ticketWithBuggyIndex;

	private static final String RELEASE_DATE = "releaseDate";
	private static final int METRICS_NUMBER = 10;

	public D2M1Utils(Multimap<LocalDate, String> versionListWithDate, MultiKeyMap fileMapDataset, Map<Integer, List<Integer>> ticketWithBuggyIndex) {
		this.versionListWithDateAndIndex = versionListWithDate;
		this.fileMapDataset = fileMapDataset;
		this.ticketWithBuggyIndex = ticketWithBuggyIndex;
	}


	/** This function give the affected version list (if not empty) of a Jira ticket
	 * 
	 * @param json, the JSON array from Jira
	 * @return affectedVersionList, the list containing the name of the Jira affected version list
	 * 
	 */ 
	public List<String> getJsonAffectedVersionList(JSONArray json) throws JSONException{

		List<String> affectedVersionList = new ArrayList<>();

		if (json.length() > 0) {

			// For each release in the AV version...
			for (int k = 0; k < json.length(); k++) {

				JSONObject singleRelease = json.getJSONObject(k);

				// ... check if the single release has been released
				if (singleRelease.has(RELEASE_DATE)) {
					affectedVersionList.add(singleRelease.getString("name"));
				}
			}
		}

		return affectedVersionList;
	}


	/** This function check if some word(s) ProjectName-TickedID is contained in the commit's message
	 * 
	 * @param commitMessage, the message of the commit
	 * @param projectName, the name of the project
	 * @return resultList, the list containing the pair (ticketIV, ticketFV, ticketID) for each ticket contained in the commit's message
	 * 
	 */ 
	public List<Integer> getTicketAssociatedToCommit(String commitMessage, String projectName) {
		List<Integer> resultList = new ArrayList<>();
		Pattern pattern = null;
		Matcher matcher = null;

		for (Map.Entry<Integer,List<Integer>> entry : ticketWithBuggyIndex.entrySet()) {

			// Use pattern to check if the commit message contains the word "*ProjectName-IssuesID*"
			pattern = Pattern.compile("\\b"+ projectName + "-" + entry.getKey() + "\\b", Pattern.CASE_INSENSITIVE);
			matcher = pattern.matcher(commitMessage);

			// Check if commit message contains the issues ID and the issues is labeled like "not checked"
			if (matcher.find()) {
				resultList.add(ticketWithBuggyIndex.get(entry.getKey()).get(0));
				resultList.add(ticketWithBuggyIndex.get(entry.getKey()).get(1));
				resultList.add(entry.getKey());
			}
		}
		return resultList;
	}


	/** This function calculate the required metrics for the single file contained in the commit
	 * 
	 * @param entry, object needed to get the line of code changed 
	 * @param version, the appartain's version of the file
	 * @param diffFormatter, object needed to get the line of code changed 
	 * @param filesChanged, the list of all the file changed in the commit
	 * @param ticketAssociated, the list (could be empty) of the ticket associated with the commit
	 * @param limitVersion, the index of the upper bound version (we have to consider just the metrics for the first half of the releases)
	 * @return result, the list containing the calculated value for each metrics
	 *
	 */ 
	public List<Integer> getMetrics (DiffEntry entry, int version, DiffFormatter diffFormatter, List<DiffEntry> filesChanged, List<Integer> ticketAssociated, int limitVersion) throws IOException{

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
		 *  8 - AVG_LOC_Added
		 * 
		 * */

		// Take the current metrics for the pair (version, fileName)
		ArrayList<Integer> result = (ArrayList<Integer>) fileMapDataset.get(version, entry.getNewPath());

		// Check if the appartaining version of the file is less than the upper bound
		if (version < limitVersion) {
			int locTouched = 0;
			int locAdded = 0;
			int chgSetSize = 0;

			// Get the total number of file committed
			chgSetSize = filesChanged.size();

			// For each edit made to the file...
			for (Edit edit : diffFormatter.toFileHeader(entry).toEditList()) {

				// Check the type of the edit and increment the corresponding variable
				if (edit.getType() == Edit.Type.INSERT) {
					locAdded += edit.getEndB() - edit.getBeginB();
					locTouched += edit.getEndB() - edit.getBeginB();
				} else if (edit.getType() == Edit.Type.DELETE) {
					locTouched += edit.getEndA() - edit.getBeginA();
				} else if (edit.getType() == Edit.Type.REPLACE) {
					locTouched += edit.getEndA() - edit.getBeginA();
				}
			}

			// Update each metrics 
			result.set(0, result.get(0) + locTouched);
			result.set(1, result.get(1) + 1);

			// Check if the commit is associated to some ticket
			if (ticketAssociated.isEmpty()) {
				result.set(2, result.get(2) + 0);

			} else {
				// If yes, set the call buggy and calculate the number of "NumberBugFix"
				result.set(2, result.get(2) + ticketAssociated.size()/3);
				result.set(9, 1);
			}

			result.set(3, result.get(3) + locAdded);

			if (locAdded > result.get(4)) {
				result.set(4,  locAdded);
			}

			result.set(5, result.get(5) + chgSetSize);

			if (chgSetSize > result.get(6)) {
				result.set(6,  chgSetSize);
			} 
		} 

		return result;
	}


	/** This function put an empty record on the multy key map (appartainVersion, nameFile)
	 * 
	 * @param appartainVersion, the index of the release
	 * @param nameFile, the name of the file
	 * 
	 */ 
	public void putEmptyRecord(int appartainVersion, String nameFile) {

		if (!fileMapDataset.containsKey(appartainVersion, nameFile)) {

			ArrayList<Integer> emptyArrayList = new ArrayList<>();

			for (int k = 0; k < METRICS_NUMBER; k++) {
				emptyArrayList.add(0);
			}

			fileMapDataset.put(appartainVersion, nameFile, emptyArrayList);
		}
	}


	/** This function set the file "buggy" in the multy key map of the dataset
	 * 
	 * @param ticket, the list (could be empty) of the ticket IDs contained in the commit's message
	 * @param entry, the object needed to get the type of the change made to the file
	 * @param numberOfVersions, the upper bound for the version's index (we just work with the first half ot the releases)
	 *
	 */ 
	public void setClassBuggy(List<Integer> ticketAssociatedWithCommit, DiffEntry entry, int numberOfVersions) {

		// Check the ticket list associated to the commit  and the edit type of the file
		if (!ticketAssociatedWithCommit.isEmpty() && (entry.getChangeType() == DiffEntry.ChangeType.MODIFY
				|| entry.getChangeType() == DiffEntry.ChangeType.DELETE)) {

			// For each ticket (IV, OV, ID, ..., IV, OV, ID)...
			for (int j = 0; j< ticketAssociatedWithCommit.size(); j= j+3) {
				int startVersion = ticketAssociatedWithCommit.get(j);
				int endVersion = ticketAssociatedWithCommit.get(j + 1);

				// ... for each version in the affected version range (list) check if the version index is included in the first half of the release ...
				for (int version = startVersion; version < endVersion && version < numberOfVersions; version++) {

					if (!fileMapDataset.containsKey(version, entry.getNewPath())) {
						putEmptyRecord(version, entry.getNewPath());

						// ... set the class "Buggy"
						List<Integer> result = (ArrayList<Integer>) fileMapDataset.get(version, entry.getNewPath());
						result.set(9, 1);
						fileMapDataset.replace(version, entry.getNewPath(), result);
					}
				}
			}
		}
	}


	/** This function (try) to calculate the value of P using the ticket with AV list
	 * 
	 * @param affectedVersionList, the AV list taked from Jira
	 * @param resolutionDate, the resolution date of the ticket
	 * @param creationDate, the creation date of the ticket
	 * @param ticketID, the ID of the ticket
	 */ 
	public void getBuggyVersionListAV(List<String> affectedVersionList, String resolutionDate,
			String creationDate, int ticketID) {

		double proportion = 0;
		int fvIndex = 0;
		int ivIndex = 0;
		int ovIndex = 0;

		// Get the three version index
		ovIndex = getOpeningVersion(creationDate);
		fvIndex = getFixedVersion(resolutionDate);
		ivIndex = getAffectedVersionByList(affectedVersionList, creationDate);

		// Check if the index of IV is different from 0, then the ticket has associated valid AV
		if (ivIndex != 0) {

			// Calculate (if check is ok) the proportion of the ticket and add it to the list of all ticket with proportion
			if (!(fvIndex == ovIndex || fvIndex == ivIndex || fvIndex < ivIndex)) {
				proportion = getAVProportion(ivIndex, fvIndex, ovIndex);
				if (proportion > 0) {
					ticketWithProportion.put(ticketID, 1.0);
					ticketWithProportion.put(ticketID, proportion);
				}
			}

			// Get the IV and FV index of the ticket
			getBuggyVersionList(ticketID, fvIndex, ivIndex);

		} else {

			// If AV is not present, then put the ticket in the list of the tickets that needs proportion for estimate IV
			ticketWithoutAffectedVersionList.put(ticketID, ovIndex);
			ticketWithoutAffectedVersionList.put(ticketID, fvIndex);
		}
	}


	/** This function calculate the IV for the with AV list from Jira
	 * 
	 */ 
	public void getBuggyVersionProportionTicket() {
		
		int ivIndex = 1;

		// Iterate over all the commit without affected version list
		for (int k : ticketWithoutAffectedVersionList.keySet()) {
			int fvIndex = Iterables.get(ticketWithoutAffectedVersionList.get(k), 1);
			int ovIndex = Iterables.get(ticketWithoutAffectedVersionList.get(k), 0);

			// Get the mean proportion of the previous tickets
			int proportion = (int) Math.round(getProportionPreviousTicket(k));

			// Check if the affected version list is not empty
			if (fvIndex != ovIndex) {

				// Use the P formula, if the mean value of previous tickets it's greater than 0
				if (proportion > 0) {
					ivIndex = fvIndex - (fvIndex - ovIndex)*proportion;
					if (ivIndex < 1)
						ivIndex = 1;

				} else {
					// otherwise use the "simple approach"
					ivIndex = ovIndex;
				}

				// Get the IV and FV index of the ticket
				getBuggyVersionList(k,fvIndex, ivIndex);
			} 
		}
	}


	/** This function calculate the index of the appertaining version of a file
	 * 
	 * @param fileCommitDate,the date of the commit
	 * @return lastIndex, the index of the appertaining version of the file
	 */ 
	public int getCommitAppartainVersion(LocalDate fileCommitDate) {

		int lastIndex = 0;

		// Iterate over all version with release date
		for (LocalDate k : versionListWithDateAndIndex.keySet()) {

			lastIndex = Integer.valueOf(Iterables.get(versionListWithDateAndIndex.get(k), 1));

			// Break if we found the appartaining version of the file before the end of the iteration
			if (k.isAfter(fileCommitDate)) {
				break;
			}
		}
		return lastIndex;
	}


	/** This function calculate value of P of the previous tickets (if any available)
	 * 
	 * @param ticketID, the ID of the ticket
	 * @return result, the value of P (could be 0 if no previous tickets)
	 */ 
	public double getProportionPreviousTicket(int ticketID) {

		int counter = 0;
		double proportion = 0;
		double result;

		// For each ticket with correct calculated P value...
		for (int k : ticketWithProportion.keySet()) {

			// ... check iv the ticket ID is lower than the current ticket and sum the P value
			if (k < ticketID && Iterables.get(ticketWithProportion.get(k), 0) != -1.0) {
				counter = counter + 1;
				proportion = proportion + Iterables.get(ticketWithProportion.get(k), 1);
			}
		}

		// If number of previous ticket is greater than 0, calculate the mean value P
		if (counter > 0) {
			result = counter / proportion;
		} else {

			// Else, return 0, to signal that we need to use the simple method
			result = 0;
		}

		return result;
	}


	/** This function calculate the index of the fixed version
	 * 
	 * @param ticketCreationDate, the date of the creation of the ticket
	 * @return fvIndex, the index of the fixed version
	 */ 
	public int getFixedVersion(String resolutionDate) {

		int fvIndex = 0;

		// Iterate over all version with release date
		for (LocalDate k : versionListWithDateAndIndex.keySet()) {

			/*  Why this assign before the check? If we have a ticket with resolutionDate date after the last released version
			 * in that way we associate it to the last released version. Is this wrong? Not in our scope, because we just
			 * want to build the dataset for the first half of the release. In this way we assign a "fake" FV to the ticket,
			 * because we don't want to loose the affected version list of the ticket. */
			fvIndex = Integer.valueOf(Iterables.get(versionListWithDateAndIndex.get(k), 1));

			if (k.isEqual(LocalDate.parse(resolutionDate)) || k.isAfter(LocalDate.parse(resolutionDate))) {

				// Break if we found the fixed version of the file before the end of the iteration
				break;
			}
		}

		return fvIndex;
	}


	/** This function calculate the index of the opening version
	 * 
	 * @param ticketCreationDate, the date of the creation of the ticket
	 * @return ov, the index of the opening version
	 */ 
	public int getOpeningVersion(String ticketCreationDate) {

		int ovIndex = 0;

		// Iterate over all version with release date
		for (LocalDate k : versionListWithDateAndIndex.keySet()) {

			/*  Why this assign before the check? If we have a commit with commit date after the last released version
			 * in that way we associate it to the last released version. Is this wrong? Not in our scope, because we just
			 * want to build the dataset for the first half of the release. In this way we assign a "fake" FV to the ticket,
			 * because we don't want to loose the affected version list of the ticket. */
			ovIndex = Integer.valueOf(Iterables.get(versionListWithDateAndIndex.get(k), 1));
			if (k.isAfter(LocalDate.parse(ticketCreationDate)) || k.isEqual(LocalDate.parse(ticketCreationDate))) {

				// Break if we found the opening version of the file before the end of the iteration
				break;
			}
		}

		return ovIndex;
	}


	/** This function, given the list of the Affected Version taked from Jira, return the index of the oldest IV version
	 * 
	 * @param versionList, the list of all the AV from Jira
	 * @param creationDate, the creation date of the ticket
	 * @return version, the index of the oldest IV version
	 */ 
	public int getAffectedVersionByList(List<String> versionList, String creationDate) {

		int ivVersion = 0;

		// Iterate over all the version with release date and the versionList to found the oldest affected version
		for (LocalDate k : versionListWithDateAndIndex.keySet()) {
			for (String k1 : versionList) {

				/* Check if the versio'ns index is equals to the one contained in the list,
				 * but also check that the release date of the version is before the creation of the ticket
				 * (check needed because of some wrong data on Jira)*/
				if (Iterables.get(versionListWithDateAndIndex.get(k), 0).equals(k1) && k.isBefore(LocalDate.parse(creationDate))) {
					ivVersion = Integer.valueOf(Iterables.get(versionListWithDateAndIndex.get(k), 1));
					break;
				}
			}
		}

		return ivVersion;
	}


	/** This function calculate the value P (proportion) for the given ticket
	 * 
	 * @param iv, the index of the IV version
	 * @param fv, the index of the FV version
	 * @param ov, the index of the OV version
	 * @return P, the value P for the proportion method
	 */ 
	public double getAVProportion(int iv, int fv, int ov) {
		double fvIv = (double)fv - iv;
		double fvOv = (double)fv - ov;
		return fvIv / fvOv;
	}


	/** This function check the IV and FV index and add the ticket to the commit with valid AV list, with [IV,FV) index
	 * 
	 * @param tickedID, the ID of the ticket
	 * @param fvIndex, the index of the FV version
	 * @param ivIndex, the index of the OV version
	 */ 
	public void getBuggyVersionList(int ticketID, int fvIndex, int ivIndex) {

		List<Integer> affectedVersionBoundaryList = new ArrayList<>();

		// Check if the index of the versions are ok
		if (fvIndex != ivIndex && ivIndex < fvIndex) {

			// Then add the ticket to the list of the tickets with not empty affected version list
			affectedVersionBoundaryList.add(ivIndex);
			affectedVersionBoundaryList.add(fvIndex);
			ticketWithBuggyIndex.put(ticketID, affectedVersionBoundaryList);
		}
	}
}
