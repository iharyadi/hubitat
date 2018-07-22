metadata {
    definition (name: "Environment Sensor", namespace: "iharyadi", author: "iharyadi", ocfDeviceType: "oic.r.temperature") {
        capability "Configuration"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
        capability "Illuminance Measurement"
        capability "PressureMeasurement"
        capability "Sensor"
                
        MapDiagAttributes().each{ k, v -> attribute "$v", "number" }

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0006, 0402, 0403, 0405, 0400, 0B05", manufacturer: "KMPCIL", model: "RES001BME280", deviceJoinName: "Environment Sensor"
    }
    
    tiles(scale: 2) {
        multiAttributeTile(name: "temperature", type: "generic", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState "temperature", label: '${currentValue}°',
                        backgroundColors: [
                                [value: 31, color: "#153591"],
                                [value: 44, color: "#1e9cbb"],
                                [value: 59, color: "#90d2a7"],
                                [value: 74, color: "#44b621"],
                                [value: 84, color: "#f1d801"],
                                [value: 95, color: "#d04e00"],
                                [value: 96, color: "#bc2323"]
                        ]
            }
        }
        valueTile("humidity", "device.humidity", inactiveLabel: false, width: 3, height: 2, wordWrap: true) {
            state "humidity", label: 'Humidity ${currentValue}${unit}', unit:"%", defaultState: true
        }
        valueTile("pressure", "device.pressureMeasurement", inactiveLabel: false, width: 3, height: 2, wordWrap: true) {
            state "pressure", label: 'Pressure ${currentValue}${unit}', unit:"kPa", defaultState: true
        }
        
        valueTile("illuminance", "device.illuminance", width:6, height: 2) {
            state "illuminance", label: 'illuminance ${currentValue}${unit}', unit:"Lux", defaultState: true
        }
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        
        def tiles_detail = [];
        tiles_detail.add("temperature")
        tiles_detail.add("humidity")
        tiles_detail.add("pressure")
        tiles_detail.add("illuminance")
        MapDiagAttributes().each{ k, v -> valueTile("$v", "device.$v", width: 2, height: 2, wordWrap: true) {
                state "val", label: "$v \n"+'${currentValue}', defaultState: true
            };
            tiles_detail.add(v);
        }
        tiles_detail.add("refresh")
                
        main "temperature"        
        details(tiles_detail)        
    }

    // simulator metadata
    simulator {
    }
        
    preferences {
        input "tempOffset", "decimal", title: "Degrees", description: "Adjust temperature by this many degrees in Celcius",
              range: "*..*", displayDuringSetup: false
    }
    
    preferences {
        input "tempFilter", "decimal", title: "Coeficient", description: "Temperature filter between 0.0 and 1.0",
              range: "0..1", displayDuringSetup: false
    }
    
    preferences {
        input "humOffset", "decimal", title: "Percent", description: "Adjust humidity by this many percent",
              range: "*..*", displayDuringSetup: false
    }
    
    preferences {
        input "illumAdj", "decimal", title: "Factor", description: "Adjust illuminace base on formula illum / Factor", 
            range: "1..*", displayDuringSetup: false
    }
}

private def NUMBER_OF_RESETS_ID()
{
    return 0x0000;
}

private def MAC_TX_UCAST_RETRY_ID()
{
    return 0x0104;
}

private def MAC_TX_UCAST_FAIL_ID()
{
    return 0x0105;
}

private def NWK_DECRYPT_FAILURES_ID()
{
    return 0x0115;
}

private def PACKET_VALIDATE_DROP_COUNT_ID()
{
    return 0x011A;
}

private def PARENT_COUNT_ID()
{
    return 0x011D+1;
}

private def CHILD_COUNT_ID()
{
    return 0x011D+2;
}

private def NEIGHBOR_COUNT_ID()
{
    return 0x011D+3;
}

private def LAST_RSSI_ID()
{
    return 0x011D;
}

private def DIAG_CLUSTER_ID()
{
    return 0x0B05;
}

