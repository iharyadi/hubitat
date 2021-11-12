import groovy.transform.Field
@Field static HashMap mapBeacon = [:]

metadata {
    definition (name: "ATC_Thermometer", namespace: "iharyadi", author: "iharyadi") {
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "ContactSensor"
        capability "SignalStrength"
        capability "Battery"
        capability "PresenceSensor"
        capability "Configuration"        
    } 
    
    command "setTempUnitCelsius"
    command "setTempUnitFarenheit"
    
    preferences {
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

private short WRITE_ATTRIBUTE_FRAME()
{
    return 0x02
}

private short UPDATE_ADDRESS_FILTER()
{
    return 0x50
}

private short READ_ATTRIBUTE()
{
    return 0x07
}

private short WRITE_ATTRIBUTE()
{
    return 0x06
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
    if(eirMap[0x16] == null)
    {
        return null;   
    }
    
    if(eirMap[0x16].size != 15 && eirMap[0x16].size != 17)
    {
        return null;
    }
       
    byte [] data = eirMap[0x16]
    def beaconData = [:]
    
    if(eirMap[0x16].size == 15)
    {
        beaconData["temperature"] = (float)byteArrayInt(data[8..9])/10
        beaconData["humidity"] = data[10]
        beaconData["battery"] = data[11]
        beaconData["batteryMV"] = byteArrayInt(data[12..13])
        beaconData["counter"] =  data[14]
    }
    else if(eirMap[0x16].size == 17)
    {
        beaconData["temperature"] = (float)byteArrayInt(data[9..8])/100
        beaconData["humidity"] = (float)byteArrayInt(data[11..10])/100
        beaconData["batteryMV"] = byteArrayInt(data[13..12])
        beaconData["battery"] = data[14]
        beaconData["counter"] =  data[15]
        beaconData["flag"] =  data[16]
    }
    
    return beaconData
}

private def parseBleAdverstiment(byte[] data)
{
    
    if(data[0] != ADRVERTISEMENT_FRAME())
    {
        return null;
    }
    
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

private def handleTemperature(def beaconData)
{
    def result = [:]
    result.name = "temperature"
    result.value = beaconData["temperature"]
    result.unit = "°${location.temperatureScale}"
    
    result.value = convertTemperatureIfNeeded(result.value,"c",1) 
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    sendEvent(result)
}

private def handleHumidity(def beaconData)
{
    def result = [:]
    result.name = "humidity"
    result.value = beaconData["humidity"]
    result.unit = "%"
    
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    sendEvent(result)
}

private def handleBattery(def beaconData)
{
    def result = [:]
    result.name = "battery"
    result.value = beaconData["battery"]
    result.unit = "%"
    
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    sendEvent(result)
}

private def handlegpio(def beaconData)
{
    def result = [:]
    result.name = "contact"
    
    def conStatus = "unknown" 
    if(beaconData["flag"] != null)
    {
        conStatus  =  beaconData["flag"] & 0x01 ? "open":"closed"
    }
    result.value = conStatus
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    sendEvent(result)
}

def  parse(def data) { 
    
    if(data[0] == ADRVERTISEMENT_FRAME())
    { 
        updatePresent()
    
        def dataMap = parseBleAdverstiment(data)
           
        def beaconData = parseBeaconData(dataMap["eirData"])
    
        if(!beaconData)
        {
            return null
        }
    
        def deviceBeaconData = mapBeacon[device.deviceNetworkId]
        if(deviceBeaconData && 
           Math.abs(deviceBeaconData["temperature"] - beaconData["temperature"]) < 0.5 && 
           Math.abs(deviceBeaconData["humidity"] -  beaconData["humidity"]) < 0.5  &&
           deviceBeaconData["battery"] == beaconData["battery"] &&
           (beaconData["flag"] == null || deviceBeaconData["flag"] == beaconData["flag"]))
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
    
        mapBeacon[device.deviceNetworkId] = beaconData
    
        handleRssi(dataMap)
        handleTemperature(beaconData)
        handleHumidity(beaconData)
        handleBattery(beaconData)  
        handlegpio(beaconData)
    }
    else if (data[0] == WRITE_ATTRIBUTE_FRAME())
    {
        disconnectBTConnection()
    }
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

private def sendBTWriteAttribute(short svc, short chr, String val)
{
    byte[] command = byteToByteArray((byte)WRITE_ATTRIBUTE())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()

    byte[] service = shortToByteArray(svc)
    byte[] characteristic = shortToByteArray(chr)
    byte[] value  = val.decodeHex()
    byte[] valuelen = byteToByteArray((byte)value.size())

    byte[] totalDataLen = byteToByteArray((byte) (service.size()+characteristic.size()+valuelen.size()+value.size()))

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    outputStream.write(command)
    outputStream.write(page)
    outputStream.write((byte[])address[-1..0])
    outputStream.write(totalDataLen)
    outputStream.write(service)
    outputStream.write(characteristic)
    outputStream.write(valuelen)
    outputStream.write(value)

    byte[] temp = outputStream.toByteArray( )

    return parent.sendToSerialdevice(temp)    
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
    sendBTFilterValue((byte)0x16)
}

private def sendBTClearFilter()
{
    sendBTFilterValue((byte)0x00)
}

private def sendBTDisconnectCommand()
{   
  byte[] command = byteToByteArray((byte)DISCONNECT_COMMAND())  
  byte[] page = byteToByteArray((byte)2)
  byte[] address = getBTMac().decodeHex()
  
  byte[] totalDataLen = byteToByteArray((byte)0)
 
  ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
  outputStream.write(command)
  outputStream.write(page)
  outputStream.write((byte[])address[-1..0])
  outputStream.write(totalDataLen)
    
  byte[] temp = outputStream.toByteArray( )

  return parent.sendToSerialdevice(temp)    
}

def disconnectBTConnection()
{
    parent.sendCommandP(sendBTDisconnectCommand()) 
}

def updateNotPresent()
{
    sendEvent([name:"presence",value:"not present"])
}

def updatePresent()
{
    if(device.currentValue("presence") != "present")
    {
        sendEvent([name:"presence",value:"present"])
    }
    runIn(120,updateNotPresent)
}


def setTempUnitFarenheit()
{
    parent.sendCommandP(sendBTWriteAttribute((short)0x1F10, (short)0x1F1F, "FF"))
}

def setTempUnitCelsius()
{
    parent.sendCommandP(sendBTWriteAttribute((short)0x1F10, (short)0x1F1F, "CC"))
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
    mapBeacon.remove(device.deviceNetworkId)
    sendBTClearFilter()
    unschedule()
}
