import groovy.json.JsonSlurper
import hubitat.zigbee.clusters.iaszone.ZoneStatus

metadata {
    definition (name: "Environment Sensor EX", namespace: "iharyadi", author: "iharyadi", ocfDeviceType: "oic.r.temperature") {
        capability "Configuration"
        capability "Refresh"
        capability "Battery"
        capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
        capability "Illuminance Measurement"
        capability "PressureMeasurement"
        capability "Switch"
        capability "SmokeDetector"
        capability "PowerSource"
        capability "CarbonMonoxideDetector"
        capability "Sensor"
                
        MapDiagAttributes().each{ k, v -> attribute "$v", "number" }
        
        attribute "BinaryOutput", "BOOLEAN"
        attribute "BinaryInput", "BOOLEAN"
        attribute "AnalogInput", "number"
        attribute "relativePressure", "number"

        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0402, 0403, 0405, 0400, 0B05, 000F, 000C, 0010", manufacturer: "KMPCIL", model: "RES001", deviceJoinName: "Environment Sensor"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0006, 0402, 0403, 0405, 0400, 0B05, 000F, 000C, 0010,1001", manufacturer: "KMPCIL", model: "RES001", deviceJoinName: "Environment Sensor"
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0402, 0403, 0405, 0B05, 000F, 000C, 0010", manufacturer: "KMPCIL", model: "RES002", deviceJoinName: "Environment Sensor"
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0400, 0B05, 000F, 000C, 0010", manufacturer: "KMPCIL", model: "RES003", deviceJoinName: "Environment Sensor"
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0B05, 000F, 000C, 0010", manufacturer: "KMPCIL", model: "RES004", deviceJoinName: "Environment Sensor"
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0402, 0403, 0405, 0400, 0B05, 000F, 000C, 0010, 1001", manufacturer: "KMPCIL", model: "RES005", deviceJoinName: "Environment Sensor"
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0402, 0403, 0405, 0400, 0B05, 000F, 000C, 0010, 0500", manufacturer: "KMPCIL", model: "RES006", deviceJoinName: "Environment Sensor"

    }
    
    preferences {
    
        section("Environment Sensor")
        {
            input name:"tempOffset", type:"decimal", title: "Degrees", description: "Adjust temperature by this many degrees in Celcius",
                  range: "*..*", displayDuringSetup: false
            input name:"tempFilter", type:"decimal", title: "Coeficient", description: "Temperature filter between 0.0 and 1.0",
                  range: "0..1", displayDuringSetup: false
            input name:"humOffset", type:"decimal", title: "Percent", description: "Adjust humidity by this many percent",
                  range: "*..*", displayDuringSetup: false
            input name:"illumAdj", type:"decimal", title: "Factor", description: "Adjust illuminace base on formula illum / Factor", 
                range: "1..*", displayDuringSetup: false
            input name:"relativePressOffset", type:"decimal", title: "Relative Pressure", description: "Relative pressure offset in kPA", 
                range: "0..*", displayDuringSetup: false
            input name: "pressureInHg", defaultValue: "false", type: "bool", title: "Report pressure in inhg", description: "",
                displayDuringSetup: false
        }
        
        section("Expansion Sensor")
        {
            input name:"enableAnalogInput", type: "bool", title: "Analog Input", description: "Enable Analog Input",
                defaultValue: "false", displayDuringSetup: false 
            
            input name:"childAnalogInput", type:"text", title: "Analog Input Handler", description: "Analog Input Child Handler",
                   displayDuringSetup: false
              
            input name:"enableBinaryInput", type: "bool", title: "Binary Input", description: "Enable Binary Input",
                   defaultValue: "false", displayDuringSetup: false
            
            input name:"childBinaryInput", type:"string", title: "Binary Input Handler", description: "Binary Input Device Handler",
                   displayDuringSetup: false
              
            input name:"enableBinaryOutput", type: "bool", title: "Binary Output", description: "Enable Binary Output",
                   defaultValue: "false", displayDuringSetup: false  
            
            input name:"childBinaryOutput", type:"text", title: "Binary Output Handler", description: "Binary Output Child Handler",
                   displayDuringSetup: false
        }
        
        section("Serial Device Children")
        {
            input name:"childSerialDevices", type:"text", title: "Children[JSON]", description: "Serial Children Handler",
                   displayDuringSetup: false
        }
        
        section("Debug Messages")
        {
            input name: "logEnabled", defaultValue: "true", type: "bool", title: "Enable info message logging", description: "",
                displayDuringSetup: false
        }
    }
}

