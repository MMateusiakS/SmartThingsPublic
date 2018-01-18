/**
 *  EatonRF9542-Z
 *
 *  Copyright 2018 Malgorzata Mateusiak
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 
 
 
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
 
 
 
 
 
 Switch Level	capability.switchLevel
 */
 
 
 
metadata {
	definition (name: "EatonRF9542-Z", namespace: "MMateusiakS", author: "Malgorzata Mateusiak") {
		capability "Switch Level"
        capability "Switch"
        
        //fingerprint inClusters: "0x26, 0x27"
        fingerprint inClusters: "0x26, 0x27, 0x75, 0x70, 0x71, 0x85, 0x77, 0x2B, 0x2C, 0x72, 0x73, 0x86, 0x87"
        fingerprint mfr:"001A", prod:"4441", model:"0000"
	}

	tiles(size : 2) {
      
        multiAttributeTile(name:"switchM", type: "lighting", width: 6, height: 4, canChangeIcon: true) { 
 			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
 				attributeState "on", label:'${name}', action:"switch.off", icon:"st.lights.philips.hue-single", backgroundColor:"#00A0DC", nextState:"off" 
 				attributeState "off", label:'${name}', action:"switch.on", icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"on" 

 			} 
 		
 			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
 				attributeState "level", label: '${lavel.value} %', action:"switch level.setLevel" 
 			}
 	
 		}
    
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "refresh", label:"", action:"refresh.refresh", icon:"st.secondary.refresh", defaultState: true
		}
    
    main(["switchM"])
    details(["switchM", "refresh"])
    
    }     
}


def parse(String description) {
	log.debug "Parsing '${description}'"

	def result = null

	if(description){
    
		def cmd = zwave.parse(description)
		if (cmd) {
        
        	log.debug "Comand in Zwave language:  '${cmd}'"
			result = zwaveEvent(cmd)
			
		}	
    }
    
	log.debug("'$description' parsed to $result")
	return result

}


def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelGet cmd){
     log.debug "Comand in SwitchMultilevelGet:  '${cmd}'"
     
     def result = []
     return result
     
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

     log.debug "In SwitchMultilevelSet: '${cmd.dimmingDuration}' and '${cmd.value}'"
     createEvent(name: "switchLevel", level : cmd.value)
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd){
	
    log.debug "From BasicSet: ${cmd}"
    
    def results = [];
    
	if (cmd.value == 0) {
		createEvent(name: "switch", value: "off")
	} else if (cmd.value == 255) {
		createEvent(name: "switch", value: "on")
	}else{
    	createEvent(name: "level", value: cmd.value)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd){

    log.debug "From SwitchMultilevelStartLevelChange: ${cmd}"
    
    createEvent(name: "switchLevel", startLevel : cmd.startLevel, dimmingDuration: cmd.dimmingDuration)
   
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd){

	log.debug "From BasicReport: ${cmd.value}"
    
    if(cmd.value != 0 || cmd.value!= 1){
  		createEvent(name: "level", value: cmd.value)
    }
}


def setLevel(val) {
	log.debug "Executing 'setLevel' and number}"

	log.debug "${val}"
    def level = 1;
    
    if(val>=0 && val<13){
		level = 1;
    }else if(val>=13 && val<26){
    	level = 2;
    }else if(val>=26 && val<39){
    	level = 3;
    }else if(val>=39 && val<52){
    	level = 4;
    }else if(val>=52 && val<65){
    	level = 5;
    }else if(val>=65 && val< 78){
    	level = 6;
    }else{
    	level = 7;
    }
    
    
    def results = []
    
    results << sendEvent(name: "level", value: val)

    results << delayBetween([zwave.basicV1.basicSet(value: val).format(), zwave.basicV1.basicGet().format()], 1000)

	//physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelSet(value: 0) 

	return results
}



def on(){

	log.debug "I am trying to ON"
	delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(), zwave.basicV1.basicGet().format()], 10)

}


def off(){

	log.debug "I am trying to OFF"
	delayBetween([zwave.basicV1.basicSet(value: 0).format(), zwave.basicV1.basicGet().format()], 10)

}

def refresh() {
	log.debug "Executing 'refresh'"
}


def updated(){

    log.debug "updated() is being called"
   
    def cmds = configure1()
    if(cmds) sendHubCommand(cmds)
    
    
    cmds = configure2()
    if(cmds) sendHubCommand(cmds)
    
    log.debug "From updated and sendhub command: ${cmds}"
    
}


def installed(){

	log.debug "installed() is being called"
    def cmds = configure1() 
    if (cmds) sendHubCommand(cmds)
}


def initialize(){

    log.debug "initialize() is being called"
    def cmds = configure1()
    if (cmds) sendHubCommand(cmds)
}

def configure1(){

	def str = zwave.configurationV1.configurationSet(configurationValue: [5], parameterNumber: 1, size: 1).format()
    log.debug "Configuration info: ${str}"
    
	return new physicalgraph.device.HubAction(str)
}

def configure2(){
	def str = zwave.configurationV1.configurationSet(configurationValue: [5], parameterNumber: 7, size: 1).format()
    log.debug "Configuration info: ${str}"
    
	return new physicalgraph.device.HubAction(str)
}