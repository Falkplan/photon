package de.komoot.photon;

import com.google.common.base.Joiner;
import de.komoot.photon.elasticsearch.Searcher;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * date: 31.10.14
 *
 * @author christoph
 */
public class RequestHandler extends Route {
	private final Searcher searcher;
	private final Set<String> supportedLanguages;

	protected RequestHandler(String path, Searcher searcher, String languages) {
		super(path);
		this.searcher = searcher;
		this.supportedLanguages = new HashSet<String>(Arrays.asList(languages.split(",")));
	}

	@Override
	public String handle(Request request, Response response) {             
                // parse query term
                String reverse = request.queryParams("reverse");
            String query = request.queryParams("q");
            // parse preferred language
            String lang = request.queryParams("lang");
            String osmKey = request.queryParams("osm_key");
            String osmValue = request.queryParams("osm_value");

            String country = request.queryParams("country");
            Integer adminLevel;
            try {
                adminLevel = Integer.parseInt(request.queryParams("adminLevel"));
            } catch (Exception e) {
                adminLevel = 0;
            }

            if (query == null && reverse == null && adminLevel == 0) {
                halt(400, "missing search term 'q': /?q=berlin or 'reverse': /?reverse=true or 'adminlevel': /?adminlevel=8");
                }

                if(lang == null) lang = "en";
                if(!supportedLanguages.contains(lang)) {
                        halt(400, "language " + lang + " is not supported, supported languages are: " + Joiner.on(", ").join(supportedLanguages));
                }

                // parse location bias
                Double lon = null, lat = null;
                try {
                        lon = Double.valueOf(request.queryParams("lon"));
                        lat = Double.valueOf(request.queryParams("lat"));
                } catch(Exception nfe) {
                }
                
                if (reverse != null && reverse.equalsIgnoreCase("true") && (lat == null || lon == null)) {
                        halt(400, "missing search term 'lat' and/or 'lon': /?reverse=true&lat=51.5&lon=8.0");
                }

                // parse limit for search results
                int limit;
            int maxLimit = adminLevel == 0 ? 50 : 20000;

                try {
                    limit = Math.min(maxLimit, Integer.parseInt(request.queryParams("limit")));
                } catch(Exception e) {
                        limit = 15;
                }

                List<JSONObject> results;
                if (reverse != null && reverse.equalsIgnoreCase("true")) {
                        results = searcher.reverse(lang, lon, lat);
            } else if (adminLevel != 0) {
                results = searcher.adminlevel(lang, adminLevel, country, limit);
            } else {
                results = searcher.search(query, lang, lon, lat, osmKey, osmValue, limit, true);
            }
            if (results.isEmpty() && (reverse == null || !reverse.equalsIgnoreCase("true")) && adminLevel == 0) {
                        // try again, but less restrictive
                        results = searcher.search(query, lang, lon, lat, osmKey,osmValue,limit, false);
                }
        
		// build geojson
		final JSONObject collection = new JSONObject();
		collection.put("type", "FeatureCollection");
		collection.put("features", new JSONArray(results));

		response.type("application/json; charset=utf-8");
		response.header("Access-Control-Allow-Origin", "*");

		if(request.queryParams("debug") != null)
			return collection.toString(4);

		return collection.toString();
	}
}
