/**
 *  Hue B Smart Group
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
 *  12/11/2018 Change flash methods to match bulb
 *  13/11/2018 Fix bug in flash methods
 *  17/11/2018 Tidy up capabilities
 *  18/11/2018 Optimise device sync for multiple bridges
 */
preferences {
	input("tt", "number", title: "Time it takes for the lights to transition (default: 2 = 200ms)", defaultValue: 2)   
	input("flashNotifySecs", "number", title: "Flash notification seconds (default: 5s)", defaultValue: 5)
}  
 
metadata {
	definition (name: "Hue B Smart Group", namespace: "info_fiend", author: "Anthony Pastor") {
		capability "Actuator"
		capability "Color Control"
		capability "Color Mode"
		capability "Color Temperature"
		capability "Light"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"
        
		// extra Hue commands
		command "colorloopOn"
		command "colorloopOff"
		command "flashOn"
		command "flashOff"
		command "flashOnce"
		command "flashNotify"
        
		// extra Hue attributes
		attribute "effect", "enum", ["NONE", "COLORLOOP"]
		attribute "transitionTime", "NUMBER"
	}
}

// parse events into attributes
def parse(String description) {
	log "Parsing (ignoring) '${description}'", "debug"
}

def installed() {
	log "Installed with settings: ${settings}", "info"
	sendEvent(name: "transitionTime", value: tt)
	initialize()
}

def updated() {
	log "Updated with settings: ${settings}", "info"
	sendEvent(name: "transitionTime", value: tt)
}

def initialize() {
	state.xy = [:]
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
	log "Hue B Smart Group: setLevel ( ${inLevel} ): ", "trace"

	def level = scaleLevel(inLevel, true, 254)
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = this.device.currentValue("transitionTime") as Integer ?: 0

	def sendBody = [:]
	sendBody = ["on": true, "bri": level, "transitiontime": duration ? duration * 10 : tt]
	if (state.xy) {
		sendBody["xy"] = state.xy 
		state.xy = [:]
	} else if (state.ct) {
		sendBody["ct"] = state.ct as Integer
		state.ct = null
	}
    
	runIn(1, refresh)
    
	parent.sendHubCommand(new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
			headers: [
				host: "${commandData.ip}"
			],
			body: sendBody
		])
	)    
}

/**
 * capability.colorControl 
 **/