private def Log(message) {
    if (logEnabled)
        log.info "${message}"
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

private def BATT_REMINING_ID()
{
    return 0x0021;
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

private def POWER_CLUSTER_ID()
{
    return 0x0001;
}

private def BINARY_INPUT_CLUSTER_ID()
{
    return 0x000F;
}

private def BINARY_OUTPUT_CLUSTER_ID()
{
    return 0x0010;
}

private def ANALOG_INPUT_CLUSTER_ID()
{
    return 0x000C;
}

private def SENSOR_VALUE_ATTRIBUTE()
{
    return 0x0000;
}

private def SERIAL_TUNNEL_CLUSTER_ID()
{
    return 0x1001;
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
    
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value}"

    return createEvent(result)
}

private def parseDiagnosticEvent(def descMap)
{       
    def attr_name = MapDiagAttributes()[zigbee.convertHexToInt(descMap.attrId)];
    if(!attr_name)
    {
        return null;
    }
    
    return createDiagnosticEvent(attr_name, descMap.encoding, descMap.value)
}

private def createPressureEvent(float pressure)
{
    String unit = pressureInHg ? "inhg": "kPa"
    def result = [:]
    result.name = "pressure"
    result.translatable = true
    result.unit = unit
    result.value = (pressureInHg ? (pressure/3.386):pressure).round(2)
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    if (relativePressOffset && relativePressOffset != 0)
    {
        pressure = pressure+relativePressOffset
        def relPEvent = [:]
        relPEvent.name = "relativePressure"
        relPEvent.translatable = true
        relPEvent.unit = unit
        relPEvent.value = (pressureInHg ? (pressure/3.386):pressure).round(2)
        relPEvent.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
        sendEvent(relPEvent)
    }
    
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
    
    if (humOffset) {
        result.value = result.value + humOffset
    }
    
    result.value = result.value.round(2) 
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
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
        double val = 0.0
        if(ilumm != 0)
        {
            val = 10.0 ** (((double) illum -1.0)/10000.0)
        }
        
        result.value = val.round(2)  
    }
    else
    {
        result.value = ((double)illum / illumAdj).toInteger()
    }
    
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    return result
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

private def createTempertureEvent(float temp)
{
    def result = [:]
    result.name = "temperature"
    result.value = temperature
    result.unit = "°${location.temperatureScale}"
    
    
    result.value = convertTemperatureIfNeeded(temp,"c",1) 
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    return result
}

private float adjustTemp(float val)
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
    
    
    return val
}

private def parseTemperatureEvent(def descMap)
{    		
    float temp = adjustTemp((hexStrToSignedInt(descMap.value) / 100.00).toFloat())

    return createTempertureEvent(temp)   
}

private def createBinaryOutputEvent(boolean val)
{
    def result = [:]
    result.name = "BinaryOutput"
    result.translatable = true
    result.value = val ? "true" : "false"
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value}"
    return result
}

def parseBinaryOutputEvent(def descMap)
{     
    def present_value = descMap.attrId?.equals("0055")?
        descMap.value:
        descMap.additionalAttrs?.find { item -> item.attrId?.equals("0055")}?.value
    
    if(!present_value)
    {
        return null
    }
        
    return createBinaryOutputEvent(zigbee.convertHexToInt(present_value) > 0)
}

private def createAnalogInputEvent(float value)
{
    def result = [:]
    result.name = "AnalogInput"
    result.translatable = true
    result.value = value
    result.unit = "Volt"
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value}"
    return result
}

private Float ConvertStringIntBitsToFloat(String val)
{
    Long i = Long.parseLong(val, 16);
    Float f = Float.intBitsToFloat(i.intValue());
    return f;
}

def parseAnalogInputEvent(def descMap)
{       
    def adc;
    def vdd;
    
    if(descMap.attrId?.equals("0103"))
    {
        adc = descMap.value
    }
    else if (descMap.attrId?.equals("0104"))
    {
        vdd  = descMap.value
    }
    else
    {   
        adc = descMap.additionalAttrs?.find { item -> item.attrId?.equals("0103")}.value
        vdd = descMap.additionalAttrs?.find { item -> item.attrId?.equals("0104")}.value        
    }
    
    if(vdd)
    {
        state.lastVdd = (((float)zigbee.convertHexToInt(vdd)*3.45)/0x1FFF)
    }   
    
    if(!adc)
    {
        return null   
    }

    float volt = 0;
    if(state.lastVdd)
    {
           volt = (zigbee.convertHexToInt(adc) * state.lastVdd)/0x1FFF
    }
    
    return createAnalogInputEvent(volt)
}

