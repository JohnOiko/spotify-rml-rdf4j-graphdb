# spotify-rml-rdf4k-graphdb

This is a project that uses RML to transform datasets saved as csv files to a GraphDB RDF database. The project’s source code is inside the src\main package (and directory). Inside
of it there are three directories. The java directory includes the Main class which includes the java source code of the project and needs to be executed for the project to run. The python directory includes the python script clean_spotify which cleans the original datasets artists.csv and Spotify_Youtube.csv using the pandas library, makes the necessary programming transformations and creates the new cleaned versions of the datasets artists_cleaned.csv and spotify_youtube_cleaned.csv which are then used by the java source code. Lastly, the resources directory includes all the files needed by the source code:
- The original (_artists.csv_, _Spotify_Youtube.csv_) and cleaned/updated (_artists_cleaned.csv_, _spotify_youtube_cleaned.csv_) datasets.
- The domain ontology file _spotify.owl_.
- The RML transformations file _spotify.mappings.ttl_.
- The turtle file that is the output of the RML tranformations _spotify_triplets.ttl_.
- The text file with the ready to run SPARQL queries _sparql_queries.txt_.

Both datasets were downloaded from Kaggle. They are the [Spotify Stats](https://www.kaggle.com/datasets/meeratif/spotify-most-streamed-artists-of-all-time) and [Spotify and Youtube](https://www.kaggle.com/datasets/salvatorerastelli/spotify-and-youtube) datasets.

Before running the main class of the project, the python script clean_spotify.py needs to be run first to create the cleaned/updated datasets. In the GitHub repository, the updated datasets have already been created, so when you download it, it will be ready to run even without running the python script. The code tries to connect to a GraphDB repository at http://localhost:7200/repositories/spotify, so you need to have created a GraphDB repository named spotify using the OWL2-RL (Optimized) ruleset and GraphDB needs to be running in the background. Beware that uploading the triplets created by the RML transformations takes a couple of minutes to complete because of how many triplets need to be loaded (roughly 110690 triplets).
