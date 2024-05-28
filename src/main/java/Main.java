import be.ugent.rml.Executor;
import be.ugent.rml.Utils;
import be.ugent.rml.functions.FunctionLoader;
import be.ugent.rml.functions.lib.IDLabFunctions;
import be.ugent.rml.records.RecordsFactory;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.store.QuadStoreFactory;
import be.ugent.rml.store.RDF4JStore;
import be.ugent.rml.term.NamedNode;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws IOException {
        // Define the location of the necessary files
        String rootFolder = "./src/main/resources/";
        String templateFile = "ontologies/rml/spotify.mappings.ttl";
        String outputFile = "ontologies/triplets/spotify_triplets.ttl";
        String domainOntologyFile = "ontologies/domain/spotify.owl";

        // Run the RML mapper
        System.out.println("Applying RML transformations to input datasets...");
        runRMLMapper(rootFolder, templateFile, outputFile, false);
        System.out.println("RML transformations done\n");

        // Open a connection to the repository
        HTTPRepository repository = new HTTPRepository("http://localhost:7200/repositories/spotify");
        RepositoryConnection connection = repository.getConnection();

        // Load the domain ontology and its created triplets to the repository
        System.out.println("Loading domain ontology and created triplets to GraphDB database...");
        loadTtlToRepo(connection, rootFolder, domainOntologyFile, outputFile);
        System.out.println("Upload done\n");

        // Run the three queries and print their results
        System.out.println("=".repeat(99) + "\n");
        askFirstQuery(connection);
        askSecondQuery(connection);
        askThirdQuery(connection);

        connection.close();
        repository.shutDown();
    }

    static void runRMLMapper(String rootFolder, String templateFile, String outputFile, Boolean printTriplets) {
        try {
            String mapPath = rootFolder + templateFile;
            // Path to the mapping file that needs to be executed
            File mappingFile = new File(mapPath);

            // Get the mapping string stream
            InputStream mappingStream = new FileInputStream(mappingFile);

            // Load the mapping in a QuadStore
            QuadStore rmlStore = QuadStoreFactory.read(mappingStream);

            // Set up the basepath for the records factory, i.e., the basepath for the (local file) data sources
            RecordsFactory factory = new RecordsFactory(mappingFile.getParent());

            // Set up the functions used during the mapping
            Map<String, Class> libraryMap = new HashMap<>();
            libraryMap.put("IDLabFunctions", IDLabFunctions.class);

            FunctionLoader functionLoader = new FunctionLoader(null, libraryMap);

            // Set up the outputstore (needed when you want to output something else than nquads
            QuadStore outputStore = new RDF4JStore();

            // Create the Executor
            Executor executor = new Executor(rmlStore, factory, functionLoader, outputStore, Utils.getBaseDirectiveTurtle(mappingStream));

            // Execute the mapping
            QuadStore result = executor.executeV5(null).get(new NamedNode("rmlmapper://default.store"));

            // If enabled, output the result in console
            if (printTriplets) {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(System.out));
                result.write(out, "turtle");
                out.close();
            }

            // Output the results in a file
            String outPath = rootFolder + outputFile;
            Writer output = new FileWriter(outPath);
            result.write(output, "turtle");
            output.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void loadTtlToRepo(RepositoryConnection connection, String rootFolder, String domainOntologyFile, String outputFile) {
        // Clear the repository before we start
        connection.clear();

        // Load the ontology from the file that defines it made with protege
        connection.begin();

        // Adding the two turtle files: the ontology and the automatically created triplets based on the input csv files
        try {
            connection.add(new FileInputStream(rootFolder + domainOntologyFile),
                    "urn:base",
                    RDFFormat.TURTLE);
            connection.add(new FileInputStream(rootFolder + outputFile),
                    "urn:base",
                    RDFFormat.TURTLE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Committing the transaction persists the data
        connection.commit();
    }

    static void askFirstQuery(RepositoryConnection connection) {
        // Define the query as a string
        String queryString = "";
        queryString += "PREFIX spot: <http://www.csd.auth.gr/spotify#> \n";
        queryString += "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n";
        queryString += " \n";
        queryString += "select ?artist_name (xsd:integer(?total_streams_mil * 1000000) as ?total_streams) \n";
        queryString += "(sum(?views) as ?total_video_views) (?total_streams + ?total_video_views as ?total_clicks)  where { \n";
        queryString += "    ?video spot:views ?views . \n";
        queryString += "    ?track spot:usedIn ?video . \n";
        queryString += "    ?artist spot:isPrimaryArtistOf ?track ; \n";
        queryString += "            spot:artistName ?artist_name ; \n";
        queryString += "            spot:totalStreams ?total_streams_mil . \n";
        queryString += "} \n";
        queryString += "group by ?artist_name ?total_streams_mil \n";
        queryString += "order by desc(?total_clicks) \n";
        queryString += "LIMIT 10";

        TupleQuery query = connection.prepareTupleQuery(queryString);

        // Arrays to store the results of the query as columns
        ArrayList<String> artist_names = new ArrayList<>();
        ArrayList<String> total_streams = new ArrayList<>();
        ArrayList<String> total_video_views = new ArrayList<>();
        ArrayList<String> total_clicks = new ArrayList<>();

        // Store the results of the query as columns
        try (TupleQueryResult result = query.evaluate()) {
            for (BindingSet solution : result) {
                artist_names.add(solution.getValue("artist_name").stringValue());
                total_streams.add(solution.getValue("total_streams").stringValue());
                total_video_views.add(solution.getValue("total_video_views").stringValue());
                total_clicks.add(solution.getValue("total_clicks").stringValue());
            }
        }

        // Add the stored query results as columns to a table
        Table queryResult = Table.create("Top 10 most streamed and viewed artists").addColumns(
                StringColumn.create("artist_name", artist_names),
                StringColumn.create("total_streams", total_streams),
                StringColumn.create("total_video_views", total_video_views),
                StringColumn.create("total_clicks", total_clicks)
        );

        // Print the results
        System.out.println("-First SPARQL query (top 10 most streamed and viewed artists):\n\n" + queryString + "\n");
        System.out.println("-First SPARQL query results:\n");
        System.out.println(queryResult.printAll() + "\n");
        System.out.println("=".repeat(99) + "\n");
    }

    static void askSecondQuery(RepositoryConnection connection) {
        // Define the query as a string
        String queryString = "";
        queryString += "PREFIX spot: <http://www.csd.auth.gr/spotify#> \n";
        queryString += " \n";
        queryString += "select ?album_title ?artist_name (sum(?streams) as ?album_streams) where { \n";
        queryString += "    ?album spot:includes ?track . \n";
        queryString += "    ?track spot:primaryArtist ?artist . \n";
        queryString += "    ?album spot:albumTitle ?album_title . \n";
        queryString += "    ?artist spot:artistName ?artist_name. \n";
        queryString += "    ?track spot:streams ?streams . \n";
        queryString += "} \n";
        queryString += "group by ?album_title ?artist_name \n";
        queryString += "order by desc(?album_streams) \n";
        queryString += "LIMIT 5";

        TupleQuery query = connection.prepareTupleQuery(queryString);

        // Arrays to store the results of the query as columns
        ArrayList<String> album_titles = new ArrayList<>();
        ArrayList<String> artist_names = new ArrayList<>();
        ArrayList<String> album_streams = new ArrayList<>();

        // Store the results of the query as columns
        try (TupleQueryResult result = query.evaluate()) {
            for (BindingSet solution : result) {
                album_titles.add(solution.getValue("album_title").stringValue());
                artist_names.add(solution.getValue("artist_name").stringValue());
                album_streams.add(solution.getValue("album_streams").stringValue());
            }
        }

        // Add the stored query results as columns to a table
        Table queryResult = Table.create("Top 5 most streamed and viewed artists").addColumns(
                StringColumn.create("album_title", album_titles),
                StringColumn.create("artist_name", artist_names),
                StringColumn.create("album_streams", album_streams)
        );

        // Print the results
        System.out.println("-Second SPARQL query (top 5 most streamed and viewed artists):\n\n" + queryString + "\n");
        System.out.println("-Second SPARQL query results:\n");
        System.out.println(queryResult.printAll() + "\n");
        System.out.println("=".repeat(99) + "\n");
    }

    static void askThirdQuery(RepositoryConnection connection) {
        // Define the query as a string
        String queryString = "";
        queryString += "PREFIX spot: <http://www.csd.auth.gr/spotify#>  \n";
        queryString += " \n";
        queryString += "select ?artist_name (round(sum(?likes)/sum(?views)* 10000)/10000 as ?total_view_like_ratio) \n";
        queryString += "(sum(?likes) as ?total_likes) (sum(?views) as ?total_views) where { \n";
        queryString += "    ?video spot:views ?views . \n";
        queryString += "    ?video spot:likes ?likes . \n";
        queryString += "    ?track spot:usedIn ?video . \n";
        queryString += "    ?artist spot:isPrimaryArtistOf ?track ; \n";
        queryString += "            spot:artistName ?artist_name . \n";
        queryString += "} \n";
        queryString += "group by ?artist ?artist_name \n";
        queryString += "having(?total_views >= 1000000000) \n";
        queryString += "order by desc(?total_view_like_ratio) \n";
        queryString += "LIMIT 25";

        TupleQuery query = connection.prepareTupleQuery(queryString);

        // Arrays to store the results of the query as columns
        ArrayList<String> artist_names = new ArrayList<>();
        ArrayList<String> total_view_like_ratios = new ArrayList<>();
        ArrayList<String> total_likes = new ArrayList<>();
        ArrayList<String> total_views = new ArrayList<>();

        // Store the results of the query as columns
        try (TupleQueryResult result = query.evaluate()) {
            for (BindingSet solution : result) {
                artist_names.add(solution.getValue("artist_name").stringValue());
                total_view_like_ratios.add(solution.getValue("total_view_like_ratio").stringValue());
                total_likes.add(solution.getValue("total_likes").stringValue());
                total_views.add(solution.getValue("total_views").stringValue());
            }
        }

        // Add the stored query results as columns to a table
        Table queryResult = Table.create("Top 25 highest likes to views ratio artists with at least a billion views").addColumns(
                StringColumn.create("artist_name", artist_names),
                StringColumn.create("total_view_like_ratio", total_view_like_ratios),
                StringColumn.create("total_likes", total_likes),
                StringColumn.create("total_views", total_views)
        );

        // Print the results
        System.out.println("-Third SPARQL query (top 25 highest likes to views ratio artists with at least a billion views):\n\n" + queryString + "\n");
        System.out.println("-Third SPARQL query results:\n");
        System.out.println(queryResult.printAll() + "\n");
        System.out.println("=".repeat(99) + "\n");
    }
}
