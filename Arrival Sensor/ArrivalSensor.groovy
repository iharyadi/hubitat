import groovy.json.JsonOutput
import groovy.transform.Field
    
@Field static HashMap lastCheckin = []

metadata {
    definition (name: "Arrival Sensor HA", namespace: "iharyadi", author: "iharyadi") {
        capability "Tone"
        capability "Actuator"
        capability "Presence Sensor"
        capability "Sensor"
        capability "Battery"
        capability "PowerSource"
        capability "MotionSensor"
        capability "ShockSensor"
        capability "Temperature Measurement"
        capability "Configuration"
        capability "Refresh"

        fingerprint inClusters: "0000,0001,0003,000F,0020", outClusters: "0003,0019", manufacturer: "SmartThings", model: "tagv4", deviceJoinName: "SmartThings Presence Sensor"
    }

    preferences {
        section("Timeouts") {
            input "checkInterval", "enum", title: "Presence timeout (minutes) on dc power", description: "",
                    defaultValue:"2", options: ["1","2", "3", "5"], displayDuringSetup: false
            
            input "checkIntervalBattery", "enum", title: "Presence timeout (minutes) on battery", description: "",
                    defaultValue:"2", options: ["2", "3", "5", "7", "10", "20"], displayDuringSetup: false
        }
        
        section("Temperature") {
            input "tempAdjust", "decimal", title: "Temperature offset", description: "Adjust temperature in Celsius",
                    defaultValue:"8.55", displayDuringSetup: false
        }
        
        section("Features")
        {
            input name: "motionEnabled", defaultValue: "false", type: "bool", title: "Enable motion sensor", description: "",
                displayDuringSetup: false
        }
        
        section("Debug Messages")
        {
            input name: "logEnabled", defaultValue: "false", type: "bool", title: "Enable info message logging", description: "",
                displayDuringSetup: false
        }
    }
}

private def Log(message) {
    if (logEnabled)
        log.info "${message}"
}

def updated() {
    stopTimer()
    startTimer()
    refresh()
}

void uninstalled()
{
    lastCheckin.remove(device.deviceNetworkId)
}

def installed() {
}

def configure() {
    def cmds = []
    cmds += "delay 5000"
    cmds += zigbee.readAttribute(0x0001, 0x0020) + zigbee.configureReporting(0x0001,0x0020, DataType.UINT8,   0, 10,1)
    cmds += zigbee.readAttribute(0x000F, 0x0055) + zigbee.configureReporting(0x000F,0x0055, DataType.BOOLEAN, 0, 300,1)
    cmds += zigbee.readAttribute(0x0402, 0x0000) + zigbee.configureReporting(0x0402,0x0000, DataType.INT16,   0, 600,100)
    
    return cmds
}

def refresh() {
    def cmds = zigbee.readAttribute(0x0001, 0x0020) + 
        zigbee.readAttribute(0x000F, 0x0055) + 
        zigbee.readAttribute(0x0402, 0x0000)
    return cmds
}

def beep() {
    return zigbee.command(0x0003, 0x00, "0500")
}

def parse(String description) { 
    Log("parse ${zigbee.parseDescriptionAsMap(description)}")
    lastCheckin[device.deviceNetworkId] = now()
    handlePresenceEvent(true)

    if (description?.startsWith('read attr -')) {
        handleReportAttributeMessage(description)
    }

    return []
}

private handleReportAttributeMessage(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (descMap.clusterInt == 0x0001 && descMap.attrInt == 0x0020) {
        handleBatteryEvent(Integer.parseInt(descMap.value, 16))
    }else 
    {
        if (descMap.clusterInt == 0x000F) {
            if( descMap.attrInt == 0x0055)
            {
                handleBinaryInput(Integer.parseInt(descMap.value, 16))
            }
            else
            {
                def attr = descMap.additionalAttrs?.stream()?.filter{it.attrInt == 0x0055}?.findAny()?.orElse(null)
                if(attr)
                {
                    handleBinaryInput(Integer.parseInt(attr.value, 16))
                }
            }
        }
        else if(descMap.clusterInt == 0x00402 &&  descMap.attrInt == 0x0000)
        {
            handleTemperature((short)Integer.parseInt(descMap.value, 16))
        }
    }
}

