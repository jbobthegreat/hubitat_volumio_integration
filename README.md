# Hubitat Volumio Integration
Volumio Integration for Hubitat

Volumio Music Player Integration for Hubitat
https://github.com/jbobthegreat/hubitat_volumio_integration

Revision History
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

Known issues:
- Volumio API sometimes sends multiple push notifications in quick succession, faster than Hubitat can update the device attributes.  This produces duplicate log entries, but has no other detrimental effects.
- As of Volumio 3.512, it's not possible to remove entries from Volumio's push notifications.  However, a reboot of the Volumio device will clear the notification list.  This driver includes an option to re-enroll for push notifications daily at a specified time to work around this.
- Volumio does not send a notification when playlists are created or deleted.  In order to get a correct list of Playlists to appear in Current States, use the Refresh command.  

Installation: 
- Add contents of raw volumio_integration_driver.groovy file to custom drivers section in Hubitat
- Create new virtual device, select Volumio Music Player driver
- Enter the hostname for your volumio hardware.  By default this is "volumio.local", but it can be any hostname or IP address.  "http://" or "https://" will be removed if included
- Choose whether to automatically re-enroll in push notifications nightly and choose a time.  By default, this is "No"
- Save settings to initialize
- Recommended to disable debug logging and API debug logging unless there is a problem.  It will make the log huge

Misc Notes: 
- If needed for whatever reason, use the Refresh command to perform a manual data update
- Running the Enable Push Notifications command will manually re-enroll for push notifications
- If either the Hubitat hub or the Volumio device change MAC addresses, re-run the initialize command
  - The initialize command checks the DNI against the Volumio MAC address, enrolls in push notifications, and schedules automatic re-enrollment daily if the preference is set to do so
- This driver was tested only for local Volumio devices on the same network as the Hubitat hub.  It may work with remote access devices or over a VPN, but this is untested. 
- The track URI is shown in the "uri" attribute shown in Current States.
- The Play Track command will replace the Volumio queue and begin playback on the specified URI.  
- The Set Track command will add a specified URI to the end of the current playback queue.  