def sendToHub(values) {
	log "Hue B Smart Group: sendToHub ( ${values} ): ", "trace"
    
	def validValues = [:]
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def sendBody = [:]

	def bri
	if (values.level) {
		bri = values.level 
		validValues.bri = scaleLevel(bri, true, 254)
		sendBody["bri"] = validValues.bri
        
		if (values.level > 0) { 
			sendBody["on"] = true
		} else {
			sendBody["on"] = false
		}            
	} else {
		bri = device.currentValue("level") as Integer ?: 100
	} 

	if (values.switch == "off" ) {
		sendBody["on"] = false
	} else if (values.switch == "on") {
		sendBody["on"] = true
	}
        
	sendBody["transitiontime"] = device.currentValue("transitionTime") as Integer ?: 0
    
	def isOn = this.device.currentValue("switch")
	if (values.switch == "on" || values.level || isOn == "on") {
		state.xy = [:]
		state.ct = null
        
		if (values.hex != null) {
			if (values.hex ==~ /^\#([A-Fa-f0-9]){6}$/) {
				validValues.xy = colorFromHex(values.hex)		// getHextoXY(values.hex)
				sendBody["xy"] = validValues.xy
			} else {
				log "$values.hex is not a valid color", "warn"
			}
		} else if (values.xy) {
			validValues.xy = values.xy
		}
		
		if (validValues.xy ) {
			//log.debug "XY value found.  Sending ${sendBody} " 

			parent.sendHubCommand(new hubitat.device.HubAction(
				[
					method: "PUT",
					path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
					headers: [
						host: "${commandData.ip}"
					],
					body: sendBody 	
				])
			)
        
			sendEvent(name: "colormode", value: "XY", displayed: true, isStateChange: true) 
			sendEvent(name: "hue", value: values.hue as Integer, displayed: true) 
			sendEvent(name: "saturation", value: values.saturation as Integer, displayed: true, isStateChange: true) 
			sendEvent(name: "colorTemperature", value: -1, displayed: false, isStateChange: true)
            
		} else {
    	
			def h = values.hue.toInteger()
			def s = values.saturation.toInteger()
			//log.trace "sendToHub: no XY values, so get from Hue & Saturation."
			validValues.xy = colorFromHSB(h, s, bri) 	//values.hue, values.saturation)		// getHextoXY(values.hex)
			sendBody["xy"] = validValues.xy
			//log.debug "Sending ${sendBody} "

			parent.sendHubCommand(new hubitat.device.HubAction(
				[
					method: "PUT",
					path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
					headers: [
						host: "${commandData.ip}"
					],
					body: sendBody 
				])
			)    
			
			sendEvent(name: "colormode", value: "HS", displayed: true) //, isStateChange: true) 
			sendEvent(name: "hue", value: values.hue, displayed: true) //, isStateChange: true) 
			sendEvent(name: "saturation", value: values.saturation, displayed: true, isStateChange: true) 
			sendEvent(name: "colorTemperature", value: -1, displayed: false, isStateChange: true)   
		}
	} else {
		if (values.hue && values.saturation) {
			validValues.xy = colorFromHSB(values.hue, values.saturation, bri)
			//log.debug "Group off, so saving xy value ${validValues.xy} for later."   
			state.xy = validValues.xy
			state.ct = null
            
			sendEvent(name: "colormode", value: "HS", displayed: true) //, isStateChange: true) 
			sendEvent(name: "hue", value: values.hue, displayed: true) //, isStateChange: true) 
			sendEvent(name: "saturation", value: values.saturation, displayed: true, isStateChange: true) 
			sendEvent(name: "colorTemperature", value: -1, displayed: false, isStateChange: true)
		}    
	}    
}

def setColor(inValues) {
	log "Hue B Smart Group: setColor( ${inValues} ).", "trace"
	sendToHub(inValues)
}	

def setHue(inHue) {
	log "Hue B Smart Group: setHue( ${inHue} ).", "trace"
	def sat = this.device.currentValue("saturation") ?: 100
	if (sat == -1) { sat = 100 }
	sendToHub([saturation:sat, hue:inHue])
}

def setSaturation(inSat) {
	log "Hue B Smart Group: setSaturation( ${inSat} ).", "trace"
	def hue = this.device.currentValue("hue") ?: 70
	if (hue == -1) { hue = 70 }
	sendToHub([saturation:inSat, hue:hue])
}

/**
 * capability.colorTemperature 
 **/
def setColorTemperature(inCT) {
	log "Hue B Smart Group: setColorTemperature ( ${inCT} ): ", "trace"
    
	def colorTemp = inCT ?: this.device.currentValue("colorTemperature")
	colorTemp = Math.round(1000000/colorTemp)
    
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = device.currentValue("transitionTime") as Integer ?: 0
    
	def isOn = this.device.currentValue("switch")
	if (isOn == "on") {
    	
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

		state.ct = null

	} else {
		state.ct = colorTemp
	}

	sendEvent(name: "colormode", value: "CT", displayed: state.notiSetting2, isStateChange: true) 
	sendEvent(name: "hue", value: -1, displayed: false, isStateChange: true)
	sendEvent(name: "saturation", value: -1, displayed: false, isStateChange: true)
	sendEvent(name: "colorTemperature", value: inCT, displayed: otherNotice, isStateChange: true)
    
	state.xy = [:]  
    
}

/** 
 * capability.switch
 **/
