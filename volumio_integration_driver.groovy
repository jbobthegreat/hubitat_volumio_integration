/**
Volumio Music Player Integration for Hubitat
Author: Flint IronStag
https://github.com/jbobthegreat/hubitat_volumio_integration

Revision History
1.06 03.01.2024 - Updated random() and repeat() methods to either toggle or set explicitly
				  Added ability to play Pandora channels to Play Track command
				  Bug fixes
1.05 02.09.2024 - Fixed bug preventing setTrack and playTrack from working with some music services
1.04 02.08.2024 - Added Repeat and Random toggle commands
                  Added uri and otherzones attributes
				  Added functionality to setTrack (add to queue) and playTrack (replace queue and play) commands
                  Added functionality to get and play specified Volumio playlists - Contributed by Ashrond (modified)
                  https://github.com/ashrond/hubitat_volumio_integration/tree/main
1.03 11.17.2023 - Restructured data retrieval to use Volumio's push notification API instead of constantly polling in order to reduce load on hub
                  Ref: https://developers.volumio.com/api/rest-api#notifications
                  Set the device network ID (DNI) to Volumio's MAC address during initialization.  Necessary in order for Hubitat to forward push notifications to the correct device
                  (Hubitat receives POST data on port 39501 and forwards it to a device whose DNI matches the MAC address of the connection origin, using the parse() method in that device)
                  Multiple small bug fixes
1.02 07.06.2023 - Added trackData JSON object to refresh() method
1.01 07.04.2023 - Cleaned up attributes to avoid duplication of built-in attributes from MusicPlayer and AudioVolume capabilities
                  Added level and trackDescription to refresh() method
                  Ref: https://docs2.hubitat.com/developer/driver/capability-list
1.00 06.25.2023 - Initial Release

To reduce clutter, not all fields reported by Volumio are reported by this driver
Not all commands integral to the Hubitat Music Player capability are utilized

This driver uses the Volumio REST API. Reference Volumio REST API Manual --> https://volumio.github.io/docs/API/REST_API.html

*/

metadata {
	definition (
		name: "Volumio Music Player",
		namespace: "volumio",
		author: "FlintIronStag" )
	{
        capability "MusicPlayer"
        capability "AudioVolume"
        capability "Initialize"
        capability "Refresh"

        command "clearQueue"
        command "enablePushNotifications"
        command "repeat", [[name:"Repeat", type:"ENUM", "constraints":["","true","false"]]]
        command "random", [[name:"Random", type:"ENUM", "constraints":["","true","false"]]]
        command "playTrack", [[name:"Track URI*", type:"STRING", description:"URI/URL of track to play (required)"],[name:"Music Service*", type:"STRING", description:"Service name for track (required)"],[name:"Title", type:"STRING", description:"Track title (optional)"]]
        command "setTrack", [[name:"Track URI*", type:"STRING", description:"URI/URL of track to add to queue (required)"],[name:"Music Service*", type:"STRING", description:"Service name for track (required)"],[name:"Title", type:"STRING", description:"Track title (optional)"]]
        command "setPlaylist", [[name:"Playlist*", type:"STRING", description:"Playlist name (case sensitive)"]]
        command "clearStateVariable", [[name:"State Variable*", type:"STRING", description:"State variable name"]]
        
        attribute "title", "string"
        attribute "artist", "string"
        attribute "album", "string"
        attribute "musicservice", "string"
        attribute "uri", "string"
        attribute "playlists", "string"
        attribute "otherzones", "string"
	}
}

preferences {
    input "volumioHost", "text", title: "Enter Volumio hostname or IP", defaultValue: "volumio.local", displayDuringSetup: true, required: true
    input "schedulePush", "enum", title: "Automatically re-enroll in push notifications nightly? (enrollment resets after Volumio restarts)", options: ["No","12 AM","1 AM","2 AM","3 AM","4 AM","5 AM","6 AM","7 AM","8 AM","9 AM","10 AM","11 AM","12 PM","1 PM","2 PM","3 PM","4 PM","5 PM","6 PM","7 PM","8 PM","9 PM","10 PM","11 PM"], defaultValue: "No", displayDuringSetup: true, required: true
    input "debugOutput", "bool", title: "Enable device debug logging?", defaultValue: false, displayDuringSetup: false, required: false  //enables log messages except API responses
    input "APIdebugOutput", "bool", title: "Enable API debug logging?", defaultValue: false, displayDuringSetup: false, required: false  //enables only API response log messages
}

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def installed() {
	log.info "${device.getLabel()}: Installed with settings: ${settings}"
}

