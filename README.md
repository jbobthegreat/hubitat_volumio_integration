# Hubitat Volumio Integration
Volumio Integration for Hubitat

Volumio Music Player Integration for Hubitat
https://github.com/jbobthegreat/hubitat_volumio_integration

## Revision History
- 1.06 03.01.2024 - Updated random() and repeat() methods to either toggle or set explicitly
		    Added ability to play Pandora channels to Play Track command
		    Bug fixes
- 1.05 02.09.2024 - Fixed bug preventing setTrack and playTrack from working with some music services
- 1.04 02.08.2024 - Added Repeat and Random toggle commands
                    Added uri and otherzones attributes
				            Added functionality to setTrack (add to queue) and playTrack (replace queue and play) commands
                    Added functionality to get and play specified Volumio playlists - Contributed by Ashrond (modified)
- 1.03 11.17.2023 - Restructured data retrieval to use Volumio's push notification API instead of constantly polling in order to reduce load on hub
                    Ref: https://developers.volumio.com/api/rest-api#notifications
                    Set the device network ID (DNI) to Volumio's MAC address during initialization.  Necessary in order for Hubitat to forward push notifications to the correct device
                    (Hubitat receives POST data on port 39501 and forwards it to a device whose DNI matches the MAC address of the connection origin, using the parse() method in that device)
                    Multiple small bug fixes
- 1.02 07.06.2023 - Added trackData JSON object to refresh() method
- 1.01 07.04.2023 - Cleaned up attributes to avoid duplication of built-in attributes from MusicPlayer and AudioVolume capabilities
                    Added level and trackDescription to refresh() method
                    Ref: https://docs2.hubitat.com/developer/driver/capability-list
- 1.00 06.25.2023 - Initial Release

To reduce clutter, not all fields reported by Volumio are reported by this driver
Not all commands integral to the Hubitat Music Player capability are utilized.  The following commands are not used, but cannot be hidden
- Play Text
- Restore Track
- Resume Track

This driver uses the Volumio REST API. Reference Volumio REST API Manual --> https://volumio.github.io/docs/API/REST_API.html

## Known issues:
- Volumio API sometimes sends multiple push notifications in quick succession, faster than Hubitat can update the device attributes.  This produces duplicate log entries, but has no other detrimental effects.
- As of Volumio 3.616, it's not possible to remove entries from Volumio's push notifications.  However, a reboot of the Volumio device will clear the notification list.  This driver includes an option to re-enroll for push notifications daily at a specified time to work around this.
- Volumio does not send a notification when playlists are created or deleted.  In order to get a correct list of Playlists to appear in Current States, use the Refresh command.
- Using a URI to a local Volumio playlist with the Play Track or Set Track commands does not work.
- When using a Pandora station URI with the Set Track command, the station will begin playing immediately instead of merely adding to the queue. 

## Installation: 
- Add contents of raw volumio_integration_driver.groovy file to custom drivers section in Hubitat
- Create new virtual device, select Volumio Music Player driver
- Enter the hostname for your volumio hardware.  By default this is "volumio.local", but it can be any hostname or IP address.  "http://" or "https://" will be removed if included
- Choose whether to automatically re-enroll in push notifications nightly and choose a time (only required if your Volumio device reboots daily).  By default, this is "No"
- Save settings to initialize.  Driver will automatically enroll in push notifications when initialized
- Recommended to disable debug logging and API debug logging unless there is a problem.  It will make the log huge

## How to find the URI for a track, playlist, or station:
- The Play Track and Set Track commands both require the URI of the item to be played.  The URI can be an individual track from any music service, but the commands will also accept playlists/albums from Spotify, YouTube, etc, or Pandora stations
- For individual tracks, the URI is shown in Current States under the attribute "uri"
- Other URI's can be accessed using the browse function of the Volumio API.  Reference https://developers.volumio.com/api/rest-api#browsing
- The quick version
- In a web browser, navigate to
  ```
  http://[volumio-hostname]/api/v1/browse
  ```
