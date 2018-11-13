/**
 *  Hue B Smart Ambiance Group
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
 *  05/11/2018 xap-code fork for Hubitat
 *  13/11/2018 Link logging to parent app setting
 *
 */
preferences {
	input("tt", "number", title: "Time it takes for the lights to transition (default: 2 = 200ms)")   
    input("flashNotifySecs", "number", title: "Flash notification seconds (default: 5s)", defaultValue: 5)
}  
 
metadata {
	definition (name: "Hue B Smart White Ambiance Group", namespace: "info_fiend", author: "Anthony Pastor") {
        capability "Actuator"
        capability "Color Temperature"
        capability "Light"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
	
        command "setColorTemperature"        
        command "on"
        command "off"
        command "setLevel"
        command "refresh"
        command "updateStatus"
        command "flashNotify"
        command "flashOnce"
        command "flashOn"
        command "flashOff"	
 
        attribute "lights", "STRING"       
        attribute "transitionTime", "NUMBER"
        attribute "bri", "number"
        attribute "level", "number"
        attribute "on", "string"
        attribute "colorTemperature", "number"
	}
}

// parse events into attributes
def parse(String description) {
	log "Parsing '${description}'", "debug"
}

def installed() {
	log "Installed with settings: ${settings}", "info"
	sendEvent(name: "transitionTime", value: tt)
	initialize()
}

def updated(){
	log "Updated with settings: ${settings}", "info"
	sendEvent(name: "transitionTime", value: tt)
}

def initialize() {
	state.xy = [:]
}

/** 
 * capability.switchLevel 
 **/
def setLevel(inLevel) {
	log "Hue B Smart Ambience Group: setLevel ( ${inLevel} ): ", "trace"
    
	def level = scaleLevel(inLevel, true, 254)

	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = this.device.currentValue("transitionTime") as Integer ?: 0
        
    runIn(1, refresh)

	parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [on: true, bri: level, transitiontime: tt]
		])
	)    
}

/** 
 * capability.switch
 **/
def on() {
	log "Hue B Smart Ambience Group: on(): ", "trace"

	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = device.currentValue("transitionTime") as Integer ?: 0
	def percent = device.currentValue("level") as Integer ?: 100
	def level = scaleLevel(percent, true, 254)
        
    runIn(1, refresh)

    return new hubitat.device.HubAction(
        [
        	method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [on: true, bri: level, transitiontime: tt]
		])
}

def off() {
	log "Hue B Smart Ambience Group: off(): ", "trace"
    
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = device.currentValue("transitionTime") as Integer ?: 0
        
    runIn(1, refresh)

	return new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
	        headers: [
	        	host: "${commandData.ip}"
			],
			body: [on: false]
		])
}

/** 
 * capability.polling
 **/
def poll() {
	log "Hue B Smart Ambience Group: poll(): ", "trace"
	refresh()
}

/**
 * capability.refresh
 **/
def refresh() {
	log "Hue B Smart Ambience Group: refresh(): ", "trace"
	parent.doDeviceSync()
}

/**
 * capability.colorTemperature 
**/

def setColorTemperature(inCT) {
	log "Hue B Smart Ambience Bulb: setColorTemperature ( ${inCT} )", "trace"
    
	def colorTemp = inCT ?: this.device.currentValue("colorTemperature")
	colorTemp = Math.round(1000000/colorTemp)    
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = device.currentValue("transitionTime") as Integer ?: 0
    
    
	parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [ct: colorTemp, transitiontime: tt]
		])
	)
}

/**
 * capability.alert (flash)
 **/
def flashOnce() {
    log "Hue B Smart Ambiance Group: flashOnce(): ", "trace"
    
    def commandData = parent.getCommandData(device.deviceNetworkId)
    parent.sendHubCommand(new hubitat.device.HubAction(
        [
            method: "PUT",
            path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
            headers: [
                host: "${commandData.ip}"
            ],
            body: [alert: "select"]
        ])
    )
}

