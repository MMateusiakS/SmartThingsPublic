/**
*            EatonRF9542-Z
*
*            Copyright 2018 smartthings
*
*            Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*            in compliance with the License. You may obtain a copy of the License at:
*
*            http://www.apache.org/licenses/LICENSE-2.0
*
*            Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*            on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*            for the specific language governing permissions and limitations under the License.
*
*/


metadata {
    definition (name: "EatonRF9542-Z", namespace: "smartthings", author: "Smartthings"){
        capability "Switch Level"
        capability "Switch"
        capability "Refresh"

        // zw:L type:1202 mfr:001A prod:4441 model:0000 ver:3.17 zwv:3.67 lib:03 cc:26,27,75,86,70,71,85,77,2B,2C,72,73,87
        fingerprint mfr:"001A", prod:"4441", model:"0000"
    }

    tiles(scale : 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL"){
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff"
            }

            tileAttribute ("device.level", key: "SLIDER_CONTROL"){
                attributeState "level", label: '${level.value}%', action:"switch level.setLevel", range: "1..100"
            }
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2, backgroundColor: "#00a0dc"){
            state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "refresh"])
    }

    preferences{
        input name: "dimmerRampTime", type: "number", title: "Dimmer ramp time (from 0 to 255 s)", description: "5",
            displayDuringSetup: true, range: "0..255"
        input name: "delayedOff", type: "number", title: "Delayed time to turn off the device (from 1 to 255 s)", description: "5",
            displayDuringSetup: true, range: "1..255"
        input name: "lockout", type: "enum", title: "Lockout mode", description: "Select kind of lockout",
            required: true, options: ["DIMMER_NOT_PROTECTED", "DIMMER_ENTIRELY_PROTECTED", "DIMMER_PROTECTED_BY_SEQUENCE"]
    }
}

def parse(String description){
	def result = null

    if(description){
        def cmd = zwave.parse(description, [0x75 : 1])
        if (cmd) {
            result = zwaveEvent(cmd)
        }
    }
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){
    log.debug "SwitchMultilevelReport: $cmd"
    handleValue(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd){
    log.debug "SwitchMultilevelSet: $cmd"
    handleValue(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd){
    log.debug "BasicSet: $cmd"
    handleValue(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd){
    log.debug "SwitchMultilevelStartLevelChange: $cmd"
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd){
    log.debug "BasicReport: $cmd.value"
}

def zwaveEvent(physicalgraph.zwave.commands.protectionv1.ProtectionReport cmd){
    log.debug "ProtectionReport: $cmd"
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd){
    log.debug "ConfigurationReport: $cmd"
}

def zwaveEvent(physicalgraph.zwave.Command cmd){
    log.debug "Handling not caught events: $cmd"
}

def setLevel(val){
    log.debug "Executing 'setLevel()'. Value is: $val"
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: val)
    cmds << zwave.switchMultilevelV3.switchMultilevelGet()
    
    delayBetween cmds*.format(), calculateReportDelay()
}

def refresh(){
    def cmds = []
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 7)
    cmds << zwave.protectionV1.protectionGet()
    cmds << zwave.switchMultilevelV3.switchMultilevelGet()

    delayBetween cmds*.format(), 1000
}

def on(){
    log.debug "Device $device.displayName will be turned ON."
    getOnOffCommands(0xFF)
}

def off(){
    log.debug "Device $device.displayName will be turned OFF."
    getOnOffCommands(0x00)
}

def updated(){
    log.debug "Device $device.displayName is up-to-date."
    runIn(1, "initialize", [overwrite: true])
}

def installed(){
    log.debug "Device $device.displayName is INSTALLED."
    runIn(1, "initialize", [overwrite: true])
}

def initialize(){
    //If user does not type values for "dimmer ramp time" and "delay off" parameter,
    //default values are assigned
    def delayedOff = settings.delayedOff != null ? settings.delayedOff : 5
    def dimmerRampTime = settings.dimmerRampTime != null ? settings.dimmerRampTime : 5

    def cmds = []
    cmds << zwave.protectionV1.protectionSet(protectionState: getMode(lockout))
    cmds << zwave.switchMultilevelV3.switchMultilevelGet()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [delayedOff], parameterNumber: 1, size: 1)
    cmds << zwave.configurationV1.configurationSet(configurationValue: [dimmerRampTime], parameterNumber: 7, size: 1)

    sendHubCommand(cmds*.format(), 100)
}

def handleValue(cmd){
    def results = []

    results << createEvent(name: "switch", value: cmd.value == 0 ? "off" : "on")
    results << createEvent(name: "level", value: cmd.value)

    return results
}

def getMode(String lockout){
    def modeSet = 0

    if(lockout == "DIMMER_NOT_PROTECTED"){
        modeSet = 0
    }else if(lockout == "DIMMER_PROTECTED_BY_SEQUENCE"){
        modeSet = 1
    }else if(lockout == "DIMMER_ENTIRELY_PROTECTED"){
        modeSet = 2
    }
    return modeSet
}

def getOnOffCommands(newValue){
	//Set state "ON" or "OFF" and get report after "dimmer ramp time"
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: newValue)
	cmds << zwave.switchMultilevelV3.switchMultilevelGet()

	delayBetween cmds*.format(), calculateReportDelay()
}

def calculateReportDelay(){
	//DimmerRampTime calculated in miliseconds with offset 1000 ms
    //If default time is not provided, ramp time is 5 s
    def dimmerRampTime = settings.dimmerRampTime != null ? settings.dimmerRampTime : 5
    dimmerRampTime * 1000 + 1000
}