private def createBinaryInputEvent(boolean val)
{    
    def result = [:]
    result.name = "BinaryInput"
    result.translatable = true
    result.value = val
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value}"  
    return result
}

def parseBinaryInputEvent(def descMap)
{       
    def value = descMap.attrId?.equals("0055") ? 
        descMap.value : 
        descMap.additionalAttrs?.find { item -> item.attrId?.equals("0055")}.value
    
    if(!value)
    {
        return null
    }
           
    return createBinaryInputEvent(zigbee.convertHexToInt(value)>0)
}

private def reflectToChild(String childtype, String description)
{
    if(!childtype)
    {
        return    
    }
    
    def childDevice = getChildDevice("${device.deviceNetworkId}-$childtype")
    
    if(!childDevice)
    {
        return    
    }
        
    def childEvent = childDevice.parse(description)
    if(!childEvent)
    {
        return
    }
    
    childDevice.sendEvent(childEvent)    
}

private def reflectToSerialChild(def data)
{
    def zigbeeAddress = device.getZigbeeId()
    
    Integer page = zigbee.convertHexToInt(data[1])
        
    def childDevice = getChildDevice("$zigbeeAddress-SerialDevice-$page")
    
    if(!childDevice)
    {
        return    
    }
        
    def childEvent = childDevice.parse(data)
    if(!childEvent)
    {
        return
    }
    
    childDevice.sendEvent(childEvent)  
}

private def createBattEvent(int val)
{    
    def result = [:]
    result.name = "battery"
    result.translatable = true
    result.value = val/2
    result.unit = "%"
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value}"  
    return result
}

def parseBattEvent(def descMap)
{       
    def value = descMap?.attrId?.equals(zigbee.convertToHexString(BATT_REMINING_ID(),4)) ? 
        descMap.value : 
        null
    
    if(!value)
    {
        return null
    }
           
    return createBattEvent(zigbee.convertHexToInt(value))
}

def parseCustomEvent(String description)
{
    def event = null
    
    if(description?.startsWith("read attr - raw:"))
    {
        def descMap = zigbee.parseDescriptionAsMap(description)
        
        if(descMap?.cluster?.equals(zigbee.convertToHexString(TEMPERATURE_CLUSTER_ID(),4)))
        {
            event = parseTemperatureEvent(descMap)
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(DIAG_CLUSTER_ID(),4)))
        {
            event = parseDiagnosticEvent(descMap);
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(PRESSURE_CLUSTER_ID(),4)))
        {
            event = parsePressureEvent(descMap);
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(HUMIDITY_CLUSTER_ID(),4)))
        {
             event = parseHumidityEvent(descMap); 
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(ILLUMINANCE_CLUSTER_ID(),4)))
        {
             event = parseIlluminanceEvent(descMap); 
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(BINARY_INPUT_CLUSTER_ID(),4)))
        {
            event = parseBinaryInputEvent(descMap);
            reflectToChild(childBinaryInput,description)
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(ANALOG_INPUT_CLUSTER_ID(),4)))
        {
            event = parseAnalogInputEvent(descMap)
            reflectToChild(childAnalogInput,description)
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(BINARY_OUTPUT_CLUSTER_ID(),4)))
        {
            event = parseBinaryOutputEvent(descMap)
            reflectToChild(childBinaryOutput,description)
        }
        else if(descMap?.cluster?.equals(zigbee.convertToHexString(POWER_CLUSTER_ID(),4)))
        {
            event = parseBattEvent(descMap)
        }
   }
   return event
}

boolean parseSerial(String description)
{
    if(!description?.startsWith("catchall:"))
    {
         return false   
    }
        
    def descMap = zigbee.parseDescriptionAsMap(description)

    if( !(descMap.profileId?.equals("0104") ) )
    {
        return false
    }    
    
    if( !(descMap.clusterInt?.equals(SERIAL_TUNNEL_CLUSTER_ID()) ) )
    {
        return false
    }
    
    if( !(descMap.command?.equals("00") ) )
    {
        return false
    }
    
    if(!descMap.data)
    {
        return false
    }
    
    reflectToSerialChild(descMap.data)
      
    return true
}

