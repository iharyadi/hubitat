import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
metadata {
    definition (name: "Virtual Arrival Sensor", namespace: "iharyadi", author: "iharyadi") {
        singleThreaded: true
        
        capability "Presence Sensor"
        capability "Sensor"
        capability "Battery"
        capability "PowerSource"
        capability "MotionSensor"
        capability "ShockSensor"
        capability "Temperature Measurement"
    }

    preferences {
        section("Temperature") {
           
            def tempUnit = "Celsius"
            
            if( !"${location.temperatureScale}".equalsIgnoreCase("F"))
            {
                tempUnit = "Fahrenheit"
            }
            
            input name: "tempCF", defaultValue: "false", type: "bool", title: "Force temperature unit in ${tempUnit}", description: "",
                    displayDuringSetup: false
        }
        
        section("Features")
        {
            input name: "motionEnabled", defaultValue: "false", type: "bool", title: "Enable motion sensor", description: "",
                displayDuringSetup: false
        }
        
        section("Debug Messages")
        {
            input name: "logEnabled", defaultValue: "false", type: "bool", title: "Enable info message logging", description: "",
                displayDuringSetup: false
        }
    }
}

private def Log(message) {
    if (logEnabled)
      log.info "${message}"
}

def updated() {
    if(!motionEnabled)
    {
       device.deleteCurrentState("motion") 
    }
}

void uninstalled()
{
}

def installed() {
}

private def tempConvert(def temp)
{
    def eventMap = [:]
    eventMap.name = "temperature"
    
    if(tempCF)
    {
        float tempInCelsius =((float)temp).round(0)
        if("${location.temperatureScale}".equalsIgnoreCase("F"))
        {
          eventMap.unit = "°C"
          eventMap.value = (int) tempInCelsius
        }
        else if ("${location.temperatureScale}".equalsIgnoreCase("C"))
        {
          eventMap.unit = "°F"
          eventMap.value = (int) ((tempInCelsius*9.0/5.0) + 32.0)
        }
    }
    else
    {
      eventMap.unit = "°${location.temperatureScale}"
      eventMap.value = convertTemperatureIfNeeded(temp,"c",0)
    }
    
    eventMap.descriptionText = "${getLinkText(device)} temperature is ${eventMap.value} ${eventMap.unit}"
    
    return eventMap
}

def parse(def evt) { 
    
    def json = new JsonSlurper().parseText(evt.value)
    def deviceState = new JsonSlurper().parseText(json.value) 
   
    Log("topic : ${json.topic} value: ${json.value}")
    
    def topics = json.topic.tokenize("/")
    final def mapAttributeConverter = [presence:{it ->  return [name:it.key,value:it.value?"present":"not present",descriptionText: "${getLinkText(device)} ${it.value? "has arrived": "has left" }"]},
                                       occupancy:{it -> if(!motionEnabled) return null; def newvalue = it.value?"active":"inactive"; return [name:"motion",value:newvalue, descriptionText: "${getLinkText(device)} human presence is ${newValue == "active"? "" : "not" } detected"]},
                                       power_state:{it -> return [name:"powerSource",value:it.value? "dc":"battery",translatable:false,descriptionText: "${getLinkText(device)} is powered ${it.value?"up":"down"}"]},
                                       vibration:{it -> def newvalue = it.value? "detected": "clear"; return [name:"shock",value:newvalue, descriptionText: "${getLinkText(device)} vibration is ${newvalue}"]},
                                       temperature:{ it ->  return tempConvert(it.value)},
                                       battery: {it -> return [name:it.key,value:it.value,unit:"%",descriptionText: "${getLinkText(device)} battery is ${it.value} %"] }, ].withDefault { key -> return {it -> return null}}

    if(topics.size == 2)
    {
               
        deviceState.each{
          def event = mapAttributeConverter[it.key](it)
          if(event)
          {
              sendEvent(event)
          }
        }
    }
    else if(topics.size == 3)
    {
      def event = mapAttributeConverter[topics[2]]([key:topics[2],value:deviceState])
      if(event)
      {
          sendEvent(event)
      }
    }
    
    return null
}