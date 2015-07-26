# Red5VoiceBridge #
Red5-voicebridge is a SIP based voice bridge engine based on the Open Wonderland jVoiceBridge sub-project.<br />It enables Red5 developers to provide a single integrated open source web-based voice communication solution similiar to combining Flash Media Gateway (FMG) with Flash Media Server (FMS).
<p />
## It supports: ##
  * Outgoing RTMP call participants
  * Outgoing and Incoming SIP call participants
  * Incoming SIP call participants controlled by a config file.
  * Flash/Javascript based API using Red5 to move incoming calls from a lobby into conferences, make outgoing calls to invite SIP endpoints into conference and configure voice bridge
  * Direct calls between two SIP endpoints or between an RTMP and SIP endpont with no initial conferencing and then transfer to conference later.
  * Migrate an conference participant from an existing SIP endpoint to a new SIP endpoint.
  * Rich voice events like call state, speech detection, ability to play sound treatments, etc
<p />
<p />
## To install: ##
  * Stop red5 server
  * Unzip and move the voicebridge folder to webapps.
  * Edit ..\webapps\voicebridge\WEB-INF\red5voicebridge.xml for direct conference calls
  * Restart server
```
<config>
	<privateHost>192.168.1.70</privateHost>
	<publicHost>192.168.1.70</publicHost>	
	<defaultProxy>192.168.1.90</defaultProxy>
	
	<registration>
		<register proxy='192.168.1.90' 
			  display='Dele Olajide' 
			  host='192.168.1.90' 
			  username='1001' 
			  authname='1001' 
			  password='1001' 
			  realm='ccmsipline' />				
	</registration>	
	
	
	<conferences exten='3000'>
		<conference id='1111' pin='' />		
		<conference id='2222' pin='2222' />	
		<conference id='3333' exten='3333' pin='' />			
		<conference id='4444' exten='4444' pin='4444' />			
	</conferences>
	
</config>
```

## Outgoing Calls ##
When you specify a full SUP URI (sip:904@mouselike.org for example), the bridge will send a SIP INVITE message directly without using an outbound proxy.<p /> When you specify a telephone number, then the defaultProxy will be used unless the API is used to set the required voip gateway explicitly.<br /> If proxy authentication is required, then the registration details witll be used.<br /> In the example above, there is a registration to an Cisco gateway at 192.168.1.90 as SIP user 1001 in order to make external public calls.

## Incoming calls ##
You can set the default access incoming SIP exten number for all defined conferences or set exten numbers for specific conferences. The incoming caller is prompted for either meeting code or pass code if either is missing or blank.<p />

### Examples with above configuration. With SIP phone dial: ###
  * sip:3000@192.168.1.70 on the voice bridge and enter your conference id and pin number to enter any conference.
  * sip:3000@192.168.1.70 on the voice bridge, enter 1111 as your conference id and enter conference 1111.
  * sip:3000@192.168.1.70 on the voice bridge and enter 2222 as your conference id and enter passcode 2222 to enter conference 2222.
  * sip:3333@192.168.1.70 on the voice bridge and enter conference 3333.
  * sip:4444@192.168.1.70 on the voice bridge and enter passcode 4444 to enter conference 4444.
<p />

## Demo Application ##
Simple Softphone for outgoing calls only using an RTMP call participant and a SIP call participant.

![http://red5-voicebridge.googlecode.com/files/Image2.jpg](http://red5-voicebridge.googlecode.com/files/Image2.jpg)

## JavaScript API Demo ##
Point you browser at http://192.168.1.70:5080/voicebridge (or whatever) and use Javascript API

![http://red5-voicebridge.googlecode.com/files/Image1.jpg](http://red5-voicebridge.googlecode.com/files/Image1.jpg)

## Actionscript/Javascript API ##

```
	private function init():void {

		rtmpUrl = Application.application.parameters.rtmpUrl;
					
		ExternalInterface.addCallback("windowCloseEvent", windowCloseEvent);		
		ExternalInterface.addCallback("manageCallParticipant", manageCallParticipant);	
		ExternalInterface.addCallback("manageVoiceBridge", manageVoiceBridge);				
		
		netConnection = new NetConnection();
		netConnection.client = this;
		netConnection.addEventListener( NetStatusEvent.NET_STATUS , netStatus );
		netConnection.addEventListener(SecurityErrorEvent.SECURITY_ERROR, securityErrorHandler);
		
		netConnection.connect(rtmpUrl);
	}

	public function windowCloseEvent():void 
	{
		netConnection.close();
	}
	
	public function manageCallParticipant(uid:String, param:String, value:String):void 
	{
		netConnection.call("manageCallParticipant", null, uid, param, value);
	}
	
	public function manageVoiceBridge(param:String, value:String):void 
	{
		netConnection.call("manageVoiceBridge", null, param, value);
	}

	
	public function callsEventNotification(eventSource:String, callEvent:String, callState:String, info:String, callDtmf:String, treatmentdId:String, noOfCalls:String, callId:String, confId:String, callInfo:String):*
	{
		ExternalInterface.call("callsEventNotification", eventSource, callEvent, callState, info, callDtmf, treatmentdId, noOfCalls, callId, confId, callInfo);
	}
	
	public function errorEventNotification(error:String):*
	{
		ExternalInterface.call("errorEventNotification", error);
	}
	
	public function infoEventNotification(info:String):*
	{
		ExternalInterface.call("infoEventNotification", info);
	}	
	
	public function statisticsNotification(numberOfConferences:String, totalMembers:String, totalSpeaking:String, timeBetweenSends:String, averageSendTime:String, maxSendTime:String):*
	{
		ExternalInterface.call("statisticsNotification", numberOfConferences, totalMembers, totalSpeaking, timeBetweenSends, averageSendTime, maxSendTime);
	}

```

For mor information about jVoiceBridge and documentation of the parameters to the API above, visit

  * http://www.teknopipo.nl/archives/94
  * http://code.google.com/p/openwonderland-jvoicebridge/
  * http://wiki.java.net/bin/view/Communications/JVoiceBridge