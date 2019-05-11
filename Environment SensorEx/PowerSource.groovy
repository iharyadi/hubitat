metadata {
    definition (name: "PowerDetector", namespace: "iharyadi", author: "iharyadi") {
        capability "PowerSource"
        capability "Sensor"
    }
}

def parse(String description) { 

	def event;
    
    if(!description?.startsWith("read attr - raw:"))
    {
        return event;
    }
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    
    def adc;
    def vdd;

	if(descMap.attrInt?.equals(0x0103))
    {
    	adc = descMap.value
    }
    else if (descMap.attrInt?.equals(0x0104))
    {
        vdd = descMap.value
    }
    else
    {   
        adc = descMap.additionalAttrs?.find { item -> item.attrInt?.equals(0x0103)}?.value
	}
	
	if(vdd)
    {
    	state.lastVdd = (((float)zigbee.convertHexToInt(vdd)*3.45)/0x1FFF)
    }  
    
    if(!adc)
    {
    	return event   
    }
    
    if(!state.lastVdd)
    {
    	return event
    }

    float temp = ((zigbee.convertHexToInt(adc)*state.lastVdd)/0x1FFF)
	
	def dispValue = "unknown"
	
	if(temp > 2.2)
	{
		dispValue =  "dc"
	}
	else if(temp <=2.2 && temp > 1.5)
	{
		dispValue = "battery"
	}
    
	event = createEvent(name:"powerSource",value:dispValue)
	
    return event;
}

def configure_child() {
	
	def cmds = []
	cmds = cmds + parent.writeAttribute(0x000C, 0x0102, DataType.UINT16, 0)
    cmds = cmds + parent.writeAttribute(0x000C, 0x00105, DataType.UINT32, 250)
   	cmds = cmds + parent.configureReporting(0x000C, 0x0103, DataType.UINT16, 5, 60,500)
	return cmds
}

def installed() {
}