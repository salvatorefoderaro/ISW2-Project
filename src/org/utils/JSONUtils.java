package org.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONUtils {

	private JSONUtils() throws CustomException {
		throw new CustomException("Unable to do this operation.");
	}

	/** This function return the string needed for the JSONObject
	 * 
	 * @param rd, the Reader object
	 * @return the string needed for the JSONObject
	 *
	 */ 
	public static String readAll(Reader rd) throws IOException {

		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}


	/** This function return a JSONObrecj given an URL
	 * 
	 * @param url, URL for the get request
	 * @return json, the JSONObject associated to the reply from the URL
	 *
	 */ 
	public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		InputStream is = new URL(url).openStream();
		JSONObject json = null;
		try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			json = new JSONObject(readAll(rd));
		} finally {
			is.close();
		}
		return json;
	}

}
