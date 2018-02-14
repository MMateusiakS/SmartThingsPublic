/**
 *  Samsung Robot Vacuum
 *
 *  Copyright 2015 Samsung
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

preferences {
    input ("versionInfo", "string", title: "Version",description: "20160202", displayDuringSetup: false, type: "paragraph", element: "paragraph")
}
/*
 * Mode		: HTML Mode
 * Usage	: Convert map to Json String
 */
import groovy.json.JsonBuilder

metadata {
    definition (name: "Samsung Robot Vacuum- MM", namespace: "samsung", author: "Samsung") {
        capability "Switch"
        capability "Refresh"
        capability "Timed Session"
        capability "Battery"

        attribute "operationState", "STRING"
        attribute "operationThingsState", "ENUM", ["On", "Off", "ready", "pause", "cleaning", "Monitoring_On", "Monitoring_Off", "Monitoring_ready", "Monitoring_pause", "Monitoring_cleaning", "Monitoring_off", "ControlOff", "Disconnected"]
        attribute "remoteControlDisable", "ENUM", ["false", "true"]
        attribute "operationPower", "ENUM", ["disabled", "On", "Off", "OnDisabled", "OffDisabled"]
        attribute "alarms", "STRING"
        attribute "isConnected", "STRING"
        attribute "deviceState", "STRING"
        attribute "modeControl", "ENUM", ["Control_Idle", "Control_Homing", "Control_Alarm", "Control_Charging", "Control_Reserve", "Control_PowerOff", "Control_Cleaning"]
        attribute "modeCleaning", "ENUM", ["Cleaning_Auto", "Cleaning_Part", "Cleaning_Repeat", "Cleaning_Manual", "Cleaning_Stop"]

        command "subscribeDevice"
        command "unsubscribeDevice"
        command "setModeHoming"
        command "setMovementStop"
        command "refreshState"

        /*
         * Mode		: HTML Mode
         * Usage	: functions exposed for Solution Module
         */
        command "sendRequest", ["string", "string", "string"]
    }

    tiles(scale:2) {
        standardTile("operationThingsState", "device.operationThingsState", height: 2, width: 2) {
            state "ControlOff",             label:getLabel("Off"),          icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff", action:"on",      nextState: "ready"
            state "ready",                  label:getLabel("Ready"),        icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#dedede",	action:"start",   nextState: "cleaning"
            state "cleaning",               label:getLabel("Cleaning"),     icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#00A0DC",	action:"stop",    nextState: "pause"
            state "pause",                  label:getLabel("Pause"),        icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#dedede",	action:"start",   nextState: "cleaning"
            state "off",                    label:getLabel("Off"),          icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff"
            state "Monitoring_ready",       label:getLabel("Ready"),        icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff"
            state "Monitoring_cleaning",    label:getLabel("Cleaning"),     icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff"
            state "Monitoring_pause",       label:getLabel("Pause"),        icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff"
            state "Monitoring_Off",         label:getLabel("Off"),          icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff"
            state "Disconnected",           label:getLabel("Disconnected"), icon: "st.samsung.da.RC_ic_rc", backgroundColor: "#ffffff"
        }

        htmlTile(name:"Samsung Robot Vacuum", action:"main", width: 6, height: 12, whitelist:whitelist())
        main "operationThingsState"
        details(["Samsung Robot Vacuum"])
    }
}



def ConnectedResponse(value)
{
    def ConnectedBody = [:]
    ConnectedBody.type = "notification"
    ConnectedBody.status = 200
    def sConnected = [:]
    sConnected.connedted = value
    sConnected.scsConnectionState = value
    def sDevice = [:]
    sDevice.Device = sConnected
    def data = [:]
    data.Device = [sDevice]
    ConnectedBody.data = data.Device


    def ConnectedJson = new JsonBuilder(ConnectedBody).toString()
    log.debug "ConnectedJson : ${ConnectedJson}"

    return ConnectedJson

}


def parse(String description) {
	log.debug "Hi! I am Meg!!"
    def result = []
    def msg = parseLanMessage(description)
    def composedBody = [:]

    if (msg.status >= 400) {	// error
        composedBody.status = msg.status
        composedBody.type = 'requestResponse'	// FIXME : it assumes no error response comes for notification
        composedBody.data = ['message':msg.body]

        def jsonStr = new JsonBuilder(composedBody).toString()
        log.error "parse() : status: ${msg.status}, header: ${msg.header}, body: ${msg.body}, jsonStr: ${jsonStr}"
        notifyResponseToJS(msg.requestId, jsonStr)
        return result
    }

    if (msg.headers) {
        if (msg.headers.Location?.startsWith("/subscriptions/")) {
            log.debug "parse() : subscription response arrived"

            def identifier = msg.headers.Location - "/subscriptions/"
            device.updateDataValue("identifier", identifier)
            def action = []
            if (state.ConnectedCount > 1)
            {
                notifyResponseToJS(msg.requestId, ConnectedResponse(true))
                action += refresh()
            }

            state.ConnectedCount = 0
            state.isConnected = "connected"
            checkThingsStatus()
            return action
        }

        if (msg.headers["Content-Type"]?.contains("json")){
            if(msg.body){ // GET
                def body = parseJson(msg.body)

                if (body.Notification) {
                    // tile
                    body.Notification?.Events.each {
                        parseResource(it, result)
                    }

                    // html
                    composedBody.type = 'notification'
                    composedBody.data = body.Notification.Events
                    composedBody.status = msg.status
                    log.debug "parse() : notification(${composedBody})"
                }

                if (body.Device) {
                    // tile
                    parseResource(body.Device, result)
                    if(body.Device.Alarm == NULL)
                    {
                        log.debug "Device.Alarm is NULL : createEvent(name: \"alarms\", value: \"NONE\")"
                        result << createEvent(name: "alarms", value: "NONE", displayed:"false")
                    }
                    // html
                    composedBody.type = 'requestResponse'
                    composedBody.data = body
                    composedBody.status = msg.status

                    log.debug "parse() : DEVICE requestResponse(${composedBody})"
                }

                if (body.Configuration) {
                    parseConfiguration(body.Configuration, result)
                    // html
                    composedBody.type = 'requestResponse'
                    composedBody.data = body
                    composedBody.status = msg.status
                    log.debug "parse() : CONFIGURATION requestResponse(${composedBody})"
                }

                if (data.Action) {
                    parseAction(data.Action, result)
                }

                refreshState(result)
            }
            else{	// PUT, POST, DELETE				// constains resourceURL
                composedBody.status = msg.status
                composedBody.type = 'requestResponse'
                composedBody.data = ['message':'']

                log.debug "parse() : PUT/POST/DELETE requestResponse(${composedBody})"
            }
            if(composedBody.type){
                def jsonStr = new JsonBuilder(composedBody).toString()
                log.debug "parse() : length: ${jsonStr.length()}, jsonStr: ${jsonStr}"


                notifyResponseToJS(msg.requestId, jsonStr)
            }
        }
    }

    //Anyway it is alive when some msg is received from device
    if (state.ConnectedCount > 1)
    {
        notifyResponseToJS(msg.requestId, ConnectedResponse(true))
    }

    state.ConnectedCount = 0
    state.isConnected = "connected"
    checkThingsStatus()
    return result
}


