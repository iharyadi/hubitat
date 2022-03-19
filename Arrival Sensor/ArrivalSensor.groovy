import groovy.transform.Field
    
@Field static HashMap data = [:]

metadata {
    definition (name: "Arrival Sensor HA", namespace: "iharyadi", author: "iharyadi") {
        singleThreaded: true
        
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
        fingerprint inClusters: "0000,0001,0003,000F,0020", outClusters: "0003,0019", manufacturer: "KPMCIL", model: "tagv1", deviceJoinName: "Presence Sensor"
    }

    preferences {
        section("Timeouts") {
            input "checkInterval", "enum", title: "Presence timeout (minutes) on dc power", description: "",
                    defaultValue:"1", options: ["1","2", "3", "5"], displayDuringSetup: false
            
            input "checkIntervalBattery", "enum", title: "Presence timeout (minutes) on battery", description: "",
                    defaultValue:"7", options: ["2", "3", "5", "7", "10", "20"], displayDuringSetup: false
        }
        
        section("Temperature") {
            input "tempAdjust", "decimal", title: "Temperature offset", description: "Adjust temperature in Celsius",
                    defaultValue:"8.55", displayDuringSetup: false
            
            def tempUnit = "Celsius"
            
            if( !"${location.temperatureScale}".equalsIgnoreCase("F"))
            {
                tempUnit = "Fahrenheit"
            }
            
            input name: "tempCF", defaultValue: "false", type: "bool", title: "Force temperature unit in ${tempUnit}", description: "",
                    displayDuringSetup: false
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
    if(motionEnabled)
    {
        sendMotionEvent("inactive")
    }
    else
    {
        device.deleteCurrentState("motion") 
    }

    refresh()
}

void uninstalled()
{
    unschedule()
    data.remove(device.deviceNetworkId)
}

def installed() {
    sendShockEvent("clear")
    if(!motionEnabled)
    {
       return   
    }
    sendMotionEvent("inactive")
}

private int getDelay()
{
    int delay = zigbee.STANDARD_DELAY_INT
    if(device.currentState("powerSource")?.value == "battery")
    {
        delay = 8000   
    }
    
    return delay
}

def configure() {
    int delay = getDelay();
    
    def cmds = refresh()
    
    cmds += zigbee.configureReporting(0x0001,0x0020, DataType.UINT8,   0, 10,1, [:], delay)
    cmds += zigbee.configureReporting(0x000F,0x0055, DataType.BOOLEAN, 0, 300,1, [:], delay)
    cmds += zigbee.configureReporting(0x0402,0x0000, DataType.INT16,   0, 1200,100,[:], delay)
    
    return cmds
}

def refresh() {
    
    int delay = getDelay();
    
    def cmds = zigbee.readAttribute(0x0001, 0x0020,[:],delay) + 
        zigbee.readAttribute(0x000F, 0x0055,[:],delay) + 
        zigbee.readAttribute(0x0402, 0x0000,[:],delay)
    return cmds
}

def parse(String description) { 
    def descMap = zigbee.parseDescriptionAsMap(description)
    
    Log("parse ${descMap}")
    
    if(data[device.deviceNetworkId] == null)
    {
        data[device.deviceNetworkId] = ["lastCheckin":now()]
    }
    data[device.deviceNetworkId]["lastCheckin"] = now()
    handlePresenceEvent(true)

    if( descMap.profileId == "0000")
    {
        if (descMap.clusterId == "0013")
        {
            if(device.currentState("presence")?.value == "present")
            {
                Log("device recovered from lost of parent at ${new Date(now()).format("yyyy-MM-dd HH:mm:ss", location.timeZone)}")
            }
        }
    } 
    else if (description?.startsWith('read attr -')) 
    {
        handleReportAttributeMessage(descMap)
    }

    return []
}

private handleReportAttributeMessage(descMap) {
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

private boolean IsBinaryValueChanged(def newvalue, def previousValue, def bitMask)
{
    return (newvalue ^ previousValue) & bitMask
}

private boolean IsDCPower(int value)
{
    return (value & 0x01) == 0x01
}

private boolean IsDCPowerChanged(def newvalue, def previousValue)
{
    return IsBinaryValueChanged(newvalue,previousValue, 0x01)
}

private boolean IsShockDetected(int value)
{
    return (value & 0x02) == 0x02
}

private boolean IsShockChanged(def newvalue, def previousValue)
{
    return IsBinaryValueChanged(newvalue,previousValue, 0x02)
}

private boolean IsMotionChanged(def newvalue, def previousValue)
{
    return IsBinaryValueChanged(newvalue,previousValue, 0x04)
}

private boolean IsMotionDetected(int value)
{
    return (value & 0x04) == 0x04
}

def sendShockEvent(newValue)
{
    eventMap = [
        name: "shock",
        value: newValue,
        descriptionText: "${getLinkText(device)} vibration is ${newValue}",
        translatable: true]
    sendEvent(eventMap)
}

def sendMotionEvent(newValue)
{   
    eventMap = [
        name: "motion",
        value:  newValue,
        descriptionText: "${getLinkText(device)} human presence is ${newValue == "active"? "" : "not" } detected",
        translatable: true]
    sendEvent(eventMap)
}

private handleDCPower(binaryValue, prevValue)
{
    if(!IsDCPowerChanged(binaryValue,prevValue))
    {
        return
    }
    
    def value = "battery"
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} is powered down"
    if(IsDCPower(binaryValue))
    {
        value =  "dc"
        descriptionText = "${linkText} is powered up"
    }
        
    def eventMap = [
        name: 'powerSource',
               value: value,
               descriptionText: descriptionText,
               translatable: true
        ]
    sendEvent(eventMap)
}

private handleShock(binaryValue, prevValue)
{
    if(!IsShockChanged(binaryValue,prevValue))
    {
        return
    }
    
    if(IsShockDetected(binaryValue))
    {
        unschedule(sendShockEvent)
        sendShockEvent("detected")
    }
    else
    {
        if(device.currentState("shock")?.value == "detected")
        {
            runIn(90, sendShockEvent, [data: "clear"])
        }
    }
}

private handleMotion(binaryValue, prevValue)
{
    if(!motionEnabled)
    {
       return   
    }
    
    if(!IsMotionChanged(binaryValue,prevValue))
    {
        return
    }
    
    if(IsMotionDetected(binaryValue))
    {
        unschedule(sendMotionEvent)
        sendMotionEvent("active")
    }
    else
    {
        if(device.currentState("motion")?.value == "active")
        {
            runIn(90, sendMotionEvent, [data: "inactive"])
        }
    }
}

private handleBinaryInput(binaryValue) {
    
    def prevValue = data[device.deviceNetworkId]["binaryValue"] == null ? 0 : data[device.deviceNetworkId]["binaryValue"]
    data[device.deviceNetworkId]["binaryValue"] = binaryValue
    
    handleDCPower(binaryValue,prevValue)
    
    handleShock(binaryValue, prevValue) 
    
    handleMotion(binaryValue, prevValue)
}

private handleTemperature(temp) {
    def eventMap = [:]
    eventMap.name = "temperature"
    if(tempCF)
    {
        float tempInCelsius =  ((float)temp/100.0+tempAdjust).round(0)
        if("${location.temperatureScale}".equalsIgnoreCase("F"))
        {
            eventMap.unit = "°C"
            eventMap.value = (int) tempInCelsius
        }
        else if ("${location.temperatureScale}".equalsIgnoreCase("C"))
        {
            eventMap.unit = "°F"
            eventMap.value = (int) ((tempInCelsius*9.0/5.0) + 32.0)
        }
    }
    else
    {
        eventMap.unit = "°${location.temperatureScale}"
        eventMap.value = convertTemperatureIfNeeded((float)temp/100.0+tempAdjust,"c",0)
    }
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
    
    def batteryMap = [28:100, 27:100, 26:100, 25:90, 24:90, 23:80,
                          22:70, 21:60, 20:50, 19:40, 18:30, 17:15, 16:1, 15:0]
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
    if(data[device.deviceNetworkId] == null)
    {
        return   
    }
    
    def timeSinceLastCheckin = (now() - data[device.deviceNetworkId]["lastCheckin"])
    def checkIntervalImp = checkInterval
    if(device.currentState("powerSource")?.value == "battery" && device.currentState("shock")?.value == "clear"  && device.currentState("motion")?.value == "inactive" )
    {
        checkIntervalImp = checkIntervalBattery
    }
    
    def theCheckInterval = (checkIntervalImp ? checkIntervalImp as int : 2) * 60000
    if (timeSinceLastCheckin >= theCheckInterval) {
        handlePresenceEvent(false)
    }
}