def updated() {
    log.info "${device.getLabel()}: Updated with settings: ${settings}"
    initialize()
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
        if (hr == 12) {hr-=12}  //to deal with 12AM and 12PM correctly
        if (time.split(" ")[1] == "PM") {hr+=12}
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
	def path = "/api/v1/pushNotificationUrls?url=http://${hubIP}:${hubPort}"
	def body = "{\"url\":\"http://${hubIP}:${hubPort}\"}"
    def logMsg = "Push Notifications Enabled"
    httpPostVolumio(path, body, logMsg)
}

//Manually refresh data
def refresh() {
    volumioGet("getState")
    parseState(respData)
    volumioGet("getZones")
    parseZones(respData.zones)
    volumioGet("listplaylists")
    def playlists = "${respData}".take("${respData}".size()-1).drop(1)   //Remove [ ] brackets from string beginning and end
    sendEvent(name: "playlists", value: playlists)  //Not using updateAttribute method because no log entry is needed
}

//Manual GET
def volumioGet(cmd) {
	def path = "/api/v1/"
    httpGetVolumio(path, cmd)
    log.info "${device.getLabel()}: Sent Command: ${cmd}"
}

//Send Command
def volumioCmd(cmd) {
	def path = "/api/v1/commands/?cmd="
    httpGetVolumio(path, cmd)
    log.info "${device.getLabel()}: Sent Command: ${cmd}"
}

//Volumio REST API POST
def httpPostVolumio(path, body, logMsg) {
	def host = settings.volumioHost
    if (host.contains("//")) {host = host.drop(host.indexOf("//")+2)}  //hostname clean-up
	def headers = [:] 
    headers.put("HOST", "${host}:80")
    headers.put("Content-Type", "application/json")
    try {
        def hubAction = new hubitat.device.HubAction(
            method: "POST",
            path: path,
            body: body,
            headers: headers
            )
        log.info "${device.getLabel()}: ${logMsg}"
        return hubAction
    } catch (Exception e) {
        log.error "${device.getLabel()}: Invalid REST API Response: exception ${e} on ${hubAction}"
	}  
}

//Volumio REST API GET
def httpGetVolumio(path, cmd) {
    def host = settings.volumioHost
    if (host.contains("//")) {host = host.drop(host.indexOf("//")+2)}  //hostname clean-up
    try {
        httpGet([uri: "http://${host}${path}${cmd}",
	    contentType: "application/json",
	    timeout: 5]) { resp ->
	    respData = resp.data
        if (settings.APIdebugOutput) {log.debug "${device.getLabel()}: REST API Response: ${respData.response}"}  //log
        }
    } catch (Exception e) {
        if (resp) {log.error "${device.getLabel()}: Invalid REST API Response: ${respData}"}   //won't error on a null response
    }
}

//Hubitat POST data handler. Must be named parse() to catch push notifications from hub
def parse(input) {
    def body = input.split("body:")[1]
    byte[] decoded = body.decodeBase64()
    def decodedStr = new String(decoded)
    def decodedJson = new JsonSlurper().parseText(decodedStr)
    if (settings.APIdebugOutput) {log.debug "API Debug Push Notification from ${device.getLabel()}: ${decodedJson}"}
    if (!decodedJson.item) {log.info "Push Notification from ${device.getLabel()}: ${decodedJson}"}
    else if (decodedJson.item == "state") {parseState(decodedJson.data)}
	//else if (decodedJson.item == "queue") {parseQueue(decodedJson.data)}  //Future expansion
    else if (decodedJson.item == "zones") {parseZones(decodedJson.data.list)}
}

