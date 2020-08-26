import java.text.SimpleDateFormat;  
import java.util.Date;  
import java.lang.math.*

metadata {
    definition (name: "Airthings Wave+", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
        capability "PressureMeasurement"
        capability "CarbonDioxideMeasurement"
        capability "IlluminanceMeasurement"
        capability "PresenceSensor"
        capability "Battery"
        capability "Configuration"        
    }   
    
    attribute "radon", "number"
    attribute "radon-lt", "number"
    attribute "voc", "number"
}

private String AIR_SENSOR_CHAR()
{
    return "b42e2a68ade711e489d3123b93f75cba"    
}

private String AIR_SENSOR_SVC()
{
    return "b42e1c08ade711e489d3123b93f75cba"   
}

private byte AIR_SENSOR_DATA_SIZE()
{
    return 20    
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

private short NOTIFY_ATTRIBUTE_FRAME()
{
    return 0x03
}

private short DISCONNECTED_FRAME()
{
    return 0x04
}

private short UPDATE_ADDRESS_FILTER()
{
    return 0x50   
}

private short READ_ATTRIBUTE()
{
    return 0x02
}

private short WRITE_ATTRIBUTE()
{
    return 0x01
}

private short NOTIFY_ATTRIBUTE()
{
    return 0x03
}

private short DISCONNECT_COMMAND()
{
    return 0x03
}

private long byteArrayInt(def byteArray) {
    long i = 0
    byteArray.each{ b -> i = (i << 8) | ((int)b & 0x000000FF) }
    return i
}

def handleWriteResponse(def data)
{   
}

def handleReadResponse(def data)
{
    int dataSizeNDX = 24
    
    if(bytesToHex(data[23..8]).equalsIgnoreCase(AIR_SENSOR_CHAR()))
    { 
        if(data[dataSizeNDX] != AIR_SENSOR_DATA_SIZE())
        {
            attemptRetryRead()
            return null  
        }
        
        def sensorData = data[25..-1]
        if(sensorData[0] != 1)
        {
            return null  
        }
        
        state.connection_retry = 3
        disconnectBTConnection()
        unschedule(readAirthingsSensorsTimeout)
        //0  1  2  3 4  5  6  7  8  9  0  1
        //B  B  B  B H  H  H  H  H  H  H  H
        //0  1  2  3 45 67 89 01 23 45 67 89
        
        sendEvent([name:"humidity",unit:"%",value:(sensorData[1]*0.5)])
        sendEvent([name:"illuminance",unit:"lux",value:sensorData[2]])
        sendEvent([name:"radon",unit:"Bq/m3",value:byteArrayInt(sensorData[5..4])])
        sendEvent([name:"radon-lt",unit:"Bq/m3",value:byteArrayInt(sensorData[7..6])])
        sendEvent([name:"temperature",unit:"°${location.temperatureScale}",value:convertTemperatureIfNeeded(byteArrayInt(sensorData[9..8])*0.01,"C",1)])
        sendEvent([name:"pressure",unit:"mBar",value:byteArrayInt(sensorData[11..10])*0.02])
        sendEvent([name:"carbonDioxide",unit:"ppm",value:byteArrayInt(sensorData[13..12])])
        sendEvent([name:"voc",unit:"ppb",value:byteArrayInt(sensorData[15..14])])
      
        /*
        d[  'waves'] = data[ 3]      # Seems to count recent waves.
        d[   'mode'] = data[10]      # Usually 0; 1 = recent waves? 2 = pairing taps?
        d[     'x3'] = data[11]      # Maybe signal strength?  Free memory?  Or...???
        */         
     }
}

def attemptRetryRead()
{
    if(state.connection_retry < 3)
    {
        parent.sendCommandP(sendBTReadAttribute(AIR_SENSOR_SVC(),
                        AIR_SENSOR_CHAR(),
                        AIR_SENSOR_DATA_SIZE()))
        runIn(120,readAirthingsSensorsTimeout)
    }
    state.connection_retry = state.connection_retry + 1
}

def handleSubscibeNotifyResponse(def data)
{
    
}

def parse(def data) { 
    
    if(data[0] == ADRVERTISEMENT_FRAME())
    {    
        //check whether poll was just performed less than 5 min ago. 
        boolean allowRead = true
        if(device.lastActivity)
        {
            use(groovy.time.TimeCategory)
            {
                def currentDate = new Date()
                def duration = currentDate - device.lastActivity
                allowRead = duration.days > 0 || duration.hours > 0 || duration.minutes >= 5 
            }
        }
            
        if(state.connection_retry >= 3 && allowRead)
        {
            // this should not be called
            //if we are in the middle of polling
            //or we just poll less than 5 min ago
            readAirthingsSensors()
        }
        
        updatePresent()
    }
    if(data[0] == READ_ATTRIBUTE_FRAME())
    {
        handleReadResponse(data)
    }
    else if(data[0] == WRITE_ATTRIBUTE_FRAME())
    {        
        handleWriteResponse(data)
    }
    else if(data[0] == NOTIFY_ATTRIBUTE_FRAME())
    {
       handleSubscibeNotifyResponse(data)
    }
    else if(data[0]  ==  DISCONNECTED_FRAME())
    {
    }   
    
    return null
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

def checkActivity()
{    
    boolean runInit = true
    
    if(device.lastActivity)
    {
    
        use(groovy.time.TimeCategory)
        {
            def currentDate = new Date()
            def duration = currentDate - device.lastActivity
            runInit = duration.days > 0 || duration.hours > 0 || duration.minutes >= 5 
        }
    }
    
    if(runInit)
    {
        sendBTFilterInitialization()
    }
}

def updateNotPresent()
{
    sendEvent([name:"presence",value:"not present"])
    sendEvent([name: "switch", value: "off"])
}

def updatePresent()
{
    sendEvent([name:"presence",value:"present"])
    runIn(120,updateNotPresent)
}

def readAirthingsSensorsTimeout()
{
    attemptRetryRead()
}

def readAirthingsSensors()
{
    state.connection_retry = 0
    parent.sendCommandP(sendBTReadAttribute(AIR_SENSOR_SVC(),
                        AIR_SENSOR_CHAR(),
                        AIR_SENSOR_DATA_SIZE()))
    runIn(120,readAirthingsSensorsTimeout)
}

def initialize() { 
    state.connection_retry = 3
    unschedule()
    runEvery5Minutes(checkActivity)
    runEvery5Minutes(readAirthingsSensors)
    sendBTFilterInitialization()
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

private def sendBTFilterInitialization()
{
    sendBTFilter((byte)0xFF)
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

private def sendBTWriteAttribute(String srv, String chr, String val)
{
    byte[] command = byteToByteArray((byte)WRITE_ATTRIBUTE())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()

    byte[] service = svc.decodeHex()
    byte[] characteristic = chr.decodeHex()
    byte[] value  = val.decodeHex()
    byte[] valuelen = byteToByteArray((byte)value.size())

    byte[] totalDataLen = byteToByteArray((byte) (service.size()+characteristic.size()+valuelen.size()+value.size()))

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    outputStream.write(command)
    outputStream.write(page)
    outputStream.write((byte[])address[-1..0])
    outputStream.write(totalDataLen)
    outputStream.write((byte[])service[-1..0])
    outputStream.write((byte[])characteristic[-1..0])
    outputStream.write(valuelen)
    outputStream.write(value)

    byte[] temp = outputStream.toByteArray( )

    return parent.sendToSerialdevice(temp)    
}

private def sendBTReadAttribute(String srv, String chr, byte size)
{   
    byte[] command = byteToByteArray((byte)READ_ATTRIBUTE())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()

    byte[] service = svc.decodeHex()
    byte[] characteristic = chr.decodeHex()
    byte[] valuelen = byteToByteArray(size)

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

private def sendBTNotifyAttribute(String srv, String chr)
{   
    byte[] command = byteToByteArray((byte)NOTIFY_ATTRIBUTE())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()

    byte[] service = srv.decodeHex()
    byte[] characteristic = chr.decodeHex()
    byte[] valuelen = byteToByteArray((byte)0)

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

def disconnectBTConnection()
{
    parent.sendCommandP(sendBTDisconnectCommand()) 
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
    disconnectBTConnection()
    sendBTClearFilter()
    unschedule()
}