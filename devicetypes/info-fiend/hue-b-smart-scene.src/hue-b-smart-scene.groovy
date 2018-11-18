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
 *  18/11/2018 Simplify by remove extra commands and attributes
 *  18/11/2018 Optimise device sync for multiple bridges
 */
metadata {
	definition (name: "Hue B Smart Scene", namespace: "info_fiend", author: "Anthony Pastor") {
        
		capability "Actuator"
		capability "PushableButton"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
	}
}

// parse events into attributes
def parse(String description) {
	log "Parsing (ignoring): '${description}'", "debug"
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
def push() {
    sendEvent(name: "pushed", value: 1, isStateChange: true, display: false)
    sendEvent(name: "switch", value: "on", isStateChange: true, display: false)
    sendEvent(name: "switch", value: "off", isStateChange: true, display: false)
    activateScene()
}

def activateScene() {
	log "activateScene ${this.device.label}: Turning scene on", "trace"

 	def commandData = parent.getCommandData(this.device.deviceNetworkId)
	log "activateScene: ${commandData}", "debug"
    
	def sceneID = commandData.deviceId

	log "${this.device.label}: setToGroup: sceneID = ${sceneID} ", "debug"
	String gPath = "/api/${commandData.username}/groups/0/action"

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

	parent.doDeviceSync(device.deviceNetworkId)
}

def updateStatus(type, param, val) {
	log "updating status: ${type}:${param}:${val}", "trace"
    
	if (type == "scene") {
		if (param == "lights") {

			sendEvent(name: "lights", value: val, displayed:false, isStateChange: true)
        
		} else if (param == "lightStates") {

			log "update lightsStates! = ${lightStates}", "trace"
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
	parent.doDeviceSync(device.deviceNetworkId)
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