private boolean IsDCPower(int value)
{
    return (value & 0x01) == 0x01
}

private boolean IsShockDetected(int value)
{
    return (value & 0x02) == 0x02
}

private boolean IsMotionDetected(int value)
{
    return (value & 0x04) == 0x04
}

private handleBinaryInput(binaryValue) {
    
    def value = "battery"
    if(IsDCPower(binaryValue))
    {
        value =  "dc"
    }
        
    def linkText = getLinkText(device)
    descriptionText = "${linkText} power source is ${value}"
    def eventMap = [
    name: 'powerSource',
          value: value,
          descriptionText: descriptionText,
          translatable: true
    ]
    sendEvent(eventMap)
    
    value = "clear"
    if(IsShockDetected(binaryValue))
    {
        value =  "detected"
    }
    
    descriptionText = "${linkText} vibriation/shock is ${value}"
    eventMap = [
    name: 'shock',
          value: value,
          descriptionText: descriptionText,
          translatable: true
    ]
    sendEvent(eventMap)
    
    if(!motionEnabled)
    {
       return null   
    }
    
    value = "inactive"
    if(IsMotionDetected(binaryValue))
    {
        value =  "active"
    }
    
    descriptionText = "${linkText} motion is ${value}"
    eventMap = [
    name: 'motion',
          value: value,
          descriptionText: descriptionText,
          translatable: true
    ]
    sendEvent(eventMap)
}

private handleTemperature(temp) {
    def eventMap = [:]
    eventMap.name = "temperature"
    eventMap.unit = "°${location.temperatureScale}"
    eventMap.value = convertTemperatureIfNeeded((float)temp/100.0+tempAdjust,"c",1) 
    eventMap.descriptionText = "${device.displayName} ${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    sendEvent(eventMap)
}


/**
 * Create battery event from reported battery voltage.
 *
 * @param volts Battery voltage in .1V increments
 */
private handleBatteryEvent(volts) {
	def descriptionText
    if (volts == 0 || volts == 255) {
        return null
    }
    
    def batteryMap = [28:100, 27:100, 26:100, 25:90, 24:90, 23:70,
                          22:70, 21:50, 20:50, 19:30, 18:30, 17:15, 16:1, 15:0]
    def minVolts = 15
    def maxVolts = 28

    if (volts < minVolts)
        volts = minVolts
    else if (volts > maxVolts)
        volts = maxVolts
        
    def value = batteryMap[volts]
    if (value != null) {
        def linkText = getLinkText(device)
        descriptionText = "${linkText} power source is ${value}"
        def eventMap = [
        name: 'battery',
                value: value,
                descriptionText: descriptionText,
                translatable: true
        ]
        sendEvent(eventMap)
    }
}

private handlePresenceEvent(present) {
    boolean change = false
    def wasPresent = device.currentState("presence")?.value == "present"
    
    if (!wasPresent && present) {
        startTimer()
        change = true
    } else if (!present) {
        stopTimer()
        change = true
    }
    
    if (!change){
        return
    }
        
    def linkText = getLinkText(device)
    def descriptionText
    if ( present )
    	descriptionText = "${linkText} has arrived"
    else
    	descriptionText = "${linkText} has left"
    def eventMap = [
        name: "presence",
        value: present ? "present" : "not present",
        linkText: linkText,
        descriptionText: descriptionText,
        translatable: true
    ]
    sendEvent(eventMap)
}

private startTimer() {
    runEvery1Minute("checkPresenceCallback")
}

private stopTimer() {
    unschedule("checkPresenceCallback")
}

def checkPresenceCallback() {
    if(!lastCheckin[device.deviceNetworkId])
    {
        return   
    }
    
    def timeSinceLastCheckin = (now() - lastCheckin[device.deviceNetworkId])
    def checkIntervalImp = checkInterval
    if(device.currentState("powerSource")?.value == "battery")
    {
        checkIntervalImp = checkIntervalBattery
    }
    
    def theCheckInterval = (checkIntervalImp ? checkIntervalImp as int : 2) * 60000
    if (timeSinceLastCheckin >= theCheckInterval) {
        handlePresenceEvent(false)
    }
}