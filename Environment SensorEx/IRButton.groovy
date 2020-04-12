import groovy.json.JsonSlurper
 
metadata {
    definition (name: "IRButton", namespace: "iharyadi", author: "iharyadi") {
        capability "PushableButton"
        capability "Sensor"
    }
    
    command "SetButtonList", ["STRING"]
    command "TransmitIRTest"
    command "TransmitUnknownIRTest"
}

def parse(def data) { 
    
    log.info "data $data"
    if(!data[0].equals("00"))
    {
        return null   
    }
    
    String devicePageNumber = device.getDataValue("pageNumber")
    
    Integer page = zigbee.convertHexToInt(data[1])
    
    if(devicePageNumber.toInteger() != page)
    {
       return null   
    }
    
    String joinedString = String.join("", data[3..2]);        
    short decodeType = (short) Long.parseLong(joinedString, 16);
    
    log.info "decodeType $decodeType"
    
    if(decodeType == -1)
    {
        short len = (short) Integer.parseInt(data[4],16)
        joinedString = String.join("", data[5..5+len-1]);
        log.info "unknown remote data: $joinedString" 
        return
    }
    
    joinedString = String.join("", data[5..4]);
    Long address = Long.parseLong(joinedString, 16);
    
    log.info "address $address"
    
    joinedString = String.join("", data[9..6]);
    Long value = Long.parseLong(joinedString, 16);
    
    log.info "value $value"
    
    joinedString = String.join("", data[11..10]);
    log.info "bits $joinedString"
    Long bits = Long.parseLong(joinedString, 16);
    
    log.info "bits $bits"
    
    def jsonSlurper = new JsonSlurper()
    def butttonlistconfig = jsonSlurper.parseText(state.SetButtonList)
    
    def button = butttonlistconfig.find {
        (it.decodeType == decodeType &&
            it.address == address &&
            it.value == value &&
            it.bits == bits)
    }
    
    if(button == null)
    {
        return null;   
    }
    
    return createEvent(name: "pushed", value: button.number, displayed: false, isStateChange:true)
}

def configure_child() {
}

def installed() {
}

def SetButtonList(buttons){
	state.SetButtonList = buttons
    
    def jsonSlurper = new JsonSlurper()
    def butttonlistconfig = jsonSlurper.parseText(state.SetButtonList)
    
    int buttoncount = 0
    if(butttonlistconfig)
    {
        buttoncount = butttonlistconfig.size()
    }
    
    sendEvent(name:"numberOfButtons", value:buttoncount)	
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

def TransmitIRTest(){
    byte[] page = byteToByteArray((byte)0)
    byte[] decodeType = shortToByteArray((short)7)   
    byte[] address = shortToByteArray((short)0)
    byte[] value = intToByteArray((int)3772793023)
    byte[] bits = shortToByteArray((short)32)
    byte[] repeat = byteToByteArray((byte)0)
    
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    
    outputStream.write(page)
    outputStream.write(decodeType)
    outputStream.write(address)
    outputStream.write(value)
    outputStream.write(bits)
    outputStream.write(repeat)    
    
    byte [] temp = outputStream.toByteArray( )
    
    def cmd = []
    cmd += parent.sendToSerialdevice(temp)    
    parent.sendCommandP(cmd)
}

def TransmitUnknownIRTest()
{
    byte[] page = byteToByteArray((byte)0)
    byte[] decodeType = shortToByteArray((short)-1)
    byte[] rawData = "18081808081818081808081808180818081808180818188C".decodeHex()
    byte[] len =  byteToByteArray((byte)rawData.size())
    byte[] repeat =  byteToByteArray((byte)5)
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
    outputStream.write(page)
    outputStream.write(decodeType)
    outputStream.write(repeat)
    outputStream.write(len)
    outputStream.write(rawData)
    
    byte[] temp = outputStream.toByteArray( )
    
    log.info "temp $temp"
    
    def cmd = []
    cmd += parent.sendToSerialdevice(temp)    
    parent.sendCommandP(cmd) 
}