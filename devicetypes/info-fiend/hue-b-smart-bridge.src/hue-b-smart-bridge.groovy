/**
 *  Hue B Smart Bridge
 *
 *  Copyright 2017 Anthony Pastor
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
 *  12/11/2018 Remove healthcheck methods not used in Hubitat
 *  12/11/2018 Link logging to smart app setting
 *  18/11/2018 Optimise device sync for multiple bridges
 *  22/11/2018 Add Hubitat HTTP method usage for non-scene device polling
 *  19/03/2018 Only import JsonBuilder rather than .*
 */

import groovy.json.JsonBuilder

metadata {
	definition (name: "Hue B Smart Bridge", namespace: "info_fiend", author: "Anthony Pastor") {
		capability "Actuator"

		attribute "serialNumber", "string"
		attribute "networkAddress", "string"
		attribute "status", "string"
		attribute "username", "string"
		attribute "host", "string"
        
		command "discoverItems"
		command "pollItems"
		command "pollBulbs"
		command "pollGroups"
	}
}

void installed() {
	log "Installed with settings: ${settings}", "info"
	initialize()
}

def updated() {
	log "Updated with settings: ${settings}", "info"
	initialize()
}

def initialize() {
	def commandData = parent.getCommandData(device.deviceNetworkId)
	log "Initialize Bridge ${commandData}", "debug"
	sendEvent(name: "idNumber", value: commandData.deviceId, displayed:true, isStateChange: true)
	sendEvent(name: "networkAddress", value: commandData.ip, displayed:false, isStateChange: true)
	sendEvent(name: "username", value: commandData.username, displayed:false, isStateChange: true)
	state.host = this.device.currentValue("networkAddress") + ":80"
	state.userName = this.device.currentValue("username")
	state.initialize = true
}


def discoverItems(discoverScenes = true) {
	log "Bridge discovering all items on Hue hub.", "trace"
	
	if (state.initialize != true ) { initialize() }
 	if (state.user == null ) { initialize() }
	
	def host = state.host
	def username = state.userName

	log "*********** ${host} ********", "debug"
	log "*********** ${username} ********", "debug"

	if (discoverScenes) {
		
		// use original Hue B Smart HubAction method
		return new hubitat.device.HubAction(
			method: "GET",
			path: "/api/${username}/",
			headers: [
				HOST: host
			]
		)
		
	} else {
		
		// use direct Hubitat HTTP methods
		def lightParams = [
			uri: "http://${host}",
			path: "/api/${username}/lights"
		]
		
		def groupParams = [
			uri: "http://${host}",
			path: "/api/${username}/groups"
		]
		
		httpGet(lightParams, { resp ->
			processLights(resp.data)
		})
		
		httpGet(groupParams, { resp ->
			processGroups(resp.data)
		})
	}
}

def pollItems() {
	log "pollItems: polling state of all items from Hue hub.", "trace"

	def host = state.host
	def username = state.userName
        
	sendHubCommand(new hubitat.device.HubAction(
		method: "GET",
		path: "/api/${username}/",
		headers: [
			HOST: host
		]
	))
}

def pollBulbs() {
	log "pollBulbs: polling bulbs state from Hue hub.", "trace"

	def host = state.host
	def username = state.userName
        
	def lightParams = [
		uri: "http://${host}",
		path: "/api/${username}/lights"
	]
		
	httpGet(lightParams, { resp ->
		processLights(resp.data)
	})
}

def pollGroups() {
	log "pollGroups: polling groups state from Hue hub.", "trace"

	def host = state.host
	def username = state.userName
        
	def groupParams = [
		uri: "http://${host}",
		path: "/api/${username}/groups"
	]

	httpGet(groupParams, { resp ->
		processGroups(resp.data)
	})
}

def handleParse(desc) {
	log "handleParse(${desc})", "trace"
	parse(desc)
}