//Player State JSON data parser
def parseState(respData) {
    def updateFlag = false
    def attributes = ["status", "artist", "title", "album", "musicservice", "volume", "level", "mute", "uri"]  //all attribute names
    def respDataNames = ["status", "artist", "title", "album", "service", "volume", "volume", "mute", "uri"]  //items of interest from Volumio JSON return data
    for(int i in 0..(attributes.size-1)) {
        def oldValue = device.currentValue("${attributes[i]}")
        def newValue = respData."${respDataNames[i]}"
        if ("${newValue}") {  //checks for null data. Uses string value because "mute" JSON data is boolean and returns false when unmuted
            if ("${newValue}" != "${oldValue}") {  //Uses string value because "mute" attribute data is string, but JSON data is boolean
                ( updateAttribute(attributes[i], newValue) )
                if (i in [1, 2, 3]) {updateFlag = true} //sets flag for trackDesc and trackData updates
            }
        }
        else {
            if (oldValue != "none") {updateAttribute(attributes[i], "none") }
            if (i in [1, 2, 3]) {updateFlag = true} //sets flag for trackDesc and trackData updates
        }
        if (settings.debugOutput) {log.debug "${device.getLabel()}: ${attributes[i]} oldValue: ${oldValue} newValue: ${newValue}"} //log
    }
    if (updateFlag) {
        def trackDesc = "none"
        if (respData.artist){trackDesc = "${respData.artist} - ${respData.title} on ${respData.album}"}
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


//Zones JSON data parser
def parseZones(respData) {
    def oldZones = device.currentValue("otherzones")
    def otherZones = []
    for (int i in 0..(respData.size-1)) {
        if (!respData[i].isSelf) {
            def zoneName = respData[i].name
            def zoneStatus = respData[i].state.status
            otherZones.add("${zoneName}":zoneStatus)
        }
    }
    def otherZonesJson = JsonOutput.toJson(otherZones)
    if (otherZonesJson != oldZones) {sendEvent(name: "otherzones", value: otherZonesJson)}  //Not using updateAttribute method because no log entry is needed
}


//Device attribute updater
def updateAttribute(attrName, attrValue) {
    sendEvent(name: attrName, value: attrValue)
    log.info "${device.getLabel()}: ${attrName}: ${attrValue}"
}

//Clear device state variable
def clearStateVariable(var) {
    state.remove(var)
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
def repeat(key) {
    def cmd = ""
    if (!key) {cmd = "repeat"}
    else {cmd = "repeat&value=${key}"}
    volumioCmd(cmd)
}
def random (key) {
    def cmd = ""
    if (!key) {cmd = "random"}
    else {cmd = "random&value=${key}"}
    volumioCmd(cmd)
}
def clearQueue() {
    volumioCmd("clearQueue")
}
def setPlaylist(playlist) {
    volumioGet("listplaylists")
    for (int i in 0..(respData.size-1)) {
        if (respData[i] == playlist) {
            volumioCmd("playplaylist&name=${URLEncoder.encode(playlist, 'UTF-8')}")
            break
        }
        if (i == respData.size-1) {
            log.info "${device.getLabel()}: Invalid Playlist name: ${playlist}"
        }
    }
}
def setTrack(uri, service, title=null) {
	def path = "/api/v1/addToQueue"
    def body = [:]
    body.put("service", service)
    body.put("uri", uri)
    if (title) {body.put("title", title)}
    def bodyJson = JsonOutput.toJson(body)
    def logMsg = "Add to queue: ${body}"
    httpPostVolumio(path, body, logMsg)
}
def playTrack(uri, service, title=null) {
    if (service.contains("pandora")) {
        volumioGet("browse?uri=${uri}")
    } else {
        def path = "/api/v1/replaceAndPlay"
        def body = [:]
        body.put("service", service)
        body.put("uri", uri)
        if (title) {body.put("title", title)}
        def bodyJson = JsonOutput.toJson(body)
        def logMsg = "Replace queue and play: ${body}"
        httpPostVolumio(path, bodyJson, logMsg)
    }
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
def restoreTrack(var) {
    log.warn "${device.getLabel()}: Restore Track - This Function is Not Enabled"
}
def resumeTrack(var) {
    log.warn "${device.getLabel()}: Resume Track - This Function is Not Enabled"
}
