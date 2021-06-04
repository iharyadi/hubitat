metadata {
    definition (name: "BME680", namespace: "iharyadi", author: "iharyadi") {
        capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
        capability "PressureMeasurement"
        capability "CarbonDioxideMeasurement"
        
        attribute "iaq", "number"
        attribute "iaqStatic", "number"
        attribute "voc", "number"
        attribute "gas", "number"
        attribute "gasPercentage", "number"
        
        capability "Sensor"
    }
}

final private int UPDATE_TEMPERATURE()
{
    return 0
}

final private int UPDATE_HUMIDITY()
{
    return 1
}
final private int UPDATE_PRESSURE()
{
    return 2
}
final private int UPDATE_GAS()
{
    return 3
}

final private int UPDATE_IAQ()
{
    return 4
}

final private int UPDATE_STATICIAQ()
{
    return 5
}

final private int UPDATE_CO2()
{
    return 6
}

final private int UPDATE_VOC()
{
    return 7
}

final private int UPDATE_GAS_PERCENTAGE()
{
    return 8
}

private float byteArrayToFloat(def byteArray) {
    int intBits = 
    (byteArray[3] & 0xFF) << 24 | (byteArray[2] & 0xFF) << 16 | (byteArray[1] & 0xFF) << 8 | (byteArray[0] & 0xFF);
    return Float.intBitsToFloat(intBits);  
}

private long byteArrayInt(def byteArray) {
    long i = 0
    byteArray.each { b -> i = (i << 8) | ((int)b & 0x000000FF) }
    return i
}

def convertCompensatedValue(def data)
{
    float compensated = byteArrayToFloat(data[6..9]);

    return compensated;
}

def convertVirtualValue(def data)
{
    float current = byteArrayToFloat(data[2..5]);
    byte accuracy = data[6];

    return current
}

def parse(def data) { 

    def event;
    
    String joinedString = String.join("", data[0..-1])
    byte[] dataByte = joinedString.decodeHex() 
    
    String devicePageNumber = device.getDataValue("pageNumber")
    
    if(devicePageNumber.toInteger() != dataByte[1])
    {
        return null   
    }
    
    def mapToAttribute = [ (UPDATE_TEMPERATURE()) :    { x -> return  [name:"temperature", value:convertTemperatureIfNeeded(convertCompensatedValue(x).round(2),"c",1), unit:"°${location.temperatureScale}"]},
    (UPDATE_HUMIDITY()) :       { x -> return  [name:"humidity", value:convertCompensatedValue(x).round(1), unit:"%"]},
    (UPDATE_PRESSURE()):        { x -> return  [name:"pressure", value:(convertCompensatedValue(x)/1000).round(1), unit:"kPa"]},
    (UPDATE_GAS()) :            { x -> return  [name:"gas", value:(convertCompensatedValue(x)).round(3), unit:"Ohm"]},
    (UPDATE_IAQ()) :            { x -> return  [name:"iaq", value:convertVirtualValue(x).round(1)]},
    (UPDATE_STATICIAQ()) :      { x -> return  [name:"iaqStatic", value:convertVirtualValue(x).round(1)]},
    (UPDATE_CO2()) :            { x -> return  [name:"carbonDioxide", value:convertVirtualValue(x).round(1),unit:"ppm"]},
    (UPDATE_VOC()) :            { x -> return  [name:"voc", value:convertVirtualValue(x).round(2), unit:"ppm"]},
    (UPDATE_GAS_PERCENTAGE()) : { x -> return  [name:"gasPercentage", value:convertVirtualValue(x).round(1), unit:"%"]}
    ]
    
    
    int ndx = dataByte[0]                         
    def converter = mapToAttribute[ ndx ]
    if(converter)
    {
        return converter(dataByte)
    }
    
    return null;
}

def configure_child() {
}

def installed() {
}
