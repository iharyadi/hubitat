metadata {
    definition (name: "BeaconLocation", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
    }     
    
    attribute "location", "text"
}

def initialze() {
  
}

def parse(def data)
{
    if(data["beacon"])
    {
        if(data["nearLocation"])
        {
            String msg = "${data["beacon"]} is near"
            data["nearLocation"].each{ 
                msg = msg + " ${it}"
            }
            
            sendEvent([name:"location", value:msg])
        }
    }
}

def configure()
{
    initialze()
}

def configure_child() {
    initialze()
}

def installed() {
    initialze()
}