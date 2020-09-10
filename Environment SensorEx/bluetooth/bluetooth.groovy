import groovy.json.JsonSlurper
import groovy.transform.Field
@Field static java.util.concurrent.Semaphore mutex = new java.util.concurrent.Semaphore(1)
@Field static volatile Integer current_id = 0
@Field static volatile byte [] fragmentReceived = [-1,-1,-1]
@Field static volatile String [][] fragment = [[],[],[]]
 
metadata {
    definition (name: "Bluetooth", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        
        attribute "lastDevFound", "string"
    }    
    command "findNewBTDevice"
    command "findNewBTBeacon"
    command "stopFindNewBTDevice"
}

private short ADRVERTISEMENT_FRAME()
{
    return 0x0
}

private short READ_ATTR_FRAME()
{
    return 0x1
}

private short UPDATE_ADDRESS_FILTER()
{
    return 0x50
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

private boolean isPacketComplete(short totalframe)
{
    int res = 0;
    for (int i = 0; i< totalframe; i++)
    {
        res += fragmentReceived[i]
    }
    
    return res == 0
}

private def mergeFrame(short totalframe)
{
    def res = []
    for (int i = 0; i< totalframe; i++)
    {
        res += fragment[i]
    }
    
    return res
}

private def handlePacketFragmentation(short id, short ndxframe,short totalframe, def data)
{ 
    def packet = null;
    mutex.acquire()
    try{
    
        if(current_id != id)
        {
            current_id = id
            fragmentReceived = [-1,-1,-1]
            fragment = [[],[],[]]
            
        }
        fragmentReceived[ndxframe] = 0
        fragment[ndxframe]= data[5..-1]
        
        if(isPacketComplete(totalframe))
        {
            packet = mergeFrame(totalframe)
        }
    }
    catch(e)
    {
        log.error "Error: ${e}"
        log.error "packet: ${packet}"
        log.error "fragment: ${fragment}"
        log.error "fragmentReceived: ${fragmentReceived}"
        log.error "id: ${id}"
        log.error "ndxframe: ${ndxframe}"
        log.error "data: ${data}"
    }
    mutex.release()
    
    return packet
}

def  parse(def origData) {   
    ArrayList<String> data = new ArrayList<String>(origData);
    
    if(!data[0].equals("00"))
    {
        return null   
    }
    
    /*uint8_t command = 0xFF;
    uint8_t page = 0x02;
    uint8_t id  = 0;                    0
    uint8_t totalPkt = 0;               1
    uint8_t ndxPkt = 0;                 2
    uint8_t buff[25];                   3 */
    
    String devicePageNumber = device.getDataValue("pageNumber")
    
    Integer page = zigbee.convertHexToInt(data[1])
    
    if(devicePageNumber && devicePageNumber.toInteger() != page)
    {
       return null   
    }
    
    String joinedString = String.join("", data[2..4])
    byte[] temp = joinedString.decodeHex() 
        
    if(temp[2] < 0 || temp[2] >= temp[1])
    {
        log.error "bad data $data"
        return null
    }
    
    def packet = handlePacketFragmentation(temp[0],temp[2],temp[1],data)

    if(packet)
    {
        String joinedPacket = ""
        packet.each {
            joinedPacket += String.join("", it)
        }
        return handleChildData(joinedPacket.decodeHex())
    }
                                              
    return null
}

def findAllBluetoothChildren()
{
    def children = parent.getChildDevices()
    
    def btChildren = children.findAll  {
        String[] res = it.properties["data"]["componentName"].split("-",2);
        return res[0] == "Bluetooth"
    }
    
    log.info "child info ${btChildren}" 
}

private def handleAdvertismentData(def data)
{
    def zigbeeAddress = parent.device.getZigbeeId()
    String DNI = bytesToHex(data["address"].reverse())
     
    if(data["eirData"][-1])
    {
        String DTH = null
        if(data["eirData"][-1][0] == 89 && data["eirData"][-1][1] == 0 && data["eirData"][-1][2] == 2)
        {
            DTH = "Beacon"   
        }
        else if (data["eirData"][-1][0] == 76 && data["eirData"][-1][1] == 0 && data["eirData"][-1][2] == 2)
        {
            
            final def mapUUID = ["A495BB10C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB20C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB30C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB40C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB50C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB60C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB70C5B14B44B5121370F02D74DE":"iBeacon",
                                      "A495BB80C5B14B44B5121370F02D74DE":"iBeacon",
                                      "494E54454C4C495F524F434B535F4857":"Govee"]
           
            String uuid = ((byte[]) data["eirData"][-1][4..19]).encodeHex()
            DTH = mapUUID[uuid.toUpperCase()]     
        }
    
        def childDevice = parent.getChildDevice("$DNI-${device.deviceNetworkId}")
        if(!childDevice && DTH)
        {
            try
            {
                childDevice = parent.addChildDevice("iharyadi", 
                           "$DTH", 
                            "$DNI-${device.deviceNetworkId}",
                            [label: "${DTH}-${DNI}",
                            isComponent: false, 
                             componentName: "Bluetooth-${DNI}", 
                             componentLabel: "${device.displayName} ${DTH}-${DNI}"])
            }
            catch(e)
            {
                log.error "${e}"
            }
        
            if(!childDevice)
            {
                return null   
            }
            
            sendEvent(name:"lastDevFound", value:"${device.currentValue("lastDevFound",true)?:""} $DTH-$DNI")
            return childDevice.configure_child()
       }
    }
    else if(data["eirData"][9] || data["eirData"][8] || data["eirData"][6] )
    {
        if(data["eirData"][6])
        {
            String uuid = ((byte[]) data["eirData"][6][-1..0]).encodeHex()
            if(uuid.equalsIgnoreCase("b42e1c08ade711e489d3123b93f75cba"))
            {
                DTH = "Airthings Wave+"
            }
        }
        else
        {
            String DTH = new String((byte[])data["eirData"][9])
            if(!DTH || DTH.isEmpty())
            {
                DTH = new String((byte[])data["eirData"][8])
            }
        }
        
        if(!DTH || DTH.isEmpty())
        {
            return null    
        }
        
        String[] arrOfStr = DTH.split("-", 2);
        DTH = arrOfStr[0]
        log.info "DTH ${DTH}  eridata ${data["eirData"]}"
    
        def childDevice = parent.getChildDevice("$DNI-${device.deviceNetworkId}")
        if(!childDevice && DTH)
        {
            try
            {
                childDevice = parent.addChildDevice("iharyadi", 
                           "$DTH", 
                            "$DNI-${device.deviceNetworkId}",
                            [label: "${DTH}-${DNI}",
                            isComponent: false, 
                             componentName: "Bluetooth-${DNI}", 
                             componentLabel: "${device.displayName} ${DTH}-${DNI}"])
        
            }
            catch(e)
            {
                log.error "${e}"
            }
            
            if(!childDevice)
            {
                return null   
            }
            
            sendEvent(name:"lastDevFound", value:"${device.currentValue("lastDevFound",true)?:""} $DTH-$DNI")
            return childDevice.configure_child()
       }
    }
}

private def handleChildData(def data)
{     
    try
    {
        
        def mac = data[2..7]
        String DNI = bytesToHex(mac.reverse())
        def childDevice = parent.getChildDevice("$DNI-${device.deviceNetworkId}")
        
        if(childDevice)
        {
            
            if(state.discovering)
            {
                return null   
            }
        
            childDevice.parse(data)
        }
        else
        {
            if(!state.discovering)
            {
                return null
            }
        
            if(data[0] != ADRVERTISEMENT_FRAME())
            {
                return null;
            }
        
            handleAdvertismentData(parseBleAdverstiment(data))
        } 
    }
    catch(e)
    {
        log.error "Error: ${e} data: ${data}"
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

def sendBTFilter(byte filterValue)
{
  byte[] command = byteToByteArray((byte)UPDATE_ADDRESS_FILTER())  
  byte[] page = byteToByteArray((byte)2)
  byte[] address = "FFFFFFFFFFFF".decodeHex()
  byte[] advFilter = byteToByteArray((byte)filterValue) 
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

def stopFindNewBTDevice()
{  
    unschedule(stopFindNewBTDevice)
    sendEvent(name:"lastDevFound", value:"Discovery Stopped")
    state.discovering = false
    sendBTFilter((byte)0x00)
}

private List<Integer> searchArray() {
    return [9, 8, 6]
}

def findNewBTDeviceLoop() {
    def index = state.searching++ % searchArray().size()
    sendBTFilter((byte)searchArray()[index])
    if (state.searching < 12) {
        runIn(5,findNewBTDeviceLoop)
    } else {
        runIn(5,stopFindNewBTDevice)
    }
}

def findNewBTDevice()
{   
    state.searching = 0
    sendEvent(name:"lastDevFound", value:" ")
    state.discovering = true
    findNewBTDeviceLoop()
}

def findNewBTBeacon()
{   
    runIn(60,stopFindNewBTDevice)
    sendEvent(name:"lastDevFound", value:" ")
    sendBTFilter((byte)0xFF)
    state.discovering = true
}

def configure_child() {
}

def installed() {
}