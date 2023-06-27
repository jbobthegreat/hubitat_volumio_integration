# hubitat_volumio_integration
Volumio Integration for Hubitat

Volumio Music Player Integration for Hubitat

Revision History
06.26.2023 - Initial Release

To reduce clutter, not all fields reported by Volumio are reported by this driver
Not all commands integral to the Hubitat Music Player capability are utilized

This driver uses the Volumio REST API. Reference Volumio REST API Manual --> https://volumio.github.io/docs/API/REST_API.html

Known issues:
- Volumio API response is sometimes out of sync for playerstatus.  Running "play", "pause", or "stop" again will sync it up

Installation: 
- Add to custom drivers section in Hubitat
- Create new virtual device, select Volumio Music Player driver
- Enter the hostname for your volumio hardware.  By default this is "volumio.local"
- Save settings to initialize
