/**
Volumio Music Player Integration for Hubitat
Author: Flint IronStag

Revision History
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
        command "haltRefresh"
        
        attribute "title", "string"
        attribute "artist", "string"
        attribute "album", "string"
        attribute "musicservice", "string"
	}
}

preferences {
    input "volumiohost", "text", title: "Enter Volumio hostname", defaultValue: "volumio.local", displayDuringSetup: true, required: true
    input "debugOutput", "bool", title: "Enable device debug logging?", defaultValue: false, displayDuringSetup: false, required: false  //enables log messages except API responses
    input "APIdebugOutput", "bool", title: "Enable API debug logging?", defaultValue: false, displayDuringSetup: false, required: false  //enables only API response log messages
}

def installed() {
	log.info "${device.getLabel()}: Installed with settings: ${settings}"
}

def updated() {
    log.info "${device.getLabel()}: Updated with settings: ${settings}"
    ( initialize() )
}

def initialize() {
    log.info "${device.getLabel()}: Initializing"
    schedule("* * * ? * *", refresh)  //Cron schedule running refresh() once per second
}

//Halt refresh() in case of runaway loop. Run Initialize command to resume
def haltRefresh(){
    unschedule()
    log.warn "${device.getLabel()}: Data refresh halted"
}

//Volumio REST API call
def httpGetVolumio(path, cmd) {
    def host = settings.volumiohost
    if (host.contains("http://")) {host = host.drop(7)}  //hostname clean-up
    if (host.contains("https://")) {host = host.drop(8)} //hostname clean-up
    httpGet([uri: "http://${host}${path}${cmd}",
	contentType: "application/json",
	timeout: 5])
	{ resp ->
	respData = resp.data
    if (settings.APIdebugOutput) {log.debug "${device.getLabel()}: REST API Response: ${respData.response}"}  //log
    }
}
def volumioGet(cmd) {
	def path = "/api/v1/"
    ( httpGetVolumio(path, cmd) )
}
def volumioCmd(cmd) {
	def path = "/api/v1/commands/?cmd="
    ( httpGetVolumio(path, cmd) )
}

//Refresh Volumio status
def refresh () {
    ( volumioGet("getState") )
    def attributes = ["status", "artist", "title", "album", "musicservice", "volume", "level", "mute"]  //all attribute names
    def respDataNames = ["status","artist","title","album","service","volume", "volume", "mute"]  //items of interest from Volumio JSON return data
    for(int i in 0..(attributes.size-1)) {
        def oldValue = device.currentValue("${attributes[i]}")
        def newValue = respData."${respDataNames[i]}"
         if ("${newValue}"){  //checks for null data. Uses string value because "mute" JSON data is boolean and returns false when unmuted
            if ("${newValue}" != "${oldValue}") {( updateAttribute(attributes[i], newValue) )}  //Uses string value because "mute" attribute data is string, but JSON data is boolean
            if (settings.debugOutput) {log.debug "${device.getLabel()}: oldValue: ${oldValue} newValue: ${newValue}"}
         }
        else if (oldValue != "None") {( updateAttribute(attributes[i], "None") )}
    }
    def oldTrackDesc = device.currentValue("trackDescription")
    def newTrackDesc = "${respData.artist} - ${respData.title} (${respData.album})"
    if (newTrackDesc != oldTrackDesc){( updateAttribute("trackDescription", newTrackDesc) )}
}
def updateAttribute(attrName, attrValue) {
    sendEvent(name:attrName, value:attrValue)
    log.info "${device.getLabel()}: ${attrName} updated: ${attrValue}"
}

//Player commands
def play() {
    ( volumioCmd("play") )
}
def pause() {
    ( volumioCmd("pause") )
}
def stop() {
    ( volumioCmd("stop") )
}
def nextTrack() {
    ( volumioCmd("next") )
}
def previousTrack() {
    ( volumioCmd("prev") )
}
def clearQueue() {
    ( volumioCmd("clearQueue") )
}

//Volume commands
def mute() {
    ( volumioCmd("volume&volume=mute") )
}
def unmute() {
    ( volumioCmd("volume&volume=unmute") )
}
def volumeUp() {
    ( volumioCmd("volume&volume=plus") )
}
def volumeDown() {
    ( volumioCmd("volume&volume=minus") )
}
def setVolume(volumelevel) {
    ( volumioCmd("volume&volume=${volumelevel}") )
}
def setLevel(volumelevel) {    //same as Set Volume
    ( setVolume(volumelevel) )
}

//Unused commands
def playText(var) {
    log.warn "${device.getLabel()}: Play Text - This Function is Not Enabled"
}
def playTrack(var) {
    log.warn "${device.getLabel()}: Play Track - This Function is Not Enabled"
}
def restoreTrack(var) {
    log.warn "${device.getLabel()}: Restore Track - his Function is Not Enabled"
}
def resumeTrack(var) {
    log.warn "${device.getLabel()}: Resume Track - This Function is Not Enabled"
}
def setTrack(var) {
    log.warn "${device.getLabel()}: Set Track - This Function is Not Enabled"
}
