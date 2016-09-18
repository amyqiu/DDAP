package com.ddap.muse.museplay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.AnnotationData;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.LibmuseVersion;
import com.choosemuse.libmuse.LogManager;
import com.choosemuse.libmuse.MessageType;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConfiguration;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseFileFactory;
import com.choosemuse.libmuse.MuseFileReader;
import com.choosemuse.libmuse.MuseFileWriter;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;
import com.choosemuse.libmuse.Result;
import com.choosemuse.libmuse.ResultLevel;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


public class MainActivity extends Activity implements
        PlayerNotificationCallback, ConnectionStateCallback {

    /**
     * Tag used for logging purposes.
     */
    private final String TAG = "TestLibMuseAndroid";

    /**
     * The MuseManager is how you detect Muse headbands and receive notifications
     * when the list of available headbands changes.
     */
    private MuseManagerAndroid manager;

    /**
     * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
     * headband, register listeners to receive EEG data and get headband
     * configuration and version information.
     */
    private Muse muse;

    /**
     * The ConnectionListener will be notified whenever there is a change in
     * the connection state of a headband, for example when the headband connects
     * or disconnects.
     *
     * Note that ConnectionListener is an inner class at the bottom of this file
     * that extends MuseConnectionListener.
     */
    private ConnectionListener connectionListener;

    /**
     * The DataListener is how you will receive EEG (and other) data from the
     * headband.
     *
     * Note that DataListener is an inner class at the bottom of this file
     * that extends MuseDataListener.
     */
    private DataListener dataListener;

    /**
     * Data comes in from the headband at a very fast rate; 220Hz, 256Hz or 500Hz,
     * depending on the type of headband and the preset configuration.  We buffer the
     * data that is read until we can update the UI.
     *
     * The stale flags indicate whether or not new data has been received and the buffers
     * hold the values of the last data packet received.  We are displaying the EEG, ALPHA_RELATIVE
     * and ACCELEROMETER values in this example.
     *
     * Note: the array lengths of the buffers are taken from the comments in
     * MuseDataPacketType, which specify 3 values for accelerometer and 6
     * values for EEG and EEG-derived packets.
     */
    private final double[] eegBuffer = new double[6];
    private boolean eegStale;
    private final double[] accelBuffer = new double[3];
    private boolean accelStale;

    /**
     * We will be updating the UI using a handler instead of in packet handlers because
     * packets come in at a very high frequency and it only makes sense to update the UI
     * at about 60fps. The update functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private final Handler handler = new Handler();

    /**
     * In the UI, the list of Muses you can connect to is displayed in a Spinner object for this example.
     * This spinner adapter contains the MAC addresses of all of the headbands we have discovered.
     */
    private ArrayAdapter<String> spinnerAdapter;

    /**
     * It is possible to pause the data transmission from the headband.  This boolean tracks whether
     * or not the data transmission is enabled as we allow the user to pause transmission in the UI.
     */
    private boolean dataTransmission = true;

    /**
     * To save data to a file, you should use a MuseFileWriter.  The MuseFileWriter knows how to
     * serialize the data packets received from the headband into a compact binary format.
     * To read the file back, you would use a MuseFileReader.
     */
    private final AtomicReference<MuseFileWriter> fileWriter = new AtomicReference<>();

    /**
     * We don't want file operations to slow down the UI, so we will defer those file operations
     * to a handler on a separate thread.
     */
    private final AtomicReference<Handler> fileHandler = new AtomicReference<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        Log.i(TAG, "LibMuse version=" + LibmuseVersion.instance().getString());

        WeakReference<MainActivity> weakActivity =
                new WeakReference<MainActivity>(this);
        // Register a listener to receive connection state changes.
        connectionListener = new ConnectionListener(weakActivity);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakActivity);
        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(new MuseL(weakActivity));

        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        ensurePermissions();

        // Load and initialize our UI.

        // Start up a thread for asynchronous file operations.
        // This is only needed if you want to do File I/O.
        fileThread.start();

        // Start our asynchronous updates of the UI.
        //handler.post(tickUi);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);


        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    //--------------------------------------
    // Lifecycle / Connection code

    protected void onPause() {
        super.onPause();
        // It is important to call stopListening when the Activity is paused
        // to avoid a resource leak from the LibMuse library.
        manager.stopListening();
    }

    //--------------------------------------
    // Permissions

    /**
     * The ACCESS_COARSE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.

            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which){
                            dialog.dismiss();
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    0);
                        }
                    };

            // This is the context dialog which explains to the user the reason we are requesting
            // this permission.  When the user presses the positive (I Understand) button, the
            // standard Android permission dialog will be displayed (as defined in the button
            // listener above).
            //AlertDialog introDialog = new AlertDialog.Builder(this)
                    //.setTitle(R.string.permission_dialog_title)
                    //.setMessage(R.string.permission_dialog_description)
                    //.setPositiveButton(R.string.permission_dialog_understand, buttonListener)
                    //.create();
           // introDialog.show();
        }
    }


    //--------------------------------------
    // Listeners

    /**
     * You will receive a callback to this method each time a headband is discovered.
     * In this example, we update the spinner with the MAC address of the headband.
     */
    public void museListChanged() {
        final List<Muse> list = manager.getMuses();
        spinnerAdapter.clear();
        for (Muse m : list) {
            spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
        }
    }

    /**
     * You will receive a callback to this method each time there is a change to the
     * connection state of one of the headbands.
     * @param p     A packet containing the current and prior connection states
     * @param muse  The headband whose state changed.
     */
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;
        Log.i(TAG, status);

        // Update the UI with the change in connection state.
        handler.post(new Runnable() {
            @Override
            public void run() {
                final MuseVersion museVersion = muse.getMuseVersion();
            }
        });

        if (current == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Muse disconnected:" + muse.getName());
            // Save the data file once streaming has stopped.
            saveFile();
            // We have disconnected from the headband, so set our cached copy to null.
            this.muse = null;
        }
    }

    /**
     * You will receive a callback to this method each time the headband sends a MuseDataPacket
     * that you have registered.  You can use different listeners for different packet types or
     * a single listener for all packet types as we have done here.
     * @param p     The data packet containing the data from the headband (eg. EEG data)
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
        writeDataPacketToFile(p);

        // valuesSize returns the number of data values contained in the packet.
        final long n = p.valuesSize();
        switch (p.packetType()) {
            case EEG:
                assert(eegBuffer.length >= n);
                getEegChannelValues(eegBuffer,p);
                eegStale = true;
                break;
            case ACCELEROMETER:
                assert(accelBuffer.length >= n);
                getAccelValues(p);
                accelStale = true;
                break;
            case BATTERY:
            case DRL_REF:
            case QUANTIZATION:
            default:
                break;
        }
    }

    /**
     * You will receive a callback to this method each time an artifact packet is generated if you
     * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
     * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
     * @param p     The artifact packet with the data from the headband.
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
    }

    /**
     * Helper methods to get different packet values.  These methods simply store the
     * data in the buffers for later display in the UI.
     *
     * getEegChannelValue can be used for any EEG or EEG derived data packet type
     * such as EEG, ALPHA_ABSOLUTE, ALPHA_RELATIVE or HSI_PRECISION.  See the documentation
     * of MuseDataPacketType for all of the available values.
     * Specific packet types like ACCELEROMETER, GYRO, BATTERY and DRL_REF have their own
     * getValue methods.
     */
    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }

    private void getAccelValues(MuseDataPacket p) {
        accelBuffer[0] = p.getAccelerometerValue(Accelerometer.X);
        accelBuffer[1] = p.getAccelerometerValue(Accelerometer.Y);
        accelBuffer[2] = p.getAccelerometerValue(Accelerometer.Z);
    }



    //--------------------------------------
    // File I/O

    /**
     * We don't want to block the UI thread while we write to a file, so the file
     * writing is moved to a separate thread.
     */
    private final Thread fileThread = new Thread() {
        @Override
        public void run() {
            Looper.prepare();
            fileHandler.set(new Handler());
            final File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            final File file = new File(dir, "new_muse_file.muse" );
            // MuseFileWriter will append to an existing file.
            // In this case, we want to start fresh so the file
            // if it exists.
            if (file.exists()) {
                file.delete();
            }
            Log.i(TAG, "Writing data to: " + file.getAbsolutePath());
            fileWriter.set(MuseFileFactory.getMuseFileWriter(file));
            Looper.loop();
        }
    };

    /**
     * Writes the provided MuseDataPacket to the file.  MuseFileWriter knows
     * how to write all packet types generated from LibMuse.
     * @param p     The data packet to write.
     */
    private void writeDataPacketToFile(final MuseDataPacket p) {
        Handler h = fileHandler.get();
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    fileWriter.get().addDataPacket(0, p);
                }
            });
        }
    }

    /**
     * Flushes all the data to the file and closes the file writer.
     */
    private void saveFile() {
        Handler h = fileHandler.get();
        if (h != null) {
            h.post(new Runnable() {
                @Override public void run() {
                    MuseFileWriter w = fileWriter.get();
                    // Annotation strings can be added to the file to
                    // give context as to what is happening at that point in
                    // time.  An annotation can be an arbitrary string or
                    // may include additional AnnotationData.
                    w.addAnnotationString(0, "Disconnected");
                    w.flush();
                    w.close();
                }
            });
        }
    }

    /**
     * Reads the provided .muse file and prints the data to the logcat.
     * @param name  The name of the file to read.  The file in this example
     *              is assumed to be in the Environment.DIRECTORY_DOWNLOADS
     *              directory.
     */
    private void playMuseFile(String name) {

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, name);

        final String tag = "Muse File Reader";

        if (!file.exists()) {
            Log.w(tag, "file doesn't exist");
            return;
        }

        MuseFileReader fileReader = MuseFileFactory.getMuseFileReader(file);

        // Loop through each message in the file.  gotoNextMessage will read the next message
        // and return the result of the read operation as a Result.
        Result res = fileReader.gotoNextMessage();
        while (res.getLevel() == ResultLevel.R_INFO && !res.getInfo().contains("EOF")) {

            MessageType type = fileReader.getMessageType();
            int id = fileReader.getMessageId();
            long timestamp = fileReader.getMessageTimestamp();

            Log.i(tag, "type: " + type.toString() +
                    " id: " + Integer.toString(id) +
                    " timestamp: " + String.valueOf(timestamp));

            switch(type) {
                // EEG messages contain raw EEG data or DRL/REF data.
                // EEG derived packets like ALPHA_RELATIVE and artifact packets
                // are stored as MUSE_ELEMENTS messages.
                case EEG:
                case BATTERY:
                case ACCELEROMETER:
                case QUANTIZATION:
                case GYRO:
                case MUSE_ELEMENTS:
                    MuseDataPacket packet = fileReader.getDataPacket();
                    Log.i(tag, "data packet: " + packet.packetType().toString());
                    break;
                case VERSION:
                    MuseVersion version = fileReader.getVersion();
                    Log.i(tag, "version" + version.getFirmwareType());
                    break;
                case CONFIGURATION:
                    MuseConfiguration config = fileReader.getConfiguration();
                    Log.i(tag, "config" + config.getBluetoothMac());
                    break;
                case ANNOTATION:
                    AnnotationData annotation = fileReader.getAnnotation();
                    Log.i(tag, "annotation" + annotation.getData());
                    break;
                default:
                    break;
            }

            // Read the next message.
            res = fileReader.gotoNextMessage();
        }
    }

    //--------------------------------------
    // Listener translators
    //
    // Each of these classes extend from the appropriate listener and contain a weak reference
    // to the activity.  Each class simply forwards the messages it receives back to the Activity.
    class MuseL extends MuseListener {
        final WeakReference<MainActivity> activityRef;

        MuseL(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void museListChanged() {
            activityRef.get().museListChanged();
        }
    }

    class ConnectionListener extends MuseConnectionListener {
        final WeakReference<MainActivity> activityRef;

        ConnectionListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
            activityRef.get().receiveMuseConnectionPacket(p, muse);
        }
    }

    class DataListener extends MuseDataListener {
        final WeakReference<MainActivity> activityRef;

        DataListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
            activityRef.get().receiveMuseDataPacket(p, muse);
        }

        @Override
        public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
            activityRef.get().receiveMuseArtifactPacket(p, muse);
        }
    }


    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "a95b0749da074aadbe18497395b76949";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "museplay://callback";

    private Player mPlayer;

    private static final int REQUEST_CODE = 1337;

    static final String EXTRA_TOKEN = "EXTRA_TOKEN";
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

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

        List<Track> happyTracks = new ArrayList<>();
        happyTracks.add(0, new Track ("7BKLCZ1jbUBVqRi2FVlTVw", "Closer","The Chainsmokers / Halsey", "Closer" ));
        happyTracks.add(1, new Track ("22Re0OiJOEP7xk5XwshMEA", "Don’t Need Nobody","Ellie Goulding", "Delirium" ));
        happyTracks.add(2, new Track ("27SdWb2rFzO6GWiYDBTD9j", "Cheap Thrills","Sia", "This is Acting" ));
        happyTracks.add(3, new Track ("7xrA11O07xo57Bbg6p4hck", "Body Say","Demi Lovato", "Body Say" ));
        happyTracks.add(4, new Track ("2meEiZKWkiN28gITzFwQo5", "Into You","Ariana Grande", "Dangerous Woman" ));

        for(Track track:happyTracks)
        {
            Log.d("URI", track.getURI());
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(track.getURI());
        }
        //Return the URI of the playlist
        return "2JkjXscXs35c5wKE5ZeaYK";
    }

    public String playStressedPlaylist(Player player){

        List<Track> destressTracks = new ArrayList<>();
        destressTracks.add(0, new Track ("22NBlgXMSlPFSXoQn9GEpV", "Isle of Water","Jonatan Mollberg", "Flakes of Color" ));
        destressTracks.add(1, new Track ("5LopRVLII1pAVeQxm8tNpI", "Embers","Tracey Chattaway", "Third Place" ));
        destressTracks.add(2, new Track ("2OkkLiBryxLeqtPmaecas4", "Perla","Islands of Light", "Elements" ));
        destressTracks.add(3, new Track ("06FmYX8gaPzdYkDSMGKHzc", "A Summer by the Sea","Library Tapes", "Escapism" ));
        destressTracks.add(4, new Track ("2he1OylKI9ooYHDxL0nV37", "Dawning","LUCHS", "Dawning" ));

        for(Track track:destressTracks)
        {
            Log.d("URI", track.getURI());
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(track.getURI());
        }
        //Return the URI of the playlist
        return "3SXtTLpEuiEgovsSM6o4bF";
    }

    public String playFocusedPlaylist(Player player){

        List<Track> focusTracks = new ArrayList<>();
        focusTracks.add(0, new Track ("2vr6fjMZm1pliQ3a6HL8MT", "La Clairlere","Piano Novel", "La Clairiere" ));
        focusTracks.add(1, new Track ("07Hk13m0dIGWhGr9B4yEsJ", "Etude","Joep Beving", "Solipsism" ));
        focusTracks.add(2, new Track ("3aLof1zmaQ0GLcAc9YQ3Fq", "Nuvole Bianche","Ludovico Eianaudi", "Una Mattina" ));
        focusTracks.add(3, new Track ("6HJovBuWj41I2jL94QpVj5", "Hers","The Arcadian Wild", "The Arcadian Wild" ));
        focusTracks.add(4, new Track ("0gQiiQnqlY45tRn6AAh8Hj", "Interlude for Piano","Peter Bradley Adams", "Between Us" ));

        for(Track track:focusTracks)
        {
            Log.d("URI", track.getURI());
            mPlayer = player;
            mPlayer.addConnectionStateCallback(MainActivity.this);
            mPlayer.addPlayerNotificationCallback(MainActivity.this);
            mPlayer.play(track.getURI());
        }
        //Return the URI of the playlist
        return "63dDpdoVHvx5RkK87g4LKk";
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

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.ddap.muse.museplay/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.ddap.muse.museplay/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }
}
