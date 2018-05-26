metadata {
    definition (name: "Environment Sensor", namespace: "iharyadi", author: "iharyadi", ocfDeviceType: "oic.r.temperature") {
        capability "Configuration"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
        capability "Illuminance Measurement"
        
        attribute "pressure", "number"
        
        MapDiagAttributes().each{ k, v -> attribute "$v", "number" }

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0006, 0402, 0403, 0405, 0400, 0B05", manufacturer: "KMPCIL", model: "RES001BME280", deviceJoinName: "Environment Sensor"
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
        input "humOffset", "decimal", title: "%", description: "Adjust humidity by this many %",
              range: "*..*", displayDuringSetup: false
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
    result.name = "pressure"
    result.translatable = true
    result.unit = "kPa"
    result.value = pressure.round(1)
    result.descriptionText = "{{ device.displayName }} pressure was $result.value"
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

private def createIlluminanceEvent(int ilumm)
{
    def result = [:]
    result.name = "illuminance"
    result.translatable = true
    result.unit = "Lux"
    if(ilumm == 0)
    {
        result.value = 0.0
    }
    else
    {
        result.value = (10.0 ** (((double) ilumm / 10000.0) -1.0)).round(2)
    }
    result.descriptionText = "{{ device.displayName }} illuminance was $result.value"
    return result
}

private String ilummStringPrefix()
{
    return "illuminance: "
}

private def parseIlluminanceEventFromString(String description)
{
    if(!description.startsWith(ilummStringPrefix()))
    {
        return null
    }
    int ilumm = Integer.parseInt(description.substring(ilummStringPrefix().length()))
    
    return createIlluminanceEvent(ilumm)
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

private def createAdjustedTempString(double val)
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
    
    return tempStringPrefix() + " " +val.toString()
}

private def createAdjustedHumString(double val)
{
    double adj = 0.0
    if (humOffset) {
        adj = humOffset
    }
    
    return humidityStringPrefix() + " " +(val + adj).toString() + "%"
}

private def adjustTempValue(String description)
{
    
    if(description.startsWith(tempStringPrefix()))
    {
        double d = Double.parseDouble(description.substring(tempStringPrefix().length()))
        return createAdjustedTempString(d)
    }
   
    if(description.startsWith(humidityStringPrefix()))
    {
        double d = Double.parseDouble(description.substring(humidityStringPrefix().length()).replaceAll("[^\\d.]", ""))
        return createAdjustedHumString(d)
    }
    
    if(!description.startsWith("catchall:"))
    {
        return description
    }
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    
    if(descMap.attrInt != SENSOR_VALUE_ATTRIBUTE())
    {
        return description
    }
    
    if( descMap.clusterInt == TEMPERATURE_CLUSTER_ID() )
    {
        return createAdjustedTempString((double) zigbee.convertHexToInt(descMap.value) / 100.00)
    }
    else if(descMap.clusterInt == HUMIDITY_CLUSTER_ID())
    {
        return createAdjustedHumString((double) zigbee.convertHexToInt(descMap.value) / 100.00)
    }
    
    return description 
 }

// Parse incoming device messages to generate events
def parse(String description) {
    
    description = adjustTempValue(description)
    log.debug "description is $description"
    
    def event = zigbee.getEvent(description)
    if(event)
    {
        sendEvent(event)
        return
    }
    
    event = parseIlluminanceEventFromString(description)
    if(event)
    {
        sendEvent(event)
        return
    }
    
    event = parseCustomEvent(description)
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
    log.trace "updated():"

    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
        state.updatedLastRanAt = now()
        state.remove("tempCelcius")
        return response(refresh())
    }
    else {
        log.trace "updated(): Ran within last 2 seconds so aborting."
    }
}
