import groovy.json.JsonSlurper
 
metadata {
    definition (name: "ExampleSerial", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
    }
    attribute "ascii", "STRING"    
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
    
    joinedString = String.join("", data[4..-1])
    
    log.info "string $joinedString"
    
    Byte[] bytes = joinedString.decodeHex()
    
    String ascii = new String(bytes);
    
    log.info "$ascii"
    
    return createEvent(name:"ascii", value:ascii);
}

def configure_child() {
}

def installed() {
}
