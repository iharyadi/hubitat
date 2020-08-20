metadata {
    definition (name: "Flower care", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Illuminance Measurement"
        capability "Battery"
        capability "PresenceSensor"
        capability "Configuration"
        
        attribute "moisture", "number"
        attribute "fertility", "number"
        
        command "getBattFirmwareInfo"
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
    byteArray.reverse().each{ b -> i = (i << 8) | ((int)b & 0x000000FF) }
    return i
}

private def parseXiaomiBleAdverstimenteirData(def data)
{    
    if(data.size()<18)
    {
        log.error "Bad data ${data}"
        return null   
    }
    
    if(data.size() < 17+data[16])
    {
        log.error "Bad data ${data}"
        return null   
    }
    
    def mapEventConverter = [4:{ x -> return  [[name:"temperature", value:convertTemperatureIfNeeded((float)x /10,"c",1), unit:"°${location.temperatureScale}"]]},
               5:{ x -> return  [[name:"status", value:x]]},
               6:{ x -> return  [[name:"humidity", value:x, unit:"%"]]},
               7:{ x -> return  [[name:"illuminance", value:x]]},
               8:{ x -> return  [[name:"moisture", value:x, unit:"%"]]},
               9:{ x -> return  [[name:"fertility", value:x]]},
               10:{ x -> return [[name:"battery", value:x, unit:"%"]]},
               13:{ x -> return [[name:"temperature", value:convertTemperatureIfNeeded((float)(x&0x0000FFFF)/10.0,"c",1), unit:"°${location.temperatureScale}"],   [name:"humidity", value:(float)((x>>16)&0x0000FFFF)/10.0, unit:"%"]]} ]
    
    int ndx = data[14]  
    def eventConverter = mapEventConverter[ndx]
    if(!eventConverter)
    {
        log.info "Bad data ${data}"
        return null   
    }
    
    eventConverter(byteArrayInt(data[17..(17+data[16]-1)])).each
    {
        sendEvent(it)
    }
    
    return null
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
    
        if(!dataMap["eirData"][22])
        {
           return null;
        }
        
        if(state.lastData)
        {
            if(state.lastData == dataMap["eirData"][22])
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
        }
        
        state.lastData = dataMap["eirData"][22]
        return parseXiaomiBleAdverstimenteirData(dataMap["eirData"][22])
    }
    else if(data[0] == READ_ATTRIBUTE_FRAME())
    {
        if(bytesToHex(data[23..8]).equalsIgnoreCase("00000000000000000000000000001A02") && data[24] == 7)
        {
            sendEvent(name:"battery", value:data[25])
        }
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

def initialize() {
  
  sendBTFilterInitialization()
    
  unschedule()
  def random = new Random()
  String cronSched =  "${random.nextInt(60)} 0 1 ? * * *"
  schedule(cronSched, getBattFirmwareInfo)
  cronSched =  "${random.nextInt(60)} */5 * ? * *"
  schedule(cronSched, checkActivity)
}

private def sendBTReadAttribute(String svc, String chr, byte val)
{   
  byte[] command = byteToByteArray((byte)READ_ATTRIBUTE())  
  byte[] page = byteToByteArray((byte)2)
  byte[] address = getBTMac().decodeHex()
  
  byte[] service = svc.decodeHex()
  byte[] characteristic = chr.decodeHex()
  byte[] valuelen = byteToByteArray((byte)val)
  
  byte[] totalDataLen = byteToByteArray((byte) (service.size()+characteristic.size()+valuelen.size()))
 
  ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
  outputStream.write(command)
  outputStream.write(page)
  outputStream.write((byte[])address[-1..0])
  outputStream.write(totalDataLen)
  outputStream.write((byte[])service[-1..0])
  outputStream.write((byte[])characteristic[-1..0])
  outputStream.write(valuelen)

  byte[] temp = outputStream.toByteArray( )

  return parent.sendToSerialdevice(temp)    
}

private def sendBTReadBattery()
{   
    sendBTReadAttribute("0000120400001000800000805f9b34fb",
                        "00001a0200001000800000805f9b34fb",
                        (byte)7)
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

private def sendBTFilterInitialization()
{
    sendBTFilter((byte)22)
}

private def sendBTClearFilter()
{
    sendBTFilter((byte)0)
}
    
private def sendBTFilter(byte filter)
{   
  byte[] command = byteToByteArray((byte)UPDATE_ADDRESS_FILTER())  
  byte[] page = byteToByteArray((byte)2)
  byte[] address = getBTMac().decodeHex()
  byte[] advFilter = byteToByteArray(filter) 
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

def disconnectBTConnection()
{
    parent.sendCommandP(sendBTDisconnectCommand()) 
}

def getBattFirmwareInfo()
{
    parent.sendCommandP(sendBTReadBattery()) 
    runIn(10,disconnectBTConnection);
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