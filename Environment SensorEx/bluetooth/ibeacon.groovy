metadata {
    definition (name: "iBeacon", namespace: "iharyadi", author: "iharyadi") {
        capability "TemperatureMeasurement"
        capability "SignalStrength"
        capability "Configuration"
        capability "PresenceSensor"
        
        attribute "color","STRING"
        attribute "gravity","NUMBER"
    } 
    
    preferences {
        section("Gravity")
        {
            input name: "enGravityCalib", defaultValue: "false", type: "bool", title: "Enable Gravity Calibration", description: "", displayDuringSetup: false
            input name:"x0", defaultValue: 0.0, type:"decimal", title: "x0", description: "Gravity calibration coefficient 0", displayDuringSetup: false
            input name:"x1", defaultValue: 1.0, type:"decimal", title: "x1", description: "Gravity calibration coefficient 1", displayDuringSetup: false
            input name:"x2", defaultValue: 0.0, type:"decimal", title: "x2", description: "Gravity calibration coefficient 2", displayDuringSetup: false
            input name:"x3", defaultValue: 0.0, type:"decimal", title: "x3", description: "Gravity calibration coefficient 3", displayDuringSetup: false
            
            input name: "enTemperatureCalib", defaultValue: "false", type: "bool", title: "Enable Gravity Temperature Adjustment", description: "", displayDuringSetup: false 
        }
    }
}

private short ADRVERTISEMENT_FRAME()
{
    return 0x00
}

private short READ_ATTRIBUTE_FRAME()
{
    return 0x01
}

private short UPDATE_ADDRESS_FILTER()
{
    return 0x50
}

private short READ_ATTRIBUTE()
{
    return 0x02
}

private short DISCONNECT_COMMAND()
{
    return 0x03
}

private long byteArrayInt(def byteArray) {
    long i = 0
    byteArray.each { b -> i = (i << 8) | ((int)b & 0x000000FF) }
    return i
}

private def parseBleAdverstimentEIRData(byte[] data)
{
    def eirMap = [:]
    int eirDataLength = data[10]
    byte [] eirData = data[11..-1]
    for (int i = 0; i < eirDataLength;) {
        int eirLength = eirData[i++];
        int eirType = eirData[i++];
        eirMap[eirType] = eirData[i..i+(eirLength-2)]
        i += (eirLength - 1);
    }
    return eirMap
}

private def parseBeaconData(def eirMap)
{
    if(eirMap[-1] == null)
    {
        return null;   
    }
    
    byte [] data = eirMap[-1]
    def beaconData = [:]
    beaconData["manufactureID"] = byteArrayInt(data[0..1])
    beaconData["iBeaconType"] = data[2]
    beaconData["Length"] = data[3]
    if(beaconData["Length"] == 21)
    {
        beaconData["UUID"]   = ((byte[])data[4..19]).encodeHex()
        beaconData["Major"]   = byteArrayInt(data[20..21])
        beaconData["Minor"]   = byteArrayInt(data[22..23])
        beaconData["TXPower"]   = data[24]
    }
    
    return beaconData
}

private def parseBleAdverstiment(byte[] data)
{
    
    /*
    uint8_t frameType;
    uint8_t addressType;
    uint8_t address[6];
    uint8_t advertisementTypeMask;
    uint8_t eirDataLength;
    uint8_t eirData[31 * 2];
    int8_t  rssi;*/
    
    if(data[0] != ADRVERTISEMENT_FRAME())
    {
        return null;
    }
    
    updatePresent()
    
    def advMap = [:]
    advMap["addressType"] = data[1]
    advMap["address"] = data[2..7]
    advMap["advertisementTypeMask"] = data[8]
    advMap["rssi"] = data[9]
    advMap["eirData"] = parseBleAdverstimentEIRData(data)
    
    return advMap                      
}

private static String bytesToHex(def bytes) {
    char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    char[] hexChars = new char[bytes.size() * 2];
    for (int j = 0; j < bytes.size(); j++) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
}

private def handleRssi(def dataMap)
{
    int rssi =  dataMap["rssi"]
    if(!state.rssi)
    {
        state.rssi = rssi;
    }
    else
    {
        final double fact = 0.2
        state.rssi =  rssi * fact + state.rssi * (1 - fact)
        rssi = state.rssi.round(0)
    }        
    sendEvent([name:"rssi",value: rssi])
}

private def handleColor(def beaconData)
{
    final def mapColorUUID = ["A495BB10C5B14B44B5121370F02D74DE":"red",
    "A495BB20C5B14B44B5121370F02D74DE":"green",
    "A495BB30C5B14B44B5121370F02D74DE":"black",
    "A495BB40C5B14B44B5121370F02D74DE":"purple",
    "A495BB50C5B14B44B5121370F02D74DE":"orange",
    "A495BB60C5B14B44B5121370F02D74DE":"blue",
    "A495BB70C5B14B44B5121370F02D74DE":"yellow",
    "A495BB80C5B14B44B5121370F02D74DE":"pink"]
    
    String uuid = beaconData["UUID"]
    String color =  mapColorUUID[uuid.toUpperCase()]    
    sendEvent([name:"color",value: color])
}

