metadata {
    definition (name: "Switch", namespace: "iharyadi", author: "iharyadi") {
        capability "Switch"
        capability "Sensor"
    }
    
    tiles(scale: 2) {
       standardTile("switch", "device.switch", width: 6, height: 4, canChangeIcon: true) {
            state "off", label: '${currentValue}', action: "on",
                  icon: "st.switches.switch.off", backgroundColor: "#ffffff",nextState:"turningOn"
            state "on", label: '${currentValue}', action: "off",
                  icon: "st.switches.switch.on", backgroundColor: "#00A0DC",nextState:"turningOff"
             
            state "turningOn", label:'${currentValue}', action:"off", 
            	icon: "st.switches.switch.on", backgroundColor:"#00A0DC"
            state "turningOff", label:'${currentValue}', action:"on", 
            	icon: "st.switches.switch.off", backgroundColor:"#ffffff"
        }
        main (["switch"])
        details(["switch"])
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
    
    def present_value = descMap.attrInt?.equals(0x0055)?
    	descMap.value:
    	descMap.additionalAttrs?.find { item -> item.attrInt?.equals(0x0055)}?.value
    
    if(!present_value)
    {
        return null
    }
       
    return createEvent(name:"switch", value:(zigbee.convertHexToInt(present_value)>0)?"on":"off")
}

def off() {
    parent.sendCommandP(parent.binaryoutputOff())
}

def on() {
    parent.sendCommandP(parent.binaryoutputOn())
}

def installed() { 
}

def configure_child()
{
	def cmds = []
	cmds =  cmds + parent.configureReporting(0x0010, 0x0055, DataType.BOOLEAN, 0, 304,1)
	return cmds
}