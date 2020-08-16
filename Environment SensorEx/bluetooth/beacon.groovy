metadata {
    definition (name: "Beacon", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        capability "SignalStrength"
        capability "PresenceSensor"
        capability "Configuration"
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
        return;   
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

def  parse(def data) { 
    if(data[0] == ADRVERTISEMENT_FRAME())
    {
        updatePresent()
        def dataMap = parseBleAdverstiment(data)
    
        if(!dataMap["eirData"][-1])
        {
           return null;
        }
        
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
            
        return sendEvent([name:"rssi",value: rssi])
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

def configure_child() {
    initialize()
}

def installed() {
    initialize()
}

void uninstalled()
{
    sendBTClearFilter()
    unschedule()
}