def on() {
	log "Hue B Smart Group: on(): ", "trace"
	
	def commandData = parent.getCommandData(device.deviceNetworkId)
	def tt = device.currentValue("transitionTime") as Integer ?: 0
	def percent = device.currentValue("level") as Integer ?: 100
	def level = scaleLevel(percent, true, 254)
    
	def sendBody = [:]
	sendBody = ["on": true, "bri": level, "transitiontime": tt]
	if (state.xy) {
		sendBody["xy"] = state.xy
		state.xy = [:]
	} else if (state.ct) {
		sendBody["ct"] = state.ct
		state.ct = null
	}
    
	runIn(1, refresh)
    
	return new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
			headers: [
				host: "${commandData.ip}"
			],
			body: sendBody
		])
}

def off() {
	log "Hue B Smart Group: off(): ", "trace"
    
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
	log "Hue B Smart Group: poll(): ", "trace"
	refresh()
}

/**
 * capability.refresh
 **/
def refresh() {
	log "Hue B Smart Group: refresh(): ", "trace"
	parent.doDeviceSync(device.deviceNetworkId)
}

def reset() {
	log "Hue B Smart Group: reset(): ", "trace"

	def value = [level:70, saturation:56, hue:23]
	sendToHub(value)
}

/**
 * capability.alert (flash)
 **/
def flashOnce() {
    log "Hue B Smart Group: flashOnce(): ", "trace"
    
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
	log "Hue B Smart Group: flashNotify(): ", "trace"

	flashOn()
	runIn(flashNotifySecs ?: 5, flashOff)
}

def flashOn() {
	log "Hue B Smart Group: flashOn(): ", "trace"
  
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
	log "Hue B Smart Group: flashOff(): ", "trace"
    
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
 * Update Status
 **/
private updateStatus(action, param, val) {
	log "Hue B Smart Group: updateStatus ( ${param}:${val} )", "trace"
    
	if (action == "action") {
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
						//log.debug "NO Update Needed for switch."                	
					}
				} else {
					if (curValue != off) {
						log "Update Needed: Current Value of switch = true & newValue = ${val}", "debug"
						sendEvent(name: "switch", value: off, displayed: true)
						sendEvent(name: "effect", value: "none", displayed: false, isStateChange: true)    
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
			
	    case "hue":
				curValue = device.currentValue("hue")
				val = scaleLevel(val, false, 65535)
				if (val > 100) { val = 100 }                
				if (curValue != val) {
					log "Update Needed: Current Value of hue = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "hue", value: val, displayed: true, isStateChange: true) 
				} else {
					//log.debug "NO Update Needed for hue."                	
				}            	
				break
			
			case "sat":
				curValue = device.currentValue("saturation")
				val = scaleLevel(val)
				if (val > 100) { val = 100 }                
				if (curValue != val) { 
					log "Update Needed: Current Value of saturation = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "saturation", value: val, displayed: true, isStateChange: true) 
				} else {
					//log.debug "NO Update Needed for saturation."                	
				}
				break
			
			case "ct": 
				curValue = device.currentValue("colorTemperature")
				val = Math.round(1000000/val)
				if (curValue != val) {
					log "Update Needed: Current Value of colorTemperature = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "colorTemperature", value: val, displayed: true, isStateChange: true) 
				} else {
					//log.debug "NO Update Needed for colorTemperature."                	
				}
				break
			
			case "xy": 
				break    

			case "colormode":
				curValue = device.currentValue("colormode")
				if (curValue != val) {
					log "Update Needed: Current Value of colormode = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "colormode", value: val, displayed: false, isStateChange: true) 
				} else {
					//log.debug "NO Update Needed for colormode."                	
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
				if (val == "none" && display == 'all') {
					log "Not Flashing", "debug"
				} else if(val != "none" && display == 'all')  {
					log "Flashing", "debug"
				}
				break
			
			case "effect":
				curValue = device.currentValue("effect")
				if (curValue != val && display == 'all') { 
					log "Update Needed: Current Value of effect = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "effect", value: val, displayed: false, isStateChange: true) 
				} else {
					//log.debug "NO Update Needed for effect "                	
				}
				break
			
			case "lights":
				curValue = device.currentValue("lights")
				if (curValue != val && display == 'all') { 
					log "Update Needed: Current Value of lights = ${curValue} & newValue = ${val}", "debug"
					sendEvent(name: "lights", value: val, displayed: false, isStateChange: true) 
				} else {
					//log.debug "NO Update Needed for lights"
				}
				break
			
			case "scene":
				log "received scene ${val}", "trace"
				break    
			
			default: 
				log "Unhandled parameter: ${param}. Value: ${val}", "warn"
		}
	}
}

/**
 * capability.colorLoop
 **/
def colorloopOn() {
	log "Executing 'colorloopOn'", "trace"
	def tt = device.currentValue("transitionTime") as Integer ?: 0
    
	def dState = device.latestValue("switch") as String ?: "off"
	def level = device.currentValue("level") ?: 100
	if (level == 0) { percent = 100}
	
	def dMode = device.currentValue("colormode") as String
	if (dMode == "CT") {
		state.returnTemp = device.currentValue("colorTemperature")
	} else {
		state.returnHue = device.currentValue("hue")
		state.returnSat = device.currentValue("saturation")        
	}
	state.returnMode = dMode

	sendEvent(name: "effect", value: "colorloop", isStateChange: true)
	sendEvent(name: "colormode", value: "LOOP", isStateChange: true)
    
	def commandData = parent.getCommandData(device.deviceNetworkId)
	parent.sendHubCommand(new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
			headers: [
				host: "${commandData.ip}"
			],
			body: [on:true, effect: "colorloop", transitiontime: tt]
		])
	)

	sendEvent(name: "hue", value: -1, displayed: false, isStateChange: true)
	sendEvent(name: "saturation", value: -1, displayed: false, isStateChange: true)
	sendEvent(name: "colorTemperature", value: -1, displayed: false, isStateChange: true)
}

