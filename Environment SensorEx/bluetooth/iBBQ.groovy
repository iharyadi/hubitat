import java.text.SimpleDateFormat;  
import java.util.Date;  

metadata {
    definition (name: "iBBQ", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        capability "Battery"
        capability "Switch"
        capability "PresenceSensor"
        capability "Configuration"        
    }   
    
    command "setTempUnitCelsius"
    command "setTempUnitFarenheit"
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
    return 0x07
}

private short WRITE_ATTRIBUTE()
{
    return 0x06
}

private short NOTIFY_ATTRIBUTE()
{
    return 0x08
}

private short DISCONNECT_COMMAND()
{
    return 0x03
}

private int LOGIN_TIMEOUT()
{
    return 60    
}

private long byteArrayInt(def byteArray) {
    long i = 0
    byteArray.each{ b -> i = (i << 8) | ((int)b & 0x000000FF) }
    return i
}

def handleWriteResponse(def data)
{   
    def cmds = []
    if(state.connection == 1)
    {
        if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF2"))
        { 
            if(data[24] == 1 && data[25] == 15)
            {
                state.connection = state.connection + 1
                parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF5, "0B0100000000"))
            }
            else if(data[24] == 0)
            {
                if(state.connection_retry != null)
                {
                    if (state.connection_retry <3)
                    {
                        runIn(LOGIN_TIMEOUT(), setSwitchOff)
                        parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF2, "2107060504030201b8220000000000"))
                    }
                    state.connection_retry = state.connection_retry + 1
                }
            }
        }
    }
    else if(state.connection == 2)
    {
        if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF5") && data[24] == 1 && data[25] == 6)
        { 
            state.connection = state.connection + 1
            parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF5, 
                                                     location.temperatureScale == "F" ? "020100000000":"020000000000"))
        }
    }
    else if(state.connection == 3)        
    {
        if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF5") && data[24] == 1 && data[25] == 6)
        { 
            state.connection = state.connection + 1
            parent.sendCommandP(sendBTNotifyAttribute((short)0xFFF0, (short)0xFFF1))
        }
    }
}

def handleReadResponse(def data)
{
    if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF4"))
    { 
        for(int i = 0; i < data[24]/2; i++)
        {
            def childDevice = getChildDevice("$i-${device.deviceNetworkId}")
            if(!childDevice)
            {
                childDevice = addChildDevice("hubitat", 
                "Virtual Temperature Sensor", 
                "$i-${device.deviceNetworkId}",
                [label: "${device.deviceNetworkId} Probe $i",
                    isComponent: true, 
                    componentName: "$i-${device.deviceNetworkId}", 
                    componentLabel: "${device.deviceNetworkId} Probe $i"])
            }
            
            if(childDevice)
            {
                def result = [:]
                result.name = "temperature"
                result.value = (float) byteArrayInt(data[(25+(2*i+1))..(25+(2*i))]) / 10
                result.unit = "°${location.temperatureScale}"
                
                result.value = convertTemperatureIfNeeded(result.value,"c",1) 
                result.descriptionText = "${childDevice.displayName} ${result.name} is ${result.value} ${result.unit}"
                childDevice.sendEvent(result)
            }
        }
    }
    else if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF1"))
    {
        if(data[24] == 6 && data[25]==36)
        {
            def result = [:]
            result.name = "battery"
            result.value = ((float)byteArrayInt(data[27..26])*100/byteArrayInt(data[29..28])).round(0)
            result.unit = "%"
            
            result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
            
            sendEvent(result)
        }
    }
}

def handleSubscibeNotifyResponse(def data)
{
    if(state.connection == 4)        
    {
        if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF1") && data[24] == 1 && data[25] !=0)
        { 
           state.connection = state.connection + 1
           parent.sendCommandP(sendBTNotifyAttribute((short)0xFFF0, (short)0xFFF4))   
        }
    }
    else if(state.connection == 5)        
    {
        if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFF4") && data[24] == 1 && data[25] != 0)
        {
            unschedule(setSwitchOff)
            sendEvent([name: "switch", value: "on"])
        }
    }
}

def parse(def data) { 
    updatePresent()
    
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
       unschedule(setSwitchOff)
       setSwitchOff()
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
    unschedule()
    runEvery5Minutes(getBatteryLevel)
    runEvery5Minutes(checkActivity)
    sendBTFilterInitialization()
    state.connection = 0
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
    sendBTFilter((byte)9)
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

private def sendBTReadAttribute(short svc, short chr, byte size)
{   
    byte[] command = byteToByteArray((byte)READ_ATTRIBUTE())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()

    byte[] service = shortToByteArray(svc)
    byte[] characteristic = shortToByteArray(chr)
    byte[] valuelen = byteToByteArray(size)

    byte[] totalDataLen = byteToByteArray((byte) (service.size()+characteristic.size()+valuelen.size()))

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    outputStream.write(command)
    outputStream.write(page)
    outputStream.write((byte[])address[-1..0])
    outputStream.write(totalDataLen)
    outputStream.write(service)
    outputStream.write(characteristic)
    outputStream.write(valuelen)

    byte[] temp = outputStream.toByteArray( )

    return parent.sendToSerialdevice(temp)    
}

private def sendBTNotifyAttribute(short svc, short chr)
{   
    byte[] command = byteToByteArray((byte)NOTIFY_ATTRIBUTE())  
    byte[] page = byteToByteArray((byte)2)
    byte[] address = getBTMac().decodeHex()

    byte[] service = shortToByteArray(svc)
    byte[] characteristic = shortToByteArray(chr)
    byte[] valuelen = byteToByteArray((byte)0)

    byte[] totalDataLen = byteToByteArray((byte) (service.size()+characteristic.size()+valuelen.size()))

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    outputStream.write(command)
    outputStream.write(page)
    outputStream.write((byte[])address[-1..0])
    outputStream.write(totalDataLen)
    outputStream.write(service)
    outputStream.write(characteristic)
    outputStream.write(valuelen)

    byte[] temp = outputStream.toByteArray( )

    return parent.sendToSerialdevice(temp)    
}

def disconnectBTConnection()
{
    parent.sendCommandP(sendBTDisconnectCommand()) 
}

def setSwitchOff()
{
    if(state.connection != 1 || state.connection_retry >=3)
    {
        state.connection = 0
    }
    sendEvent([name: "switch", value: "off"])
}

def login()
{
    if(device.currentValue("switch") == null || device.currentValue("switch") == "off")
    {   
        if(state.connection == null || state.connection == 0)
        {
            state.connection = 1
            state.connection_retry = 0
            parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF2, "2107060504030201b8220000000000"))
        }
        runIn(LOGIN_TIMEOUT(), setSwitchOff)
    }
}

def subscribeRealTime()
{
    parent.sendCommandP(sendBTNotifyAttribute((short)0xFFF0, (short)0xFFF4))
}

def subscribeSettingData()
{
    parent.sendCommandP(sendBTNotifyAttribute((short)0xFFF0, (short)0xFFF1))
}

def enableRealTime()
{
    parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF5, "0B0100000000"))
}

def setTempUnitFarenheit()
{
    parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF5, "020100000000"))
}

def setTempUnitCelsius()
{
    parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF5, "020000000000"))
}

def getBatteryLevel()
{
    if(device.currentValue("switch") == "on")
    {
        parent.sendCommandP(sendBTWriteAttribute((short)0xFFF0, (short)0xFFF5, "082400000000"))
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

def on()
{
    login()
}

def off()
{
    state.connection = 0
    disconnectBTConnection()
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