- This will return a JSON object that should look something like the following.  Most browsers will pretty it up for readability
  ```
  {
    "navigation": {
      "lists": [
        {
          "albumart": "/albumart?sourceicon=music_service/mpd/musiclibraryicon.png",
          "name": "Music Library",
          "uri": "music-library",
          "plugin_type": "music_service",
          "plugin_name": "mpd"
        },
        {
          "name": "YouTube2",
          "uri": "youtube2",
          "plugin_type": "music_service",
          "plugin_name": "youtube2",
          "albumart": "/albumart?sourceicon=music_service/youtube2/dist/assets/images/youtube.svg"
        },
        {
          "name": "Spotify",
          "uri": "spotify",
          "plugin_type": "music_service",
          "plugin_name": "spop",
          "albumart": "/albumart?sourceicon=music_service/spop/spotify.png"
        }
      ]
    }
  }
  ```
- Choose the music service you want and make note of the "uri" field
- Navigate to the following, inserting your desired URI.  For example, I'm using Spotify below
  ```
  http://[volumio-hostname]/api/v1/browse?uri=spotify
  ```
- This will return another JSON object containing your Spotify items
  ```
    {
    "navigation": {
      "lists": [
        {
          "availableListViews": [
            "grid",
            "list"
          ],
          "type": "title",
          "title": "My Music",
          "items": [
            {
             "service": "spop",
              "type": "streaming-category",
              "title": "My Playlists",
              "artist": "",
              "album": "",
              "albumart": "/albumart?sourceicon=music_service/spop/icons/playlist.png",
              "uri": "spotify/playlists"
            },
            {
              "service": "spop",
              "type": "streaming-category",
              "title": "My Albums",
              "artist": "",
              "album": "",
              "albumart": "/albumart?sourceicon=music_service/spop/icons/album.png",
              "uri": "spotify/myalbums"
            },
            {
              "service": "spop",
              "type": "streaming-category",
              "title": "My Tracks",
              "artist": "",
              "album": "",
              "albumart": "/albumart?sourceicon=music_service/spop/icons/track.png",
              "uri": "spotify/mytracks"
            }
           ]
        }
      ]
    }
  }
  ```
- Choose the category you want and again make note of the URI.
- Navigate to the next desired URI, as before.
  ```
  http://[volumio-hostname]/api/v1/browse?uri=spotify/myalbums
  ```
- Continue in this pattern, until you find the track, album, or station URI you want to play, for example
  ```
  {
    "navigation": {
      "prev": {
        "uri": "spotify"
      },
      "lists": [
        {
          "availableListViews": [
            "list",
            "grid"
          ],
          "items": [
            {
     --->     "service": "spop",
              "type": "folder",
              "title": "This Endris Night",
              "albumart": "https://i.scdn.co/image/abcdefghijklmnopqrstuvwxyz1234567890",
     --->     "uri": "spotify:album:1234567890ABCDEFGHIJKLMN"
            }
          ]
        }
      ]
    }
  }
  ```
- Once located, the URI "spotify:album:1234567890ABCDEFGHIJKLMN" and music service "spop" can be given to the Play Track command using Rule Machine or manual controls to play that album.  Likewise for playlists or individual tracks

## Misc Notes: 
- If needed for whatever reason, use the Refresh command to perform a manual data update
- Running the Enable Push Notifications command will manually re-enroll for push notifications.  This can be run if the Volumio device restarts unexpectedly, rather than waiting for the automatic re-enroll
- If either the Hubitat hub or the Volumio device change MAC addresses, re-run the initialize command
  - The initialize command checks the DNI against the Volumio MAC address, enrolls in push notifications, and schedules automatic re-enrollment daily if the preference is set to do so
- This driver was tested only for local Volumio devices on the same network as the Hubitat hub.  It may work with remote access devices or over a VPN, but this is untested. 
- The track URI and Music Service required for Play Track and Set Track are shown in Current States, and are accessible to rules. 
- The Play Track command will replace the Volumio queue and begin playback on the specified URI.  The title field is optional, but can be filled in for web radio stations if desired. 
- The Set Track command will add a specified URI to the end of the current playback queue.
- Because of how Pandora stations work, it is also not possible to browse to an individual Pandora song - only to stations
