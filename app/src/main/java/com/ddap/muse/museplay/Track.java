package com.ddap.muse.museplay;

/**
 * Created by amy-q on 2016-09-17.
 */
public class Track {

    private String URI;
    private String artist;
    private String name;
    private String album;

    public Track()
    {

    }

    public Track (String newURI, String newName, String newArtist, String newAlbum)
    {
        URI = newURI;
        artist = newArtist;
        name = newName;
        album = newAlbum;
    }

    public String getURI() {
        return URI;
    }

    public String getArtist(){
        return artist;
    }

    public String getName(){
        return name;
    }

    public String getAlbum(){
        return album;
    }

}
