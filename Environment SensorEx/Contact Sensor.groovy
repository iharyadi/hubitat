metadata {
    definition (name: "Contact Sensor", namespace: "iharyadi", author: "iharyadi") {
        capability "ContactSensor"
        capability "Sensor"
    }
    
    // simulator metadata
    simulator {
    }
        
    
    preferences {    
    }
}

def parse(String description) {  
    if(!description?.startsWith("read attr - raw:"))
    {
        return null;
    }
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    
    def value = descMap.attrInt?.equals(0x0055) ? 
        descMap.value : 
    	descMap.additionalAttrs?.find { item -> item.attrInt?.equals(0x0055)}.value
    
    if(!value)
    {
        return null
    }
           
    return createEvent(name:"contact", value:(zigbee.convertHexToInt(value)>0)?"open":"close")
}

def installed() { 
}

def configure_child()
{
	def cmds = []
	cmds = cmds + parent.writeAttribute(0x000F, 0x0101, DataType.UINT32, 25)
  cmds = cmds + parent.configureReporting(0x000F, 0x0055, DataType.BOOLEAN, 0, 30,1)
	return cmds
}