private boolean parseIasMessage(String description) {
    
    if (description?.startsWith('enroll request')) 
    {
		Log ("Sending IAS enroll response...")
        
		def cmds = zigbee.enrollResponse()
        
        cmds?.collect{ sendHubCommand(new hubitat.device.HubAction(it,hubitat.device.Protocol.ZIGBEE) ) };
        return true
	}
    else if (description?.startsWith('zone status ')) 
    {        
        ZoneStatus zs = zigbee.parseZoneStatus(description)
        String[] iasinfo = description.split(" ")
        int x =  hexStrToSignedInt(iasinfo[6])
        
        def resultMap;
        
        if(zs.alarm1)
        {
            if(x == 0)
            {
                resultMap = createEvent(name: "smoke", value: "detected")
            }
            else
            {
                resultMap = createEvent(name: "carbonMonoxide", value: "detected")
            }
            
            sendEvent(resultMap)
            
        }
        else
        {
            
           resultMap = createEvent(name: "smoke", value: "clear")
           sendEvent(resultMap)
            
           resultMap = createEvent(name: "carbonMonoxide", value: "clear")
           sendEvent(resultMap)
        }
     
        if(zs.ac)
        {
            resultMap = createEvent(name: "powerSource", value: "battery")
        }
        else
        {
            resultMap = createEvent(name: "powerSource", value: "main")
        }   
        sendEvent(resultMap)
        
        return true
    }
    
    return false
}

// Parse incoming device messages to generate events
def parse(String description) {
    Log("parse: $description")
    
    if(parseIasMessage(description))
    {
        return
    }
    
    event = parseCustomEvent(description)
    if(event)
    {
        sendEvent(event)
        return
    }
    
    def event = zigbee.getEvent(description)
    if(event)
    {
        sendEvent(event)
        return
    }
    
    if(parseSerial(description))
    {
        return   
    }
    
    Log("DID NOT PARSE MESSAGE : $description")
    
}

def off() {
}

def on() {
}

def sendCommandPDelay(data)
{
    return data   
}
    
def sendCommandP(def cmd)
{    
     runIn(0, sendCommandPDelay, [overwrite: false,data: cmd])
}

def sendToSerialdevice(byte[] serialCmd)
{   
    String serial = serialCmd.encodeHex().toString()
    
    return zigbee.command(SERIAL_TUNNEL_CLUSTER_ID(), 0x00,[:],0,serial)
}

def command(Integer Cluster, Integer Command, String payload)
{
    return zigbee.command(Cluster,Command,payload)
}

def command(Integer Cluster, Integer Command)
{
    return zigbee.command(Cluster,Command)
}

def readAttribute(Integer Cluster, Integer attributeId, Map additionalParams)
{
    return zigbee.readAttribute(Cluster, attributeId, additionalParams)
}

def readAttribute(Integer Cluster, Integer attributeId)
{
    return zigbee.readAttribute(Cluster, attributeId)
}

def writeAttribute(Integer Cluster, Integer attributeId, 
                   Integer dataType, Integer value, 
                   Map additionalParams)
{
    return zigbee.writeAttribute(Cluster, attributeId, 
                                 dataType, value, 
                                 additionalParams)
}

def writeAttribute(Integer Cluster, Integer attributeId, 
                   Integer dataType, Integer value)
{
    return zigbee.writeAttribute(Cluster, attributeId, 
                                 dataType, value)
}
    
def configureReporting(Integer Cluster,
    Integer attributeId, Integer dataType,
    Integer minReportTime, Integer MaxReportTime,
    Integer reportableChange,
    Map additionalParams)
{
    return zigbee.configureReporting( Cluster,
        attributeId,  dataType,
         minReportTime,  MaxReportTime,
        reportableChange,
        aditionalParams)
}

def configureReporting(Integer Cluster,
    Integer attributeId, Integer dataType,
    Integer minReportTime, Integer MaxReportTime,
    Integer reportableChange)
{
    return zigbee.configureReporting( Cluster,
        attributeId,  dataType,
         minReportTime,  MaxReportTime,
        reportableChange)
}