def colorloopOff() {
	log "Executing 'colorloopOff'", "trace"
	def tt = device.currentValue("transitionTime") as Integer ?: 0  
	def commandData = parent.getCommandData(device.deviceNetworkId)
	
	sendEvent(name: "effect", value: "none", isStateChange: true)    
	
	parent.sendHubCommand(new hubitat.device.HubAction(
		[
			method: "PUT",
			path: "/api/${commandData.username}/groups/${commandData.deviceId}/action",
			headers: [
				host: "${commandData.ip}"
			],
			body: [on:true, effect: "none", transitiontime: tt]
		])
	)
    
	if (state.returnMode == "CT") {
		def retCT = state.returnTemp as Integer
		setColorTemperature(retCT)
	} else {
		def retHue = state.returnHue as Integer
		def retSat = state.returnSat as Integer
		setColor(hue: retHue, saturation: retSat)
	}    
}

/**
 * Color Conversions
 **/
private colorFromHex(String colorStr) {
	/**
     * Gets color data from hex values used by Smartthings' color wheel. 
     */
    //log.trace "colorFromHex( ${colorStr} ):"
    
    def colorData = [colormode: "HEX"]
    
// GET HUE & SATURATION DATA   
    def r = Integer.valueOf( colorStr.substring( 1, 3 ), 16 )
    def g = Integer.valueOf( colorStr.substring( 3, 5 ), 16 )
    def b = Integer.valueOf( colorStr.substring( 5, 7 ), 16 )

	def HS = [:]
    HS = getHSfromRGB(r, g, b) 	
	
    def h = HS.hue * 100
    def s = HS.saturation * 100
    
	sendEvent(name: "hue", value: h, isStateChange: true)
	sendEvent(name: "saturation", value: s, isStateChange: true)

// GET XY DATA   
    float red, green, blue;

    // Gamma Corrections
    red = pivotRGB( r / 255 )
    green = pivotRGB( g / 255 )
    blue = pivotRGB( b / 255 )

	// Apply wide gamut conversion D65
    float X = (float) (red * 0.664511 + green * 0.154324 + blue * 0.162028);
    float Y = (float) (red * 0.283881 + green * 0.668433 + blue * 0.047685);
    float Z = (float) (red * 0.000088 + green * 0.072310 + blue * 0.986039);

	// Calculate the xy values
    float x = (X != 0 ? X / (X + Y + Z) : 0);
    float y = (Y != 0 ? Y / (X + Y + Z) : 0);

	double[] xy = new double[2];
    xy[0] = x;
    xy[1] = y;
	//colorData = [xy: xy]

	//log.debug "xy from hex = ${xy} ."
    return xy;
}


