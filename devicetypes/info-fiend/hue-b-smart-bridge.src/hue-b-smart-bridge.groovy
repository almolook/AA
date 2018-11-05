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
 */

import groovy.json.*

metadata {
	definition (name: "Hue B Smart Bridge", namespace: "info_fiend", author: "Anthony Pastor") {
	capability "Actuator"
	capability "Health Check"

	attribute "serialNumber", "string"
	attribute "networkAddress", "string"
	attribute "status", "string"
	attribute "username", "string"
	attribute "host", "string"
        
		command "discoverItems"
        command "discoverBulbs"
        command "discoverGroups"
        command "discoverScenes"
        command "pollItems"
        command "pollBulbs"
        command "pollGroups"
        command "pollScenes"
        
	}
}

void installed() {
	log.debug "Installed with settings: ${settings}"
	sendEvent(name: "DeviceWatch-Enroll", value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}")
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	initialize()
}

def initialize() {
    def commandData = parent.getCommandData(device.deviceNetworkId)
    log.debug "Initialize Bridge ${commandData}"
    sendEvent(name: "idNumber", value: commandData.deviceId, displayed:true, isStateChange: true)
    sendEvent(name: "networkAddress", value: commandData.ip, displayed:false, isStateChange: true)
    sendEvent(name: "username", value: commandData.username, displayed:false, isStateChange: true)
    state.host = this.device.currentValue("networkAddress") + ":80"
    state.userName = this.device.currentValue("username")
    state.initialize = true
}


def discoverItems(inItems = null) {
	log.trace "Bridge discovering all items on Hue hub."
	
	if (state.initialize != true ) { initialize() }
 	if (state.user == null ) { initialize() }
	
	def host = state.host
	def username = state.userName

  	log.debug "*********** ${host} ********"
	log.debug "*********** ${username} ********"
	def result 
        
    if (!inItems) {
	    result = new hubitat.device.HubAction(
			method: "GET",
			path: "/api/${username}/",
			headers: [
				HOST: host
			]
		)
    }    
                 
	return result

}

def pollItems() {
	log.trace "pollItems: polling state of all items from Hue hub."

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

def discoverBulbs() {
	log.trace "discoverBulbs: discovering bulbs from Hue hub."

	def host = state.host
	def username = state.userName
        
	def result = new hubitat.device.HubAction(
	method: "GET",
	path: "/api/${username}/lights/",
		headers: [
			HOST: host
		]
	)
	
    return result
}

def pollBulbs() {
	log.trace "ollBulbs: polling bulbs state from Hue hub."

	def host = state.host
	def username = state.userName
        
	sendHubCommand(new hubitat.device.HubAction(
	method: "GET",
	path: "/api/${username}/lights/",
		headers: [
			HOST: host
		]
	))
	    
}

def discoverGroups() {
	log.debug("discoverGroups: discovering groups from Hue hub.")

	def host = state.host
	def username = state.userName
        
	def result = new hubitat.device.HubAction(
		method: "GET",
		path: "/api/${username}/groups/",
		headers: [
			HOST: host
		]
	)
    
	return result
}

def pollGroups() {
	log.trace "pollGroups: polling groups state from Hue hub."

	def host = state.host
	def username = state.userName
        
	sendHubCommand(new hubitat.device.HubAction(
	method: "GET",
	path: "/api/${username}/groups/",
		headers: [
			HOST: host
		]
	))
	    
}

def pollScenes() {
	log.trace "pollGroups: polling scenes state from Hue hub."

	def host = state.host
	def username = state.userName
        
	sendHubCommand(new hubitat.device.HubAction(
	method: "GET",
	path: "/api/${username}/scenes/",
		headers: [
			HOST: host
		]
	))
	    
}

def handleParse(desc) {

	log.trace "handleParse()"
	parse(desc)

}


// parse events into attributes

def parse(String description) {

	//log.trace "parse()"
	
	def parsedEvent = parseLanMessage(description)
	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = parsedEvent.headers.toString()
		if (headerString.contains("application/json")) {
			def body = new groovy.json.JsonSlurperClassic().parseText(parsedEvent.body)
			def bridge = parent.getBridge(parsedEvent.mac)
            def group 
			def commandReturn = []
            log.trace "Version 1.71"
			/* responses from bulb/group/scene/ command. Figure out which device it is, then pass it along to the device. */
			if (body[0] != null && body[0].success != null) {
            	log.trace "${body[0].success}"
				body.each{
					it.success.each { k, v ->
						def spl = k.split("/")
						//log.debug "k = ${k}, split1 = ${spl[1]}, split2 = ${spl[2]}, split3 = ${spl[3]}, split4= ${spl[4]}, value = ${v}"                            
						def devId = ""
                        def d
                        def groupScene
						
						if (spl[4] == "scene" || it.toString().contains( "lastupdated") ) {	
							log.trace "HBS Bridge:parse:scene - msg.body == ${body}"
                   			devId = bridge.value.mac + "/SCENE" + v
	                        d = parent.getChildDevice(devId)
    	                    groupScene = spl[2]
                            d.updateStatus(spl[3], spl[4], v) 
							log.debug "Scene ${d.label} successfully run on group ${groupScene}."
							//parent.doDeviceSync("bulbs")

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
                                		log.debug "GLight ${gl}"
										}
                            }
                            
						// LIGHTS		
						} else if (spl[1] == "lights") {
							spl[1] = "BULBS"
							devId = bridge.value.mac + "/" + spl[1].toUpperCase()[0..-2] + spl[2]
							d = parent.getChildDevice(devId)
	                    	d.updateStatus(spl[3], spl[4], v)
						} else {
							log.warn "Response contains unknown device type ${ spl[1] } ."                                               	            
						}
                        
                        commandReturn
						}
					}	
			} else if (body[0] != null && body[0].error != null) {
				log.warn "Error: ${body}"
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
                   		//log.trace "k=${k} and v=${v}"
                      	scenes[k] = [id: k, label: v.name, type: "scene", lights: v.lights]
                   	}
                  	state.scenes = scenes

                def data = new JsonBuilder([bulbs, scenes, groups, schedules, bridge.value.mac])
                
            	return createEvent(name: "itemDiscovery", value: device.hub.id, isStateChange: true, data: data.toString())
          
			}
			
		} else {
			log.debug("Unrecognized messsage: ${parsedEvent.body}")
		}
		
	}
		return []
}