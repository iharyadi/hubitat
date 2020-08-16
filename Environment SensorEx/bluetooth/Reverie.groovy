metadata {
    definition (name: "Reverie", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        capability "Switch"
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
    return 0x08
}

private short DISCONNECT_COMMAND()
{
    return 0x03
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

private long byteArrayInt(def byteArray) {
    long i = 0
    byteArray.reverse().eachWithIndex { b, ix -> i = i << (8*ix) | ((int)b & 0x000000FF) }
    return i
}

def  parse(def data) { 
    
    if(data[0] == READ_ATTRIBUTE_FRAME())
    {
  
    }
    else if(data[0] == WRITE_ATTRIBUTE_FRAME())
    {       
        if(bytesToHex(data[23..8]).equalsIgnoreCase("6af87926dc79412ea3e05f85c2d55de2"))
        {
            if(data[24] != 0)
            {
                disconnectBT()
                if(state.last_command == 0x00551144)
                {
                    state.command_retry = 3;
                    sendEvent([name:"switch",value:"on"])
                }
                else if( state.last_command == 0x00550550)
                {
                    state.command_retry = 3;
                    sendEvent([name:"switch",value:"off"])
                }
            }
            else
            {
                if(state.command_retry < 3)
                {
                    sendBTCommandHelper(state.last_command)
                }
                state.command_retry = state.command_retry + 1
            }
        }
     
    }
    else if(data[0] == NOTIFY_ATTRIBUTE_FRAME())
    {
    }
    else if(data[0]  ==  DISCONNECTED_FRAME())
    {
    }   
    return null;
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

def sendBTCommand(int val)
{
    
  /*uint8_t command = 0xFF;
  uint8_t page = 0x02;
  uint8_t addr[6];

  uint8_t service[BLE_UUID_MAX_LENGTH];
  uint8_t characteristic[BLE_UUID_MAX_LENGTH];
  uint8_t valuelen;
  uint8_t value[];*/
   
  byte[] command = byteToByteArray((byte)WRITE_ATTRIBUTE())  
  byte[] page = byteToByteArray((byte)2)
  byte[] address = getBTMac().decodeHex()
  
  byte[] service = "1B1D9641B9424DA889CC98E6A58FBD93".decodeHex()
  byte[] characteristic = "6af87926dc79412ea3e05f85c2d55de2".decodeHex()
  byte[] valuelen = byteToByteArray((byte)4)
  byte[] value  = intToByteArray(val)
  
  byte[] totalDataLen = byteToByteArray((byte) (service.size()+characteristic.size()+valuelen.size()+value.size()))
 
  ByteArrayOutputStream outputStream = new ByteArrayOutputStream( )
  outputStream.write(command)
  outputStream.write(page)
  outputStream.write((byte[])address[-1..0])
  outputStream.write(totalDataLen)
  outputStream.write((byte[])service[-1..0])
  outputStream.write((byte[])characteristic[-1..0])
  outputStream.write(valuelen)
  outputStream.write((byte[])value[-1..0])

  byte[] temp = outputStream.toByteArray( )

  return parent.sendToSerialdevice(temp)  
}

def sendBTDisconnectCommand()
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

def disconnectBT()
{
    parent.sendCommandP(sendBTDisconnectCommand())
}

private def sendBTCommandHelper(int val)
{
    state.command_retry = 0
    state.last_command = val
    parent.sendCommandP(sendBTCommand(val))
}

def on()
{
    sendBTCommandHelper(0x00551144)
}

def off()
{
    sendBTCommandHelper(0x00550550)
}

def configure_child() {
}

def installed() {
    state.command_retry = 0
    state.last_command = 0
}