def processJson(body, mac) {
	
	log "processJson", "trace"

	def bridge = parent.getBridge(mac)
	def group 
	def commandReturn = []

	/* responses from bulb/group/scene/ command. Figure out which device it is, then pass it along to the device. */
	if (body[0] != null && body[0].success != null) {
		log "${body[0].success}", "trace"
		body.each{
			it.success.each { k, v ->
				def spl = k.split("/")
				//log.debug "k = ${k}, split1 = ${spl[1]}, split2 = ${spl[2]}, split3 = ${spl[3]}, split4= ${spl[4]}, value = ${v}"                            
				def devId = ""
				def d
				def groupScene

				if (spl[4] == "scene" || it.toString().contains( "lastupdated") ) {	
					log "HBS Bridge:parse:scene - msg.body == ${body}", "trace"
					devId = bridge.value.mac + "/SCENE" + v
					d = parent.getChildDevice(devId)
					groupScene = spl[2]
					d.updateStatus(spl[3], spl[4], v) 
					log "Scene ${d.label} successfully run on group ${groupScene}.", "debug"

					// GROUPS
				} else if (spl[1] == "groups" && spl[2] != 0 ) {    
					devId = bridge.value.mac + "/" + spl[1].toUpperCase()[0..-2] + spl[2]
					//log.debug "GROUP: devId = ${devId}"                            
					d = parent.getChildDevice(devId)
					d.updateStatus(spl[3], spl[4], v) 
					def gLights = []
					def thisbridge = bridge.value.mac
					//log.debug "This Bridge ${thisbridge}"

					gLights = parent.getGLightsDNI(spl[2], thisbridge)
					gLights.each { gl ->
						if(gl != null){
							gl.updateStatus("state", spl[4], v)
							log "GLight ${gl}", "trace"
						}
					}

					// LIGHTS		
				} else if (spl[1] == "lights") {
					spl[1] = "BULBS"
					devId = bridge.value.mac + "/" + spl[1].toUpperCase()[0..-2] + spl[2]
					d = parent.getChildDevice(devId)
					d.updateStatus(spl[3], spl[4], v)

				} else {
					log "Response contains unknown device type ${ spl[1] } .", "warn"
				}

				commandReturn
			}
		}	
	} else if (body[0] != null && body[0].error != null) {
		log "Error: ${body}", "warn"
	} else if (bridge) {

		def bulbs = [:] 
		def groups = [:] 
		def scenes = [:] 

		body?.lights?.each { k, v ->
			bulbs[k] = [id: k, label: v.name, type: v.type, state: v.state]
		}
		state.bulbs = bulbs

		body?.groups?.each { k, v -> 
			groups[k] = [id: k, label: v.name, type: v.type, action: v.action, all_on: v.state.all_on, any_on: v.state.any_on, lights: v.lights] //, groupLightDevIds: devIdsGLights]
		}
		state.groups = groups

		body.scenes?.each { k, v -> 
			scenes[k] = [id: k, label: v.name, type: "scene", lights: v.lights]
		}
		state.scenes = scenes

		def data = new JsonBuilder([bulbs, scenes, groups, schedules, bridge.value.mac])

		return createEvent(name: "itemDiscovery", value: device.hub.id, isStateChange: true, data: data.toString())
	}
}

def processLights(json) {
	def bulbs = [:] 
	def groups = [:] 
	def scenes = [:] 

	json?.each { k, v ->
		bulbs[k] = [id: k, label: v.name, type: v.type, state: v.state]
	}
	state.bulbs = bulbs

	def data = new JsonBuilder([bulbs, scenes, groups, schedules, device.deviceNetworkId])

	sendEvent(name: "itemDiscovery", value: device.hub.id, isStateChange: true, data: data.toString())
}

def processGroups(json) {
	def bulbs = [:] 
	def groups = [:] 
	def scenes = [:] 

	json?.each { k, v -> 
		groups[k] = [id: k, label: v.name, type: v.type, action: v.action, all_on: v.state.all_on, any_on: v.state.any_on, lights: v.lights] //, groupLightDevIds: devIdsGLights]
	}
	state.groups = groups

	def data = new JsonBuilder([bulbs, scenes, groups, schedules, device.deviceNetworkId])

	sendEvent(name: "itemDiscovery", value: device.hub.id, isStateChange: true, data: data.toString())
}

// parse events into attributes
def parse(String description) {

	log "parse", "trace"
	
	def parsedEvent = parseLanMessage(description)
	if (parsedEvent.headers && parsedEvent.body) {

		def headerString = parsedEvent.headers.toString()
		if (headerString.contains("application/json")) {
			def body = parseJson(parsedEvent.body)
			return processJson(body, parsedEvent.mac)			
		} else {
			log "Unrecognized messsage: ${parsedEvent.body}", "warn"
		}
		
	}
	return []
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