def configureReporting(Integer Cluster,
    Integer attributeId, Integer dataType,
    Integer minReportTime, Integer MaxReportTime)
{
    return zigbee.configureReporting( Cluster,
        attributeId,  dataType,
         minReportTime,  MaxReportTime)
}

def binaryoutputOff()
{
    zigbee.writeAttribute(0x0010, 0x0055, DataType.BOOLEAN, 0)
}

def binaryoutputOn()
{
    zigbee.writeAttribute(0x0010, 0x0055, DataType.BOOLEAN, 1)
}

private def refreshExpansionSensor()
{
    def cmds = []
    
    def mapExpansionRefresh = [[0x0010,enableBinaryOutput,0x0055],
        [0x000F,enableBinaryInput,0x0055],
        [0x000C, enableAnalogInput,0x00104],
        [0x000C, enableAnalogInput,0x00103]]
        
    mapExpansionRefresh.findAll { return it[1] }.each{
        cmds = cmds + zigbee.readAttribute(it[0],it[2])
        }
        
    return cmds
}

private def refreshOnBoardSensor()
{
    def model = device.getDataValue("model")
    
    def cmds = [];
    
     def mapRefresh = ["RES001":[TEMPERATURE_CLUSTER_ID(), HUMIDITY_CLUSTER_ID(), PRESSURE_CLUSTER_ID(),ILLUMINANCE_CLUSTER_ID()],
         "RES002":[TEMPERATURE_CLUSTER_ID(), HUMIDITY_CLUSTER_ID(), PRESSURE_CLUSTER_ID()],
         "RES003":[ILLUMINANCE_CLUSTER_ID()],
         "RES005":[TEMPERATURE_CLUSTER_ID(), HUMIDITY_CLUSTER_ID(), PRESSURE_CLUSTER_ID(),ILLUMINANCE_CLUSTER_ID()],
         "RES006":[TEMPERATURE_CLUSTER_ID(), HUMIDITY_CLUSTER_ID(), PRESSURE_CLUSTER_ID(),ILLUMINANCE_CLUSTER_ID()]]
     
    mapRefresh[model]?.each{
        cmds = cmds + zigbee.readAttribute(it,SENSOR_VALUE_ATTRIBUTE());
    }
    
    return cmds
}

private def refreshDiagnostic()
{
    def cmds = [];
    MapDiagAttributes().each{ k, v -> cmds +=  zigbee.readAttribute(DIAG_CLUSTER_ID(), k) } 
    return cmds
}

private def refreshBatt()
{
    return zigbee.readAttribute(POWER_CLUSTER_ID(), BATT_REMINING_ID()) 
}

def refresh() {
    Log ("Refresh")
    state.lastRefreshAt = new Date(now()).format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    
    return refreshOnBoardSensor() + 
        refreshExpansionSensor() + 
        refreshDiagnostic() +
        refreshBatt()
}

private def reportBME280Parameters()
{
    def reportParameters = [];
    reportParameters = reportParameters + [[TEMPERATURE_CLUSTER_ID(),DataType.INT16, 5, 300, 10]]
    reportParameters = reportParameters + [[HUMIDITY_CLUSTER_ID(),DataType.UINT16, 5, 301, 100]]
    reportParameters = reportParameters + [[PRESSURE_CLUSTER_ID(),DataType.UINT16, 5, 302, 2]]
    return reportParameters
}

private def reportTEMT6000Parameters()
{
    def reportParameters = [];
    reportParameters = reportParameters + [[ILLUMINANCE_CLUSTER_ID(),DataType.UINT16, 5, 303, 500]]
    return reportParameters
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
	int i = 0;
	int j = array.length - 1;
	byte tmp;
	while (j > i) {
		tmp = array[j];
		array[j] = array[i];
		array[i] = tmp;
		j--;
		i++;
	}
	return array
}

private def configureIASZone()
{   
    def cmds = []
    
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0500 {${device.zigbeeId}} {}"
    cmds += "delay 1500"
            
    cmds += "he wattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0500 0x0010 0xf0 {${swapEndianHex(device.hub.zigbeeId)}}"
    cmds += "delay 2000"
          
    return cmds
}

