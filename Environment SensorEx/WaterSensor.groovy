import hubitat.zigbee.zcl.DataType

metadata {
    definition (name: "Water Sensor", namespace: "iharyadi", author: "iharyadi") {
    	capability "Water Sensor"
        capability "Sensor"
        attribute "voltage", "number"
        attribute "moisture", "number"
    }
        
    preferences {
        section("setup")
        {
        	input name:"threshold", type:"decimal", title: "Threshold", description: "Threshold voltage which considered as dry",
            	range: "0..3.3", displayDuringSetup: false, defaultValue: 2.0
            input name:"minVoltage", type:"decimal", title: "0% voltage", description: "Voltage when mositure is 0%",
            	range: "0..3.3", displayDuringSetup: false, defaultValue: 2.7
            input name:"maxVoltage", type:"decimal", title: "100% voltage", description: "Voltage when mositure is 100%",
            	range: "0..3.3", displayDuringSetup: false, defaultValue: 1.5
            
        }
    }
}

private def createVoltageEvent(float value)
{
    def result = [:]
    result.name = "voltage"
    result.translatable = true
    result.value = value.round(2)
    result.unit = "v"
    result.descriptionText = "{{ device.displayName }} Voltage was $result.value"
    return result
}

def createMoistureEvent(float voltage)
{
	def result = [:]
    
    float minVoltageTemp = 2.7 
    float maxVoltageTemp = 1.5 
    
	if(minVoltage)
    {
    	minVoltageTemp = minVoltage
    }
    
    if(maxVoltage)
    {
    	maxVoltageTemp = maxVoltage
    }
    
    int percentMoisture =  (-100.0 * (voltage - minVoltageTemp) / (minVoltageTemp - maxVoltageTemp)).round(0)
    
    if(percentMoisture > 100)
    {
    	percentMoisture = 100
    }
    
    if(percentMoisture < 0)
    {
    	percentMoisture = 0
    }
    
    result.name = "moisture"
    result.translatable = true
    result.value = percentMoisture
    result.unit = "%"
    result.descriptionText = "{{ device.displayName }} Moisture was $result.value"
    
    return result;
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

    float volt = 0;
   	volt = (zigbee.convertHexToInt(adc) * state.lastVdd)/0x1FFF
     
    sendEvent(createMoistureEvent(volt))
    sendEvent(createVoltageEvent(volt)) 
    
    float voltageDryWet = threshold? threshold:2.0
    return createEvent(name:"water",value:(volt > voltageDryWet)? "dry":"wet")
}

def configure_child() {

	def cmds = []
    cmds = cmds + parent.writeAttribute(0x000C, 0x00105, DataType.UINT32, 250)
   	cmds = cmds + parent.configureReporting(0x000C, 0x0103, DataType.UINT16, 0, 306,25)
	return cmds
}

def installed() {
}

def updated() {
	return parent.readAttribute(0x000C, 0x0103)
}
