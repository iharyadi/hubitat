definition(
	name: "Zigbee2MQTT for Arrival Sensor",
    namespace: "iharyadi",
    author: "Iman Haryadi (@iharyadi)",
    description: "Zigbee2MQTT for Arrival Sensor app to install instances link app",
	singleInstance: true,	
	category: "Convenience",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png",
	installOnOpen: true,
)

preferences {
	page(name:"pageMain")
	page(name:"pageRemove")
}

def pageMain() {	
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: false) {
		section() {
			paragraph "Create a new Zigbee2MQTT Link for Arrival Sensor for each MQTT broker"
		}
		section() {
			app(name: "zigbee2MQTT", title: "Create Zigbee2MQTT Link", appName: "Zigbee2MQTT Link for Arrival Sensor", namespace: "iharyadi", multiple: true, uninstall: false)
		}
		
		if (state.installed) {
			section() {
				href "pageRemove", title: "Remove All Zigbee2MQTT Link", description: ""
			}
		}
	}	
}


def pageRemove() {
	dynamicPage(name: "pageRemove", title: "", install: false, uninstall: true) {
		section() {			
			paragraph "<b>WARNING:</b> You are about to remove All Zigbee2MQTT Link app", required: true, state: null
		}
	}
}


def installed() {
	log.warn "installed()..."
	state.installed = true
}

def updated() {		
	log.warn "updated()..."
}