def flashNotify() {
    log "Hue B Smart Ambiance Group: flashNotify(): ", "trace"

    flashOn()
    runIn(flashNotifySecs ?: 5, flashOff)
}

def flashOn() {
    log "Hue B Smart Ambiance Group: flashOn(): ", "trace"
    
    def commandData = parent.getCommandData(device.deviceNetworkId)
    parent.sendHubCommand(new hubitat.device.HubAction(
        [
            method: "PUT",
            path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
            headers: [
                host: "${commandData.ip}"
            ],
            body: [alert: "lselect"]
        ])
    )
}

def flashOff() {
    log "Hue B Smart Ambiance Group: flashOff(): ", "trace"
    
    def commandData = parent.getCommandData(device.deviceNetworkId)
	parent.sendHubCommand(new hubitat.device.HubAction(
    	[
        	method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
	        headers: [
	        	host: "${commandData.ip}"
			],
	        body: [alert: "none"]
		])
	)
}

/**
 * scaleLevel
 **/
def scaleLevel(level, fromST = false, max = 254) {
  
    if (fromST) {
        return Math.round( level * max / 100 )
    } else {
    	if (max == 0) {
    		return 0
		} else { 	
        	return Math.round( level * 100 / max )
		}
    }      
}
                
/**
 * Update Status
 **/
private updateStatus(action, param, val) {
	log "Hue B Ambience Group: updateStatus ( ${param}:${val} )", "trace"
    

    def curValue
	switch(param) {
        case "on":
            curValue = device.currentValue("switch") 
            def onoff
            if (val == true) {
                if (curValue != on) {
                    log "Update Needed: Current Value of switch = false & newValue = ${val}", "debug"
                    sendEvent(name: "switch", value: on, displayed: true, isStateChange: true)                	     
                } else {
                    //log.debug "NO Update Needed for switch"                	
                }
            } else {
                if (curValue != off) {
                    log "Update Needed: Current Value of switch = true & newValue = ${val}", "debug"
                    sendEvent(name: "switch", value: off, displayed: true)
				} else {
                    //log.debug "NO Update Needed for switch"                	
                }
            }    
            break
        case "bri":
            curValue = device.currentValue("level")
            val = scaleLevel(val)
            if (curValue != val) {
                log "Update Needed: Current Value of level = ${curValue} & newValue = ${val}", "debug"
                sendEvent(name: "level", value: val, displayed: true, isStateChange: true) 
            } else {
                //log.debug "NO Update Needed for level"                	
            }
            break
        case "transitiontime":
            curValue = device.currentValue("transitionTime")
            if (curValue != val) {
                log "Update Needed: Current Value of transitionTime = ${curValue} & newValue = ${val}", "debug"
                sendEvent(name: "transitionTime", value: val, displayed: false, isStateChange: true)
            } else {
                //log.debug "NO Update Needed for transitionTime"                	
            }    
            break
        case "ct": 
            curValue = device.currentValue("colorTemperature")
            val = Math.round(1000000/val)
            if (curValue != val) {
                log "Update Needed: Current Value of colorTemperature = ${curValue} & newValue = ${val}", "debug"
                sendEvent(name: "colorTemperature", value: val, displayed: false, isStateChange: true) 
            } else {
                //log.debug "NO Update Needed for colorTemperature"                	
            }
            break
        case "alert":
            if (val == "none") {
                log "Not Flashing", "debug"
            } else {
                log "Flashing", "debug"
            }              
            break
        case "lights":
            curValue = device.currentValue("lights")
            if (curValue != val) {
                log "Update Needed: Current Value of lights = ${curValue} & newValue = ${val}", "debug"
                sendEvent(name: "lights", value: val, displayed: false, isStateChange: true) 
            } else {
                //log.debug "NO Update Needed for lights"
            }
            break

        default: 
            log "Unhandled parameter: ${param}. Value: ${val}", "debug"
    }
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