private colorFromHSB (h, s, level) {
	/**
     * Gets color data from Hue, Sat, and Brightness(level) slider values .
     */
	//log.trace "colorFromHSB ( ${h}, ${s}, ${level}):  really h ${h/100*360}, s ${s/100}, v ${level/100}"
    
 //   def colorData = [colormode: "HS"]
    
// GET RGB DATA       
	
    // Ranges are 0-360 for Hue, 0-1 for Sat and Bri
    def hue = (h * 360 / 100).toInteger()
    def sat = (s / 100)//.toInteger()
    def bri = (level / 100)	//.toInteger()
//	log.debug "hue = ${hue} / sat = ${sat} / bri = ${bri}"
    float i = Math.floor(hue / 60) % 6;
    float f = hue / 60 - Math.floor(hue / 60);
//	log.debug "i = ${i} / f = ${f} "
    
    bri = bri * 255
    float v = bri //as Integer //.toInteger()
    float p = (bri * (1 - sat)) //as Integer	// .toInteger();
    float q = (bri * (1 - f * sat)) //as Integer //.toInteger();
	float t = (bri * (1 - (1 - f) * sat)) //as Integer //.toInteger();

//	log.debug "v = ${v} / p = ${p} / q = ${q} / t = ${t} "
    
	def r, g, b
    
    switch(i) {
    	case 0: 
        	r = v
            g = t
            b = p
            break;
        case 1: 
        	r = q
            g = v
            b = p
            break;
        case 2: 
        	r = p
            g = v
            b = t
            break;
        case 3: 
        	r = p
            g = q
            b = v
            break;
        case 4: 
        	r = t
            g = p
            b = v
            break;
        case 5: 
        	r = v
            g = p
            b = q
            break;
	}
    
    r = r * 255
    g = g * 255
    b = b * 255
    
//	colorData = [R: r, G: g, B: b]

// GET XY DATA	
    float red, green, blue;
	
    // Gamma Corrections
    red = pivotRGB( r / 255 )
    //log.debug "red = ${red} / r = ${r}"
    green = pivotRGB( g / 255 )
    blue = pivotRGB( b / 255 )

	// Apply wide gamut conversion D65
    float X = (float) (red * 0.664511 + green * 0.154324 + blue * 0.162028);
    float Y = (float) (red * 0.283881 + green * 0.668433 + blue * 0.047685);
    float Z = (float) (red * 0.000088 + green * 0.072310 + blue * 0.986039);

	// Calculate the xy values
    float x = (X != 0 ? X / (X + Y + Z) : 0);
    float y = (Y != 0 ? Y / (X + Y + Z) : 0);

	double[] xy = new double[2];
    xy[0] = x as Double;
    xy[1] = y as Double;
//	colorData = [xy: xy]

	//log.debug "xy from HSB = ${xy[0]} , ${xy[1]} ."
    return xy;
    
}

private colorFromXY(xValue, yValue){
	/**
     * Converts color data from xy values
     */
    
    def colorData = [:]
    
    // Get Brightness & XYZ values
	def bri = device.currentValue("level") as Integer ?: 100
    
    float x = xValue
	float y = yValue
    float z = (float) 1.0 - x - y;
    float Y = bri; 
	float X = (Y / y) * x;
	float Z = (Y / y) * z;

// FIND RGB VALUES

    // Convert to r, g, b using Wide gamut D65 conversion
    float r =  X * 1.656492f - Y * 0.354851f - Z * 0.255038f;
	float g = -X * 0.707196f + Y * 1.655397f + Z * 0.036152f;
	float b =  X * 0.051713f - Y * 0.121364f + Z * 1.011530f;
                
	float R, G, B;
    // Apply Reverse Gamma Corrections
    def red = revPivotRGB( r * 255 )
    def green = revPivotRGB( g * 255 )	
    def blue = revPivotRGB( b * 255 )

	colorData = [red: red, green: green, blue: blue]

	//log.debug "RGB colorData = ${colorData}"	
/**
	def maxValue = Math.max(r, Math.max(g,b) );
    r /= maxValue;
    g /= maxValue;
    b /= maxValue;
    r = r * 255; if (r < 0) { r = 255 };
    g = g * 255; if (g < 0) { g = 255 };
    b = b * 255; if (b < 0) { b = 255 };
**/	

// GET HUE & SAT VALUES	
    def HS = [:]
    HS = getHSfromRGB(r, g, b) 
    
    colorData = [hue: HS.hue, saturation: HS.saturation]

	//log.debug "HS from XY = ${colorData} "    
    return colorData
}

