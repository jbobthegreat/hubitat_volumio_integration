/**
Volumio Music Player Integration for Hubitat
Author: Flint IronStag
https://github.com/jbobthegreat/hubitat_volumio_integration

Revision History
1.04a - Added functionality to fetch and select playlists from Volumio -Cont Ashrond
1.03 - Restructured data retrieval to use Volumio's push notification API
       Other revisions as previously documented
This driver uses the Volumio REST API. Reference Volumio REST API Manual --> https://volumio.github.io/docs/API/REST_API.html
*/

metadata {
    definition (
        name: "Volumio Music Player",
        namespace: "volumio",
        author: "FlintIronStag"
    ) {
        capability "MusicPlayer"
        capability "AudioVolume"
        capability "Initialize"
        capability "Refresh"

        command "clearQueue"
        command "enablePushNotifications"
        command "fetchPlaylists" // Custom command to fetch playlists

        attribute "title", "string"
        attribute "artist", "string"
        attribute "album", "string"
        attribute "musicservice", "string"
    }
}

preferences {
    input "volumioHost", "text", title: "Enter Volumio hostname or IP", defaultValue: "volumio.local", displayDuringSetup: true, required: true
    input "schedulePush", "enum", title: "Automatically re-enroll in push notifications nightly?", options: ["No","12 AM","1 AM","2 AM","3 AM","4 AM","5 AM","6 AM","7 AM","8 AM","9 AM","10 AM","11 AM","12 PM","1 PM","2 PM","3 PM","4 PM","5 PM","6 PM","7 PM","8 PM","9 PM","10 PM","11 PM"], defaultValue: "No", displayDuringSetup: true, required: true
    input "debugOutput", "bool", title: "Enable device debug logging?", defaultValue: false, displayDuringSetup: false, required: false
    input "APIdebugOutput", "bool", title: "Enable API debug logging?", defaultValue: false, displayDuringSetup: false, required: false
    input name: "selectedPlaylist", type: "enum", title: "Select Playlist (Click 'Fetch Playlists' button first)", options: state.playlists, required: false
    input "randomizePlaylist", "bool", title: "Randomize Playlist", defaultValue: false, required: false
    input "repeatPlaylistTrack", "bool", title: "Repeat Playlist Track", defaultValue: false, required: false
}

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def installed() {
    log.info "${device.getLabel()}: Installed with settings: ${settings}"
}

def updated() {
    log.info "${device.getLabel()}: Updated with settings: ${settings}"
    initialize()
    if (settings.selectedPlaylist) {
        SetPlayPlayListandName()
    }
}

def initialize() {
    log.info "${device.getLabel()}: Initializing"
    setDNI()
    scheduler(settings.schedulePush)
    enablePushNotifications()
}

//Scheduler for re-enrolling push notifications
def scheduler(time) {
    if (time == "No") {
        unschedule(enablePushNotifications)
    } else {
        int hr = time.split(" ")[0] as int
        if (hr == 12) { hr -= 12 }  //to deal with 12AM and 12PM correctly
        if (time.split(" ")[1] == "PM") { hr += 12 }
        schedule("0 0 ${hr} * * ?", enablePushNotifications)  //Cron schedule running enablePushNotifications()
        log.info "${device.getLabel()}: Scheduled push notification enrollment every day at ${time}"
    }    
}

//Set device network ID to be Volumio MAC address to catch push notifications
def setDNI() {
    volumioGet("getSystemInfo")
    def host = respData.host
    def ip = host.drop(host.indexOf("//")+2)  //IP clean-up
    def volumioMAC = getMACFromIP(ip)
    if (device.deviceNetworkId != volumioMAC) {
        device.deviceNetworkId = volumioMAC  //set device network ID to MAC
        log.info "${device.getLabel()}: Device Network ID set to Volumio MAC address ${volumioMAC}"
    } else {
        log.info "${device.getLabel()}: Device Network ID already set to Volumio MAC address ${volumioMAC}"
    }
}

