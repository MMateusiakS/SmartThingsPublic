/**
 *	EatonRF9542-Z
 *
 *	Copyright 2018 Malgorzata Mateusiak
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 
 
 
zw:L type:1202 mfr:001A prod:4441 model:0000 ver:3.17 zwv:3.67 lib:03 cc:26,27,75,86,70,71,85,77,2B,2C,72,73,87

 
 *26 -> Switch Multilevel 
 *27 -> Switch All
 *75 -> Protection
 *70 -> Configuration
 *71 -> Alarm
 *85 -> Association
 *77 -> Node Naming
 *2B -> Scene Activation
 *2C -> Scene Actuator Conf
 *72 -> Manufacturer Specific
 *73 -> Powerlevel
 *86 -> Version
 *87 -> Indicator
 
*/
 
 
metadata {
	definition (name: "EatonRF9542-Z", namespace: "MMateusiakS", author: "Malgorzata Mateusiak") {
		capability "Switch Level"
		capability "Switch"
		capability "Refresh"

		fingerprint inClusters: "0x26, 0x27, 0x75, 0x70, 0x71, 0x85, 0x77, 0x2B, 0x2C, 0x72, 0x73, 0x86, 0x87"
		fingerprint mfr:"001A", prod:"4441", model:"0000"
	}

	tiles(size : 2) {

		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) { 
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"off" 
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"on" 
			}
		
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", label: '${lavel.value}%', action:"switch level.setLevel", range: "1..100" 
			}
		}

		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2, backgroundColor: "#00a0dc") { 
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"	 
		}


	main(["switch"])
	details(["switch", "refresh"])

}

preferences{
	input name: "dimmerRampTime", type: "number", title: "Dimmer ramp time (from 0 to 255 s)", description: "Enter Value:", 
		required: true,	 displayDuringSetup: true, range: "0..255", defaultValue : 10
	input name: "delayedOff", type: "number", title: "Delayed time to turn off the device (from 0 to 255)", description: "Enter Value:",
		required: true,	 displayDuringSetup: true, range: "0..255", defaultValue : 10
	input name: "lockout", type: "enum", title: "Select kind of lockout", description: "Select kind of lockout", 
		required: true, displayDuringSetup: true, options: ["DIMMER_ACTIVE", "DIMMER_ENTIRELY_PROTECTED", "DIMMER_PROTECTED_BY_SEQUENCE"]
	input name: "speedOfLevelChange", type: "enum", title: "Select speed of level change:", description: "Select speed of level change", 
		required: true, displayDuringSetup: true, options: ["SLOW", "MEDIUM", "FAST"]
	}
}

def parse(String description) {

	log.debug "Parsing ${description}"
	def result = null

	if(description){

		def cmd = zwave.parse(description, [0x75 : 1])
		if (cmd) {
			log.debug "Comand after Zwave parsing:	'${cmd}'"
			result = zwaveEvent(cmd)
			log.debug "${description} parsed to ${result.inspect()}"
		}
	}

	return result
}