def configure() {

    Log("Configuring Reporting and Bindings.")
    state.remove("tempCelcius")
    
    def mapConfigure = ["RES001":reportBME280Parameters()+reportTEMT6000Parameters(),
        "RES002":reportBME280Parameters(),
        "RES003":reportTEMT6000Parameters(),
        "RES005":reportBME280Parameters()+reportTEMT6000Parameters(),
        "RES006":reportBME280Parameters()+reportTEMT6000Parameters()]
    
    def model = device.getDataValue("model")
    
    def cmds = [];
    mapConfigure[model]?.each{
        cmds = cmds + zigbee.configureReporting(it[0], SENSOR_VALUE_ATTRIBUTE(), it[1],it[2],it[3],it[4])
    }
    
    cmds += zigbee.configureReporting(POWER_CLUSTER_ID(), BATT_REMINING_ID(), DataType.UINT8,60,307,4)
    
    if(model == "RES006")
    {
       cmds = cmds + configureIASZone()
    }
    
    cmds = cmds + refresh();
    
    return cmds
}

private def createChild(String childDH, String component)
{    
    if(!childDH)
    {
        return null
    }
    
    def childDevice = getChildDevice("${device.deviceNetworkId}-$childDH")
    if(!childDevice)
    {
        childDevice = addChildDevice("iharyadi", 
                       "$childDH", 
                       "${device.deviceNetworkId}-$childDH",
                       [label: "${device.displayName} $childDH",
                        isComponent: false, 
                        componentName: component, 
                        componentLabel: "${device.displayName} $childDH"])
    }
    
    return childDevice?.configure_child()
}

private def updateExpansionSensorSetting()
{    
    def cmds = []
    
    def mapExpansionEnable = [[0x0010,enableBinaryOutput,DataType.BOOLEAN,0x0055],
        [0x000F,enableBinaryInput,DataType.BOOLEAN,0x0055],
        [0x000C, enableAnalogInput,DataType.UINT16,0x0103]]
        
    mapExpansionEnable.each{ 
        cmds = cmds + zigbee.writeAttribute(it[0], 0x0051, DataType.BOOLEAN, it[1]?1:0)
        if(!it[1])
        {
            cmds = cmds + zigbee.configureReporting(it[0], it[3], it[2], 0xFFFF, 0xFFFF,1)
        }
    }
    
    def mapExpansionChildrenCreate = [[enableBinaryOutput,childBinaryOutput,"BinaryOutput"],
        [enableBinaryInput,childBinaryInput,"BinaryInput"],
        [enableAnalogInput,childAnalogInput,"AnalogInput"]]

    mapExpansionChildrenCreate.findAll{return (it[0] && it[1])}.each{
        cmds = cmds + createChild(it[1],it[2])
    }
    
    return cmds
}

private def createSerialDeviceChild(String childDH, Integer page)
{
    createSerialDeviceChildWithLabel(childDH,page, "${device.displayName} SerialDevice-$page")
}

def createSerialDeviceChildWithLabel(String childDH, Integer page, String label)
{    
    if(!childDH)
    {
        return null
    }
    
    def zigbeeAddress = device.getZigbeeId()
    def childDevice = getChildDevice("$zigbeeAddress-SerialDevice-$page")
    if(!childDevice)
    {
        childDevice = addChildDevice("iharyadi", 
                       "$childDH", 
                       "$zigbeeAddress-SerialDevice-$page",
                       [label: "${label}",
                        isComponent: false, 
                        componentName: "SerialDevice-$page", 
                        componentLabel: "${device.displayName} SerialDevice-$page",
                        pageNumber: page])
    }
    
    return childDevice?.configure_child()
}

private def updateSerialDevicesSetting()
{   
    def cmds = []
    if(!childSerialDevices)
    {
        return cmds;   
    }
    
    def jsonSlurper = new JsonSlurper()
    def serialchild = jsonSlurper.parseText(childSerialDevices)
    
    serialchild.each{
        createSerialDeviceChild(it.DH, it.Page)
    } 
    
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x${Integer.toHexString(SERIAL_TUNNEL_CLUSTER_ID())} {${device.zigbeeId}} {}"
    cmds += "delay 1500"       
    
    return cmds
}

def updated() {
    Log("updated():")

    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
        state.updatedLastRanAt = now()
        state.remove("tempCelcius")
        
        def cmds = updateExpansionSensorSetting()
        
        if(device.getDataValue("model") == "RES005")
        {
            cmds += updateSerialDevicesSetting()
        }
        
        cmds += refresh()
        return cmds
    }
    else {
        Log("updated(): Ran within last 2 seconds so aborting.")
    }
}
