/**
 *  Hue B Smart Scene
 *
 *  Copyright 2016 Anthony Pastor
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	Changelog:
 *  04/11/2018 xap-code fork for Hubitat
 *  12/11/2018 Link logging to smart app setting
 */
metadata {
    definition (name: "Hue B Smart Scene", namespace: "info_fiend", author: "Anthony Pastor") {
        
        capability "Actuator"
        capability "PushableButton"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        
        command "setToGroup", ["NUMBER"]
        command "setTo2Groups", ["NUMBER", "NUMBER"]
        command "updateScene"
        command	"updateSceneFromDevice"
        command "refresh"
        command "push", ["NUMBER"]
        
        attribute "getSceneID", "STRING"        
        attribute "lights", "STRING"  
        attribute "sceneID", "string"
        attribute "host", "string"
        attribute "group", "NUMBER"
        attribute "lightStates", "json_object"  
	}
}


private configure() {		
    def commandData = parent.getCommandData(device.deviceNetworkId)
    log "${commandData = commandData}", "debug"
    sendEvent(name: "sceneID", value: commandData.deviceId, displayed:true, isStateChange: true)
    sendEvent(name: "scheduleId", value: commandData.scheduleId, displayed:true, isStateChange: true)
    sendEvent(name: "host", value: commandData.ip, displayed:false, isStateChange: true)
    sendEvent(name: "lights", value: commandData.lights, displayed:false, isStateChange: true)
    sendEvent(name: "lightStates", value: commandData.lightStates, displayed:false, isStateChange: true)
    sendEvent(name: "numberOfButtons", value: 1)
}

// parse events into attributes
def parse(String description) {
	log "Parsing '${description}'", "debug"
}

/** 
 * capability.switch
 **/
def on() {
	push()
}

def off() {

}

/**
 * capablity.momentary
 **/
def push(button = 1) {
    def theGroup = device.currentValue("group") ?: 0
    sendEvent(name: "pushed", value: 1, isStateChange: true, display: false)
    sendEvent(name: "switch", value: "on", isStateChange: true, display: false)
    sendEvent(name: "switch", value: "off", isStateChange: true, display: false)
    setToGroup()
}

def setToGroup ( Integer inGroupID = 0) {
    log "setToGroup ${this.device.label}: Turning scene on for group ${inGroupID}!", "trace"

 	def commandData = parent.getCommandData(this.device.deviceNetworkId)
	log "setToGroup: ${commandData}", "debug"
    
	def sceneID = commandData.deviceId

	log "${this.device.label}: setToGroup: sceneID = ${sceneID} ", "debug"
    log "${this.device.label}: setToGroup: theGroup = ${inGroupID} ", "debug"
    String gPath = "/api/${commandData.username}/groups/${inGroupID}/action"

    parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "${gPath}",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [scene: "${commandData.deviceId}"]
		])
	)

    parent.doDeviceSync()
}

def setTo2Groups ( group1, group2 ) {
	log "setTo2Groups ${this.device.label}: Turning scene on for groups ${group1} , ${group2}!", "trace"

 	def commandData = parent.getCommandData(this.device.deviceNetworkId)
	log "setTo2Groups: ${commandData}", "debug"
    
	def sceneID = commandData.deviceId

	log "${this.device.label}: setTo2Groups: sceneID = ${sceneID} ", "debug"
    log "${this.device.label}: setTo2Groups: group1 = ${group1} ", "debug"
    
    String gPath = "/api/${commandData.username}/groups/${group1}/action"

    parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "${gPath}",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [scene: "${commandData.deviceId}"]
		])
	)

    log "${this.device.label}: setTo2Groups: group2 = ${group2} ", "debug"
    gPath = "/api/${commandData.username}/groups/${group2}/action"

    parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "${gPath}",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [scene: "${commandData.deviceId}"]
		])
	)
    
    parent.doDeviceSync()
}

def turnGroupOn(inGroupID) {
	log "Executing 'turnGroupOn ( ${inGroupID} )'", "trace"

    def commandData = parent.getCommandData(device.deviceNetworkId)
    
        return new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "/api/${commandData.username}/groups/${inGroupID}/action",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [on: true]
		])

	parent.doDeviceSync()
}

def updateScene() {
	log "Updating scene!", "trace"
    
    def commandData = parent.getCommandData(this.device.deviceNetworkId)
	log "${commandData}", "debug"
    def sceneLights = this.device.currentValue("lights")
    log "sceneLights = ${sceneLights}", "debug"
    parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "/api/${commandData.username}/scenes/${commandData.deviceId}/",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [storelightstate: true]
		])
	)	
}

def updateSceneFromDevice() {
	log "${this}: Update updateSceneFromDevice Reached.", "trace"

    def sceneIDfromD = device.currentValue("sceneID")

    log "Retrieved sceneIDfromD: ${sceneIDfromD}.", "debug"

	String myScene = sceneIDfromD

    if (sceneIDfromD) {
    	updateScene()
		//log.debug "Executing 'updateScene' for ${device.label} using sceneID ${myScene}."
	}
}

def updateStatus(type, param, val) {
	log "updating status: ${type}:${param}:${val}", "trace"
    
	if (type == "scene") {
		if (param == "lights") {

            sendEvent(name: "lights", value: val, displayed:false, isStateChange: true)
        
        } else if (param == "lightStates") {
			log.trace "update lightsStates! = ${lightStates}"
            sendEvent(name: "lightStates", value: val, displayed:true, isStateChange: true)
            
        } else if (param == "scheduleId") {
        
           	log "Should be updating scheduleId with value of ${val}", "debug"
           	sendEvent(name: "scheduleId", value: val, displayed:false, isStateChange: true)
                
		} else if (param == "schedule") {

			sendEvent(name: "schedule", value: val, displayed:false, isStateChange: true)
            
		} else {                

			log "Unhandled parameter: ${param}. Value: ${val}", "debug"
        }
    }
}

def refresh() {
	log "refresh(): ", "trace"
	parent.doDeviceSync()
    configure()
}

def log(String text, String type = null){
    
   	if (type == "warn") {
        log.warn "${text}"
    } else if (type == "error") {
        log.error "${text}"
    } else if (parent.debugLogging) {
        if (type == "info") {
            log.info "${text}"
        } else if (type == "trace") {
    		log.trace "${text}"
        } else {
    		log.debug "${text}"
        }
	}
}