def zwaveEvent(physicalgraph.zwave.Command cmd){
	log.debug "Handling not catched events"
	return createEvent(descriptionText: "${device.displayName} : ${cmd}")
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){

	log.debug "In SwitchMultilevelReport: '${cmd}'"

	def result = []
	def value = (cmd.value ? "on" : "off")
	def switchEvent = createEvent(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
	result << switchEvent

	if (cmd.value) {
		result << createEvent(name: "level", value: cmd.value, unit: "%")
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd){

	log.debug "In SwitchMultilevelSet: 'dimming time: ${cmd.dimmingDuration}' and value:'${cmd.value}'"

	sendHubCommand(configDimmerRampTime())	
	createEvent(name: "level", value : cmd.value)
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd){
	
	log.debug "From BasicSet: ${cmd}"
	def results = []

	if (cmd.value == 0) {
		results << createEvent(name: "switch", value: "off")
	} else if (cmd.value == 255) {
		results << createEvent(name: "switch", value: "on")
	}else{
		results << createEvent(name: "switch", value: "on")
		results << createEvent(name: "level", value: cmd.value)
	}

	return results
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd){

log.debug "From SwitchMultilevelStartLevelChange: ${cmd}"

	Short stepOfLevelChange = 5

	if(speedOfLevelChange == "MEDIUM"){
		stepOfLevelChange = 10
	}

	if(speedOfLevelChange == "FAST"){
		stepOfLevelChange = 15
	}

	log.debug "Speed of increasing or decreasing light is ${stepOfLevelChange}"

	if(cmd.upDown == 1){
		log.debug "The action for decreasing the light intensity was done"
		def newVal = cmd.startLevel - stepOfLevelChange
		if(newVal > 3){
				setLevel(newVal)
			}
	}

	if(cmd.upDown == 0){
		log.debug "The action for increasing the light intensity was done"
		def newVal = cmd.startLevel + stepOfLevelChange
		if(newVal < 100){
			setLevel(newVal)
		}	
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd){
	log.debug "From BasicReport: ${cmd.value}"
	def results = []
}


def zwaveEvent(physicalgraph.zwave.commands.protectionv1.ProtectionReport  cmd){
	log.debug "From ProtectionReport : ${cmd}"
}

def setLevel(val) {
	log.debug "Executing 'setLevel()'. Value is : ${val}"

	def results = []

	results << sendEvent(name: "level", value: val)
	results << delayBetween([zwave.basicV1.basicSet(value: val).format(), zwave.basicV1.basicGet().format()], 10)

	return results
}



def refresh(){
	def now = now()
	log.debug "Refreshed clicked on ${now}."

	if(!state.refreshTriggeredAt || (1000 <	 now - state.refreshTriggeredAt)){

	log.debug "Refresh Triggered state is null or delay is longer that one second"
	state.refreshTriggeredAt = now
	def cmds = [] 
	cmds << zwave.configurationV1.configurationGet() 
	cmds << zwave.protectionV1.protectionGet() 
	cmds << zwave.basicV1.basicGet() 

	delayBetween cmds*.format(), 1000
	}else{
		return null
	}
}

def on(){
	log.debug "Device will be ON."
	delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(), zwave.basicV1.basicGet().format()], 10)
}

def off(){
	log.debug "Device will be OFF."
	delayBetween([zwave.basicV1.basicSet(value: 0x00).format(), zwave.basicV1.basicGet().format()], 10)
}

def updated(){
	log.debug "Updated() was called. Device $device.displayName is up-to-date."
	initialize()
}

def installed(){
	log.debug "Installed() was called. Device $device.displayName is INSTALLED."
	initialize() 
}

def initialize(){

	def results = []
	results << configCurrentStateOnorOffAndLevelValue()
	results << configDelayedOff()
	results << configDimmerRampTime()
	results << configChildLockout()

	sendHubCommand(results)
}

def configCurrentStateOnorOffAndLevelValue(){
	//get data from device to update

	def cmds = []
	cmds << zwave.switchMultilevelV1.switchMultilevelGet()
	cmds << zwave.basicV1.basicGet()
	cmds << zwave.protectionV1.protectionGet()


	log.debug "Actual status of device: ${cmds*.format()}"
	delayBetween cmds*.format()
}

def configChildLockout(){
	//configuration to set selected protection mode. 3 modes are possible.
	def results = []
	def modeSet

	if(lockout == "DIMMER_ACTIVE"){
		modeSet = zwave.protectionV1.protectionSet(protectionState: 0).format()
		log.debug "Dimmer will be active. ${modeSet}"
	}

	if(lockout == "DIMMER_PROTECTED_BY_SEQUENCE"){
		modeSet = zwave.protectionV1.protectionSet(protectionState: 1).format()
		log.debug "Dimmer will be active after taping the button 3 times. ${modeSet}"
	}

	if(lockout == "DIMMER_ENTIRELY_PROTECTED"){
		modeSet = zwave.protectionV1.protectionSet(protectionState: 2).format()
		log.debug "Dimmer will be not active. ${modeSet}"
	}

	results << new physicalgraph.device.HubAction(modeSet)
}


def configDelayedOff(){
	log.debug "configuration of time of delay to turn off"
	if(delayedOff){
		def str = zwave.configurationV1.configurationSet(configurationValue: [delayedOff], parameterNumber: 1, size: 1).format()
		log.debug "Delayed to off is: $delayedOff"
		return new physicalgraph.device.HubAction(str)
	}
}

def configDimmerRampTime(){
	log.debug "configuration of ramp time"
	if(dimmerRampTime){
		def str = zwave.configurationV1.configurationSet(configurationValue: [dimmerRampTime], parameterNumber: 7, size: 1).format()
		log.debug "Ramp time is: $dimmerRampTime"
		return new physicalgraph.device.HubAction(str)
	}
}
