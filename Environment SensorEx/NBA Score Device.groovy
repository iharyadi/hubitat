metadata {
    definition (name: "NBAScore", namespace: "iharyadi", author: "iharyadi") {
        capability "Sensor"
    }
    
	capability "Sensor"
	attribute "ScoreBoard", "string"
	command "sendScore", ["string"]
	
    // simulator metadata
    simulator {
    }
        
    
    preferences {    
    }
}

def sendScore(String msg)
{
	sendEvent(name: "ScoreBoard", value: msg, displayed: true)
}

def installed() { 
}

def configure_child()
{
}