//Enroll for push notifications
def enablePushNotifications() {
    def hubIP = location.hub.localIP
    def hubPort = "39501"
    def host = settings.volumioHost
    if (host.contains("//")) { host = host.drop(host.indexOf("//")+2) }  //hostname clean-up
    def path = "/api/v1/pushNotificationUrls?url=http://${hubIP}:${hubPort}"
    def body = "{\"url\":\"http://${hubIP}:${hubPort}\"}"
    def method = "POST"
    def headers = [:] 
    headers.put("HOST", "${host}:80")
    headers.put("Content-Type", "application/json")
    try {
        def hubAction = new hubitat.device.HubAction(
            method: method,
            path: path,
            body: body,
            headers: headers
        )
        log.info "${device.getLabel()}: Push Notifications Enabled"
        return hubAction
    } catch (Exception e) {
        log.error "enablePushNotifications exception ${e} on ${hubAction}"
    }  
}

//Manually refresh data
def refresh() {
    volumioGet("getState")
    parseResp(respData)
}

//Manual GET
def volumioGet(cmd) {
    def path = "/api/v1/"
    httpGetVolumio(path, cmd)
}

//Send Command
def volumioCmd(cmd) {
    def path = "/api/v1/commands/?cmd="
    httpGetVolumio(path, cmd)
    log.info "${device.getLabel()}: Sent Command: ${cmd}"
}

//Volumio REST API data handler
def httpGetVolumio(path, cmd) {
    def host = settings.volumioHost
    if (host.contains("//")) { host = host.drop(host.indexOf("//")+2) }  //hostname clean-up
    httpGet([uri: "http://${host}${path}${cmd}",
        contentType: "application/json",
        timeout: 5]) { resp ->
        respData = resp.data
        if (settings.APIdebugOutput) { log.debug "${device.getLabel()}: REST API Response: ${respData.response}" }  //log
    }
}

//Hubitat POST data handler. Must be named parse() to catch push notifications from hub
def parse(input) {
    def body = input.split("body:")[1]
    byte[] decoded = body.decodeBase64()
    def decodedStr = new String(decoded)
    def decodedJson = new JsonSlurper().parseText(decodedStr)
    if (!decodedJson.item) { log.info "Push Notification from ${device.getLabel()}: ${decodedJson}" }
    if (settings.APIdebugOutput) { log.debug "API Debug Push Notification from ${device.getLabel()}: ${decodedJson}" }
    if (decodedJson.item == "state") { parseResp(decodedJson.data) }
}

//JSON response data parser
def parseResp(respData) {
    def updateFlag = false
    def attributes = ["status", "artist", "title", "album", "musicservice", "volume", "level", "mute"]  //all attribute names
    def respDataNames = ["status", "artist", "title", "album", "service", "volume", "volume", "mute"]  //items of interest from Volumio JSON return data
    for (int i in 0..(attributes.size - 1)) {
        def oldValue = device.currentValue("${attributes[i]}")
        def newValue = respData."${respDataNames[i]}"
        if ("${newValue}") {  //checks for null data. Uses string value because "mute" JSON data is boolean and returns false when unmuted
            if ("${newValue}" != "${oldValue}") {  //Uses string value because "mute" attribute data is string, but JSON data is boolean
                updateAttribute(attributes[i], newValue)
                if (i in [1, 2, 3]) { updateFlag = true } //sets flag for trackDesc and trackData updates
            }
        } else {
            if (oldValue != "none") { updateAttribute(attributes[i], "none") }
            if (i in [1, 2, 3]) { updateFlag = true } //sets flag for trackDesc and trackData updates
        }
        if (settings.debugOutput) { log.debug "${device.getLabel()}: ${attributes[i]} oldValue: ${oldValue} newValue: ${newValue}" } //log
    }
    if (updateFlag) {
        def trackDesc = "none"
        if (respData.artist) { trackDesc = "${respData.artist} - ${respData.title} on ${respData.album}" }
        def trackData = [
            artist: respData.artist,
            title: respData.title,
            album: respData.album,
            image: respData.albumart,
            source: respData.service
        ]
        def trackDataJson = JsonOutput.toJson(trackData)
        updateAttribute("trackDescription", trackDesc)
        updateAttribute("trackData", trackDataJson)
    }
}

