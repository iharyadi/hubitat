metadata {
    definition (name: "Motion Sensor", namespace: "iharyadi", author: "iharyadi") {
        capability "Motion Sensor"
        capability "Sensor"
    }
    
    tiles(scale: 2) {
       multiAttributeTile(name: "motion", type: "generic", width: 6, height: 4) {
			tileAttribute("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label: 'motion', icon: "st.motion.motion.active", backgroundColor: "#00A0DC"
				attributeState "inactive", label: 'no motion', icon: "st.motion.motion.inactive", backgroundColor: "#cccccc"
			}
		}
        main (["motion"])
        details(["motion"])
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
           
    return createEvent(name:"motion", value:(zigbee.convertHexToInt(value)>0)?"active":"inactive")
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