private getHSfromRGB(r, g, b) {
	//log.trace "getHSfromRGB ( ${r}, ${g}, ${b}):  " 
    
    r = r / 255
    g = g / 255 
    b = b / 255
    
    def max = Math.max( r, Math.max(g, b) )
    def min = Math.min( r, Math.min(g, b) )
    
    def h, s, v = max;

    def d = max - min;
    s = max == 0 ? 0 : d / max;

    if ( max == min ){
        h = 0; // achromatic
    } else {
        switch (max) {
            case r: 
            	h = (g - b) / d + (g < b ? 6 : 0)
                break;
            case g: 
            	h = (b - r) / d + 2
                break;
            case b: 
            	h = (r - g) / d + 4
                break;
        }
        
        h /= 6;
    }
	
    def colorData = [:]
    
    colorData["hue"] = h
    colorData["saturation"] = s
    //log.debug "colorData = ${colorData} "
    return colorData
}

private pivotRGB(double n) {
	return (n > 0.04045 ? Math.pow((n + 0.055) / 1.055, 2.4) : n / 12.92) * 100.0;
}

private revPivotRGB(double n) {
	return (n > 0.0031308 ? (float) (1.0f + 0.055f) * Math.pow(n, (1.0f / 2.4f)) - 0.055 : (float) n * 12.92);
}    

/**
* original color conv
**/
private getHextoXY(String colorStr) {

    def cred = Integer.valueOf( colorStr.substring( 1, 3 ), 16 )
    def cgreen = Integer.valueOf( colorStr.substring( 3, 5 ), 16 )
    def cblue = Integer.valueOf( colorStr.substring( 5, 7 ), 16 )

    double[] normalizedToOne = new double[3];
    normalizedToOne[0] = (cred / 255);
    normalizedToOne[1] = (cgreen / 255);
    normalizedToOne[2] = (cblue / 255);
    float red, green, blue;

    // Make red more vivid
    if (normalizedToOne[0] > 0.04045) {
       red = (float) Math.pow(
                (normalizedToOne[0] + 0.055) / (1.0 + 0.055), 2.4);
    } else {
        red = (float) (normalizedToOne[0] / 12.92);
    }

    // Make green more vivid
    if (normalizedToOne[1] > 0.04045) {
        green = (float) Math.pow((normalizedToOne[1] + 0.055) / (1.0 + 0.055), 2.4);
    } else {
        green = (float) (normalizedToOne[1] / 12.92);
    }

    // Make blue more vivid
    if (normalizedToOne[2] > 0.04045) {
        blue = (float) Math.pow((normalizedToOne[2] + 0.055) / (1.0 + 0.055), 2.4);
    } else {
        blue = (float) (normalizedToOne[2] / 12.92);
    }

    float X = (float) (red * 0.649926 + green * 0.103455 + blue * 0.197109);
    float Y = (float) (red * 0.234327 + green * 0.743075 + blue * 0.022598);
    float Z = (float) (red * 0.0000000 + green * 0.053077 + blue * 1.035763);

    float x = (X != 0 ? X / (X + Y + Z) : 0);
    float y = (Y != 0 ? Y / (X + Y + Z) : 0);

    double[] xy = new double[2];
    xy[0] = x;
    xy[1] = y;
    return xy;
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