//Device attribute updater
def updateAttribute(attrName, attrValue) {
    sendEvent(name: attrName, value: attrValue)
    log.info "${device.getLabel()}: ${attrName}: ${attrValue}"
}

//Player commands
def play() {
    volumioCmd("play")
}
def pause() {
    volumioCmd("pause")
}
def stop() {
    volumioCmd("stop")
}
def nextTrack() {
    volumioCmd("next")
}
def previousTrack() {
    volumioCmd("prev")
}
def clearQueue() {
    volumioCmd("clearQueue")
}

//Playlist Commands
def ClearPlayListQueue() {
    volumioCmd("clearQueue")
}
def SetPlayPlayListandName() {
    String PlayListNameVar = settings.selectedPlaylist
    if (PlayListNameVar) {
        // Set the playlist
        volumioCmd("playplaylist&name=${URLEncoder.encode(PlayListNameVar, 'UTF-8')}")

        // Add a short delay before sending the play command
        runIn(1, play)

        log.info "${device.getLabel()}: Set and started playing playlist: ${PlayListNameVar}"
    } else {
        log.warn "${device.getLabel()}: Playlist name is not provided."
    }
}
def RepeatPlayListTrack() {
    String RepeatPlayItem = settings.repeatPlaylistTrack ? "true" : "false"
    volumioCmd("repeat&value=${RepeatPlayItem}")
}
def RandomizePlayList() {
    String RandomizeTrueorFalse = settings.randomizePlaylist ? "true" : "false"
    volumioCmd("random&value=${RandomizeTrueorFalse}")
}

//Volume commands
def mute() {
    volumioCmd("volume&volume=mute")
}
def unmute() {
    volumioCmd("volume&volume=unmute")
}
def volumeUp() {
    volumioCmd("volume&volume=plus")
}
def volumeDown() {
    volumioCmd("volume&volume=minus")
}
def setVolume(volumelevel) {
    volumioCmd("volume&volume=${volumelevel}")
}
def setLevel(volumelevel) {    //same as Set Volume
    setVolume(volumelevel)
}

//Unused commands
def playText(var) {
    log.warn "${device.getLabel()}: Play Text - This Function is Not Enabled"
}
def playTrack(var) {
    log.warn "${device.getLabel()}: Play Track - This Function is Not Enabled"
}
def restoreTrack(var) {
    log.warn "${device.getLabel()}: Restore Track - This Function is Not Enabled"
}
def resumeTrack(var) {
    log.warn "${device.getLabel()}: Resume Track - This Function is Not Enabled"
}
def setTrack(var) {
    log.warn "${device.getLabel()}: Set Track - This Function is Not Enabled"
}

// Custom command to fetch playlists
def fetchPlaylists() {
    log.debug "fetchPlaylists method called"
    def host = settings.volumioHost
    if (host.contains("//")) { host = host.drop(host.indexOf("//")+2) }  //hostname clean-up
    def path = "/api/v1/listplaylists"
    try {
        httpGet([uri: "http://${host}${path}",
            contentType: "application/json",
            timeout: 5]) { resp ->
            log.debug "Response from Volumio: ${resp.data}" // Debugging the response
            // Assuming the response is a simple list of playlist names
            if (resp.data instanceof List) {
                state.playlists = resp.data
                log.debug "Playlists updated: ${state.playlists}"
            } else {
                log.error "Unexpected response format: ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.error "Error fetching playlists: ${e.message}"
    }
}