private def handleTemperature(def beaconData)
{
    def result = [:]
    result.name = "temperature"
    result.value = beaconData["Major"]
    result.unit = "°${location.temperatureScale}"
    
    result.value = convertTemperatureIfNeeded(result.value,"f",1) 
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    sendEvent(result)
}

private def handleGravity(def beaconData)
{
    double d_grav = (double) beaconData["Minor"];
    double d_temp = (double) beaconData["Major"];

    if(enGravityCalib)
    {
        double d_x0 = 0.0
        double d_x1 = 1.0
        double d_x2 = 0.0
        double d_x3 = 0.0
        
        if(x0)
        {
            d_x0 = x0
        }
        
        if(x1)
        {  
            d_x1 = x1
        }
        
        if(x2)
        {
            d_x2 = x2
        }
        
        if(x3)
        {
            d_x3 = x3
        }
        
        d_grav = d_x0 + d_x1 * d_grav + d_x2 * d_grav * d_grav + d_x3 * d_grav * d_grav * d_grav
    }
    
    if(enTemperatureCalib)
    {
        double ref_temp = 60.0;
        d_grav = d_grav * ((1.00130346 - 0.000134722124 * d_temp + 0.00000204052596 * d_temp * d_temp - 0.00000000232820948 * d_temp * d_temp * d_temp) / (1.00130346 - 0.000134722124 * ref_temp + 0.00000204052596 * ref_temp * ref_temp - 0.00000000232820948 * ref_temp * ref_temp * ref_temp));
    }
    
    def result = [:]
    result.name = "gravity"
    result.value = (d_grav/1000.0).round(4)
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value}"
    
    sendEvent(result)
}

def  parse(def data) { 
    
    if(data[0] != ADRVERTISEMENT_FRAME())
    {
        return null
    }
    
    def dataMap = parseBleAdverstiment(data)
    
    def beaconData = parseBeaconData(dataMap["eirData"])
    
    if(!beaconData)
    {
        return null
    }
    
    if(state.beaconData && 
       state.beaconData["Major"] == beaconData["Major"] && 
       state.beaconData["Minor"] == beaconData["Minor"] )
    {
        boolean forceUpdate = true
        if(device.lastActivity)
        {    
            use(groovy.time.TimeCategory)
            {
                def currentDate = new Date()
                def duration = currentDate - device.lastActivity
                forceUpdate = duration.days > 0 || duration.hours > 0 || duration.minutes >= 1
            }
        }
        
        if(!forceUpdate)
        {
            return null
        }
    }
    
    state.beaconData = beaconData
    
    handleRssi(dataMap)
    handleTemperature(beaconData)
    handleGravity(beaconData)    
    handleColor(beaconData)
}

private static byte[] intToByteArray(final int data) {
    byte[] temp = [ 
    (byte)((data >> 0) & 0xff), 
    (byte)((data >> 8) & 0xff),
    (byte)((data >> 16) & 0xff),
    (byte)((data >> 24) & 0xff)]
    return temp;
}

private static  byte[] shortToByteArray(final short data) {
    byte[] temp = [ 
    (byte)((data >> 0) & 0xff), 
    (byte)((data >> 8) & 0xff),]
    return temp;
}

private static  byte[] byteToByteArray(final byte data) {
    byte[] temp = [data,]
    return temp;
}

private String getBTMac()
{
    def DNI = device.deviceNetworkId.split("-",2)
    return DNI[0]
}

def checkActivity()
{    
    boolean runInit = false
    
    if(!device.lastActivity)
    {
        return    
    }
    
    use(groovy.time.TimeCategory)
    {
        def currentDate = new Date()
        def duration = currentDate - device.lastActivity
        runInit = duration.days > 0 || duration.hours > 0 || duration.minutes >= 5 
    }
    
    if(runInit)
    {
        sendBTFilterInitialization()
    }
}

private def sendBTFilterValue(byte filterValue)
{
    byte[] command = byteToByteArray((byte)UPDATE_ADDRESS_FILTER())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()
    byte[] advFilter = byteToByteArray(filterValue) 
    byte[] advFilterLen = byteToByteArray((byte)1) 

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    outputStream.write(command)
    outputStream.write(page)
    outputStream.write((byte[])address[-1..0])
    outputStream.write(advFilterLen)
    outputStream.write(advFilter)

    byte[] temp = outputStream.toByteArray( )
    
    def cmd = []
    cmd += parent.sendToSerialdevice(temp)    
    parent.sendCommandP(cmd)  
}

private def sendBTFilterInitialization()
{
    sendBTFilterValue((byte)0xFF)
}

private def sendBTClearFilter()
{
    sendBTFilterValue((byte)0x00)
}

def updateNotPresent()
{
    sendEvent([name:"presence",value:"not present"])
}

def updatePresent()
{
    sendEvent([name:"presence",value:"present"])
    runIn(120,updateNotPresent)
}

def initialize() 
{
    sendBTFilterInitialization()    
    unschedule()
    def random = new Random()
    cronSched =  "${random.nextInt(60)} */5 * ? * *"
    schedule(cronSched, checkActivity)
}

def configure()
{
    initialize()
}

def configure_child() 
{
    initialize()
}

def installed() 
{
    initialize()
}

void uninstalled()
{
    sendBTClearFilter()
    unschedule()
}