private def TEMPERATURE_CLUSTER_ID()
{
    return 0x0402;
}

private def PRESSURE_CLUSTER_ID()
{
    return 0x0403;
}

private def HUMIDITY_CLUSTER_ID()
{
    return 0x0405;
}

private def ILLUMINANCE_CLUSTER_ID()
{
    return 0x0400;
}

private def SENSOR_VALUE_ATTRIBUTE()
{
    return 0x0000;
}

private def MapDiagAttributes()
{
    def result = [(CHILD_COUNT_ID()):'Children',
        (NEIGHBOR_COUNT_ID()):'Neighbor',
        (NUMBER_OF_RESETS_ID()):'ResetCount',
        (MAC_TX_UCAST_RETRY_ID()):'TXRetry',
        (MAC_TX_UCAST_FAIL_ID()):'TXFail',
        (LAST_RSSI_ID()):'RSSI',
        (NWK_DECRYPT_FAILURES_ID()):'DecryptFailure',
        (PACKET_VALIDATE_DROP_COUNT_ID()):'PacketDrop'] 

    return result;
}

private def createDiagnosticEvent( String attr_name, type, value )
{
    def result = [:]
    result.name = attr_name
    result.translatable = true
    
    def converter = [(DataType.INT8):{int val -> return (byte) val},
    (DataType.INT16):{int val -> return val},
    (DataType.UINT16):{int val -> return (long)val}] 
    
    result.value = converter[zigbee.convertHexToInt(type)]( zigbee.convertHexToInt(value));
    
    result.descriptionText = "{{ device.displayName }} $attr_name was $result.value"

    return createEvent(result)
}

private def parseDiagnosticEvent(def descMap)
{       
    def attr_name = MapDiagAttributes()[descMap.attrInt];
    if(!attr_name)
    {
        return null;
    }
    
    return createDiagnosticEvent(attr_name, descMap.encoding, descMap.value)
}

private def createPressureEvent(float pressure)
{
    def result = [:]
    result.name = "pressureMeasurement"
    result.translatable = true
    result.unit = "kPa"
    result.value = pressure.round(1)
    result.descriptionText = "{{ device.displayName }} pressureMeasurement was $result.value"
    return result
}

private def parsePressureEvent(def descMap)
{       
    if(zigbee.convertHexToInt(descMap.attrId) != SENSOR_VALUE_ATTRIBUTE())
    {
        return null
    }
    float pressure = (float)zigbee.convertHexToInt(descMap.value) / 10.0
    return createPressureEvent(pressure)
}

private def createHumidityEvent(float humidity)
{
    def result = [:]
    result.name = "humidity"
    result.translatable = true
    result.value = humidity
    result.unit = "%"
    result.descriptionText = "{{ device.displayName }} humidity was $result.value"
    
    if (humOffset) {
        result.value = result.value + humOffset
    }
    
    result.value = result.value.round(2) 
    return result
}
    
private def parseHumidityEvent(def descMap)
{       
    if(zigbee.convertHexToInt(descMap.attrId) != SENSOR_VALUE_ATTRIBUTE())
    {
        return null
    }
    
    float humidity = (float)zigbee.convertHexToInt(descMap.value)/100.0
    return createHumidityEvent(humidity)
}

private def createIlluminanceEvent(int illum)
{
    def result = [:]
    result.name = "illuminance"
    result.translatable = true
    result.unit = "Lux"
    
    if(!illumAdj ||  illumAdj < 1.0)
    {
        if(ilumm == 0)
        {
            result.value = 0.0
        }
        else
        {
            result.value = 10.0 ** (((double) illum / 10000.0) -1.0)
        }
        
    	result.value = result.value.round(2)  
    }
    else
    {
        result.value = ((double)illum / illumAdj).toInteger()
    }
    
    result.descriptionText = "{{ device.displayName }} illuminance was $result.value"
    return result
}

private String ilummStringPrefix()
{
    return "illuminance: "
}

