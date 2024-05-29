import pandas as pd

artists_input_file = "../resources/datasets/original/artists.csv"
artists_output_file = "../resources/datasets/updated/artists_cleaned.csv"

spotify_youtube_input_file = "../resources/datasets/original/Spotify_Youtube.csv"
spotify_youtube_output_file = "../resources/datasets/updated/spotify_youtube_cleaned.csv"

spotify_youtube_df = pd.read_csv(spotify_youtube_input_file)
spotify_youtube_df = spotify_youtube_df.drop(spotify_youtube_df.columns[0], axis=1)
spotify_youtube_df = spotify_youtube_df.dropna().reset_index(drop=True)
spotify_youtube_df["Artist_id"] = spotify_youtube_df["Url_spotify"].astype(str).str.replace("https://open.spotify.com/artist/", "")
spotify_youtube_df["Track_id"] = spotify_youtube_df["Uri"].astype(str).str.replace("spotify:track:", "")
spotify_youtube_df["Video_id"] = spotify_youtube_df["Url_youtube"].astype(str).str.replace("https://www.youtube.com/watch?v=", "")
spotify_youtube_df = spotify_youtube_df.drop([i for i in range(10250, 11250)], axis='rows')
spotify_youtube_df.to_csv(spotify_youtube_output_file, index=False)

artists_df = pd.read_csv(artists_input_file)
artists_df["Streams"] = artists_df["Streams"].str.replace(",", "")
artists_df["As lead"] = artists_df["As lead"].str.replace(",", "")
artists_df["Solo"] = artists_df["Solo"].str.replace(",", "")
artists_df["As feature"] = artists_df["As feature"].str.replace(",", "")
artists_df = artists_df.merge(spotify_youtube_df[["Artist", "Artist_id"]], how="left", on="Artist")
artists_df = artists_df.dropna().reset_index(drop=True)
artists_df.to_csv(artists_output_file)
