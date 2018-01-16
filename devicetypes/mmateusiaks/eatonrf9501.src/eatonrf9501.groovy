/**
 *  EatonRF9501
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
 *
 */

 
metadata {
	definition (name: "EatonRF9501", namespace: "MMateusiakS", author: "Malgorzata Mateusiak") {
		capability "Switch"
        
        fingerprint inClusters: "0x25,0x70"
        fingerprint mfr:"001A", prod:"534C", model:"0000"
	}



	tiles {
	
    	standardTile("switchEaton", "device.switch", width: 2, height: 2, canChangeIcon: true) {
                        state "on", label: '${Name}', action: "switch.off",
                              icon: "st.Lighting.light11", backgroundColor: "#79b821"
                        state "off", label: '${Name}', action: "switch.on",
                              icon: "st.Lighting.light13", backgroundColor: "#ffffff"
                }
   
                
        main("switchEaton")
        details(["switchEaton"])
    
    }
}

def parse(String description) {

	log.debug "Parsing description: ${description}"
    
    def result = null
  	def command = zwave.parse(description)
    
    if(command){
    	result = zwaveEvent(command)
        log.debug "Parsed ${command} to $result ${result.inspect()}"
    }else{
    	log.debug "NO parsed event: ${description}"
    }
    
    return result

}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{

	log.debug "Value of command is : ${cmd.value}" 
    def result = null
    
    if (cmd.value == 0) {
        result = createEvent(name: "switch", value: "off")
        log.debug "I send info to turn OFF"
        log.debug "basic set comand ${result}"
    }
    
    if (cmd.value == 255){
        result = createEvent(name: "switch", value: "on")
        log.debug "I send info to turn ON"
        log.debug "basic set comand ${result}"
    }
    
    return result
}


def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    def result = null

    result = createEvent(name:"switch", value: cmd.value ? "on" : "off")
    log.debug " zawave event SwitchBinaryReport ${result}"

    return result;

}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
    def result = null
    result =  createEvent(name:"switch", value: cmd.value ? "on" : "off")

    log.debug " zawave event basic report ${result}"

    return result

}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd){

    if(cmd){
        log.debug "V1: ${cmd}"
    }
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd){

    if(cmd){
        log.debug "V2: ${cmd.configurationValue}"
        return createEvent( name : "configurationValue", value: cmd.configurationValue)
    }
}

def on() {
	log.debug "Executing 'on'"
   
    delayBetween([
        zwave.basicV1.basicSet(value: 0xFF).format(),
        zwave.basicV1.basicGet().format()
    ], 10)
      
}

def off() {

	log.debug "Executing 'off'"

    delayBetween([
        zwave.basicV1.basicSet(value: 0x00).format(),
        zwave.basicV1.basicGet().format()
    ], 10)
     
}

def updated(){

    log.debug "updated() is being called"
    
    def cmds = configure1()

    if(cmds) sendHubCommand(cmds)
    
    log.debug "From updated and sendhub command: ${cmds}"
    log.debug "From updated and sendhub command: ${hubComand}"
    
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

	def str = zwave.configurationV1.configurationSet(configurationValue: [0x10], parameterNumber: 1, size: 1).format()
    
    log.debug "Configuration info: ${str}"
    
	return new physicalgraph.device.HubAction(str)

}