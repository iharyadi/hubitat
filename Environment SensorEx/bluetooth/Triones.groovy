metadata {
    definition (name: "Triones", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
        capability "Switch"
        capability "Light"
    }    
    
    command "sendcolor", ["INTEGER", "INTEGER", "INTEGER"]
    command "sendBuiltinMode", ["INTEGER", "INTEGER"]
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

def attemptRetry()
{
    if(state.command_retry < 3)
    {
        parent.sendCommandP(sendBTCommand((byte[])state.last_command))
    }
    state.command_retry = state.command_retry + 1
}

def  parse(def data) {
    if(data[0] == READ_ATTRIBUTE_FRAME())
    {
  
    }
    else if(data[0] == WRITE_ATTRIBUTE_FRAME())
    {       
        if(bytesToHex(data[23..8]).equalsIgnoreCase("0000000000000000000000000000FFD9"))
        {
            if(data[24] != 0)
            {
                disconnectBT()
                if(state.last_command == (byte[]) [0xCC,0x23,0x33])
                {
                     state.command_retry =  3
                     sendEvent([name:"switch",value:"on"])
                }
                else if( state.last_command == (byte[]) [0xCC,0x24,0x33])
                {
                    state.command_retry =  3
                    sendEvent([name:"switch",value:"off"])
                }
            }
            else
            {
                attemptRetry()
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

def sendBTCommand(byte[] value)
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
  
  byte[] service = "0000ffd500001000800000805f9b34fb".decodeHex()
  byte[] characteristic = "0000ffd900001000800000805f9b34fb".decodeHex()
  byte[] valuelen = byteToByteArray((byte) value.size())
  
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

def sendCommandHelper(byte[] value)
{
    state.command_retry = 0
    state.last_command = value
    parent.sendCommandP(sendBTCommand(value))
}

def on()
{
    sendCommandHelper((byte[])[0xCC,0x23,0x33])
}

def off()
{
     sendCommandHelper((byte[])[0xCC,0x24,0x33])
}

def sendcolor(String red, String green, String blue)
{
    sendcolor(Integer.parseInt(red),
              Integer.parseInt(green),
              Integer.parseInt(blue))
}

def sendcolor(int red, int green, int blue)
{  
    sendCommandHelper((byte[])[0x56,(byte)red,(byte)green,(byte)blue,0x00,0xF0,0xAA])
}

def sendBuiltinMode(String mode, String speed)
{
    sendBuiltinMode(Integer.parseInt(mode),
              Integer.parseInt(speed))
}

def sendBuiltinMode(int mode, int speed)
{
    sendCommandHelper((byte[])[0xBB,(byte)mode,(byte)speed,0x44])
}

def configure_child() {
}

def installed() {
    state.command_retry = 0
    state.last_command = []
}