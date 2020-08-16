definition(
    name: "BeaconTracking",
    namespace: "iharyadi",
    author: "Iman Haryadi",
    description: "Track Beacon device",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
    
    section("Beacons:") {
        input "beacons", "capability.signalStrength", title: "Beacons", multiple: true, required: true
    }
    
    section("Locations:") {
        input "locations", "capability.sensor", title: "Locations", multiple: true, required: true
    }
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(beacons, "rssi", handler)
}

def handler(evt) {
    String [] DNI =  evt.getDevice().deviceNetworkId.split("-",3)
    
    if(!state.mapRssi)
    {
        state.mapRssi = new HashMap<String,Integer>()
    }
    
    state.mapRssi[DNI[1]] = evt.getIntegerValue()
    
    def nearDNI = []
    
    int minDNI = -500;
    
    state.mapRssi.each { key,val ->
        if(minDNI<val) 
        {
            nearDNI = [key]
            minDNI = val
        } else if(minDNI == val)
        {
            nearDNI += key
        }
    }
    
    nearDNI = nearDNI.collect{
        def foundLocation = locations.find {x->
            if(x.zigbeeId)
            {
                return it == x.zigbeeId
            }
            return it == x.deviceNetworkId;
        }
        
        if(foundLocation)
        {
            return foundLocation.getDisplayName()
        }
        
        return it;
    }
    
    def child =  getChildDevice("BeaconLocation-${DNI[0]}")
    if(!child)
    {
        child = addChildDevice("iharyadi", 
            "BeaconLocation", 
            "BeaconLocation-${DNI[0]}",
            [label: "Beacon Location -${DNI[0]}",
                isComponent: false])
    }
    
    if(child)
    {
        child.parse([beacon:DNI[0], nearLocation:nearDNI])
    }
}