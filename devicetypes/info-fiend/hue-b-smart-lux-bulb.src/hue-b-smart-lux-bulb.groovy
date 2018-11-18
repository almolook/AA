/**
 *  Hue B Smart Lux Bulb
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
 *  18/11/2018 Refactor to align with other drivers
 */
preferences {
	input("tt", "number", title: "Time it takes for the lights to transition (default: 2 = 200ms)", defaultValue: 2)   
	input("flashNotifySecs", "number", title: "Flash notification seconds (default: 5s)", defaultValue: 5)
} 

// for the UI
metadata {
	definition (name: "Hue B Smart Lux Bulb", namespace: "info_fiend", author: "Anthony Pastor") {
		capability "Actuator"
		capability "Light"
		capability "Polling"
		capability "Refresh"					
		capability "Switch"
		capability "Switch Level"
		capability "Sensor"
        
		// extra Hue commands
		command "flashOn"
		command "flashOff"
		command "flashOnce"
		command "flashNotify"

		// extra Hue attributes
		attribute "reachable", "STRING"
		attribute "transitionTime", "NUMBER"
	}
}

// parse events into attributes
def parse(String description) {
	log "Parsing (ignoring) '${description}'", "debug"
}

def installed() {
	log "Installed with settings: ${settings}", "info"
	initialize()
}

def updated(){
	log "Updated with settings: ${settings}", "info"
	sendEvent(name: "transitionTime", value: tt)
}

def initialize() {
}

/** 
 * capability.switchLevel 
 **/
def scaleLevel(level, fromHub = false, max = 254) {
	if (fromHub) {
		return Math.round( level * max / 100 )
	} else {
		if (max == 0) {
			return 0
		} else {
			return Math.round( level * 100 / max )
		}
	}       
}

def setLevel(inLevel, duration = null) {
	log "Hue B Smart Lux Bulb: setLevel ( ${inLevel} ): ", "trace"

	def level = scaleLevel(inLevel, true, 254)
	def commandData = parent.getCommandData(device.deviceNetworkId)    
	def tt = this.device.currentValue("transitionTime") as Integer ?: 0
        
	sendEvent name: "level", value: inLevel

	parent.sendHubCommand(new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/lights/${commandData.deviceId}/state",
			headers: [
				host: "${commandData.ip}"
			],
			body: [on: true, bri: level, transitiontime: duration ? duration * 10 : tt]
		])
	)    
}

/** 
 * capability.switch
 **/
def on() {
	log "Hue B Smart Lux Bulb: on(): ", "trace"

	def commandData = parent.getCommandData(device.deviceNetworkId)    
	def tt = device.currentValue("transitionTime") as Integer ?: 0
	def percent = device.currentValue("level") as Integer ?: 100
	def level = scaleLevel(percent, true, 254)
    
	sendEvent name: "switch", value: "on"

	return new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/lights/${commandData.deviceId}/state",
			headers: [
				host: "${commandData.ip}"
			],
			body: [on: true, bri: level, transitiontime: tt]
		])
}

def off() {
	log "Hue B Smart Lux Bulb: off(): ", "trace"
    
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = device.currentValue("transitionTime") as Integer ?: 0
    
	sendEvent name: "switch", value: "off"

	return new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/lights/${commandData.deviceId}/state",
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
	log "Hue B Smart Lux Bulb: poll(): ", "trace"
	refresh()
}

/**
 * capability.refresh
 **/
def refresh() {
	log "Hue B Smart Lux Bulb: refresh(): ", "trace"
	parent.doDeviceSync(device.deviceNetworkId)
}

/**
 * Extra Hue Commands
 **/
def flashOnce() {
    log "Hue B Smart Lux Bulb: flashOnce(): ", "trace"
    
    def commandData = parent.getCommandData(device.deviceNetworkId)
    parent.sendHubCommand(new hubitat.device.HubAction(
			[
				method: "PUT",
				path: "/api/${commandData.username}/lights/${commandData.deviceId}/state",
				headers: [
					host: "${commandData.ip}"
				],
				body: [alert: "select"]
			])
    )
}

def flashNotify() {
	log "Hue B Smart Lux Bulb: flashNotify(): ", "trace"

	flashOn()
	runIn(flashNotifySecs ?: 5, flashOff)
}

def flashOn() {
	log "Hue B Smart Lux Bulb: flashOn(): ", "trace"
  
	def commandData = parent.getCommandData(device.deviceNetworkId)
	parent.sendHubCommand(new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/lights/${commandData.deviceId}/state",
			headers: [
				host: "${commandData.ip}"
			],
			body: [alert: "lselect"]
		])
  )
}

def flashOff() {
	log "Hue B Smart Lux Bulb: flashOff(): ", "trace"
    
	def commandData = parent.getCommandData(device.deviceNetworkId)
	parent.sendHubCommand(new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/lights/${commandData.deviceId}/state",
			headers: [
				host: "${commandData.ip}"
			],
			body: [alert: "none"]
		])
	)
}
                
/**
 * Update Status
 **/
private updateStatus(action, param, val) {
	log "Hue B Smart Lux Bulb: updateStatus ( ${param}:${val} )", "trace"
	
	if (action == "state") {
		def curValue
		switch(param) {
			
			case "on":
				curValue = device.currentValue("switch")
				if (val == true) {
					if (curValue != on) {
						log "Update Needed: Current Value of switch = false & newValue = ${val}", "debug"
						sendEvent(name: "switch", value: on, displayed: true, isStateChange: true)                	     
					} else {
						//log.debug "NO Update Needed for switch."                	
					}
				} else {
					if (curValue != off) {
						log "Update Needed: Current Value of switch = true & newValue = ${val}", "debug"
						sendEvent(name: "switch", value: off, displayed: true)
					} else {
						//log.debug "NO Update Needed for switch."                	
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
					//log.debug "NO Update Needed for level."                	
				}
				break
			
	    case "reachable":
				if (val == true){
					sendEvent(name: "reachable", value: true, displayed: false, isStateChange: true)
				} else {
					sendEvent(name: "reachable", value: false, displayed: false, isStateChange: true)
				}
				break
			
			case "transitiontime":
				curValue = device.currentValue("transitionTime")
				if (curValue != val) {
					log "Update Needed: Current Value of transitionTime = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "transitionTime", value: val, displayed: true, isStateChange: true)
				} else {
					//log.debug "NO Update Needed for transitionTime."                	
				}    
				break
			
			case "alert":
				if (val == "none") {
					log "Not Flashing", "trace"
				} else {
					log "Flashing", "trace"
				}
				break

			default: 
				log.debug("Unhandled parameter: ${param}. Value: ${val}")    
		}
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
