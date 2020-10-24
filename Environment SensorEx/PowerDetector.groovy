metadata {
    definition (name: "Power Detector", namespace: "iharyadi", author: "iharyadi") {
        capability "PowerSource"
        capability "Sensor"
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
           
    return createEvent(name:"powerSource", value:(zigbee.convertHexToInt(value)>0)?"dc":"battery")
}

def installed() { 
}

def configure_child()
{
    sendEvent(name:"powerSource", value:"unknown")
	def cmds = []
	cmds = cmds + parent.writeAttribute(0x000F, 0x0101, DataType.UINT32, 25)
    cmds = cmds + parent.configureReporting(0x000F, 0x0055, DataType.BOOLEAN, 0, 30,1)
	return cmds
}