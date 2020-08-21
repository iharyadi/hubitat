metadata {
    definition (name: "DS18B20", namespace: "iharyadi", author: "iharyadi") {
        capability "Temperature Measurement"
        capability "Sensor"
    }
}

private float byteArrayToFloat(def byteArray) {
    int intBits = 
      zigbee.convertHexToInt(byteArray[3]) << 24 | (zigbee.convertHexToInt(byteArray[2]) & 0xFF) << 16 | (zigbee.convertHexToInt(byteArray[1]) & 0xFF) << 8 | (zigbee.convertHexToInt(byteArray[0]) & 0xFF);
    return Float.intBitsToFloat(intBits);  
}

def parse(def data) 
{ 
	def event;
        
    String devicePageNumber = device.getDataValue("pageNumber")
    
    Integer page = zigbee.convertHexToInt(data[1])
    Integer command = zigbee.convertHexToInt(data[0])
    
    if(devicePageNumber.toInteger() != page)
    {
       return null   
    }
    
    float dispValue = (float) byteArrayToFloat(data[2..5]).round(2);
    
    def result = [:]
    result.name = "temperature"
    result.unit = "°${location.temperatureScale}"
    
    result.value = convertTemperatureIfNeeded(dispValue,"c",1) 
    result.descriptionText = "${device.displayName} ${result.name} is ${result.value} ${result.unit}"
    
    return createEvent(result);
}

def configure_child() {
}

def installed() {
}