def parseIlluminanceEvent(def descMap)
{       
    if(zigbee.convertHexToInt(descMap.attrId) != SENSOR_VALUE_ATTRIBUTE())
    {
        return null
    }
    
    int res =  zigbee.convertHexToInt(descMap.value)
    
    return createIlluminanceEvent(res)
}

def parseCustomEvent(String description)
{
    def event = null
    def descMap = zigbee.parseDescriptionAsMap(description)
    if(description?.startsWith("read attr - raw:"))
    {
        if(descMap?.clusterInt == DIAG_CLUSTER_ID())
        {
            event = parseDiagnosticEvent(descMap);
        }
        else if(descMap?.clusterInt == PRESSURE_CLUSTER_ID())
        {
            event = parsePressureEvent(descMap);
        }
        else if(descMap?.clusterInt == HUMIDITY_CLUSTER_ID())
        {
         	event = parseHumidityEvent(descMap); 
        }
        else if(descMap?.clusterInt == ILLUMINANCE_CLUSTER_ID())
        {
         	event = parseIlluminanceEvent(descMap); 
        }
   }
   return event
}

private String tempStringPrefix()
{
    return "temperature:"
}

private String humidityStringPrefix()
{
    return "humidity:"
}

private def adjustTemp(double val)
{
    if (tempOffset) {
        val = val + tempOffset
    }
        
    if(tempFilter)
    {
    	if(state.tempCelcius)
        {
    		val = tempFilter*val + (1.0-tempFilter)*state.tempCelcius
        }
        state.tempCelcius = val
    }
    
    return zigbee.convertToHexString((int)(val*100),4)
}

private def adjustTempValue(String description)
{    
    if(!description?.startsWith("read attr - raw:"))
    {
        return description   
    }
    
    def descMap = zigbee.parseDescriptionAsMap(description) 

    if( descMap.clusterInt != TEMPERATURE_CLUSTER_ID() )
    {  
        return description
    }

    if(descMap.attrInt != SENSOR_VALUE_ATTRIBUTE())
    {
        return description
    }
    
    String newValue = adjustTemp((double) zigbee.convertHexToInt(descMap.value) / 100.00)
    
    return description.replaceAll("value: [0-9A-F]{4}", "value: $newValue")    
 }

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
    
    event = parseCustomEvent(description)
    if(event)
    {
        sendEvent(event)
        return
    }
    
    description = adjustTempValue(description)
    def event = zigbee.getEvent(description)
    if(event)
    {
        sendEvent(event)
        return
    }
    
    log.warn "DID NOT PARSE MESSAGE : $description"
}

def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def refresh() {
    log.debug "Refresh"
    state.lastRefreshAt = new Date(now()).format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    def cmds = zigbee.readAttribute(TEMPERATURE_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE()) +
        zigbee.readAttribute(HUMIDITY_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE()) + 
        zigbee.readAttribute(PRESSURE_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE()) +
        zigbee.readAttribute(ILLUMINANCE_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE()) 
    MapDiagAttributes().each{ k, v -> cmds +=  zigbee.readAttribute(DIAG_CLUSTER_ID(), k) }  
    return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    state.remove("tempCelcius")
    List cmds = zigbee.temperatureConfig(5,300)
    cmds = cmds + zigbee.configureReporting(HUMIDITY_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE(), DataType.UINT16, 5, 300, 100)
    cmds = cmds + zigbee.configureReporting(PRESSURE_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE(), DataType.UINT16, 5, 300, 2)
    cmds = cmds + zigbee.configureReporting(ILLUMINANCE_CLUSTER_ID(), SENSOR_VALUE_ATTRIBUTE(), DataType.UINT16, 1, 300, 500)
    cmds = cmds + refresh();
    return cmds
}

def updated() {
    log.trace "Updated()"

    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
        state.updatedLastRanAt = now()
        state.remove("tempCelcius")
        return refresh()
    }
    else {
        log.trace "updated(): Ran within last 2 seconds so aborting."
    }
}