String generateUUID() {
    def chars = ('0'..'9')+('a'..'z')+('A'..'Z')
    def rnd = new Random()
    String result = ""
    for (i in 1..16) {
        result += chars[rnd.nextInt(chars.size())]
    }
    return result
}

private notifyResponseToJS(requestId, jsonBody)
{
    log.trace("notifyResponseToJS()")
    // Max size of an event (if jsonBody exceeds this value, it's split into multiple events)
    int maxSize = 64*1024

    // Encode data into BASE64
    String payload = jsonBody.bytes.encodeBase64()

    // Generate packet ID
    def id = generateUUID()

    // Figure out parts needed and size
    def count = (int)(Math.ceil(payload.length() / maxSize))
    def part = 0
    def len = payload.length()

    // Break it down
    while (len > 0) {
        part = part + 1
        def data = ["id" :  id, "count" : count, "part" : part, "seq" : generateUUID()]
        def tosend = ((len < maxSize) ? len : maxSize)
        data["data"] = payload.substring(0, tosend)
        len = len - tosend
        log.trace("Sending event: " + tosend)
        //sendEvent(name: "updateUI", value:new JsonBuilder(data).toString(), data: [], isStateChange: true, displayed: false)
        sendEvent(name: "updateUI", value:tosend, data: ["payload":new JsonBuilder(data).toString()], isStateChange: true, displayed: false)
        if (len > 0) {
            payload = payload.substring(maxSize)
        }
    }
}

def installed() {
    log.trace "installed()"
    initialize()
    	log.debug "Hi! I am Meg!!"

}

def uninstalled() {
    log.trace "uninstall()"
    	log.debug "Hi! I am Meg!!"

    sendHubCommand(unsubscribeDevice())
}

def refresh() {
    log.trace "refresh()"
    return query()
    	log.debug "Hi! I am Meg!!"

}

def query() {
    log.trace "query()"
    return createHTTPHubAction("GET", "/devices/0")
}

def unsubscribeDevice() {
    log.trace "unsubscribeDevice()"

    createHTTPHubAction("DELETE", "/subscriptions/${device.getDataValue('identifier')}")
}

