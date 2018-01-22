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
 */
 
 
metadata {
	definition (name: "EatonRF9542-Z", namespace: "MMateusiakS", author: "Malgorzata Mateusiak") {
		capability "Switch Level"
        capability "Switch"
        
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
			state "refresh", label: "", action:"refresh.refresh", icon:"st.secondary.refresh", defaultState: true
		}
    

    main(["switchM"])
    details(["switchM", "refresh", "dimmingTime"])
    
    }
    
    preferences {
    	input name: "DimmerRampTime", type: "Integer", title: "Dimmer ramp time (from 0 to 255)", description: "Enter Value:", required: true,  displayDuringSetup: true, range: "0..255", defaultValue : "10"
        input name: "DelayedOff", type: "Integer", title: "Delayed OFF (from 0 to 255)", description: "Enter Value:", required: true,  displayDuringSetup: true, range: "0..255", defaultValue : "10"
        input name: "Lockout", type: "enum", title: "Select kind of lockout", description: "Select kind of lockout", required: true, displayDuringSetup: true, options: ["APP_NOT_ACTIVE", "DIMMER_NOT_ACTIVE"]
        input name: "DisactivationTime", type: "Integer", title: "Seconds of disactivation (from 0 to 255)", description: "Enter Value:", required: true,  displayDuringSetup: true, range: "0..255", defaultValue : "10"

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

     log.debug "In SwitchMultilevelSet: 'dimming time: ${cmd.dimmingDuration}' and value:'${cmd.value}'"
     
	 sendHubCommand(config2())  
     createEvent(name: "level", value : cmd.value)
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd){
	
    log.debug "From BasicSet: ${cmd}"
    
    def results = [];
    
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
    
    //createEvent(name: "switchLevel", startLevel : cmd.startLevel, dimmingDuration: cmd.dimmingDuration)
   
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd){

	log.debug "From BasicReport: ${cmd.value}"
   
    def results = [];
    return results;
}



def zwaveEvent(physicalgraph.zwave.commands.protectionv2.ProtectionReport cmd){

	log.debug "ProtectionReport ${cmd}"


}


def zwaveEvent(physicalgraph.zwave.commands.protectionv2.ProtectionGet cmd){
	log.debug "ProtectionGet ${cmd}"

}

def setLevel(val) {
	log.debug "Executing 'setLevel'. Value is : ${val}"
    
    def results = []
    
    results << sendEvent(name: "level", value: val)
    results << delayBetween([zwave.basicV1.basicSet(value: val).format(), zwave.basicV1.basicGet().format()], 10)

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
    
    def results = [];
    
    results << createEvent(name: "refresh")
    results << createEvent(name: "level", value: level.value, unit: "%")

	return results;
}


def updated(){

    log.debug "updated() is being called"
   
    def cmds = configure()
    if(cmds) sendHubCommand(cmds)
   
    log.debug "From updated and sendhub command: ${cmds}"  
}


def installed(){

	log.debug "installed() is being called"
    def cmds = configure() 
    if (cmds) sendHubCommand(cmds)
}


def initialize(){

    log.debug "initialize() is being called"
    def cmds = configure()
    if (cmds) sendHubCommand(cmds)
}

def configure(){

	def results = []

	results << config1()
	results << config2()
    results << configChildLockout()

    return results
}



def configChildLockout(){
	def results = []
    
    log.debug "Rest of child lockout feature"

    def str1 = zwave.protectionV2.protectionSet(localProtectionState: 0).format()
    def str2 = zwave.protectionV2.protectionSet(rfProtectionState: 0).format()

   // results << new physicalgraph.device.HubAction(str1)
  //  results << new physicalgraph.device.HubAction(str2)



    if(Lockout == "APP_NOT_ACTIVE"){
    
    	log.debug "APP will be not active"
    	def str = zwave.protectionV2.protectionSet(rfProtectionState: 1).format()
        results << new physicalgraph.device.HubAction(str)
    }

    if(Lockout == "DIMMER_NOT_ACTIVE"){
        log.debug "Dimmer will be not active"
        
        def strTime = zwave.protectionV2.protectionTimeoutSet(timeout: toShort(DisactivationTime)).format()
   		results << new physicalgraph.device.HubAction(strTime)

        def str = zwave.protectionV2.protectionSet(localProtectionState: 2).format()
        results << new physicalgraph.device.HubAction(str)
        

    }
    
   log.debug "Disactivation time is : ${DisactivationTime}."



	results
}
def config1(){

	if(DelayedOff){
    
        def str = zwave.configurationV1.configurationSet(configurationValue: [toShort(DelayedOff)], parameterNumber: 1, size: 1).format()
        log.debug "Configuration info: ${str}"

        return new physicalgraph.device.HubAction(str)
    }
}



def config2(){

    if(DimmerRampTime){
      
        def str = zwave.configurationV1.configurationSet(configurationValue: [toShort(DimmerRampTime)], parameterNumber: 7, size: 1).format()
        log.debug "Configuration info: ${str}"

        return new physicalgraph.device.HubAction(str)
     }
}

def toShort(value){
	return Short.parseShort(value.toString())
}