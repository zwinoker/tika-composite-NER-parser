import java.io.*;
import java.util.*;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.mime.MediaType;

import org.apache.tika.parser.ner.opennlp.OpenNLPNameFinder;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;

import org.apache.tika.io.IOUtils;

import java.net.*;

import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;


public class CompositeNERAgreementParser implements Parser
{

	private Metadata mdata;
	private OpenNLPNameFinder nlp;
	private CoreNLPNERecogniser cnlp = new CoreNLPNERecogniser();

	private String nltkServerURL = "http://127.0.0.1:8888/nltk";
	private String gQServerURL = "http://127.0.0.1:8080/processQuantityText";
	private String[] entityTypes = {"LOCATION","PERSON","ORGANIZATION","TIME","DATE","PERCENT","MONEY"};

	private Map<String, Map<String,Integer>> openNLPEntities;	
	private Map<String, Map<String,Integer>> coreNLPEntities;	
	private Map<String, Map<String,Integer>> nltkEntities;
	private String gQMeasurements;

	public Set<MediaType> getSupportedTypes(ParseContext context) {
		Set<MediaType> types = new HashSet<MediaType>();
		return types;
	}

	// Assumes the input stream is text extracted from TTR. 
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
		throws IOException,SAXException,TikaException {

		// Get metadata
		this.mdata = metadata;

		// Read in all text
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		String line = reader.readLine();
		String text = "";
		while (line != null) {
			text += line;
			line = reader.readLine();
		}
		reader.close();

		try {
			// Get NER results from each NER toolkit.
			this.openNLPEntities = this.openNLPParse(text);	
			this.coreNLPEntities = this.coreNLPParse(text);	
			this.nltkEntities = this.nltkParse(text);
			this.gQMeasurements = this.gQParse(text);

			// Combine results and add to metadata.
			Map<String, Map<String,Integer>> combo = combineResults();
			String json = mapToJSON(combo);
			pushToMetadata("entities",json);
			pushToMetadata("quantities", this.gQMeasurements);
		
		} catch (Exception e) {
				e.printStackTrace();
		}
	}

	// Each OpenNLP parser can only find one type of named entities. This method parses data for each type and returns
	// 	the aggregated results.
	private Map<String, Map<String,Integer>> openNLPParse(String text) {
		Map<String, Map<String,Integer>> result = new HashMap<String, Map<String,Integer>>();
		String path = "";
		
		for (String type : this.entityTypes) {
			path = "en-ner-" + type.toLowerCase() + ".bin";
			this.nlp = new OpenNLPNameFinder(type, path);
			Map<String,Integer> entities = nlp.recogniseWithCounts(text);
			result.put(type, entities);
		}

		return result;
	}

	// Stanford coreNLP parser
	private Map<String, Map<String,Integer>> coreNLPParse(String text) {
		Map<String, Map<String,Integer>> entities = this.cnlp.recogniseWithCounts(text);
		return entities;
	}

	// NLTK parsing
	private Map<String, Map<String,Integer>>  nltkParse(String text) {
		Map<String, Map<String,Integer>> result = new HashMap<>();
		try {
			URLConnection connection = new URL(this.nltkServerURL).openConnection();
			connection.setDoOutput(true);
			OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());

			writer.write(text);
			writer.flush();

			InputStream response = connection.getInputStream();

       	 	String inputStreamString = new Scanner(response,"UTF-8").useDelimiter("\\A").next();
       	 	result = openNLPParse(inputStreamString);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}


	// Grobid-quantities parser
	private String  gQParse(String text) {
		String measurements = "{\"measurements";
		try {
			// Connect to grobid-quantities REST server
			String fileName = this.mdata.get("source-file-path");
			URL url = new URL(this.gQServerURL);
			HttpURLConnection hc = (HttpURLConnection) url.openConnection();
			HttpURLConnection.setFollowRedirects( true );
			hc.setDoOutput( true );
			hc.setRequestMethod("POST");	
			PrintStream ps = new PrintStream(hc.getOutputStream());
			String args = "text=" + text;
			ps.print(args);
			ps.close();
			hc.connect();

			// If we successfully connected to the GQ server then get the response.
			if( HttpURLConnection.HTTP_OK == hc.getResponseCode() ){
				InputStream response = hc.getInputStream();
       	 		String inputStreamString = new Scanner(response,"UTF-8").useDelimiter("\\A").next();
				String[] splitMeas = inputStreamString.split("measurements");
			    measurements += splitMeas[1];
				response.close();
				hc.disconnect();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		return measurements;
	}

	// Convert maps to JSON for storage in metadata
	private String mapToJSON(Map<String, Map<String,Integer>> map) {
		String json = "{";
		for (String key : map.keySet()) {
			String category = "\"" + key + "\":[";
			json += category;
			Map<String,Integer> names = map.get(key);
			for (String val : names.keySet()) {
				String entry = "{\"count\":" + names.get(val) + ", \"name\":\"" + val.replace("\"","").replace(",","") + "\"},";
				json += entry;
			}
			json += "],";
		}
		json += "}";
		json = json.replace(",]","]").replace(",}","}");
		return json;
	}

	// Finds maximal joint agreement from all NER results. Takes union of all results.
	private Map<String, Map<String,Integer>> combineResults() {
		Map<String, Map<String,Integer>>maxJointAgreement = new HashMap<>();

		for (String type : this.entityTypes) {
			Map<String,Integer> allResults = new HashMap<>();
			Map<String,Integer> onlpRes = this.openNLPEntities.get(type);
			Map<String,Integer> cnlpRes = this.coreNLPEntities.get(type);
			Map<String,Integer> nltkRes = this.nltkEntities.get(type);

			// Merge individual NER results together
			if (onlpRes != null) {
				allResults = maxCountsMapMerge(onlpRes, allResults);
			}
			if (cnlpRes != null) {
				allResults = maxCountsMapMerge(cnlpRes, allResults);
			}
			if (nltkRes != null) {
				allResults = maxCountsMapMerge(nltkRes, allResults);
			}
			if (!allResults.isEmpty()) {
				maxJointAgreement.put(type, allResults);
			}
		}
		return maxJointAgreement;
	}

	// Given two maps, combines their key,value pairs such that the max value is taken. map2 is the map to merge to.
	private Map<String,Integer> maxCountsMapMerge(Map<String,Integer> map1, Map<String,Integer> map2) {
		for (String name : map1.keySet()) {
			// If name is already in map, then just take the higher count.
			Integer map1Count = map1.get(name);
			if (map2.containsKey(name)) {
				Integer map2Count = map2.get(name);
				if (map1Count > map2Count) {
					map2.put(name,map1Count);
				}
			}
			else {
				map2.put(name,map1Count);
			}
		}	
		return map2;
	}

	// Add results to the parsed file's metadata.
	private void pushToMetadata(String jsonData, String label) {
		this.mdata.set(label, jsonData);
	}

	public static void main(final String[] args) throws IOException,TikaException {
		System.out.println("main method");
	}


}






