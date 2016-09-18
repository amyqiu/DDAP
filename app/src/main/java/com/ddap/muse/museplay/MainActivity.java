package com.ddap.muse.museplay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

import java.util.List;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.Track;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends Activity implements
        PlayerNotificationCallback, ConnectionStateCallback {

    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "a95b0749da074aadbe18497395b76949";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "museplay://callback";

    private Player mPlayer;

    private static final int REQUEST_CODE = 1337;

    static final String EXTRA_TOKEN = "EXTRA_TOKEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        mPlayer = player;
                        mPlayer.addConnectionStateCallback(MainActivity.this);
                        mPlayer.addPlayerNotificationCallback(MainActivity.this);
                        //mPlayer.play("spotify:user:spotify:playlist:3rgsDhGHZxZ9sB9DQWQfuf");
                        playHappyPlaylist(player);
                        Log.d("Playlist", "Playlist was initiated");
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });

                /*SpotifyApi api = new SpotifyApi();

                String token = intent.getStringExtra(EXTRA_TOKEN);

                // Most (but not all) of the Spotify Web API endpoints require authorisation.
                // If you know you'll only use the ones that don't require authorisation you can skip this step
                api.setAccessToken(token);

                SpotifyService spotify = api.getService();

                spotify.getPlaylistTracks("spotify_netherlands","3r8ok7gRfb23XIQTZ3ttOK", new Callback<Pager<PlaylistTrack>>() {
                    @Override
                    public void success(Pager<PlaylistTrack> playlistTrackPager, Response response) {
                        Log.e("TEST", "GOT the tracks in playlist");
                        List<PlaylistTrack> tracks = playlistTrackPager.items;
                        for( PlaylistTrack pt : tracks){
                            Log.e("TEST", pt.track.name + " - " + pt.track.id);
                            Track track = pt.track;
                            mPlayer.play(track.uri);
                            Log.d("Track name", track.name);
                        }
                    }
                    @Override
                    public void failure(RetrofitError error) {
                        Log.e("TEST", error.getMessage());

                    }
                });*/
            }
        }
    }
    public String playHappyPlaylist(Player player){

        List<String> tracks = Arrays.asList("spotify:track:50mwGp3PgKoZldhBvfy2cf", "spotify:track:10Fb5CydCmYW3lqQGjNb04");
        for(String trackURI:tracks)
        {
            Log.d("URI", trackURI);
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(trackURI);
        }
        //Return the URI of the playlist
        return "3SXtTLpEuiEgovsSM6o4bF";
    }

    public String playStressPlaylist(Player player){

        List<String> tracks = Arrays.asList("spotify:track:50mwGp3PgKoZldhBvfy2cf", "spotify:track:10Fb5CydCmYW3lqQGjNb04");
        for(String trackURI:tracks)
        {
            Log.d("URI", trackURI);
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(trackURI);
        }
        //Return the URI of the playlist
        return "3SXtTLpEuiEgovsSM6o4bF";
    }

    public String playSleepPlaylist(Player player){

        List<String> tracks = Arrays.asList("spotify:track:50mwGp3PgKoZldhBvfy2cf", "spotify:track:10Fb5CydCmYW3lqQGjNb04");
        for(String trackURI:tracks)
        {
            Log.d("URI", trackURI);
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(trackURI);
        }
        //Return the URI of the playlist
        return "5UMleIsaNDK5LzZRbrbcXr";
    }

    public String playFocusPlaylist(Player player){

        List<String> tracks = Arrays.asList("spotify:track:50mwGp3PgKoZldhBvfy2cf", "spotify:track:10Fb5CydCmYW3lqQGjNb04");
        for(String trackURI:tracks)
        {
            Log.d("URI", trackURI);
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(trackURI);
        }
        //Return the URI of the playlist
        return "2ujjMpFriZ2nayLmrD1Jgl";
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }
}
