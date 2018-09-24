metadata {
    definition (name: "MQ9 Sensor", namespace: "iharyadi", author: "iharyadi") {
    	capability "Configuration"
        capability "Carbon Dioxide Measurement"
        capability "Sensor"
    }
    
    /*MQ9 Sensor does not detect CO2.  This driver is just an example
    to show how to read an Analog voltage and convert them to a PPM value.
    This calculation actually assume the gas detected in an CO.  ST capability
    for CO does not expect a numercal value.*/
    
    tiles(scale: 2) {
       multiAttributeTile(name: "carbonDioxide", type: "generic", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.carbonDioxide", key: "PRIMARY_CONTROL") {
                attributeState "carbonDioxide", label: '${currentValue}',
                        backgroundColors: [
                                [value: 31, color: "#153591"],
                                [value: 44, color: "#1e9cbb"],
                                [value: 59, color: "#90d2a7"],
                                [value: 70, color: "#44b621"],
                                [value: 84, color: "#f1d801"],
                                [value: 95, color: "#d04e00"],
                                [value: 96, color: "#bc2323"]
                        ]
            }
        }
        main (["carbonDioxide"])
        details(["carbonDioxide"])
    }

    // simulator metadata
    simulator {
    }
        
    
    preferences {    
    }
}

private float  MQGetPercentage(double rs_ro_ratio, float [] pcurve)
{
  return Math.pow(10,( Math.log10(rs_ro_ratio)*pcurve[1] + pcurve[0]));
}

private float MQGetResistance(int adc)
{
	float adc_volt = ((adc * state.lastVdd)/ 0x1FFF) * 2 
    return (5.0 - adc_volt)/adc_volt
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

    int temp = zigbee.convertHexToInt(adc)
    if (state.calibration != null)
    {
    	if( state.calibration < 5)
        {
            state.total_RO = state.total_RO + MQGetResistance(temp)
            
            state.calibration =  state.calibration + 1
            if(state.calibration >= 5)
            {
                state.total_RO = state.total_RO/5
                state.RO = state.total_RO/9.565101474 //9.83 Datasheet clean air R
                state.remove("total_RO")
                state.remove("calibration")
            }
        }
    }
    else
    {
        final float[] COCurve  =  [2.794987055,-2.263362378] as float[]
        float RS = MQGetResistance(temp)
        float RO = 10.0
        if(state.RO)
        {
        	RO = state.RO;
        }
        float result = MQGetPercentage(RS/RO, COCurve)
                
        event = createEvent(name:"carbonDioxide",value:(int)result, unit:"ppm")
    }
      
    return event;
}

def start_calibration() {
	state.calibration = 0
    state.total_RO = 0.0
    def cmd = []
    cmd = cmd + parent.readAttribute(0x000C, 0x00104)
	for(int i=0; i < 10; i++){
    	cmd = cmd + parent.readAttribute(0x000C, 0x00103)
    }
    
    return cmd
}

def configure_child() {

	def cmds = []
    cmds = cmds + parent.writeAttribute(0x000C, 0x00105, DataType.UINT32, 250)
   	cmds = cmds + parent.configureReporting(0x000C, 0x0103, DataType.UINT16, 5, 306,25)
	cmds = cmds + start_calibration();
	return cmds
}

def installed() {
}
