import groovy.json.*
import java.text.SimpleDateFormat 

definition(
name: "NBAGameOfDay",
namespace: "iharyadi",
author: "Iman Haryadi",
description: "Get NBA Score of your favorite Team",
category: "My Apps",
iconUrl: "",
iconX2Url: "",
iconX3Url: "")


preferences {
    
    section("Team:") {
        input "teamFavorite", "text", title: "FavoriteTeam", multiple: false, required: true
    }
    
    section("Devices:") {
        input "displayDevice", "capability.sensor", title: "Score Board Device", multiple: false, required: false
    }

}

def processGetScore(response, data) {
    
    def jsonSlurper = new JsonSlurper()
    
    def object = jsonSlurper.parseText(response.data)
    
    def favorite = object.games?.find {  item -> item.vTeam.triCode.equals(teamFavorite) || item.hTeam.triCode.equals(teamFavorite) }
    
    state.favorite = favorite;
    
    if(favorite)
    {
        
        if(favorite.isGameActivated)
        {
            runIn(60, GetScore)
            
        }
        else
        {
            runIn(300, GetScore)
        }
    }
    else
    {
        runIn(3600, GetScore)
    }
    
    if(displayDevice)
    {
        if(!favorite)
        {
            displayDevice.sendScore(teamFavorite+":No Game")
        }
        else if( !favorite.isGameActivated)
        {
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

            Date date = utcFormat.parse(favorite.startTimeUTC)
            displayDevice.sendScore(teamFavorite+": Next Game " + date.format("MM/dd/yyyy HH:mm"))
        }
        else
        {
            displayDevice.sendScore(favorite.vTeam.triCode + ":" +
            favorite.vTeam.score   + " " +
            favorite.hTeam.triCode + ":" +
            favorite.vTeam.score)
        }
    }
    
}

String GetFavoriteTeamName()
{
    return teamFavorite
}

boolean IsGameAvailableToday()
{
    return state.favorite != null
}

boolean IsGameON()
{
    if(!state.favorite)
    {
        return false
    }
    
    return state.favorite.isGameActivated
}

String GetStartDate()
{
    if(!state.favorite)
    {
        return null
    }
    
    SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

    Date date = utcFormat.parse(state.favorite.startTimeUTC)
    
    return date.format("MM/dd/yyyy")
}

String GetStartTime()
{
    if(!state.favorite)
    {
        return null
    }
    
    SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

    Date date = utcFormat.parse(state.favorite.startTimeUTC)
    
    return date.format("HH:mm")
}

String GetHomeTeamName()
{
    if(!state.favorite)
    {
        return "NA"
    }
    
    return state.favorite.hTeam.triCode
}

String GetHomeTeamScore()
{
    if(!state.favorite)
    {
        return "NA"
    }
    
    return state.favorite.hTeam.score
}

String GetVisitingTeamName()
{
    if(!state.favorite)
    {
        return "NA"
    }
    
    return state.favorite.vTeam.triCode
    
}

String GetVisitingTeamScore()
{
    if(!state.favorite)
    {
        return "NA"
    }
    
    return state.favorite.vTeam.score
}

def GetScore()
{
    
    def today = new Date();	
    String url = "http://data.nba.net/10s/prod/v1/"+today.format("yyyyMMdd")+"/scoreboard.json"
    def requestParams =
    [
uri:  url,
requestContentType: "application/json",
contentType: "application/json"
    ]
    
    asynchttpGet(processGetScore,requestParams)
}


def installed() {
    runIn(5, GetScore)
}

def updated() {
    runIn(5, GetScore)
}