def subscribeDevice() {
    log.trace "subscribeDevice()"

    if (state.ConnectedCount >= 1)
    {
        state.isConnected = "disconnected"
        checkThingsStatus()
        notifyResponseToJS(0, ConnectedResponse(false))
    	state.ConnectedCount = state.ConnectedCount + 1
    }
    else
    {
    	state.ConnectedCount = state.ConnectedCount + 1
    }


    def body =	[Subscription: [
            description: "subscriptionRequest",
            notificationURI: "http://${device.hub.getDataValue('localIP')}:39500/notifications",
            resourceURIs: ["/devices"],
            uuid: device.id
    ]]

    createHTTPHubAction("POST", "/subscriptions", body)
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// html

def sendRequest(method, path, body){
    log.debug "sendRequest() : stringParam: ${method}, ${path}, ${body}"

    def hubaction=[]
    hubaction += createHTTPHubAction(method, path, body)
    sendHubCommand(hubaction)
}

private createHTTPHubAction(method, path, body = null) {
    log.trace "createHTTPHubAction(${method}, ${path}, ${body})"

    def token = device.getDataValue('deviceToken')
    if (!token) {
        log.error "createHTTPHubAction() : deviceToken is null"
        return
    }

    def action
    def headers = [
            Authorization: "Bearer ${token}",
            "Content-Type": "application/json; charset=utf-8",
            Connection: "Close",
            Host: "${device.getDataValue('ip')}:${device.getDataValue('port')}",
            "X-API-Version": "v1.0.0"
    ]

    if (body) {
        action = new physicalgraph.device.HubAction(method: method, path: path, headers: headers, body: body)
    } else {
        headers.put("Content-Length", "0")
        action = new physicalgraph.device.HubAction(method: method, path: path, headers: headers,)
    }

    action.options = [security:"LAN_SECURITY_TLS", auth:"LAN_AUTHENTICATION_MUTUAL"]

	/*
    log.trace "createHTTPHubAction( ${action} )"
	*/
    return action
}

private delayAction(long time) {
    new physicalgraph.device.HubAction("delay $time")
}

def mybuildResourceUrl(str) {
    log.trace("mybuildResourceUrl() : Translating: \"${str}\"")
    try {
        return buildResourceUrl(str)
    } catch (all) {
        log.error("mybuildResourceUrl() :  Failed to load \"${str}\"")
    }
    return str
}

def getRequestHandler(){
    def path = params.uri
    log.debug "getRequestHandler() : get path:${path}"

    sendRequest("GET", path, json)

    return [ response: "get method sent" ]
}

def putRequestHandler(){
    def json = request.JSON
    def payload = json.payload
    def path = json.uri

    log.debug "putRequestHandler() : put payload:${payload}, path:${path}"

    sendRequest("PUT", path, payload)

    return [ response: "put method sent" ]
}

def postRequestHandler(){
    def json = request.JSON
    def payload = json.payload
    def path = json.uri

    log.debug "postRequestHandler() : post payload:${payload}, path:${path}"

    sendRequest("POST", path, payload)

    return [ response: "post method sent" ]
}

def deleteRequestHandler(){
    def path = params.uri
    log.debug "deleteRequestHandler() : path : ${path} in delete"

    sendRequest("DELETE", path, payload)

    return [ response: "delete method sent" ]
}


mappings {
    path("/home"){
        action: [
                GET: "home"
        ]
    }
    path("/request"){
        action: [
                GET: 	"getRequestHandler",
                PUT: 	"putRequestHandler",
                POST: 	"postRequestHandler",
                DELETE: "deleteRequestHandler"
        ]
    }
    path("/runOnNative"){
        action: [
                GET: "runOnNative"
        ]
    }
    path("/getInitial"){
        action: [
                GET: "getInitial"
        ]
    }
    path("/main") { action: [GET:"main"] }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// tile parse function

def initialize() {
    log.trace "initialize()"
   	log.debug "Hi! I am Meg!!"

    state.ConnectedCount = 0
    state.isConnected = "connected"
    sendHubCommand(subscribeDevice())

    state.operationPower = "Off"
    state.operationState = "Ready"
    state.operationThingsState = state.operationPower
    state.remoteControlDisable = true
    state.modeControl = "Control_Idle"
    state.modeCleaning = "Cleaning_Stop"

    state.alarms = "NONE"
    state.lastError = ""
    delayAction(1000)
    refresh()
    sendEvent(name: "isConnected", value:state.isConnected , displayed:false)
}

def refreshState(result) {
    log.trace "refreshState()"
    log.debug "operationPower: ${state.operationPower}"
    log.debug "remoteControlDisable: ${state.remoteControlDisable}"
    log.debug "modeControl: ${state.modeControl}"
    log.debug "modeCleaning: ${state.modeCleaning}"

    result << createEvent(name: "switch", value: "on", displayed:"false")
    if (state.modeControl == "Control_Idle") {
        state.operationState = "ready"
    } else if (state.modeControl == "Control_Cleaning") {
        switch(state.modeCleaning)
        {
            case "Cleaning_Auto":
            case "Cleaning_Part":
            case "Cleaning_Manual":
                state.operationState = "cleaning"
            break
        }
    } else if (state.modeControl == "Control_Pause") {
        state.operationState = "pause"
    } else if (state.modeControl == "Control_Charging") {
        state.operationState = "ready"
    } else if (state.modeControl == "Control_Homing") {
        state.operationState = "cleaning"
    } else if (state.modeControl == "Control_Point") {
        state.operationState = "off"
    } else if (state.modeControl == "Control_PowerOff") {
        state.operationState = "ControlOff"
    }
    state.deviceState = state.operationState
    result << createEvent(name: "alarms", value: state.alarms, displayed:"false")
    result << createEvent(name: "deviceState", value: state.deviceState, displayed:"false")
    result << createEvent(name: "operationPower", value: state.operationPower, displayed:"false")

    return result
}



// @capability Switch
def on() {
    log.trace "on()"
    def actions=[]
    def switchValue = device.currentValue("switch")
    def remoteControlValue = device.currentValue("remoteControlDisable")
    def modeCleaningValue = device.currentValue("modeCleaning")

    log.trace "switch:${switchValue}, remoteControlDisable:${remoteControlValue}, modeCleaningValue:${modeCleaningValue}"
    //if(switchValue == "off" && remoteControlValue == "true" && modeCleaningValue != "Control_Charging")
    //	actions += createHTTPHubAction("PUT", "/devices/0/operation", [Operation: [power:"On"]])

    actions += start()
    return actions
}

// @capability Switch
def off() {
    log.trace "off()"
    def actions=[]
    //actions += createHTTPHubAction("PUT", "/devices/0/operation", [Operation: [power:"Off"]])
    actions += stop()
    return actions
}

//Attributes: sessionStatus, timeRemaining
//Commands: setTimeRemaining, start, stop, pause, cancel
// @capability Timed Session
def start()
{
    log.trace "start()"
    def actions=[]
    actions += createHTTPHubAction("PUT", "/devices/0/mode", [Mode: [modes: ["Cleaning_Auto"]]])
    return actions
}
// @capability Timed Session
def stop()
{
    log.trace "stop()"
    def actions=[]
    actions += createHTTPHubAction("PUT", "/devices/0/mode", [Mode: [modes: ["Cleaning_Stop"]]])
    return actions
}
// @capability Timed Session
def pause()
{
    log.trace "pause()"
    def actions=[]
    actions += setMovementStop()
    return actions
}
// @capability Timed Session
def cancel() //Recharging
{
    log.trace "cancel()"
    def actions=[]
    actions += setModeHoming()
    return actions
}

private checkThingsStatus()
{
    log.debug "Samsung " + device.displayName + " disconnected, Count : " + state.ConnectedCount

    if(state.isConnected == "disconnected")
    {
        state.operationThingsState = "Disconnected"
    }
    else
    {
        if(state.remoteControlDisable == true)
        {
            if(state.operationPower == "On")
            {
                state.operationThingsState = state.operationState
            }
            else
            {
                state.operationThingsState = "Monitoring_Off"
                if(state.operationState == "ControlOff")
                {
                    state.operationThingsState = "ControlOff"
                }
            }
        }
        else
        {
            if(state.operationPower == "On")
            {
                switch(state.operationState)
                {
                    case "ready":
                        state.operationThingsState = "Monitoring_ready";
                    break
                    case "pause":
                        state.operationThingsState = "Monitoring_pause";
                    break
                    case "cleaning":
                        state.operationThingsState = "Monitoring_cleaning";
                    break
                }
            }
            else
            {
                state.operationThingsState = "Monitoring_Off"
            }
        }
    }

    sendEvent(name: "operationState", value: state.operationState, displayed:false)
    sendEvent(name: "operationThingsState", value: state.operationThingsState, displayed:false)
    sendEvent(name: "isConnected", value:state.isConnected , displayed:false)
}



def setModeHoming() {
    log.trace "setModeHoming()"
    def actions=[]
    actions += createHTTPHubAction("PUT", "/devices/0/mode", [Mode: [modes: ["Control_Homing"]]])
    return actions
}





def setMovementStop()
{
    log.trace "setMovementStop()"
    //Add Reliability
    def actions=[]
    actions += createHTTPHubAction("PUT", "/devices/0/movement", [Movement: [behavior: "Stop"]])
    return actions
}

private parseResource(resource, result) {
    log.trace "parseResource(${resource})"

    if (resource.Operation) {
        parseOperation(resource.Operation, result)
    }

    if (resource.Mode) {
        parseMode(resource.Mode, result)
    }

    if (resource.EnergyConsumption) {
        parseEnergyConsumption(resource.EnergyConsumption, result)
    }

    if (resource.Configuration) {
        parseConfiguration(resource.Configuration, result)
    }

    if (resource.Alarms) {
        parseAlarms(resource.Alarms, result)
    }

    if (resource.Alarm) {
        parseAlarm(resource.Alarm, result)
    }

    checkThingsStatus()
}

private parseConfiguration(configuration, result) {
    log.trace "parseConfiguration(${configuration})"

}

private parseAction(action, result) {
    log.trace "parseAction(${action})"


}

private parseOperation(operation, result) {
    log.trace "parseOperation(${operation})"

    if (operation.power) {
        state.operationPower = operation.power
        if(operation.power == "On")
        {
            result << createEvent(name: "recently_onoff", value: state.operationPower, descriptionText: getLabel("recently_on"), displayed:"true")
            result << createEvent(name: "switch", value: "on", displayed:"false")

        }
        else
        {
            result << createEvent(name: "recently_onoff", value: state.operationPower, descriptionText: getLabel("recently_off"), displayed:"true")
            result << createEvent(name: "switch", value: "off", displayed:"false")
        }
    }
}

private parseMode(mode, result) {

    mode.modes?.each {
        if (it.startsWith("Control")) {
            log.trace "parseMode(${mode})"
            state.modeControl = it

            if(state.modeControl.contains("Charging"))
            {
                result << createEvent(name: "modeCleaning", value: state.modeControl, descriptionText: getLabel("recently_charging"), displayed:"true")
            }
        } else if (it.startsWith("Cleaning")) {
            log.trace "parseMode(${mode})"
            state.modeCleaning = it

            if (state.modeCleaning.contains("Auto"))
            {
                result << createEvent(name: "modeCleaning", value: state.modeCleaning, descriptionText: getLabel("recently_cleaning"), displayed:"true")
            }
            else if (state.modeCleaning.contains("Stop"))
            {
                result << createEvent(name: "modeCleaning", value: state.modeCleaning, descriptionText: getLabel("recently_pause"), displayed:"true")
            }
        } else if (it.startsWith("Access")) {
            log.trace "parseMode(${mode})"

            if(it.contains("UnLock"))
            {
                state.remoteControlDisable = true
                result << createEvent(name: "remoteControlDisable", value: state.remoteControlDisable, descriptionText: getLabel("recently_smart_on"), displayed:"true")
            }
            else
            {
                state.remoteControlDisable = false
                result << createEvent(name: "remoteControlDisable", value: state.remoteControlDisable, descriptionText: getLabel("recently_smart_off"), displayed:"true")
            }
        }
    }
}

private parseEnergyConsumption(energy, result) {
    if (energy.batteryCharge) {
        log.trace "parseEnergyConsumption(${energy})"
        result << createEvent(name: "battery", value: energy.batteryCharge, displayed:"false")
    }
}

private parseAlarms(alarms, result) {
    log.trace "parseAlarm(${alarm})"

    alarms.each { alarm ->
        parseAlarm(alarm, result)
    }
}

private parseAlarm(alarm, result) {
    log.trace "parseAlarm(${alarm})"

    if (state.lastError == alarm.code) return;
    state.lastError = alarm.code;
    log.trace "state.lastError(${state.lastError})"

    switch (alarm.code) { // TODO
        case "ErrorCode_0": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_Imprisonment_0"),"MSG_ERROR"); break
        case "ErrorCode_1": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_BrushDamage_1"),"MSG_ERROR"); break
        case "ErrorCode_2": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_LeftWheelDamage_2"),"MSG_ERROR"); break
        case "ErrorCode_3": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_RightWheelDamage_3"),"MSG_ERROR"); break
        case "ErrorCode_5": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_BumperSensorDamage_5"),"MSG_ERROR"); break
        case "ErrorCode_6": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_ObstacleSensorDamage_6"),"MSG_ERROR"); break
        case "ErrorCode_7": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_FallSensorDamage_7"),"MSG_ERROR"); break
        case "ErrorCode_8": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_DustCanisterNotDetect_8"),"MSG_ERROR"); break
        case "ErrorCode_9": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_SideBrushDamage_9"),"MSG_ERROR"); break
        case "ErrorCode_10": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_ChargingForBattery_10"),"MSG_ERROR"); break
        case "ErrorCode_27": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_LiftedUp_27"),"MSG_ERROR"); break
        case "ErrorCode_29": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_ScheduleCleaning_29"),"MSG_ERROR"); break
        case "ErrorCode_30": parent.send("${device.displayName}, " + getLabel("VCMOB_alarm_FailScheduleCleaning_30"),"MSG_ERROR"); break
            default: break
    }



    if(alarm.code)
    {
        result << createEvent(name: "alarms", value: alarm.code, displayed:"false")
    }
    else
    {
        result << createEvent(name: "alarms", value: "NONE", displayed:"false")
    }
}

def runOnNative(){
    log.trace "runOnNative"
    def type = params.type
    def data = params.data
    log.trace "runOnNative type:${type}, data:${data}"

     if(type == "disconnected")
    {
            log.debug "sendDisconnectedEvt()"
            state.ConnectedCount = 2
            state.isConnected = "disconnected"
            checkThingsStatus()
            return
    }

}


def getInitial(){
    def numberMap = [
            "ar":"0800-333-3733",
            "uy":"40543733",
            "py":"98005420001",
            "bo":"800-10-7260",
            "cr":"0-800-507-7267",
            "dm":"1-800-751-2676",
            "ec":"1-800-10-7267",
            "sv":"800-6225",
            "gt":"1-800-299-0013",
            "hn":"800-27919267",
            "jm":"1-800-234-7267",
            "ni":"00-1800-5077267",
            "pe":"336-8686",
            "pr":"1-800-682-3180",
            "tt":"1-800-726-7864",
            "ve":"0-800-100-5303",
            "ba":"051-133-1999",
            "bg":"07001-33-11",
            "hr":"062-726-786",
            "cz":"800-726786",
            "dk":"70-70-19-70",
            "fi":"030-6227-515",
            "cy":"210-6897691",
            "gr":"210-6897691",
            "hu":"06-80-726-7864",
            "lu":"261-03-710",
            "me":"020-405-888",
            "no":"815-56480",
            "ro":"08008-726-7864",
            "rs":"381-11-321-6899",
            "sk":"0800-726-786",
            "se":"0771-726-7864",
            "ie":"0818-717100",
            "li":"8-800-77777",
            "lv":"8000-7267",
            "ee":"800-7267",
            "ru":"8-800-555-55-55",
            "ge":"0-800-555-555",
            "am":"0-800-05-555",
            "az":"088-55-55-555",
            "kz":"8-10-800-500-55-500",
            "uz":"8-10-800-500-55-500",
            "kg":"00-800-500-55-500",
            "tj":"8-10-800-500-55-500",
            "mn":"7-800-555-55-55",
            "ua":"0-800-502-000",
            "by":"810-800-500-55-500",
            "md":"0-800-614-40",
            "nz":"0800-726-786",
            "cn":"400-810-5858",
            "hk":"852-3698-4698",
            "in":"1800-3000-8282",
            "id":"0800-112-8888",
            "jp":"0120-327-527",
            "my":"1800-88-9999",
            "ph":"1-800-8-726-7864",
            "sg":"1800-726-7864",
            "tw":"0800-329-999",
            "vn":"1-800-588-889",
            "ae":"800-726-7864",
            "om":"800-726-7864",
            "kw":"183-2255",
            "bh":"8000-4726",
            "qa":"800-2255",
            "eg":"08000-726786",
            "jo":"800-22273",
            "sy":"18252273",
            "ir":"021-8255",
            "ma":"080-100-2255",
            "sa":"9200-21230",
            "tr":"444-77-11",
            "ng":"0800-726-7864",
            "gh":"0800-10077",
            "ci":"8000-0077",
            "sn":"800-00-0077",
            "cm":"7095-0077",
            "ke":"0800-545-545",
            "ug":"0800-300-300",
            "tz":"0685-88-99-00",
            "za":"0860-726-7864",
            "bw":"0800-726-000",
            "na":"8197267864",
            "ao":"91-726-7864",
            "zm":"211350370",
            "il":"972-1800-804050",
            "dz":"00-213-800-100-100",
            "tn":"80100012",
            "ly":"00218-2148-73-296",
            "lb":"961-1-882333",
            "iq":"9.72E+11",
            "ps":"972-26273025",
            "kr":"1588-3366",
            "us":"1-800-726-7864",
            "ca":"1-800-726-7864",
            "mx":"01-800-726-7864",
            "pa":"800-7267",
            "co":"01-8000-112-112",
            "br":"0800-124-421",
            "cl":"02-24-82-82-00",
            "gb":"0330-7267864",
            "fr":"01-48-63-00-00",
            "ch":"0848-7267864",
            "de":"0180-5-7267864",
            "at":"0810-7267864",
            "it":"800-726-7864",
            "es":"902-172-678",
            "be":"02-201-24-18",
            "pt":"808-20-7267",
            "nl":"0900-7267864",
            "au":"1300-362-603",
            "th":"1800-29-3232",
            "pl":"0-801-172-678",
            "le":"962-6-5777444"
    ]

    def number
    def country
    if(clientLocale?.language == "ko")
    {
        number = numberMap["kr"]
        country = "KR"
    }
    else
    {
        number = numberMap["us"]
        country = "US"
    }
    def initialBody = [:]
    initialBody.type = "initial"
    initialBody.selectedCountry = country
    initialBody.serviceCenterNumber = number
    initialBody.supportCalling = "true"

    def initialJson = new JsonBuilder(initialBody).toString()
    log.debug "initialJson : ${initialJson}"
    return [ response: initialJson ]
}

////////////////////////////////////////////////////////////////////////////////
// multi language



def getLabel(value)
{
    def tileMap_Ready=[
        "kr": "???",
        "us": "Ready"
    ]

    def tileMap_Off=[
        "kr": "??",
        "us": "Off"
    ]

    def tileMap_Disconnected=[
    	"kr":"?? ??",
        "us":"Disconnected"
    ]

    def tileMap_Cleaning=[
        "kr": "??",
        "us": "Cleaning"
    ]

    def tileMap_Pause=[
        "kr": "?? ??",
        "us": "Pause"
    ]


    def recently_on=[
        "kr" : "??? ?????.",
        "us" : "Device is on."
    ]

    def recently_off=[
        "kr" : "??? ?????.",
        "us" : "Device is off."
    ]

    def recently_cleaning=[
        "kr" : "??? ???.",
        "us" : "Cleaning."
    ]

    def recently_pause=[
        "kr" : "??? ???.",
        "us" : "Waiting."
    ]

    def recently_charging=[
        "kr" : "??? ???.",
        "us" : "Charging."
    ]

    def recently_smart_on=[
    	"kr":"??? ???? ?????. ",
        "us":"Smart control is on."
    ]

    def recently_smart_off=[
    	"kr":"??? ???? ?????.",
        "us":"Smart control is off."
    ]


    def VCMOB_alarm_Imprisonment_0=["kr":"?? ? ??, ??, ?? ? ?? ??", "us":"Caught, stuck, or trapped while navigating"]
    def VCMOB_alarm_BrushDamage_1=["kr":"?? ??? ??? (?, ??, ??? ?) ??", "us":"Foreign substance in the power brush"]
    def VCMOB_alarm_LeftWheelDamage_2=["kr":"?? ???? ??? (?, ??, ??? ?) ??", "us":"Foreign substance in the left driving wheel"]
    def VCMOB_alarm_RightWheelDamage_3=["kr":"??? ???? ??? (?, ??, ??? ?) ??", "us":"Foreign substance in the right driving wheel"]
    def VCMOB_alarm_BumperSensorDamage_5=["kr":"??? ???? ?? ??", "us":"Bumper sensor error"]
    def VCMOB_alarm_ObstacleSensorDamage_6=["kr":"??? ??? ??? (??, ?? ?) ??", "us":"Foreign substance on the obstacle sensor"]
    def VCMOB_alarm_FallSensorDamage_7=["kr":"???? ??? ??? (??, ?? ?) ??", "us":"Foreign substance on the cliff sensor"]
    def VCMOB_alarm_DustCanisterNotDetect_8=["kr":"??? ?? ??", "us":"Dust bin not inserted"]
    def VCMOB_alarm_SideBrushDamage_9=["kr":"?? ??? ??? (?, ? ?) ??", "us":"Foreign substance on the edge-cleaning side rotating brush"]
    def VCMOB_alarm_ChargingForBattery_10=["kr":"?? ??? ?? ?? ?", "us":"Charging for cleaning resumption"]
    def VCMOB_alarm_LiftedUp_27=["kr":"?? ??", "us":"Lifted up"]
    def VCMOB_alarm_ScheduleCleaning_29=["kr":"?? ??", "us":"Schedule cleaning"]
    def VCMOB_alarm_FailScheduleCleaning_30=["kr":"?? ?? ??", "us":"Fail to Schedule cleaning."]

    if(value == "deviceState")
    {
    	value = device.currentValue(value)
    }

    if(clientLocale?.language == "ko")
    {
        switch(value)
        {
            case "Ready":
            case "ready":
            	return tileMap_Ready["kr"]
            case "Off":
            case "off":
            	return tileMap_Off["kr"]
            case "Cleaning":
            case "cleaning":
            	return tileMap_Cleaning["kr"]
            case "Pause":
            case "pause":
            	return tileMap_Pause["kr"]
            case "Disconnected":
            	return tileMap_Disconnected["kr"]

            case "recently_on":
            	return recently_on["kr"]
            case "recently_off":
            	return recently_off["kr"]
            case "recently_cleaning":
            	return recently_cleaning["kr"]
            case "recently_pause":
            	return recently_pause["kr"]
            case "recently_charging":
            	return recently_charging["kr"]
            case "recently_smart_on":
            	return recently_smart_on["kr"]
            case "recently_smart_off":
            	return recently_smart_off["kr"]

            case "VCMOB_alarm_Imprisonment_0": return VCMOB_alarm_Imprisonment_0["kr"]
            case "VCMOB_alarm_BrushDamage_1": return VCMOB_alarm_BrushDamage_1["kr"]
            case "VCMOB_alarm_LeftWheelDamage_2": return VCMOB_alarm_LeftWheelDamage_2["kr"]
            case "VCMOB_alarm_RightWheelDamage_3": return VCMOB_alarm_RightWheelDamage_3["kr"]
            case "VCMOB_alarm_BumperSensorDamage_5": return VCMOB_alarm_BumperSensorDamage_5["kr"]
            case "VCMOB_alarm_ObstacleSensorDamage_6": return VCMOB_alarm_ObstacleSensorDamage_6["kr"]
            case "VCMOB_alarm_FallSensorDamage_7": return VCMOB_alarm_FallSensorDamage_7["kr"]
            case "VCMOB_alarm_DustCanisterNotDetect_8": return VCMOB_alarm_DustCanisterNotDetect_8["kr"]
            case "VCMOB_alarm_SideBrushDamage_9": return VCMOB_alarm_SideBrushDamage_9["kr"]
            case "VCMOB_alarm_ChargingForBattery_10": return VCMOB_alarm_ChargingForBattery_10["kr"]
            case "VCMOB_alarm_LiftedUp_27": return VCMOB_alarm_LiftedUp_27["kr"]
            case "VCMOB_alarm_ScheduleCleaning_29": return VCMOB_alarm_ScheduleCleaning_29["kr"]
            case "VCMOB_alarm_FailScheduleCleaning_30": return VCMOB_alarm_FailScheduleCleaning_30["kr"]

        }
    }
    else
    {
        switch(value)
        {
            case "Ready":
            case "ready":
            	return tileMap_Ready["us"]
            case "Off":
            case "off":
            	return tileMap_Off["us"]
            case "Cleaning":
            case "cleaning":
            	return tileMap_Cleaning["us"]
            case "Pause":
            case "pause":
            	return tileMap_Pause["us"]
            case "Disconnected":
            	return tileMap_Disconnected["us"]

            case "recently_on":
            	return recently_on["us"]
            case "recently_off":
            	return recently_off["us"]
            case "recently_cleaning":
            	return recently_cleaning["us"]
            case "recently_pause":
            	return recently_pause["us"]
            case "recently_charging":
            	return recently_charging["us"]
            case "recently_smart_on":
            	return recently_smart_on["us"]
            case "recently_smart_off":
            	return recently_smart_off["us"]

            case "VCMOB_alarm_Imprisonment_0": return VCMOB_alarm_Imprisonment_0["us"]
            case "VCMOB_alarm_BrushDamage_1": return VCMOB_alarm_BrushDamage_1["us"]
            case "VCMOB_alarm_LeftWheelDamage_2": return VCMOB_alarm_LeftWheelDamage_2["us"]
            case "VCMOB_alarm_RightWheelDamage_3": return VCMOB_alarm_RightWheelDamage_3["us"]
            case "VCMOB_alarm_BumperSensorDamage_5": return VCMOB_alarm_BumperSensorDamage_5["us"]
            case "VCMOB_alarm_ObstacleSensorDamage_6": return VCMOB_alarm_ObstacleSensorDamage_6["us"]
            case "VCMOB_alarm_FallSensorDamage_7": return VCMOB_alarm_FallSensorDamage_7["us"]
            case "VCMOB_alarm_DustCanisterNotDetect_8": return VCMOB_alarm_DustCanisterNotDetect_8["us"]
            case "VCMOB_alarm_SideBrushDamage_9": return VCMOB_alarm_SideBrushDamage_9["us"]
            case "VCMOB_alarm_ChargingForBattery_10": return VCMOB_alarm_ChargingForBattery_10["us"]
            case "VCMOB_alarm_LiftedUp_27": return VCMOB_alarm_LiftedUp_27["us"]
            case "VCMOB_alarm_ScheduleCleaning_29": return VCMOB_alarm_ScheduleCleaning_29["us"]
            case "VCMOB_alarm_FailScheduleCleaning_30": return VCMOB_alarm_FailScheduleCleaning_30["us"]

        }
    }
    return "Unknown"
}

////////////////////////////////////////////////////////////////////////////////
// html code



def whitelist() {
    return [
            "cdnjs.cloudflare.com",
            "fonts.googleapis.com",
            "s3.amazonaws.com",
            "ajax.googleapis.com",
            "www.samsung.com",
            "192.168.38.199",
            "geo.itunes.apple.com",
            "play.google.com",
            "developer.android.com",
            "linkmaker.itunes.apple.com",
            "ajax.googleapis.com",

            "www.youtube.com",
            "s.ytimg.com",
            "apis.google.com",
            "googleads.g.doubleclick.net",
            "static.doubleclick.net",
            "pagead2.googlesyndication.com",
            "partner.googleadservices.com",
            "www.googletagservices.com",
            "redirector.googlevideo.com",
            "r17---sn-nwj7knel.googlevideo.com",
            "r12---sn-q4f7dm76.googlevideo.com",
            "fonts.gstatic.com",
            "10.250.141.220",
            "10.250.142.179",
            "10.250.142.63"
    ]
}

def main() {
    renderHTML("Samsung Robot Vacuum", true) {
        head {
            """
                <title>Samsung RVC</title>
		        <meta charset="UTF-8">
		        <meta name="viewport" content="width=device-width, target-densitydpi=device-dpi, initial-scale=1.0, user-scalable=no" />
		        <link rel="shortcut icon" href="data:image/x-icon;," type="image/x-icon">
		        <link rel="stylesheet" href="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/css/Comm/common_em_no_anim.css" />
		        <link rel="stylesheet" href="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/css/rvc.css"/>
            """
        }
        body {
            """
                <body id="bodyTag" ng-class="containSubCl" ng-app="SmartThings" ng-controller="RVCController" ng-cloak ontouchstart="">

        <svg class="defs-only">
	        <filter id="daBlue" color-interpolation-filters="sRGB" x="0" y="0" height="100%" width="100%">
	            <feColorMatrix type="matrix"
	                           values="0 0 0 0 0
	                           0 0 0 0 0.67058823529412
	                           0 0 0 0 0.94509803921569
	                           0 0 0 1 0" />
	        </filter>

	        <filter id="black" color-interpolation-filters="sRGB" x="0" y="0" height="100%" width="100%">
	            <feColorMatrix type="matrix"
	                           values="0 0 0 0 0
	                           0 0 0 0 0
	                           0 0 0 0 0
	                           0 0 0 1 0" />
	        </filter>

	        <filter id="white" color-interpolation-filters="sRGB" x="0" y="0" height="100%" width="100%">
	            <feColorMatrix type="matrix"
	                           values="0 0 0 0 1
	                           0 0 0 0 1
	                           0 0 0 0 1
	                           0 0 0 1 0" />
	        </filter>

	        <filter id="grey" color-interpolation-filters="sRGB" x="0" y="0" height="100%" width="100%">
		        <feColorMatrix type="matrix"
		                       values="0 0 0 0 0.3215696274509804
		                       0 0 0 0 0.3215696274509804
		                       0 0 0 0 0.3215696274509804
		                       0 0 0 0.3 0" />
		    </filter>
		    <filter id="pressed" color-interpolation-filters="sRGB" x="0" y="0" height="100%" width="100%">
		        <feColorMatrix type="matrix"
		                       values="0 0 0 0 0
		                       0 0 0 0 0.5725490196078431
		                       0 0 0 0 0.803921568627451
		                       0 0 0 1 0" />
		    </filter>
		    <filter id="device_OFF" color-interpolation-filters="sRGB" x="0" y="0" height="100%" width="100%">
		        <feColorMatrix type="matrix"
		                       values="0 0 0 0 0.3764705882352941
		                       0 0 0 0 0.3764705882352941
		                       0 0 0 0 0.3764705882352941
		                       0 0 0 1 0" />
		    </filter>
	    </svg>

	<!-- Loading bar Start-->
        <div class="loading-bar" ng-show="loadingBar">
            <div class="loadingAnim"></div>
        </div>
        <!-- Loading bar End -->
        <div class="MainLoadingDiv" ng-hide="mainLoadingScreen" style="display: block" ng-cloak >
            <p class="MainLoadingTitle Regular">SAMSUNG HOME</p>
            <img class="MainLoadingDevicesImage" src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/common/st_ws_loading_img.png" onerror="this.style.visibility='hidden'" />
            <div class="MainLoadingAnimDiv">
                <div class="loadingAnimSmall"></div>
                <p class="MainLoadingAnimText Regular">Loading...</p>
            </div>
        </div>


        <div class="mainscrollContainer">

            <div class="OvenScrollArea left">
                <div class="press-list header_div no-animate">
                    <div class="touchEffect power-on-button left" ng-click="powerOnOff(powerState)">
                    	<img ng-src="{{'https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/common/main_subbar_ic_power.png'}}" class="powerOn daBlue noTransform" ng-class="{'device_OFF': remoteLocked || !powerState || DeviceOptions.cleaning_type === 10 || DeviceOptions.cleaning_type === 9 || DeviceOptions.cleaning_type === 5}" onerror="this.style.visibility='hidden'">
                    </div>

                </div>

                <div class="scrollable_area_no_map" ng-class="{'noscroll' : isPopupOpen}">
                    <div class="mainscreenAlarmPopup" ng-if="DeviceOptions.alarm">
                        <div class="popupdiv1">
                            <!--
                            <img class="error-icon" src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/common/main_rf_ic_noti.svg"/>
                             -->
                            <div class="popup-body-text">
                                <p ng-repeat="errors in Device.Alarms" class="notificationErrMsg">
                                    {{errors.errormsg}}
                                </p>
                            </div>
                        </div>
                    </div>
                    <div id="home_page">
                        <div class="home_center_area">
                            <div class="div-progress-outer">
                                <div class="div-progress-inner">
                                    <img class="outerCircle" src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/rvc/main_control_btn_large.png" ng-class="{'ON-PAUSEOuter': DeviceOptions.selectedArrow == '', 'onLeftPress':DeviceOptions.selectedArrow == 'LEFT', 'onRightPress':DeviceOptions.selectedArrow == 'RIGHT', 'onUpPress':DeviceOptions.selectedArrow == 'TOP', onPauseOuter: !onPause}" onerror="this.style.visibility='hidden'"/>
                                    <div class="list-arrows">
                                        <div class="arrowUp" ng-touchstart="touchStart('TOP')" ng-touchend="dontShowButtonClicked && touchEnd()" ng-class="{'disabledManualDiv': !DeviceOptions.controls.manualCtrlEnabled}">
                                            <img id="arrUp" class='arrowUpImg' ng-class="{'disabledArrowUp': !DeviceOptions.controls.manualCtrlEnabled}" onerror="this.style.visibility='hidden'">
                                        </div>
                                        <div class="arrowLeft" ng-touchstart="touchStart('LEFT')" ng-touchend="dontShowButtonClicked && touchEnd()" ng-class="{'disabledManualDiv': !DeviceOptions.controls.manualCtrlEnabled}">
                                            <img id="arrLeft" class='arrowLeftImg' ng-class="{'disabledArrowLeft':  !DeviceOptions.controls.manualCtrlEnabled}" onerror="this.style.visibility='hidden'">
                                        </div>
                                        <div class='arrowRight' ng-touchstart="touchStart('RIGHT')"  ng-touchend="dontShowButtonClicked && touchEnd()" ng-class="{'disabledManualDiv': !DeviceOptions.controls.manualCtrlEnabled}">
                                            <img id="arrLeft" class='arrowRightImg' ng-class="{'disabledArrowRight': !DeviceOptions.controls.manualCtrlEnabled}" onerror="this.style.visibility='hidden'">
                                        </div>
                                        <div id="spotTextId" class="spotText" ng-click="startProgress('SPOT')" ng-class="{'disabledManualDiv': !DeviceOptions.controls.spot}">
                                            <span class="spotText-font Regular" ng-class="{'spotText-font_disabled': !DeviceOptions.controls.spot}">{{translation.VCMOB_cleaning_spot}}</span>
                                        </div>
                                    </div>
                                    <div class="circleBackground">
                                        <img class="innerCircle" src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/rvc/main_control_btn_small.png" onerror="this.style.visibility='hidden'"/>
                                        <div class="circle-div" ng-click="startProgress(!DeviceOptions.controls.play ? 'STOP' : 'AUTO')" ng-class="{'disabledManualDiv': DeviceOptions.controls.playDisable}"></div>
                                        <img class="playButton" id="playBtn" ng-hide="!DeviceOptions.controls.play" ng-class="{'playBtn_disabled': DeviceOptions.controls.playDisable}" onerror="this.style.visibility='hidden'"/>
                                        <img class="pauseButton" id="pauseBtn" src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/rvc/main_control_btn_ic_stop.png" ng-if="!DeviceOptions.controls.play" onerror="this.style.visibility='hidden'"/>
                                    </div>
                                </div>
                            </div>

                            <div class="estTimeText1 Light" ng-class="{'estTimeText1_disabled': !powerState}">{{(powerState ? ((translation[DeviceOptions.selectedText] + additionalInfoSelectedText) || "&nbsp;"): translation.VCMOB_status_poweroff)}}</div>

                            <div class="multi-img" ng-class="{'multi-sel-disabled': !DeviceOptions.controls.charging, 'multi-pressed': isActiobBtnPressed}" ng-touchstart="actionButtonStateChange(true)" ng-touchend="ModeChangeRVC('CHARGE')">
                                <div class="multi-img-inner Regular" ng-bind-html="translation.WEBMOB_device_rvc_comm_charge_button"></div>
                            </div>
                        </div>
                        <div class="bottom_item">
                            <img class="bottem_item_image commonSelectedIcon daBlue noTransform" ng-class="getBatteryClass()" onerror="this.style.visibility='hidden'"/>
                            <p class="bottom_item_singleline Regular">{{translation.WEBMOB_device_rvc_battery}}</p>
                        </div>
                        <div class="bottom_item" ng-class="{'modeDisabled' : !DeviceOptions.controls.suction, 'touchEffect' : DeviceOptions.controls.suction}" ng-click="openPopoverTemp()">
                            <img ng-src="{{DeviceOptions.selectedMode.imageID}}" class="bottem_item_image daBlue noTransform" ng-class="{'grey': !DeviceOptions.controls.suction}" onerror="this.style.visibility='hidden'">
                            <div class="bottom_item_multiline">
                                <p class="bottom_item_multiline_1 Regular" ng-class="{'bottom_item_multiline_1_disabled':!DeviceOptions.controls.suction}" ng-bind-html="translation.WEBMOB_device_rvc_suction_power"></p>
                                <p class="bottom_item_multiline_2 Regular" ng-class="{'bottom_item_multiline_2_disabled':!DeviceOptions.controls.suction}" ng-bind-html="translation[DeviceOptions.selectedMode.modeName]"></p>
                            </div>

                        </div>
                        <div class="bottom_item" ng-class="{'modeDisabled' : !DeviceOptions.controls.setting, 'touchEffect' : DeviceOptions.controls.setting}" ng-click="openPopoverTempSetting()">
                            <img ng-src="{{DeviceOptions.selectedVoice.imageID}}" class="bottem_item_image daBlue noTransform" ng-class="{'grey': !DeviceOptions.controls.setting}" onerror="this.style.visibility='hidden'">
                            <div class="bottom_item_multiline">
                                <p class="bottom_item_multiline_1 Regular" ng-class="{'bottom_item_multiline_1_disabled':!DeviceOptions.controls.setting}" ng-bind-html="translation.VCMOB_setting_voice_title"></p>
                                <p class="bottom_item_multiline_2 Regular" ng-class="{'bottom_item_multiline_2_disabled':!DeviceOptions.controls.setting}" ng-bind-html="translation[DeviceOptions.selectedVoice.modeName]"></p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="OvenScrollArea left" ng-if="!!showController && !isPopup">
                <div class="scrollable_area" ng-if="homePage">
                    <div class="mainscreenpopup" ng-if="DeviceOptions.alarm">
                        <div class="popupdiv1">
                        	<!--
                            <img class="error-icon" src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/common/main_rf_ic_noti.svg"/>
                             -->
                            <div class="popup-body-text">
                                  <p ng-repeat="errors in Device.Alarms" class="notificationErrMsg Regular">
                                    {{errors.errormsg}}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!--Bottom popup (Suction Power) start -->
        <div ng-class="BottomPopupDimBackgroundDiv" ng-click="closeSuctionPowerPopup()" ng-show="showSuctionPowerPopup">
            <div ng-class="BottomPopupDivCls" ng-click="\$event.stopPropagation()">
                <div class="BottomPopupTitle">
                    <p class="BottomPopupTitleText Bold">{{translation.WEBMOB_device_rvc_suction_power}}</p>
                </div>
                <div class="BottomPopupItem touchEffect" ng-repeat='item in SuctionPowerList | filter: suctionPowerFilter(isSuctionA)' ng-click="setSuctionPower(item)">
                    <img ng-src="{{item.imageID}}" class="bottem_item_image noTransform" ng-class="{'daBlue': DeviceOptions.selectedMode.modeName == item.modeName}" onerror="this.style.visibility='hidden'">
                    <p class="BottomPoupItemText Regular" ng-class="{BottomPopupItemTextSelected: DeviceOptions.selectedMode.modeName == item.modeName}">{{translation[item.modeName]}}</p>
                </div>
            </div>
        </div>
        <!--Bottom popup (Suction Power) end -->

        <!--Bottom popup (Voice Control Guide) start -->
        <div ng-class="BottomPopupDimBackgroundDiv" ng-click="closePopoverTempSetting()" ng-show="showVoiceControlPopup">
            <div ng-class="BottomPopupDivCls" ng-click="\$event.stopPropagation()">
                <div class="BottomPopupTitle">
                    <p class="BottomPopupTitleText Bold">{{translation.VCMOB_setting_voice_title}}</p>
                </div>
                <div class="BottomPopupItem touchEffect" ng-repeat='item in voiceControlList' ng-click="changeVoiceControl(item)">
                    <img ng-src="{{item.imageID}}" class="bottem_item_image noTransform" ng-class="{'daBlue': DeviceOptions.selectedVoice.modeName == item.modeName}" onerror="this.style.visibility='hidden'">
                    <p class="BottomPoupItemText Regular" ng-class="{BottomPopupItemTextSelected: DeviceOptions.selectedVoice.modeName == item.modeName}">{{translation[item.modeName]}}</p>
                </div>
            </div>
        </div>
        <!--Bottom popup (Voice Control Guide) end -->

        <div class="fullscreenpopup" ng-click="closeAllPopups()" ng-show="popUpVisible">
            <div class="custPopupDiv" ng-click="\$event.stopPropagation()">
                <div class="custPopupTitle">
                    <p id="custpopupheadertext" class="custPopupTitleText Regular"></p>
                </div>
                <p id="custpopuptext" class="custPopupText Regular"></p>
                <div>
                    <img class='PC_UsageLimitCheckBox touchEffect' src='https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/common/ws_cp_checkbtn_off_normal.png' ng-show="!dontShowButtonClicked" ng-click="onDontShowAgainButtonClicked()" onerror="this.style.visibility='hidden'"/>
                    <img class='PC_UsageLimitCheckBox touchEffect' src='https://s3.amazonaws.com/smartthingsda/STRobotVaccum/images/common/ws_cp_checkbtn_on_normal.png' ng-show="dontShowButtonClicked" ng-click="onDontShowAgainButtonClicked()" onerror="this.style.visibility='hidden'"/>
                    <p class="custPopupTextDontShowAgain Regular left touchEffect">{{translation.VC_mmsg_manualguide_notshow}}</p>
                </div>
                <div class="custokcanceldiv">
                    <p class="custCancel Regular touchEffect" ng-class="{custCancelInvisible:txtButton2Invisbile === true}" ng-click="popUpVisible = false" id="alertbutton1">{{translation.WEBMOB_common_cancel}}</p>
                    <p class="custOk Regular touchEffect" ng-click="popUpVisible = false" id="alertbutton2">{{translation.WEBMOB_common_ok}}</p>
                </div>
            </div>
        </div>

        <!-- Connection failure popup start -->
        <div ng-class="BottomPopupDimBackgroundDiv" ng-if="dialogPopup.show" ng-cloack>
            <div class="custPopupDiv" ng-click="\$event.stopPropagation()">
                <div class="custPopupTitle">
                    <p class="custPopupTitleText Bold">{{dialogPopup.dialogPopupTitle}}</p>
                </div>
                <p class="custPopupText Regular" ng-bind-html="dialogPopup.dialogPopupText"></p>
                <div class="custokcanceldiv">
                    <p class="custOk touchEffect" ng-click="dialogPopup.button2Handler()" ng-show="dialogPopup.button2Enabled">{{dialogPopup.button2Title}}</p>
                    <!--p class="custCancel" ng-click="dialogPopup.button1Handler()" id="popupcancel">{{dialogPopup.button1Title}}</p-->
                </div>
            </div>
        </div>
        <!-- Connection failure end -->
        <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.4.5/angular.min.js"></script>
        <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.4.5/angular-sanitize.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/angularjs-toaster/0.4.16/toaster.min.js"></script>
        <script src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/javascript/lang/web_multiLanguage_rvc.js"></script>
		<script src="https://s3.amazonaws.com/smartthingsda/STRobotVaccum/javascript/rvc.js"></script>
        <script src="https://s3.amazonaws.com/smartthingsda/javascript_comm/app.js"></script>
        <script src="https://s3.amazonaws.com/smartthingsda/javascript_comm/groovyInterface.js"></script>
        <script src="http://code.jquery.com/jquery-1.12.0.min.js"></script>

        <script type="text/javascript">
            setBaseFont();
        </script>

    </body>
            """
        }
    }
}
// EOF