/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

/*
 * FIXME:  Please rewrite me.  I started out as just handful of
 * commands now there are over 150!
 */
package com.sun.voip.server;

import com.sun.voip.CallParticipant;
import com.sun.voip.CallEvent;
import com.sun.voip.CallEventListener;
import com.sun.voip.Logger;
import com.sun.voip.Recorder;
import com.sun.voip.RtpPacket;
import com.sun.voip.RtpSocket;
import com.sun.voip.SdpManager;
import com.sun.voip.TickerSleep;

import com.sun.voip.LowPassFilter;

import com.sun.voip.client.BridgeConnector;

import com.sun.stun.StunClient;
import com.sun.stun.StunServerImpl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import java.io.IOException;

import java.text.ParseException;

import java.lang.NumberFormatException;

import java.net.Socket;

import java.util.logging.Level;

import java.util.NoSuchElementException;
import java.util.Vector;

/*
 * Parse requests.
 *
 * A request is a String of the forms listed below.

 * Parameters for third-party call setup are collected in a
 * CallParticipant object.
 *
 * Below are the requests for TPCC.  Many have a short name alias intended for
 * interactive users.
 *
 *	callAnswerTimeout | to		    = <seconds to timeout if no answer>
 *	callAnsweredTreatment | at          = <audio treatment>
 *	callEndTreatment | et               = <audio treatment>
 *	callEstablishedTreatment | cet	    = <audio treatment>
 *	callId | id			    = <String>
 *	callTimeout			    = <int>
 *	conferenceId | c                    = <String>
 *      conferenceJoinTreatment | jt        = <audio treatment>
 *      conferenceLeaveTreatment | lt       = <audio treatment>
 *	displayName | d                     = <String>
 *	distributedBridge		    = true | false
 *	doNotRecord | dnr		    = true | false
 *	dtmfDetection | dtmf                = true | false
 *	dtmfSuppression | ds		    = true | false
 *	ignoreTelephoneEvents		    = true | false
 *	inputTreatment			    = <String>
 *	encrytionKey | ek 		    = <String>
 *	encryptionAlgorithm | ea	    = <String>
 *	firstConferenceMemberTreatment | fm = <audio treatment>
 *	forwardDtmfKeys
 *	handleSessionProgress | hsp         = true | false
 *      joinConfirmationTimeout | jc        = <seconds to timeout
 *					      if no dtmf key is pressed>
 *      mediaPreference			    = <PCM[U]|SPEEX/<sampleRate>/<channels>
 *      mute | m			    = true | false
 *	muteWhisperGroup | mwg		    = true | false
 *	muteConference | mc 		    = true | false
 *	name | n                            = <name>
 *	phoneNumber | pn                    = <phone number>
 *	phoneNumberLocation | pnl           = <3 char phone number location>
 *	protocol | p			    = H323 | SIP
 *	sipProxy | sp			    = <SIP Proxy host name>
 *      speexEncode | se		    = true | false
 *	whisperGroup | wg		    = <whisperGroup to join>
 *	voipGateway | vg		    = <VoIP gateway server name>
 * 					      or IP address>
 *	useConferenceReceiverThread | ucrt  = true | false
 *	voiceDetection | v       	    = true | false
 *	voiceDetectionWhileMuted | vm       = true | false
 *
 * <audio treatment> is either
 *      file:<path to audio file>
 *	  or
 *	dtmf:<numbers 0-9 # *>
 *	  or
 *	tts:<text>
 *
 * Audio silence can be added before or after <treatment> by
 * putting commas before and/or after the path or intermixed with
 * dtmf characters.  Each comma represents 100ms of silence.
 * multiple <treatments> can be specified by separating them with ";".
 *
 * A "?" followed by a newline will display call parameters for the
 * call about to be placed.
 *
 * Immediate action requests are also handled here.
 *
 * The requests are listed below.
 *
 *	addCallToWhisperGroup | acwg = <whisperGroupId> : <callId>
 *
 *	allowShortNames | asn     = <true> | <false>
 *
 *	bridgeLocation | bl       = <3 charaction location of bridge>
 *
 *      callAnswerTimeout | cat   = <seconds>
 *
 *	cancel			  = <callId>
 *
 *	cancelMigration | cm      = <callId>
 *
 *	cnThresh | ct             = <int cnThresh> : <callId>
 *
 *	comfortNoiseType | cnt    = 0 | 1 | 2
 *
 *	comfortNoiseLevel | cnl   = <byte level>*
 *
 *	conferenceInfo | ci
 *
 *	conferenceInfoLong | cil
 *
 *	createConference | cc     =
 *		<conferenceId>:PCM[U]|SPEEX/<sampleRate>/<channels>[:<displayName>]
 *
 *	createWhisperGroup | cwg  = <whisperGroupId> [:<whisper attenuation factor>]
 *
 *	deferMixing | dm          = true | false
 *
 *	destroyWhisperGroup | dwg = <whisperGroupId>
 *
 *      directConferencing        = true | false
 *
 *	distributedConferenceInfo | dci
 *
 *	dropDb
 *
 *	dtmfSuppression | ds      = true | false : <callId>
 *
 *	duplicateCallLimit | dcl  = <int>
 *
 *	endConference             = <conferenceId>
 *
 *	firstRtpPort | frp = <int>
 *
 *	flush
 *
 *      forcePrivateMix | fpm     = true | false
 *
 *	forwardDtmfKeys	| fdtmf	  = true | false
 *
 *	gc
 *
 *	getCallState | gcs	  = <callId>
 *
 *	getMixDescriptors | gpm	  = <callId>
 *
 *	gs
 *
 * 	help
 *
 *	incomingCallTreatment | ict = <treatment>
 *
 *	incomingCallVoiceDetection | icvd = true | false
 *
 *      internationalPrefix       = <String>
 *
 *	incomingCallVoiceDetection | icvd = true | false
 *
 *	localhostSecurity	  = true | false
 *
 *	logLevel | l              = [0 - 10]
 *
 *	loneReceiverPort | lrp    = <int>
 *
 *      longDistancePrefix        = <String>
 *
 *	loud =			  = <int>
 *
 *	minJitterBufferSize | minjb = <int> : <callId>
 *
 *      maxJitterBufferSize | maxjb = <int> : <callId>
 *
 *	migrate			  = <existing callId> :
 * 	    <new Phone Number> | Id-<new CallId>
 *
 *	monitorCallStatus | mcs   = true | false : <callId>
 *
 *	monitorConferenceCalls | mcc  = true | false : <conferenceId>
 *
 *	monitorIncomingCalls | mic= true | false
 *
 *	monitorOutgoingCalls | moc= true | false
 *
 *	mute 			  = true | false : <callId>
 *
 *      muteWhisperGroup | mwg    = true | false : <callId>
 *
 *	muteConference | mc 	  = true | false : <callId>
 *
 *	numberOfMembers | nm        = <conferenceId> : <IdString>
 *
 *      outsideLinePrefix         = <String>
 *
 *	pause                     = <int ms>
 *
 *	pauseTreatmentToCall      = <callId>
 *
 *	playTreatmentToCall | ptc = <treatment> : <callId>
 *
 *	playTreatmentToConference | pc = <treatment> : <conferenceId>
 *
 *	playTreatmentToAllConferences | pca = <treatment>
 *
 *	packetLossConcealmentClass | plcc = <String class name> : <callId>
 *
 *	powerThresholdLimit       = <double> : <callId>
 *
 *	prefixPhoneNumber | ppn   = true | false
 *
 *	printStatistics | ps
 *
 *	privateMix | pm           = <c0 left volume> : <c0 right vol> :
 *	   			    <c1 left volume> : <c1 right vol> :<callId>:
 *				    <callId with private mix>
 *
 *	recordConference | rc     = true | false : <conferenceId> [: <file path> [: type]]
 *
 *	recordingDirectory | rd   = <directory path>
 *
 *	recordFromMember | rfm    = true | false : <callId> [: <file path> [: type]]
 *
 *	recordToMember | rtm      = true | false : <callId> [: <file path> [: type]]
 *
 *	releaseCalls              = true | false
 *
 *	removeCallFromWhisperGroup | rcwg = <whisperGroupId> : <callId>
 *
 *	removeConference | rconf  = <conferenceId>
 *
 * 	restartInputTreatment = <callId>
 *
 *	resumeTreatmentToCall | rtc = <callId>
 *
 *	resumeTreatmentToConference = <conferenceId>
 *
 *	sendSipUriToProxy	  = true | false
 *
 * 	senderThreads | st	  = <int>
 *
 *	setInputVolume | siv      = <volume> : <callId>
 *
 *	setOutVolume | sov        = <volume> : <callId>
 *
 *	showWhisperGroups | swg
 *
 *	silenceMainConference | smc = true | false : <callId>
 *
 *      speexEncode | se	  = true | false : <callId>
 *
 *	stopTreatmentToCall | stc = <callId>
 *
 *      stopTreatmentToConference | sc = <conferenceId>
 *
 *	SHUTDOWN
 *
 *	synchronousMode | sm      = true | false
 *
 *	defaultProtocol		  = SIP
 *
 *	defaultSipProxy | sp	  = <ip address>
 *
 *	traceCall                 = <callId>
 *
 *	transferCall | tc         = <callId> : <conferenceId>
 *
 *	timeBetweenPackets        = <int ms>
 *
 *	timeStamp
 *
 *	tuneableparameters | tp
 *
 *	useSingleSender | uss     = true | false
 *
 *	useTelephoneEvent         = true | false
 *
 *	VoIPGateways | vgs        = <ip address>[,<ip address>...]
 *
 *	wisperGroupOptions | wgo  = <whisperGroupId> [: locked=t|f ]
 *						     [: transient=t|f]
 *						     [: attenuation=<double>]
 *
 *	whisper | w 		  = <whisperGroupId> : <callId>
 *
 *	whisperAttenuation	  = <double>
 *
 *      writeThru | wt		  = true | false
 *
 * A call is identified by a unique <callId> String.
 * Information about the call follows "::".
 *
 *    <callId>::<name>@<phoneNumber> or <callId>::<name>@<host>:<port>
 */
public class RequestParser {
    private RequestHandler requestHandler;
    private CallParticipant cp;
    private String lastCallId;
    private String lastConferenceId;

    private boolean synchronousMode = false;

    public RequestParser(RequestHandler requestHandler, CallParticipant cp) {
	this.requestHandler = requestHandler;
	this.cp = cp;
    }

    public void setSynchronousMode(boolean synchronousMode) {
	this.synchronousMode = synchronousMode;
    }

    public boolean synchronousMode() {
	return synchronousMode;
    }

    /*
     * Set previous values of callId and conferenceId for convenience
     * so a user doesn't have to type them for every command.
     */
    public void setPreviousValues(String callId, String conferenceId) {
	lastCallId = callId;
	lastConferenceId = conferenceId;
    }

    /*
     * Parse a request and handle it if it's an immediate request
     */
    public boolean parseRequest(String request) throws ParseException {
	if (parseImmediateRequest(request)) {
	    return true;	// it's been handled
	}

	parseCallParameters(request);
	return false;
    }

    class ParameterException extends Exception {
	public ParameterException(String error) {
	    super(error);
	}
    }

    /**
     * Parse a request and gather call parameters.
     * @param request String with command terminated with "\n"
     */
    public void parseCallParameters(String request) throws ParseException {
	String value;
	int integerValue;
	boolean booleanValue;

	try {
	    integerValue = getIntegerValue("callAnswerTimeout", "to", request);
	    cp.setCallAnswerTimeout(integerValue);
	    return;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("callEndTreatment", "et", request);
	    cp.setCallEndTreatment(value);
	    return;
	} catch (ParameterException e) {
        }

	try {
            value = getValue("callEstablishedTreatment", "cet", request);
            cp.setCallEstablishedTreatment(value);
            return;
	} catch (ParameterException e) {
        }

        try {
            value = getValue("callId", "id", request);
	    cp.setCallId(value);
	    lastCallId = value;
            return;
        } catch (ParameterException e) {
        }

	try {
	    integerValue = getIntegerValue("callTimeout", "cto", request);
	    cp.setCallTimeout(integerValue * 1000);  // timeout in ms
	    return;
	} catch (ParameterException e) {
        }

	try {
            value = getValue("conferenceId", "c", request);

	    String[] tokens = value.split(":");

	    cp.setConferenceId(tokens[0].trim());

	    if (tokens.length > 1) {
		cp.setMediaPreference(tokens[1]);
	    }

	    if (tokens.length > 2) {
	        cp.setConferenceDisplayName(tokens[2]);
	    }

	    return;
	} catch (ParameterException e) {
        }

	try {
            value = getValue("displayName", "d", request);
	    cp.setDisplayName(value.trim());
	    return;
	} catch (ParameterException e) {
        }

	try {
            value = getValue("distributedBridge", "db", request);
            cp.setDistributedBridge(getBoolean(value));
            return;
        } catch (ParameterException e) {
        }

	try {
	    booleanValue = getBooleanValue("doNotRecord" , "dnr", request);
	    cp.setDoNotRecord(booleanValue);
	    return;
	} catch (ParameterException e) {
        }

        try {
            booleanValue = getBooleanValue("dtmfDetection", "dtmf", request);
	    cp.setDtmfDetection(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
            booleanValue = getBooleanValue("dtmfSuppression", "ds", request);
	    cp.setDtmfSuppression(booleanValue);
	    return;
	} catch (ParameterException e) {
        }

	try {
	    value = getValue("forwardDataFrom", "fdf", request);

	    cp.setForwardingCallId(value);
	    return;
	} catch (ParameterException e) {
        }

        try {
            value = getValue("ignoreTelephoneEvents", "ite", request);

            booleanValue = getBoolean(value);

            cp.setIgnoreTelephoneEvents(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
	    cp.setInputTreatment(getValue("inputTreatment", "it", request));
            return;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("encryptKey", "ek", request);
            cp.setEncryptionKey(value);
            return;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("encryptAlgorithm", "ea", request);
            cp.setEncryptionAlgorithm(value);
            return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("firstConferenceMemberTreatment", "fm", request);
	    cp.setFirstConferenceMemberTreatment(value);
	    return;
	} catch (ParameterException e) {
        }

        try {
            booleanValue = getBooleanValue(
		"handleSessionProgress", "hsp", request);

            cp.setHandleSessionProgress(booleanValue);
            return;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("joinConfirmationKey", "jck", request);
	    MemberReceiver.setJoinConfirmationKey(value);
            return;
        } catch (ParameterException e) {
	}

        try {
            integerValue =
		getIntegerValue("joinConfirmationTimeout", "jc", request);
	    cp.setJoinConfirmationTimeout(integerValue);
            return;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("mediaPreference" , "mp", request);

	    cp.setMediaPreference(value);
            return;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("migrate" , "", request);
            String callId = getFirstString(value);
            cp.setCallId(callId);
            lastCallId = callId;

	    /*
	     * The second party number may be a sip address
	     * with colons.  So we treat everything after the
	     * first colon as the second party number.
	     */
	    int ix;

	    if ((ix = value.indexOf(":")) < 0) {
                throw new ParseException(
                    "secondPartyNumber must be specified:  " + request, 0);
	    }

            String secondPartyNumber = value.substring(ix + 1);

            if (secondPartyNumber == null) {
                throw new ParseException(
                    "secondPartyNumber must be specified:  " + request, 0);
            }

            cp.setSecondPartyNumber(secondPartyNumber);
            cp.setMigrateCall(true);
	    return;
        } catch (ParameterException e) {
        }

	try {
            booleanValue = getBooleanValue("mute" , "m", request);
	    cp.setMuted(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
	    booleanValue = getBooleanValue("muteWhisperGroup" , "mwg", request);
	     cp.setMuteWhisperGroup(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
	    booleanValue = getBooleanValue("muteConference" , "mc", request);
	    cp.setConferenceMuted(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("name", "n", request);
	    cp.setName(value);
	    return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("phoneNumber", "pn", request);
	    cp.setPhoneNumber(value);
	    return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("phoneNumberLocation", "pnl", request);
	    cp.setPhoneNumberLocation(value);
	    return;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("protocol", "p", request);

	    if (value.equalsIgnoreCase("h.323")) {
		value = "h323";
	    }

	    if (value.equalsIgnoreCase("h323") == false &&
		    value.equalsIgnoreCase("SIP") == false &&
		    value.equalsIgnoreCase("NS") == false) {

		throw new ParseException("Invalid protocol:  " +
		    value, 0);
	    }

            cp.setProtocol(value);
            return;
        } catch (ParameterException e) {
        }

	try {
	    cp.setRecorder(getBooleanValue("recorder", "", request));
	    return;
        } catch (ParameterException e) {
        }

	try {
	    cp.setRecordDirectory(getValue("recordDirectory", "", request));
	    return;
        } catch (ParameterException e) {
        }

	try {
	    cp.setRemoteCallId(getValue("remoteCallId", "rid", request));
            return;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("sipProxy", "sp", request);
	    cp.setSipProxy(value);
	    return;
	} catch (ParameterException e) {
        }

	try {
	    value = getValue("whisperGroup", "wg", request);
	    cp.setWhisperGroupId(value);
	    return;
	} catch (ParameterException e) {
        }

	try {
            value = getValue("voipGateway", "vg", request);
	    cp.setVoIPGateway(value);
	    return;
	} catch (ParameterException e) {
        }

	try {
	    booleanValue = getBooleanValue("voiceDetectionWhileMuted", "vm", request);
	    cp.setVoiceDetectionWhileMuted(booleanValue);
	    return;
	} catch (ParameterException e) {
        }

        try {
            value = getValue("useConferenceReceiverThread" , "ucrt", request);
            booleanValue = getBoolean(value);
            cp.setUseConferenceReceiverThread(booleanValue);
            return;
        } catch (ParameterException e) {
	}

        try {
            booleanValue = getBooleanValue("voiceDetection", "v",
		request);
	    cp.setVoiceDetection(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
	    getTwoPartyCallRequest(request);
	    return;
	} catch (ParameterException e) {
	}

	throw new ParseException("Invalid request ignored:  " + request, 0);
    }

    /*
     * parameters for two party calls.  The "first*" parameters
     * are here for compatibility and will go away soon.
     */
    private void getTwoPartyCallRequest(String request) throws ParseException,
	    ParameterException {

	/*
         *	firstPartyCallId | id1		    = <String>
         *      firstPartyName | name               = <name>
         *      firstPartyNumber | n1               = <phone number>
         *      firstPartyTimeout | to1             = <seconds to timeout>
         *      firstPartyTreatment | t1            = <audio treatment>
         *      firstPartyVoiceDetection | v1       = true | false
         *
         *      secondPartyCallEndTreatment | e2    = <audio treatment>
         *	secondPartyCallId | id2		    = <String>
         *      secondPartyName | name2             = <name>
         *      secondPartyNumber | n2              = <number>
         *      secondPartyTimeout | to2            = <seconds to timeout>
         *      secondPartyTreatment | t2           = <audio treatment>
         *      secondPartyVoiceDetection  |  v2    = true | false
         */
	String value;
	int integerValue;
	boolean booleanValue;

	try {
	    value = getValue("firstPartyCallId", "id1", request);
	    cp.setCallId(value);
	    return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("firstPartyName", "name1", request);
	    cp.setName(value);
	    return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("firstPartyNumber", "n1", request);
	    cp.setPhoneNumber(value);
            return;
        } catch (ParameterException e) {
        }

	try {
	    integerValue = getIntegerValue("firstPartyTimeout", "to1", request);
	    cp.setCallAnswerTimeout(integerValue);
	    return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("firstPartyTreatment", "t1", request);
	    cp.setCallAnsweredTreatment(value);
	    return;
        } catch (ParameterException e) {
        }

        try {
            booleanValue = getBooleanValue("firstpartyVoiceDetection", "v1",
		request);
	    cp.setVoiceDetection(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("secondPartyCallId", "id2", request);
	    cp.setSecondPartyCallId(value);
	    return;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("secondPartyCallEndTreatment", "e2", request);
	    cp.setSecondPartyCallEndTreatment(value);
	    return;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("secondPartyName", "name2", request);
	    cp.setSecondPartyName(value);
	    return;
	} catch (ParameterException e) {
        }

	try {
	    value = getValue("secondPartyNumber", "n2", request);
	    cp.setSecondPartyNumber(value);
            return;
	} catch (ParameterException e) {
        }

	try {
	    integerValue =
		getIntegerValue("secondPartyTimeout", "to2", request);
	    cp.setSecondPartyTimeout(integerValue);
	    return;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("secondPartyTreatment", "t2", request);
	    cp.setSecondPartyTreatment(value);
	    return;
	} catch (ParameterException e) {
        }

        try {
            booleanValue =
		getBooleanValue("secondpartyVoiceDetection", "v2", request);
	    cp.setSecondPartyVoiceDetection(booleanValue);
            return;
        } catch (ParameterException e) {
        }

	throw new ParameterException("parameter not found");
    }

    /*
     * Determine if parameter name is one of ours
     */
    private void parameterMatch(String name, String shortName, String s)
	    throws ParameterException {

	String command = s.trim();

	if (name.equalsIgnoreCase(command)
		|| shortName.equalsIgnoreCase(command)) {

	    return;
	}

	throw new ParameterException("parameter not found");
    }

    /*
     * Check for <name>=<value>
     */
    private String getValue(String name, String shortName, String s)
	    throws ParseException, ParameterException {

	if (s == null) {
	    throw new ParseException("null parameter", 0);
	}

	int n;

        if ((n = s.indexOf('=')) < 0) {
            n = s.length();
        }

	String command = s.substring(0, n).trim();

	if (!name.equalsIgnoreCase(command) &&
	        !shortName.equalsIgnoreCase(command)) {

	    throw new ParameterException("parameter not found");
	}

	if ((n = s.indexOf('=')) < 0) {
	    throw new ParseException("Invalid specification, '" + s
		+ "', must be <request>=<value>", 0);
	}

        s = s.substring(n + 1);

	while (s.startsWith(" ")) {
	    s = s.substring(1);	   // get rid of leading spaces
	}

        if (s.length() == 0) {
            throw new ParseException("Invalid specification, '" + s
                + "', must be <request>=<value>", 0);
        }

        return s;
    }

    /*
     * Check for <name>=<integer>
     */
    private int getIntegerValue(String name, String shortName, String s)
	throws ParseException, ParameterException {

	String value = getValue(name, shortName, s);

	int integerValue;

	try {
	    return Integer.parseInt(value);
	} catch (NumberFormatException e) {
	    throw new ParseException("invalid integer value:  " + value, 0);
	}
    }

    /*
     * Check for <name>=true | false | t | f
     */
    private boolean getBooleanValue(String name, String shortName, String s)
	    throws ParseException, ParameterException {

	if (s == null) {
	    throw new ParseException("null parameter", 0);
	}

	int n;

        if ((n = s.indexOf('=')) < 0) {
            n = s.length();
        }

	String command = s.substring(0, n).trim();

	if (!name.equalsIgnoreCase(command) &&
	        !shortName.equalsIgnoreCase(command)) {

	    throw new ParameterException("parameter not found:  " + s);
	}

	if ((n = s.indexOf('=')) < 0) {
	    throw new ParseException("Invalid specification, '" + s
                + "', must be <request>=<value>", 0);
	}

        s = s.substring(n + 1);

	while (s.startsWith(" ")) {
	    s = s.substring(1);	   // get rid of leading spaces
	}

	return stringToBoolean(s);
    }

    /*
     * check for boolean:String and return the boolean.
     */
    private boolean getBoolean(String value) throws ParseException {
	int n;

	if ((n = value.indexOf(":")) > 0) {
	    value = value.substring(0, n);
	}

	return stringToBoolean(value);
    }

    private boolean stringToBoolean(String value) throws ParseException {
	if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("t")) {
	    return true;
	}

	if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("F")) {
	    return false;
	}

        throw new ParseException(
	    "Invalid boolean value, must be true or false:  " + value, 0);
    }

    /*
     * check for int:String and return the int
     */
    private int getInteger(String value) throws ParseException {
	int n;

	if ((n = value.indexOf(":")) > 0) {
	    value = value.substring(0, n);
	}

	int i = 0;

	try {
            i = Integer.parseInt(value);
	} catch (NumberFormatException e) {
	    throw new ParseException("Invalid integer value: " + value, 0);
	}

	return i;
    }

    /*
     * check for double:String and return the double value
     */
    private double getDouble(String value) throws ParseException {
	int n;

	if ((n = value.indexOf(":")) > 0) {
	    value = value.substring(0, n);
	}

	double f = (double)0.0;

	try {
            f = Double.parseDouble(value);
	} catch (NumberFormatException e) {
	    throw new ParseException("Invalid double value: " + value, 0);
	}

	return f;
    }

    /*
     * check for <String>:... and return the string
     */
    private String getString(String value) {
	int n;

	if ((n = value.lastIndexOf(":")) > 0) {
	    value = value.substring(0, n);
	}

	return value;
    }

    /*
     * get first string up to colon
     */
    private String getFirstString(String value) {
	int ix;

	if ((ix = value.indexOf(":")) < 0) {
	    return value;
	}

	return value.substring(0, ix);
    }

    /*
     * Get a treatment String.
     */
    private String getTreatment(String value) {
	String v = new String(value);

	int n;

	if ((n = v.indexOf(":volume=")) >= 0) {
	    v = v.substring(0, n);
	}

	if ((n = v.lastIndexOf(":")) < 0) {
	    /*
	     * There's no ":", so the whole string is the treatment
	     */
	    return v;
	}

	String s = v.substring(0, n);

	if (s.equalsIgnoreCase("f") || s.equalsIgnoreCase("file")
	       || s.equalsIgnoreCase("d") || s.equalsIgnoreCase("dtmf")
	       || s.equalsIgnoreCase("t") || s.equalsIgnoreCase("tts")) {

	    /*
	     * The only ":" is preceded by the type of treatment.
	     * The whole string is the treatment.
	     */
	    return v;
	}

	/*
	 * The treatment is the string up to the last ":".
	 */
	return s;
    }

    private double[] getVolume(String value) throws ParseException {
	String v = new String(value);

	int n;

        if ((n = v.indexOf(":volume=")) < 0) {
	    return null;
	}

	v = v.substring(n + 8);

	String[] tokens = v.split(":");

	double[] volume = new double[tokens.length];

	for (int i = 0; i < volume.length; i++) {
	    try {
		volume[i] = Double.parseDouble(tokens[i]);
	    } catch (NumberFormatException e) {
		throw new ParseException("Invalid floating point value:  "
		    + tokens[i], 0);
	    }
	}

	return volume;
    }

    /*
     * check for :String and return the string
     */
    private String getQualifierString(String value) {
	int n;

	if (value == null) {
	    return null;
	}

	if ((n = value.lastIndexOf(":")) >= 0) {
	    return value.substring(n+1);
	}

	return null;
    }

    /*
     * Parse and handle a Immediate request.
     */
    private boolean parseImmediateRequest(String request)
	    throws ParseException {

	String value;
	int integerValue;
	boolean booleanValue;

	try {
	    integerValue = getIntegerValue("nAvg", "", request);
	    LowPassFilter.setNAvg(integerValue);
	    Logger.println("nAvg " + integerValue);
	    return true;
	} catch (ParameterException e) {
        }

        try {
	    value = getValue("lpfv", "", request);
	    double lpfv = getDouble(value);
            LowPassFilter.setLpfVolumeAdjustment(lpfv);
            Logger.println("lpfVolumeAdjustment " + lpfv);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    SipTPCCallAgent.forceGatewayError(getBooleanValue("forceGatewayError", "fe", request));
	    return true;
	} catch (ParameterException e) {
        }

	try {
 	    value = getValue("addCallToWhisperGroup" , "acwg", request);

	    String[] tokens = value.split(":");

	    if (tokens.length != 2) {
		throw new ParseException("You must specify both "
		   + " a whisperGroupId and a callId", 0);
	    }

            String whisperGroupId = tokens[0];
            String callId = tokens[1];

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                throw new ParseException(
                    "Invalid callId:  " + callId, 0);
            }

            /*
             * Add call
             */
            callHandler.getMember().addCall(whisperGroupId);
            return true;
        } catch (ParameterException e) {
        }

        try {
            ConferenceManager.setAllowShortNames(
                getBooleanValue("allowShortNames", "asn", request));
            return true;
        } catch (ParameterException e) {
        }

        try {
            /*
             * XXX this is overloaded to support the audio study
             * without having to modify MC to set the
	     * callAnsweredTreatment to null
             * and to specify a call established treatment.
             * By allowing a conference id to be specified, this breaks
             * treatments which have a qualifier such as dtmf:.
             * XXX FIX MC!
             */
            value = getValue("callAnsweredTreatment", "at", request);

            cp.setCallAnsweredTreatment(value);

            //String[] tokens = value.split(":");

            //if (tokens.length == 1) {
            //    cp.setCallAnsweredTreatment(tokens[0]);
            //    return true;
            //}

            //ConferenceManager.setConferenceAnswerTreatment(tokens[1],
	    //	tokens[0]);
	    return true;
        } catch (ParameterException e) {
        }

	try {
	    Bridge.setBridgeLocation(
		getValue("bridgeLocation", "bl", request));

	    return true;
	} catch (ParameterException e) {
	}

	try {
            value = getValue("cancel" , "", request);
            CallHandler.hangup(value, "User requested call termination");
            return true;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("cancelMigration" , "cm", request);
            CallMigrator.hangup(value, "User requested call termination");
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("cnThresh" , "ct", request);

            integerValue = getInteger(value);

            String callId = getQualifierString(value);

            CallHandler.setCnThresh(callId, integerValue);
            lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

	try {
            MemberSender.setComfortNoiseType(
	    	getIntegerValue("comfortNoiseType", "cnt", request));
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    RtpPacket.setDefaultComfortNoiseLevel((byte)
 	        getIntegerValue("comfortNoiseLevel" , "cnl", request));
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("commonMixDefault", "cmd", request);

	    WhisperGroup.setCommonMixDefault(getBoolean(value));
            return true;
        } catch (ParameterException e) {
        }

	try {
	    parameterMatch("conferenceInfoShort", "ci", request);
	    /*
             * This is a request for conference information
	     * with abbreviated id's so it's readable.
             */
	    requestHandler.writeToSocket(
		ConferenceManager.getAbbreviatedConferenceInfo());
            return true;
	} catch (ParameterException e) {
	}

        try {
            parameterMatch("conferenceInfo", "cil", request);
            /*
             * This is a request for conference information
	     * with full id's
             */
            requestHandler.writeToSocket(
		ConferenceManager.getDetailedConferenceInfo());
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("createConference" , "cc", request);

	    String[] tokens = value.split(":");

	    if (tokens.length < 2) {
		throw new ParseException("Missing parameters", 0);
	    }

	    String conferenceId = tokens[0];

            if (tokens[1].indexOf("PCM") != 0 &&
		    tokens[1].indexOf("SPEEX") != 0) {

                throw new ParseException("invalid media specification", 0);
            }

	    String mediaPreference = tokens[1];

	    String displayName = null;

	    if (tokens.length > 2) {
		displayName = tokens[2];
	    }

	    ConferenceManager.createConference(conferenceId, mediaPreference,
		displayName);

	    cp.setConferenceId(conferenceId);
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("createWhisperGroup" , "cwg", request);

	    String[] tokens = value.split(":");

	    if (tokens.length < 2) {
		throw new ParseException("You must specify both "
		    + " a conferenceId and a whisperGroupId", 0);
	    }

	    String conferenceId = tokens[0];
	    String whisperGroupId = tokens[1];

	    double attenuation = WhisperGroup.getDefaultAttenuation();

	    if (tokens.length == 3) {
		attenuation = getDouble(tokens[2]);
	    }

	    try {
	        ConferenceManager.createWhisperGroup(conferenceId,
		    whisperGroupId, attenuation);
	    } catch (ParseException e) {
		throw new ParseException("Can't create Whisper group "
		    + whisperGroupId + " " + e.getMessage(), 0);
	    }
	    return true;
	} catch (ParameterException e) {
	}

        try {
            MemberReceiver.deferMixing(
                getBooleanValue("deferMixing", "dm", request));
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("destroyWhisperGroup" , "dwg", request);

	    String[] tokens = value.split(":");

	    if (tokens.length != 2) {
		throw new ParseException("You must specify both "
		   + " a conferenceId and a whisperGroupId", 0);
	    }

	    ConferenceManager.destroyWhisperGroup(tokens[0], tokens[1]);
	    return true;
	} catch (ParameterException e) {
	}

        try {
            CallSetupAgent.setDefaultCallAnswerTimeout(
                getIntegerValue("callAnswerTimeout", "cat", request));
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("doNotRecord" , "dnr", request);
	    booleanValue = getBoolean(value);
	    String callId = getQualifierString(value);

	    if (callId == null) {
		return false;  // let call setup handle it
	    }

	    try {
	        CallHandler.setDoNotRecord(callId, booleanValue);
	    } catch (NoSuchElementException e) {
		throw new ParseException(
                    "Invalid callId specified:  " + request, 0);
	    }
	    lastCallId = callId;
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("dtmfSuppression", "ds", request);
	    booleanValue = getBoolean(value);

	    String callId = getQualifierString(value);

	    if (callId == null) {
		return false;  // let call setup handle it
	    }
	    try {
	        CallHandler.setDtmfSuppression(callId, booleanValue);
	    } catch (NoSuchElementException e) {
		throw new ParseException(
                    "Invalid callId specified:  " + request, 0);
	    }
	    lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("drop" , "", request);

	    integerValue = getInteger(value);
            String callId = getQualifierString(value);

            if (callId == null) {
                throw new ParseException("missing call id " + request, 0);
            }

            CallHandler.setDropPackets(callId, integerValue);
            lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

	try {
	    CallHandler.setDuplicateCallLimit(
	        getIntegerValue("duplicateCallLimit" , "dcl", request));
	    return true;
	} catch (ParameterException e) {
	}

	try {
            booleanValue = getBooleanValue("directConferencing", "dc", request);
	    IncomingCallHandler.setDirectConferencing(booleanValue);
            return true;
        } catch (ParameterException e) {
        }

        try {
            parameterMatch("distributedConferenceInfo", "dci", request);
	    requestHandler.writeToSocket(
                ConferenceManager.getDistributedConferenceInfo());
            return true;
        } catch (ParameterException e) {
        }

        try {
            parameterMatch("dropDb", "", request);
            ConferenceManager.dropDb();
            return true;
        } catch (ParameterException e) {
        }

        try {
            CallHandler.enablePSTNCalls(
		getBooleanValue("enablePSTNCalls" , "epc", request));

	    return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("endConference" , "ec", request);
	    ConferenceManager.endConference(value);
	    return true;
        } catch (ParameterException e) {
        }

        try {
            int firstRtpPort =
                getIntegerValue("firstRtpPort", "frp", request);

            ConferenceMember.setFirstRtpPort(firstRtpPort);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    parameterMatch("flush", "", request);
	    Logger.flush();
	    Logger.println("Logger flushed...");
	    return true;
        } catch (ParameterException e) {
        }

        try {
            MixManager.setForcePrivateMix(
                getBooleanValue("forcePrivateMix", "fpm", request));
	    return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("forwardData", "fd", request);

	    String[] tokens = value.split(":");

	    if (tokens.length < 2) {
		throw new ParseException("Missing parameters:  " + request, 0);
	    }

	    CallHandler dest = CallHandler.findCall(tokens[0]);

	    if (dest == null) {
		throw new ParseException("Invalid callId:  " + tokens[0], 0);
	    }

	    CallHandler src = CallHandler.findCall(tokens[1]);

	    if (src == null) {
		throw new ParseException("Invalid callId:  " + tokens[1], 0);
	    }

	    src.getMember().getMemberReceiver().addForwardMember(
		dest.getMember().getMemberSender());

	    return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("forwardDtmfKeys" , "fdtmf", request);
            MemberReceiver.setForwardDtmfKeys(getBoolean(value));
            return true;
        } catch (ParameterException e) {
        }

	try {
	    parameterMatch("gc", "", request);
	    System.gc();
	    return true;
        } catch (ParameterException e) {
        }


	try {
	    parameterMatch("gcs", "", request);
	    requestHandler.writeToSocket(CallHandler.getCallStateForAllCalls());
	    return true;
        } catch (ParameterException e) {
	    try {
	        String callId = getValue("getCallState" , "gcs", request);

	        CallHandler callHandler = CallHandler.findCall(callId);

	        if (callHandler == null) {
                    throw new ParseException("Invalid callId:  " + callId, 0);
	        }

	        requestHandler.writeToSocket(callHandler.getCallState());
	        return true;
            } catch (ParameterException ee) {
	    }
        }

        try {
	    parameterMatch("gmd", "", request);
	    requestHandler.writeToSocket(
		CallHandler.getAllAbbreviatedMixDescriptors());
	    return true;
	} catch (ParameterException e) {
            try {
                String callId = getValue("getMixDescriptors" , "gmd", request);

                CallHandler callHandler = CallHandler.findCall(callId);

                if (callHandler == null) {
                    throw new ParseException("Invalid callId:  " + callId, 0);
                }

	        requestHandler.writeToSocket("MixDescriptors for "
		    + callId + "\n"
	            + callHandler.getMember().getAbbreviatedMixDescriptors());
                return true;
            } catch (ParameterException ee) {
            }
	}

        try {
            parameterMatch("gmdl", "", request);
            requestHandler.writeToSocket(CallHandler.getAllMixDescriptors());
            return true;
        } catch (ParameterException e) {
            try {
                String callId = getValue("getMixDescriptors" , "gmdl", request);

                CallHandler callHandler = CallHandler.findCall(callId);

                if (callHandler == null) {
                    throw new ParseException("Invalid callId:  " + callId, 0);
                }

                requestHandler.writeToSocket(callId + ":  "
                    + callHandler.getMember().getMixDescriptors());
                return true;
            } catch (ParameterException ee) {
            }
        }

        try {
            parameterMatch("gs", "", request);
            requestHandler.writeToSocket(
		"Conferences:\t"
            	+ ConferenceManager.getNumberOfConferences()
        	+ "\nCalls:\t\t" + ConferenceManager.getTotalMembers()
        	+ "\nSpeaking:\t" + CallHandler.getTotalSpeaking()
		+ "\n" + ConferenceManager.getBriefConferenceInfo());
            return true;
        } catch (ParameterException e) {
        }

	try {
	    parameterMatch("help", "h", request);
	    displayCallParameters();
	    displayImmediateCommands();
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("incomingCallTreatment", "ict", request);
	    IncomingCallHandler.setIncomingCallTreatment(value);
	    return true;
        } catch (ParameterException e) {
        }

        try {
            booleanValue = getBooleanValue(
                "incomingCallVoiceDetection", "icvd", request);

            IncomingCallHandler.setIncomingCallVoiceDetection(booleanValue);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("internationalPrefix", "ip", request);

	    if (value.equals("\"\"") || value.equals("''")) {
		value = "";
	    }

	    RequestHandler.setInternationalPrefix(value);
	    return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("conferenceJoinTreatment", "jt", request);

            String[] tokens = value.split(":");

            if (tokens.length == 1) {
                cp.setConferenceJoinTreatment(tokens[0]);
                return true;
            }

            ConferenceManager.setConferenceJoinTreatment(tokens[1], tokens[0]);
            return true;
	} catch (ParameterException e) {
        }

	try {
	    value = getValue("conferenceLeaveTreatment", "lt", request);

            String[] tokens = value.split(":");

            if (tokens.length == 1) {
                cp.setConferenceLeaveTreatment(tokens[0]);
                return true;
            }

            ConferenceManager.setConferenceLeaveTreatment(tokens[1], tokens[0]);
	    return true;
	} catch (ParameterException e) {
        }

	try {
            int lastRtpPort = getIntegerValue("lastRtpPort", "frp", request);

            ConferenceMember.setLastRtpPort(lastRtpPort);
            return true;
	} catch (ParameterException e) {
        }

	try {
	    value = getValue("localhostSecurity" , "lhs", request);
	    Bridge.setLocalhostSecurity(getBoolean(value));
	    return true;
	} catch (ParameterException e) {
	}

	try {
            Logger.logLevel = getIntegerValue("logLevel" , "l", request);

	    if (Logger.logLevel <= Logger.LOG_PRODUCTION) {
                StunClient.setLogLevel(Level.INFO);
                StunServerImpl.setLogLevel(Level.INFO);
            } else if (Logger.logLevel >= Logger.LOG_INFO) {
                StunClient.setLogLevel(Level.FINE);
                StunServerImpl.setLogLevel(Level.INFO);
            } else if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                StunClient.setLogLevel(Level.FINER);
                StunServerImpl.setLogLevel(Level.INFO);
            } else if (Logger.logLevel >= Logger.LOG_DETAIL) {
                StunClient.setLogLevel(Level.FINEST);
                StunServerImpl.setLogLevel(Level.INFO);
            }

	    return true;
	} catch (ParameterException e) {
	}

	try {
	    ConferenceManager.setLoneReceiverPort(
		getIntegerValue("loneReceiverPort", "lrp", request));

	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("longDistancePrefix", "ldp", request);

	    if (value.equals("\"\"") || value.equals("''")) {
		value = "";
	    }

	    RequestHandler.setLongDistancePrefix(value);
	    return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("migrateToBridge" , "mtb", request);

	    String[] tokens = value.split(":");

	    if (tokens.length != 3) {
                throw new ParseException("Missing parameters:  " + request, 0);
	    }

	    String bridge = tokens[0];
	    String port = tokens[1];
            String callId = tokens[2];

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + callId);
                throw new ParseException("Invalid callId: " + callId, 0);
            }

	    CallParticipant cp = callHandler.getCallParticipant();

	    if (cp.getInputTreatment() != null) {
		cp.setPhoneNumber(null);
	    }

    	    BridgeConnector bridgeConnector;

            int serverPort;

	    try {
		serverPort = Integer.parseInt(port);
	    } catch (NumberFormatException e) {
		Logger.println("Invalid bridge server port:  " + port);
		throw new ParseException("Invalid bridge server port:  " + port, 0);
	    }

	    try {
	        bridgeConnector = new BridgeConnector(bridge, serverPort, 5000);
	    } catch (IOException e) {
                Logger.println("Unable to connect to bridge " + bridge
		    + " " + e.getMessage());
                throw new ParseException("Unable to connect to bridge "
		    + bridge + " " + e.getMessage(), 0);
	    }

	    callHandler.suppressStatus(true);

	    try {
		String s = cp.getCallSetupRequest();

		s = s.substring(0, s.length() - 1);  // get rid of last new line
		bridgeConnector.sendCommand(s);
	    } catch (IOException e) {
		Logger.println("Unable to send command to bridge:  "
		    + e.getMessage());
                throw new ParseException("Unable to send command to bridge:  "
		    + e.getMessage(), 0);
	    }

	    bridgeConnector.addCallEventListener(requestHandler);
	    // XXX need to figure out how to deal with Private Mixes
	    // now that the call has moved!
            return true;
        } catch (ParameterException e) {
        }


        try {
            ConferenceSender.setSenderThreads(
		getIntegerValue("senderThreads" , "st", request));

            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("minJitterBufferSize" , "minjb", request);

            String[] tokens = value.split(":");

	    if (tokens.length < 2) {
                throw new ParseException("Missing parameters: " + value, 0);
	    }

	    int minJitterBufferSize = getInteger(tokens[0]);

            CallHandler callHandler = CallHandler.findCall(tokens[1]);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + tokens[1]);
                throw new ParseException("Invalid callId: " + tokens[1], 0);
            }

            callHandler.getMember().getMemberReceiver().setMinJitterBufferSize(
		minJitterBufferSize);
            return true;

        } catch (ParameterException e) {
        }

        try {
            value = getValue("maxJitterBufferSize" , "maxjb", request);

            String[] tokens = value.split(":");

            if (tokens.length < 2) {
                throw new ParseException("Missing parameters: " + value, 0);
            }

            int maxJitterBufferSize = getInteger(tokens[0]);

            CallHandler callHandler = CallHandler.findCall(tokens[1]);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + tokens[1]);
                throw new ParseException("Invalid callId: " + tokens[1], 0);
            }

            callHandler.getMember().getMemberReceiver().setMaxJitterBufferSize(
                maxJitterBufferSize);

	    return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("monitorCallStatus" , "mcs", request);
	    booleanValue = getBoolean(value);
	    String callId = getQualifierString(value);

	    if (callId == null) {
		throw new ParseException(
                    "callId must be specified:  " + request, 0);
	    }

	    lastCallId = callId;
	    requestHandler.monitorCallStatus(callId, booleanValue);
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("monitorConferenceStatus" , "mcc", request);
            booleanValue = getBoolean(value);
            String conferenceId = getQualifierString(value);

            if (conferenceId == null) {
                throw new ParseException(
                    "conferenceId must be specified:  " + request, 0);
            }

            requestHandler.monitorConferenceStatus(conferenceId, booleanValue);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("monitorIncomingCalls" , "mic", request);
	    booleanValue = getBoolean(value);

	    if (requestHandler.monitorIncomingCalls(booleanValue) == false) {
		//throw new ParseException(
		//    "There is already an incoming call monitor!", 0);
	    }
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("monitorOutgoingCalls" , "moc", request);
	    booleanValue = getBoolean(value);

	    requestHandler.monitorOutgoingCalls(booleanValue);
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("mute" , "m", request);
	    booleanValue = getBoolean(value);
	    String callId = getQualifierString(value);

	    if (callId == null) {
		return false;  // let call setup handle this.
	    }

	    CallHandler.setMuted(callId, booleanValue);
	    lastCallId = callId;
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("muteWhisperGroup" , "mwg", request);
            booleanValue = getBoolean(value);
            String callId = getQualifierString(value);

            if (callId == null) {
		return false;  // let call setup handle it
            }

            CallHandler.setMuteWhisperGroup(callId, booleanValue);
            lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("muteConference" , "mc", request);
	    booleanValue = getBoolean(value);
	    String callId = getQualifierString(value);

	    if (callId == null) {
	    	return false;  // let call setup handle it.
	    }

	    CallHandler.setConferenceMuted(callId, booleanValue);
	    lastCallId = callId;
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("numberOfCalls", "nm", request);

            String[] tokens = value.split(":");

            if (tokens.length != 1) {
                throw new ParseException("You must specify a conference id", 0);
            }

	    CallEvent event = new CallEvent(CallEvent.NUMBER_OF_CALLS);

	    event.setNumberOfCalls(
		+ ConferenceManager.getNumberOfMembers(tokens[0]));

	    requestHandler.writeToSocket(event.toString());
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("outsideLinePrefix", "olp", request);

	    if (value.equals("\"\"") || value.equals("''")) {
		value = "";
	    }

	    RequestHandler.setOutsideLinePrefix(value);
	    return true;
        } catch (ParameterException e) {
        }

	try {
	    integerValue = getIntegerValue("pause" , "", request);
	    ConferenceReceiver.setReceiverPause(integerValue);
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("pauseTreatmentToCall" , "", request);

	    String[] tokens = value.split(":");

            CallHandler callHandler = CallHandler.findCall(tokens[0]);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + tokens[0]);
                throw new ParseException("Invalid callId: " + tokens[0], 0);
            }

	    String treatmentId = null;

	    if (tokens.length > 1) {
		treatmentId = tokens[1];
	    }

            callHandler.getMember().pauseTreatment(treatmentId, true);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("pauseTreatmentToConference" , "" , request);
            String treatment = getTreatment(value);

            value = value.substring(treatment.length());

            String conferenceId = getQualifierString(value);

            if (conferenceId == null) {
                throw new ParseException(
                    "conferenceId must be specified:  " + request, 0);
            }

            cp.setConferenceId(conferenceId);
            ConferenceManager.pauseTreatment(conferenceId, treatment, true);
            return true;
        } catch (ParameterException e) {
        }

	try {
            value = getValue("playTreatmentToCall" , "ptc", request);
            String treatment = getTreatment(value);

	    double volume[] = getVolume(value);

	    value = value.substring(treatment.length());

            String callId = getQualifierString(value);

            if (callId == null) {
		callId = lastCallId;
	    }

            if (callId == null) {
                throw new ParseException(
                    "callId must be specified:  " + request, 0);
            }

	    try {
		// XXX need to pass volume
	        CallHandler.playTreatmentToCall(callId, treatment);
            } catch (NoSuchElementException e) {
                throw new ParseException(
                    "Invalid callId specified:  " + request, 0);
            } catch (IOException e) {
                throw new ParseException(
                    "Unable to read treatment file " + treatment
		    + " " + e.getMessage(), 0);
            }
	    lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("playTreatmentToConference" , "pc", request);
	    String treatment = getTreatment(value);

	    double[] volume = getVolume(value);

	    value = value.substring(treatment.length());

	    String conferenceId = getQualifierString(value);

	    if (conferenceId == null) {
		conferenceId = cp.getConferenceId();
	    }

	    if (conferenceId == null) {
		throw new ParseException(
                    "conferenceId must be specified:  " + request, 0);
	    }

	    cp.setConferenceId(conferenceId);
	    // XXX need to pass volume
	    ConferenceManager.playTreatment(conferenceId, treatment);
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("playTreatmentToAllConferences" , "pca", request);
            String treatment = getTreatment(value);

            double[] volume = getVolume(value);

            value = value.substring(treatment.length());
	    // XXX need to pass volume
            ConferenceManager.playTreatmentToAllConferences(treatment);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("packetLossConcealmentClass" , "plcc", request);

            String[] tokens = value.split(":");

            if (tokens.length < 2) {
                throw new ParseException("Missing parameters: " + value, 0);
            }

            CallHandler callHandler = CallHandler.findCall(tokens[1]);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + tokens[1]);
                throw new ParseException("Invalid callId: " + tokens[1], 0);
            }

            callHandler.getMember().getMemberReceiver().setPlcClassName(
                tokens[0]);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("powerThresholdLimit" , "ptl", request);

            double powerThresholdLimit = getDouble(value);

            String callId = getQualifierString(value);

            CallHandler.setPowerThresholdLimit(callId, powerThresholdLimit);
            lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("prefixPhoneNumber" , "ppn", request);
	    booleanValue = getBoolean(value);
	    RequestHandler.setPrefixPhoneNumber(booleanValue);
	    return true;
	} catch (ParameterException e) {
	}

	if (request.equalsIgnoreCase("printStatistics") ||
	        request.equalsIgnoreCase("ps")) {

	    ConferenceManager.printStatistics();
	    return true;
	}

	try {
	    String s = getValue("privateMix", "pm", request);

	    String tokens[] = s.split(":");

	    if (tokens.length < 3) {
		throw new ParseException("missing parameters " + s, 0);
	    }

	    String callId = tokens[tokens.length - 2];

	    CallHandler callHandler = CallHandler.findCall(callId);

	    if (callHandler == null) {
                throw new ParseException("Invalid callId:  " + callId, 0);
	    }

	    String privateMixCallId = tokens[tokens.length - 1];

	    //if (callId.equals(privateMixCallId)) {
            //    throw new ParseException(
	    //	    "Can't set private mix for self " + callId, 0);
	    //}

	    CallHandler privateMixCallHandler =
		CallHandler.findCall(privateMixCallId);

	    if (privateMixCallHandler == null) {
                throw new ParseException("Invalid callId:  "
		    + privateMixCallId, 0);
	    }

	    double[] volume = new double[tokens.length - 2];

	    for (int i = 0; i < volume.length; i++) {
		try {
		    volume[i] = Double.parseDouble(tokens[i]);
		} catch (NumberFormatException e) {
	    	    throw new ParseException("Invalid floating point value:  "
			+ tokens[i], 0);
		}
	    }

	    if (volume.length == 1) {
		double v = volume[0];

		volume = new double[4];

		volume[0] = v;
		volume[3] = v;
	    }

	    double[] spatialValues = new double[4];

	    spatialValues[0] = 1;	// front/back
	    spatialValues[2] = 0;	// up/down

	    if (volume[1] == 0) {
		spatialValues[1] = -volume[2];  // leftRight
	        spatialValues[3] = volume[0];	// volume
	    } else if (volume[2] == 0) {
		spatialValues[1] = volume[1];  // leftRight
	        spatialValues[3] = volume[3];	// volume
	    } else {
		throw new ParseException("Invalid private mix:  ", 0);
	    }

	    privateMixCallHandler.getMember().setPrivateMix(
		callHandler.getMember(), spatialValues);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("xp" , "", request);
	    RequestHandler.ignorePmx(getBoolean(value));
	    return true;
        } catch (ParameterException e) {
        }

	try {
	    /*
	     * pmx = <FrontBack> : <LeftRight> : <upDown> : <volume> : <callId> : <pmCallId>
	     */
	    String s = getValue("pmx", "", request);

	    String[] tokens = s.split(":");

	    if (tokens.length < 6) {
		if (tokens.length != 5) {
		    throw new ParseException("missing parameters " + s, 0);
		}

		/*
		 * For compatibility before up/down was added
		 */
		String[] t = new String[6];

		t[0] = tokens[0];
		t[1] = tokens[1];
		t[2] = "0";
		t[3] = tokens[2];
		t[4] = tokens[3];
		t[5] = tokens[4];

		tokens = t;
	    }

	    double[] spatialValues = new double[4];

	    try {
		// frontBack
                spatialValues[0] = Double.parseDouble(tokens[0]);
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid front/back value:  "
                    + tokens[0], 0);
            }

	    try {
		// leftRight
                spatialValues[1] = Double.parseDouble(tokens[1]);
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid left/right value:  "
                    + tokens[1], 0);
            }

	    try {
		// upDown
                spatialValues[2] = Double.parseDouble(tokens[2]);
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid up/down value:  "
                    + tokens[2], 0);
            }

	    try {
		spatialValues[3] = Double.parseDouble(tokens[3]);
	    } catch (NumberFormatException e) {
	    	throw new ParseException("Invalid volume value:  "
		    + tokens[3], 0);
	    }

	    String callId = tokens[4];

	    CallHandler callHandler = CallHandler.findCall(callId);

	    if (callHandler == null) {
                throw new ParseException("Invalid callId:  " + callId, 0);
	    }

	    String privateMixCallId = tokens[5];

	    CallHandler privateMixCallHandler =
		CallHandler.findCall(privateMixCallId);

	    if (privateMixCallHandler == null) {
                throw new ParseException("Invalid callId:  "
		    + privateMixCallId, 0);
	    }

	    privateMixCallHandler.getMember().setPrivateMix(
		callHandler.getMember(), spatialValues);

            return true;
        } catch (ParameterException e) {
        }

	try {
            String s = getValue("recordConference" , "rc", request);

            String tokens[] = s.split(":");

            if (tokens.length < 2) {
                throw new ParseException("missing parameters " + s, 0);
            }

	    booleanValue = getBoolean(tokens[0]);

            String conferenceId = tokens[1];

	    String recordingFile = null;
	    String type = null;

            if (booleanValue == true) {
		if (tokens.length < 3) {
                    throw new ParseException("missing parameters " + s, 0);
		}
	        recordingFile = tokens[2];

		//Recorder.checkPermission(recordingFile);

		if (tokens.length > 3) {
		    type = tokens[3];
		}
            }

	    ConferenceManager.recordConference(conferenceId, booleanValue,
		recordingFile, type);
	    return true;
	} catch (ParameterException e) {
	}

        try {
	    value = getValue("remoteMediaInfo", "rm", request);

	    //XXX remoteMediaInfo=<String>:<callId> won't work
	    //	if the String has a : in it.  FIX ME!

	    String tokens[] = value.split("\\+");

	    String callId = tokens[tokens.length - 1];

	    if (callId.indexOf(":") == 0) {
		callId = callId.substring(1);
	    } else {
		callId = null;
	    }

            if (callId == null) {
                cp.setRemoteMediaInfo(value);
            } else {
                CallHandler.setRemoteMediaInfo(callId, value);
                lastCallId = callId;
            }
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("restartInputTreatment" , "rit", request);

            String[] tokens = value.split(":");

            CallHandler callHandler = CallHandler.findCall(tokens[0]);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + tokens[0]);
                throw new ParseException("Invalid callId: " + tokens[0], 0);
            }

            callHandler.getMember().getMemberReceiver().restartInputTreatment();
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("resumeTreatmentToCall" , "", request);

	    String[] tokens = value.split(":");

            CallHandler callHandler = CallHandler.findCall(tokens[0]);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + tokens[0]);
                throw new ParseException("Invalid callId: " + tokens[0], 0);
            }

	    String treatmentId = null;

	    if (tokens.length > 1) {
		treatmentId = tokens[1];
	    }

            callHandler.getMember().pauseTreatment(treatmentId, false);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("resumeTreatmentToConference" , "" , request);
            String treatment = getTreatment(value);

            value = value.substring(treatment.length());

            String conferenceId = getQualifierString(value);

            if (conferenceId == null) {
                throw new ParseException(
                    "conferenceId must be specified:  " + request, 0);
            }

            cp.setConferenceId(conferenceId);
            ConferenceManager.pauseTreatment(conferenceId, treatment, false);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    Recorder.setDefaultRecordingDirectory(
		getValue("recordingDirectory", "rd", request));
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    String s = getValue("recordFromMember" , "rfm", request);

            String tokens[] = s.split(":");

            if (tokens.length < 2) {
                throw new ParseException("missing parameters " + s, 0);
            }

	    booleanValue = getBoolean(tokens[0]);

            String callId = tokens[1];

	    String recordingFile = null;
	    String type = null;

            if (booleanValue == true) {
		if (tokens.length < 3) {
                    throw new ParseException("missing parameters " + s, 0);
		}
	        recordingFile = tokens[2];

		//Recorder.checkPermission(recordingFile);

		if (tokens.length > 3) {
		    type = tokens[3];
		}
            }

	    if (booleanValue == true && callId.equals("0")) {
		cp.setFromRecordingFile(recordingFile);
		cp.setFromRecordingType(type);
	    } else {
	        try {
	            CallHandler.recordMember(callId, booleanValue,
			recordingFile, type, true);
	        } catch (NoSuchElementException e) {
		    throw new ParseException(
                        "Invalid callId specified:  " + request, 0);
	        } catch (IOException e) {
		    throw new ParseException(e.getMessage(), 0);
		}
	    }
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    String s = getValue("recordToMember" , "rtm", request);

            String tokens[] = s.split(":");

            if (tokens.length < 2) {
                throw new ParseException("missing parameters " + s, 0);
            }

	    booleanValue = getBoolean(tokens[0]);

            String callId = tokens[1];

	    String recordingFile = null;
	    String type = null;

            if (booleanValue == true) {
		if (tokens.length < 3) {
                    throw new ParseException("missing parameters " + s, 0);
		}
	        recordingFile = tokens[2];

		//Recorder.checkPermission(recordingFile);

		if (tokens.length > 3) {
		    type = tokens[3];
		}
            }

	    if (booleanValue == true && callId.equals("0")) {
		cp.setToRecordingFile(recordingFile);
		cp.setToRecordingType(type);
	    } else {
	        try {
	            CallHandler.recordMember(callId, booleanValue,
			recordingFile, type, false);
	        } catch (NoSuchElementException e) {
		    throw new ParseException(
                        "Invalid callId specified:  " + request, 0);
	        } catch (IOException e) {
		    throw new ParseException(e.getMessage(), 0);
		}
	    }
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    value = getValue("releaseCalls" , "", request);
	    booleanValue = getBoolean(value);
	    requestHandler.setReleaseCalls(booleanValue);
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("removeCallFromWhisperGroup" , "rcwg", request);

            int ix = value.indexOf(":");

            if (ix < 0) {
                throw new ParseException("No callId's specified ", 0);
            }

            String whisperGroupId = value.substring(0, ix);

            String callId = value.substring(ix + 1);

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                throw new ParseException("Invalid callId:  " + callId, 0);
            }

            /*
             * remove call from the whisper group
             */
            callHandler.getMember().removeCall(whisperGroupId);
            return true;
        } catch (ParameterException e) {
        }

	try{
	    parameterMatch("resume", "", request);

	    requestHandler.resumeBridge();
	   return true;
	}  catch (ParameterException e) {
        }

	try {
	    RtpSocket.setRtpTimeout(getIntegerValue("rtpTimeout", "rt", request));
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("removeConference" , "rconf", request);

	    ConferenceManager.removeConference(value);
            return true;
        } catch (ParameterException e) {
        }

        try {
            SipServer.setSendSipUriToProxy(
                getBooleanValue("sendSipUriToProxy", "stp", request));
            return true;
        } catch (ParameterException e) {
        }

	try {
	    String s = getValue("traceCall" , "", request);

            String tokens[] = s.split(":");

            if (tokens.length < 2) {
                throw new ParseException("Missing parameters", 0);
            }

	    booleanValue = getBoolean(tokens[0]);

            String callId = tokens[1];

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                throw new ParseException(
                    "Invalid callId:  " + callId, 0);
            }

            callHandler.getMember().traceCall(booleanValue);
            lastCallId = callId;
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("transferCall" , "tc", request);
            String callId = getString(value);

	    if (callId == null) {
		callId = lastCallId;
	    }

            if (callId == null) {
                throw new ParseException(
                    "callId must be specified:  " + request, 0);
            }

            String conferenceId = getQualifierString(value);

	    try {
                IncomingCallHandler.transferCall(callId, conferenceId);
	    } catch (NoSuchElementException e) {
                Logger.println("Can't transfer call: " + e.getMessage());

		throw new ParseException(
		    "Can't transfer call " + callId + " to conference "
		    + conferenceId + " " + e.getMessage(), 0);
	    } catch (IOException e) {
                Logger.println("Can't transfer call: " + e.getMessage());

                throw new ParseException(
                    "Can't transfer call " + callId + " to conference "
                    + conferenceId + " " + e.getMessage(), 0);
	    }

	    cp.setCallId(callId);
	    cp.setConferenceId(conferenceId);
	    lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

        try {
            parameterMatch("showWhisperGroups" , "swg", request);

	    String s = ConferenceManager.getAbbreviatedWhisperGroupInfo(true);
	    requestHandler.writeToSocket(s);
            return true;
        } catch (ParameterException e) {
        }

        try {
            parameterMatch("showWhisperGroups" , "swgl", request);

            String s = ConferenceManager.getWhisperGroupInfo();
            requestHandler.writeToSocket(s);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("silenceMainConference" , "smc", request);
            booleanValue = getBoolean(value);
            String callId = getQualifierString(value);

            if (callId == null) {
		throw new ParseException("missing call id " + request, 0);
            }

            CallHandler.setConferenceSilenced(callId, booleanValue);
            lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("spatialBehindVolume" , "sev", request);
            SunSpatialAudio.setSpatialBehindVolume(getDouble(value));
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("spatialEchoDelay" , "sed", request);
            SunSpatialAudio.setSpatialEchoDelay(getDouble(value));
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("spatialEchoVolume" , "sev", request);
            SunSpatialAudio.setSpatialEchoVolume(getDouble(value));
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("spatialFalloff" , "sfo", request);
            SunSpatialAudio.setSpatialFalloff(getDouble(value));
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("spatialMinVolume" , "smv", request);
            SunSpatialAudio.setSpatialMinVolume(getDouble(value));
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("startInputTreatment" , "sti", request);

	    String treatment = getTreatment(value);

	    String callId = getQualifierString(value);

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + callId);
                throw new ParseException("Invalid callId: " + callId, 0);
            }

            callHandler.getMember().getMemberReceiver().startInputTreatment(treatment);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("statistics", "stat", request);

	    String[] tokens = value.split(":");

	    if (tokens.length < 1) {
                throw new ParseException("Missing parameters: " + request, 0);
	    }

	    // TODO:  Maybe allow specification of what stats are desired

	    try {
	        requestHandler.setStatisticsTimeout(Integer.parseInt(tokens[0]));
	    } catch (NumberFormatException e) {
                throw new ParseException("invalid timeout value: " + request, 0);
	    }

	    return true;
        } catch (ParameterException e) {
        }

        try {
            String callId = getValue("stopInputTreatment" , "sit", request);

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                Logger.println("Invalid callId:  " + callId);
                throw new ParseException("Invalid callId: " + callId, 0);
            }

            callHandler.getMember().getMemberReceiver().stopInputTreatment();
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("stopTreatmentToConference" , "sc", request);
            String treatment = getTreatment(value);

            value = value.substring(treatment.length());

            String conferenceId = getQualifierString(value);

            if (conferenceId == null) {
                throw new ParseException(
                    "conferenceId must be specified:  " + request, 0);
            }

            cp.setConferenceId(conferenceId);
            ConferenceManager.stopTreatment(conferenceId, treatment);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("stopTreatmentToCall" , "stc", request);

            String[] tokens = value.split(":");

	    CallHandler callHandler = CallHandler.findCall(tokens[0]);

            if (callHandler == null) {
		Logger.println("Invalid callId:  " + tokens[0]);
                throw new ParseException("Invalid callId: " + tokens[0], 0);
            }

            String treatmentId = null;

            if (tokens.length > 1) {
                treatmentId = tokens[1];
            }

            callHandler.getMember().stopTreatment(treatmentId);
	    return true;
        } catch (ParameterException e) {
        }

	try {
	    parameterMatch("shutdown", "", request);
	    /*
             * This is a request for us to exit.
	     * End any calls in progress and exit.
             */
            CallHandler.shutdown();   	// end calls
            Logger.flush();
            System.exit(0); // this call doesn't return
	} catch (ParameterException e) {
	}

	try{
	    parameterMatch("suspend", "", request);

	    requestHandler.suspendBridge(0);
	   return true;
	}  catch (ParameterException e) {
        }

	try{
	    integerValue = getIntegerValue("suspend", "", request);
	    requestHandler.suspendBridge(integerValue);
	   return true;
	}  catch (ParameterException e) {
        }

	try {
	   synchronousMode = getBooleanValue("synchronousMode", "sm", request);
	   return true;
	}  catch (ParameterException e) {
        }

	try {
	    integerValue = getIntegerValue("shutdown", "", request);
	    /*
             * This is a request for us to exit after some number of seconds.
	     * Wait, then end any calls in progress and exit.
             */
            CallHandler.shutdown(integerValue);   	// end calls
            Logger.flush();
            System.exit(0); // this call doesn't return
	} catch (ParameterException e) {
	}

        try {
            value = getValue("defaultProtocol", "dp", request);

	    if (value.equalsIgnoreCase("h.323")) {
		value = "h323";
	    }

	    if (value.equalsIgnoreCase("h323") == false &&
		    value.equalsIgnoreCase("SIP") == false) {

		throw new ParseException("Invalid protocol:  " +
		    value, 0);
	    }

            Bridge.setDefaultProtocol(value);
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("defaultSipProxy", "dsp", request);
            SipServer.setDefaultSipProxy(value);
            return true;
        } catch (ParameterException e) {
        }

        try {
            String s = getValue("setInputVolume", "siv", request);

            String tokens[] = s.split(":");

            if (tokens.length != 2) {
                throw new ParseException("invalid number of parameters " + s, 0);
            }

            String callId = tokens[1];

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                throw new ParseException("Invalid callId:  " + callId, 0);
            }

            double volume;

            try {
                volume = Double.parseDouble(tokens[0]);
	    } catch (NumberFormatException e) {
                throw new ParseException("Invalid floating point value:  "
                    + tokens[0], 0);
            }

            callHandler.getMember().setInputVolume(volume);
            return true;
        } catch (ParameterException e) {
        }

        try {
            String s = getValue("setOutputVolume", "sov", request);

            String tokens[] = s.split(":");

            if (tokens.length != 2) {
                throw new ParseException("invalid number of parameters " + s, 0);
            }

            String callId = tokens[1];

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null) {
                throw new ParseException("Invalid callId:  " + callId, 0);
            }

	    double volume;

            try {
                volume = Double.parseDouble(tokens[0]);
	    } catch (NumberFormatException e) {
                throw new ParseException("Invalid floating point value:  "
                    + tokens[0], 0);
            }

            callHandler.getMember().setOutputVolume(volume);
            return true;
        } catch (ParameterException e) {
        }

	try {
	    parameterMatch("timestamp", "ts", request);
    	    /*
     	     * This is solely for debugging.
     	     *
     	     * Write a line to log and send a packet
     	     * to the Cisco gateway port 9 (DISCARD port).
     	     *
     	     * These timestamp messages may help identify the place in the log
     	     * or snoop trace to look for problems.
     	     */
	    long now = System.currentTimeMillis();

	    Logger.println("TIMESTAMP:  " + now);
	    requestHandler.writeToSocket("TIMESTAMP:  " + now);
	    Bridge.sendMarkerPacket("User timestamp...");
	    return true;
	} catch (ParameterException e) {
	}

	try {
	    parameterMatch("tuneableparameters" , "tp", request);
	    displayTuneableParameters();
	    return true;
	} catch (ParameterException e) {
	    try {
	        String path = getValue("tuneableparameters" , "tp", request);
		// XXX need to open file and write parameters to a file.
	        displayTuneableParameters();
	        return true;
	    } catch (ParameterException ee) {
	    }
	}

        try {
            ConferenceManager.useSingleSender(
		getBooleanValue("useSingleSender", "uss", request));
            return true;
        } catch (ParameterException e) {
        }

        try {
            SdpManager.useTelephoneEvent(
                getBooleanValue("useTelephoneEvent", "ute", request));
            return true;
        } catch (ParameterException e) {
        }

        try {
	    value = getValue("voiceDetectionWhileMuted", "vm", request);
	    booleanValue = getBoolean(value);
	    String callId = getQualifierString(value);

            if (callId == null) {
	        return false;  // let call setup handle it
	    }

	    CallHandler.setVoiceDetectionWhileMuted(callId, booleanValue);
            lastCallId = callId;
            return true;
        } catch (ParameterException e) {
        }

        try {
            value = getValue("voIPGateways", "vgs", request);
            if (SipServer.setVoIPGateways(value) == false) {
		throw new ParseException("invalid host or IP address "
		    + value, 0);
	    }
            return true;
        } catch (ParameterException e) {
        }

	try {
	    String s = getValue("whisperGroupOptions", "wgo", request);

            String tokens[] = s.split(":");

            if (tokens.length < 3) {
                throw new ParseException("missing parameters " + s, 0);
            }

	    String conferenceId = tokens[0];
	    String whisperGroupId = tokens[1];

	    for (int i = 2; i < tokens.length; i++) {
		String[] options = tokens[i].split("=");

		if (options.length != 2) {
                    throw new ParseException("invalid option " + s, 0);
		}

		if (options[0].equalsIgnoreCase("transient")) {
		    boolean b;

		    if (options[1].equalsIgnoreCase("true") ||
			    options[1].equalsIgnoreCase("t")) {

			b = true;
		    } else if (options[1].equalsIgnoreCase("false") ||
			    options[1].equalsIgnoreCase("f")) {

			b = false;
		    } else {
                        throw new ParseException("invalid option value " + s, 0);
		    }

		    ConferenceManager.setTransientWhisperGroup(conferenceId,
			whisperGroupId, b);
		} else if (options[0].equalsIgnoreCase("locked")) {
		    boolean b;

		    if (options[1].equalsIgnoreCase("true") ||
			    options[1].equalsIgnoreCase("t")) {

			b = true;
		    } else if (options[1].equalsIgnoreCase("false") ||
			    options[1].equalsIgnoreCase("f")) {

			b = false;
		    } else {
                        throw new ParseException("invalid option value " + s, 0);
		    }

		    ConferenceManager.setLockedWhisperGroup(conferenceId,
			whisperGroupId, b);
		} else if (options[0].equalsIgnoreCase("attenuation")) {
		    double attenuation;

		    try {
			attenuation = Double.parseDouble(options[1]);
		    } catch (NumberFormatException e) {
                        throw new ParseException(
			    "invalid attenuation value " + options[1], 0);
		    }

		    ConferenceManager.setWhisperGroupAttenuation(conferenceId,
			whisperGroupId, attenuation);
		} else if (options[0].equalsIgnoreCase("noCommonMix")) {
		    boolean b;

		    if (options[1].equalsIgnoreCase("true") ||
			    options[1].equalsIgnoreCase("t")) {

			b = true;
		    } else if (options[1].equalsIgnoreCase("false") ||
			    options[1].equalsIgnoreCase("f")) {

			b = false;
		    } else {
                        throw new ParseException("invalid option value " + s, 0);
		    }

		    ConferenceManager.setWhisperGroupNoCommonMix(conferenceId,
			whisperGroupId, b);
		} else {
                    throw new ParseException("invalid option " + s, 0);
		}
	    }

            return true;
        } catch (ParameterException e) {
        }

	try {
	    value = getValue("whisper", "w", request);

	    String[] tokens = value.split(":");

	    if (tokens.length == 0) {
                throw new ParseException(
		    "missing whisper group:  " + request, 0);
	    }

	    if (tokens.length == 1) {
                throw new ParseException("missing call Id:  " + request, 0);
	    }

            CallHandler callHandler = CallHandler.findCall(tokens[1]);

	    if (callHandler == null) {
                throw new ParseException("Invalid callId:  " + tokens[1], 0);
            }

            callHandler.getMember().setWhispering(tokens[0]);
	    return true;
	} catch (ParameterException e) {
	}

        try {
            value = getValue("whisperAttenuation" , "wa", request);
            double whisperAttenuation = getDouble(value);

            WhisperGroup.setDefaultAttenuation(whisperAttenuation);

            return true;
        } catch (ParameterException e) {
        }

	try {
            Logger.writeThru = getBooleanValue("writeThru", "wt", request);
	    return true;
	} catch (ParameterException e) {
	}

	return false;
    }

    private void displayTuneableParameters() {
	requestHandler.writeToSocket("Build date			= " + BuildDate.getBuildDate());

	requestHandler.writeToSocket("allowShortNames			= "
	    + ConferenceManager.allowShortNames());

	requestHandler.writeToSocket("available processors		= "
	    + Runtime.getRuntime().availableProcessors());

	requestHandler.writeToSocket("bridgeSuspended			= "
	    + RequestHandler.isBridgeSuspended());

	requestHandler.writeToSocket("bridgeLocation			= "
	    + Bridge.getBridgeLocation());

	requestHandler.writeToSocket("bridge Log Directory		= "
	    + Bridge.getBridgeLogDirectory());

	requestHandler.writeToSocket("comfortNoiseType		= "
	    + MemberSender.getComfortNoiseType());

	requestHandler.writeToSocket("comfortNoiseLevel		= "
	    + RtpPacket.getDefaultComfortNoiseLevel());

	requestHandler.writeToSocket("commonMixDefault		= "
	    + WhisperGroup.getCommonMixDefault());

        requestHandler.writeToSocket("callAnswerTimeout		= "
            + CallSetupAgent.getDefaultCallAnswerTimeout());

	requestHandler.writeToSocket("defaultProtocol  		= "
	    + Bridge.getDefaultProtocol());

	requestHandler.writeToSocket("defaultSipProxy  		= "
	    + SipServer.getDefaultSipProxy());

	requestHandler.writeToSocket("defermixing  			= "
	    + MemberReceiver.deferMixing());

	requestHandler.writeToSocket("directConferencing		= "
	    + IncomingCallHandler.getDirectConferencing());

	requestHandler.writeToSocket("dtmfSuppression			= "
	    + CallHandler.dtmfSuppression());

	requestHandler.writeToSocket("duplicateCallLimit		= "
	    + CallHandler.getDuplicateCallLimit());

	requestHandler.writeToSocket("enablePSTNCalls			= "
	    + CallHandler.enablePSTNCalls());

 	requestHandler.writeToSocket("firstRtpPort			= "
	    + ConferenceMember.getFirstRtpPort());

	requestHandler.writeToSocket("forwardDtmfKeys			= "
	    + MemberReceiver.getForwardDtmfKeys());

	requestHandler.writeToSocket("forcePrivateMix			= "
            + MixManager.getForcePrivateMix());

	requestHandler.writeToSocket("incomingCallHandler		= "
	    + RequestHandler.getIncomingCallListenerInfo());

	requestHandler.writeToSocket("incomingCallTreatment		= "
	    + IncomingCallHandler.getIncomingCallTreatment());

	requestHandler.writeToSocket("incomingCallVoiceDetection	= "
	    + IncomingCallHandler.getIncomingCallVoiceDetection());

	requestHandler.writeToSocket("internationalPrefix		= "
	    + RequestHandler.getInternationalPrefix());

	requestHandler.writeToSocket("joinConfirmationKey		= "
	    + MemberReceiver.getJoinConfirmationKey());

 	requestHandler.writeToSocket("lastRtpPort			= "
	    + ConferenceMember.getLastRtpPort());

	requestHandler.writeToSocket("localhostSecurity		= "
	    + Bridge.getLocalhostSecurity());

	requestHandler.writeToSocket("logLevel			= "
	    + Logger.logLevel);

	requestHandler.writeToSocket("loneReceiverPort		= "
	    + ConferenceManager.loneReceiverPort());

	requestHandler.writeToSocket("longDistancePrefix		= "
	    + requestHandler.getLongDistancePrefix());

	requestHandler.writeToSocket("lpfVolumeAdjustment		= "
	    + LowPassFilter.getLpfVolumeAdjustment());

	requestHandler.writeToSocket("nAvg 				= "
	    + LowPassFilter.getNAvg());

	requestHandler.writeToSocket("outsideLinePrefix		= "
	    + RequestHandler.getOutsideLinePrefix());

	requestHandler.writeToSocket("prefixPhoneNumber		= "
	    + RequestHandler.prefixPhoneNumber());

	requestHandler.writeToSocket("recordingDirectory		= "
	    + Recorder.getRecordingDirectory());

	requestHandler.writeToSocket("rtpTimeout			= "
	    + RtpSocket.getRtpTimeout());

 	requestHandler.writeToSocket("senderThreads			= "
	    + ConferenceSender.getSenderThreads());

	requestHandler.writeToSocket("sendSipUriToProxy		= "
	    + SipServer.getSendSipUriToProxy());

 	requestHandler.writeToSocket("spatialBehindVolume		= "
	    + SunSpatialAudio.getSpatialBehindVolume());

 	requestHandler.writeToSocket("spatialEchoDelay		= "
	    + SunSpatialAudio.getSpatialEchoDelay());

 	requestHandler.writeToSocket("spatialEchoVolume		= "
	    + SunSpatialAudio.getSpatialEchoVolume());

 	requestHandler.writeToSocket("spatialFalloff			= "
	    + SunSpatialAudio.getSpatialFalloff());

 	requestHandler.writeToSocket("spatialMinVolume		= "
	    + SunSpatialAudio.getSpatialMinVolume());

	requestHandler.writeToSocket("useSingleSender			= "
	    + ConferenceManager.useSingleSender());

	requestHandler.writeToSocket("useTelephoneEvent		= "
	    + SdpManager.useTelephoneEvent());

	requestHandler.writeToSocket("voIPGateways			= "
	    + SipServer.getAllVoIPGateways());

        requestHandler.writeToSocket("whisperAttenuation		= "
            + WhisperGroup.getDefaultAttenuation());

	requestHandler.writeToSocket("writeThru         		= "
	    + Logger.writeThru);

	requestHandler.writeToSocket("\n");
    }

    private void displayCallParameters() {
	requestHandler.writeToSocket("\nPARAMETERS FOR CALL SETUP:\n");

	requestHandler.writeToSocket("callAnswerTimeout | to = <seconds>");

	requestHandler.writeToSocket(
	    "callAnsweredTreatment | at = <treatment>");

        requestHandler.writeToSocket("callEndTreatment | et = <treatment>");

        requestHandler.writeToSocket(
	    "callEstablishedTreatment | et = <treatment>");

        requestHandler.writeToSocket("callId | id = <String>");

        requestHandler.writeToSocket("conferenceId | c = <string>");

	requestHandler.writeToSocket(
	    "conferenceJoinTreatment | jt = <treatment>");

	requestHandler.writeToSocket(
	    "conferenceLeaveTreatment | lt = <treatment>");

        requestHandler.writeToSocket("displayName | d = <string>");

	requestHandler.writeToSocket(
	    "doNotRecord | dnr = true | false");

	requestHandler.writeToSocket("directConferencing | dc = true | false");

	requestHandler.writeToSocket("dtmfDetection | dtmf = true | false");

	requestHandler.writeToSocket("dtmfSuppression | ds = true | false");

	requestHandler.writeToSocket("duplicateCallLimit | dcl = <int>");

	requestHandler.writeToSocket("firstRtpPort = <int>");

	requestHandler.writeToSocket("encryptionKey | ek = true | false");

	requestHandler.writeToSocket("encryptionAlgorithm | ea = true | false");

	requestHandler.writeToSocket("forwardDataFrom | fdf = <callId");

	requestHandler.writeToSocket(
	    "firstConferenceMemberTreatment | fm = <treatment>");

	requestHandler.writeToSocket(
	    "handleSessionProgress | hsp = true | false");

	requestHandler.writeToSocket(
	    "ignoreTelephoneEvents | ite = true | false");

	requestHandler.writeToSocket("inputTreatment | it = <treatment>");

	requestHandler.writeToSocket(
	    "joinConfirmationKey | jck = <string 1 char dtmf key>");

	requestHandler.writeToSocket(
	    "joinConfirmationTimeout | jc = <seconds>");

	requestHandler.writeToSocket("lastRtpPort = <int>");

	requestHandler.writeToSocket(
	    "migrate = <existing callId> : <new Phone Number> "
	    + "| Id-<new callId>");

	requestHandler.writeToSocket(
	    "migrateToBridge | mtb = <bridge host> : <bridge port> : <callId>");

	requestHandler.writeToSocket("mute | m = true | false");

	requestHandler.writeToSocket("muteWhisperGroup | mwg = true | false");

	requestHandler.writeToSocket("muteConference | mc = true | false");

	requestHandler.writeToSocket("name | n = <string>");

	requestHandler.writeToSocket("phoneNumber | pn = <phone number>");

	requestHandler.writeToSocket(
	    "phoneNumberLocation | pnl = <3 character phone number location>");

	requestHandler.writeToSocket("protocol | p = <signaling protocol>");

	requestHandler.writeToSocket("remoteCallId | rid = <remote callId>");

	requestHandler.writeToSocket("remoteMediaInfo | rm = <String>");

	//requestHandler.writeToSocket("speexEncode | se = true | false");

	requestHandler.writeToSocket("voipGateway | vg = <ip address>");

	requestHandler.writeToSocket(
	    "useConferenceReceiverThread | ucrt = true | false");

	requestHandler.writeToSocket("voiceDetection | v = true | false");

	requestHandler.writeToSocket(
	    "voiceDetectionWhileMuted | vm = true | false");

	requestHandler.writeToSocket("\n<treatment> is either");
	requestHandler.writeToSocket("\tfile:<path to audio file> or");
	requestHandler.writeToSocket("\tdtmf:<numbers 0-9 # *> or");
	requestHandler.writeToSocket("\ttts:<text>");

	requestHandler.writeToSocket(
	    "\nEnter ? to display current call parameters");

	requestHandler.writeToSocket("\n");
    }

    private void displayImmediateCommands() {
	requestHandler.writeToSocket("IMMEDIATE COMMANDS:\n");

 	requestHandler.writeToSocket("addCallToWhisperGroup | acwg = "
	    + "<whisperGroupId> : <callId>");

 	requestHandler.writeToSocket("allowShortNames | asn = true | false");

	requestHandler.writeToSocket(
	    "bridgeLocation | bl = <3 character bridge location>");

        requestHandler.writeToSocket("callAnswerTimeout | cat = <seconds>");

	requestHandler.writeToSocket(
	    "callAnsweredTreatment | at = <answer treatment>: <conferenceId>");

	requestHandler.writeToSocket("cancel = <callId>");

	requestHandler.writeToSocket("cancelMigration | cm = <callId>");

	requestHandler.writeToSocket("comfortNoiseType | cnt = 0 | 1 | 2");

	requestHandler.writeToSocket("comfortNoiseLevel | cnl = <byte level>");

	requestHandler.writeToSocket("commonMixDefault | cmd = true | false");

	requestHandler.writeToSocket("conferenceInfo | ci");

	requestHandler.writeToSocket(
	    "createConference | cc = <conferenceId>:PCM[U]|SPEEX/<sampleRate>"
		+ "/<channels>[:<displayName>]");

	requestHandler.writeToSocket(
	    "createWhisperGroup | cwg = <conferenceId> : <whisperGroupId> "
	    + "[: <attenuation factor>]");

	requestHandler.writeToSocket("endConference | ec = <conferenceId>");

	requestHandler.writeToSocket("deferMixing | dm = true | false");

	requestHandler.writeToSocket(
	    "destroyWhisperGroup | dwg = <conferenceId> : <whisperGroup>");

	requestHandler.writeToSocket("conferenceInfo | ci");

	requestHandler.writeToSocket("cnThresh | ct = <int>");

        requestHandler.writeToSocket("dtmfSuppression | ds = true | false");

	requestHandler.writeToSocket("detach");

	requestHandler.writeToSocket(
	    "doNotRecord | dnr = true | false [:<callId>]");

        requestHandler.writeToSocket("prefixPhoneNumber | ppn = true | false");

	requestHandler.writeToSocket("flush");

        requestHandler.writeToSocket("forcePrivateMix | fpm = true | false");

        requestHandler.writeToSocket(
	    "fowardData | fd = <dest callId> : <src callId");

        requestHandler.writeToSocket("fowardDtmfKeys | fdtmf = true | false");

	requestHandler.writeToSocket("gc");

	requestHandler.writeToSocket("getMixDescriptos | gmd = <callId>");

	requestHandler.writeToSocket("gs");

	requestHandler.writeToSocket("help | h | ?");

	requestHandler.writeToSocket(
	    "incomingCallTreatment | ict = <treatment>");

	requestHandler.writeToSocket(
	    "incomingCallVoiceDetection | icvd = true | false");

	requestHandler.writeToSocket("internationalPrefix | ip = <String>");

	requestHandler.writeToSocket("localhostSecurity | lhs = true | false");

	requestHandler.writeToSocket("logLevel | l = [0 - 10]");

	requestHandler.writeToSocket("longDistancePrefix = <String>");

        requestHandler.writeToSocket("maxJitterBufferSize | maxjb = <int> : <callId>");

        requestHandler.writeToSocket("minJitterBufferSize | minjb = <int> : <callId>");

	requestHandler.writeToSocket(
	    "monitorCallStatus | mcs = true | false :<callId>");

	requestHandler.writeToSocket(
	    "monitorConferenceStatus | mcc = true | false :<conferenceId>");

	requestHandler.writeToSocket(
	    "monitorIncomingCalls | mic = true | false");

	requestHandler.writeToSocket(
	    "monitorOutgoingCalls | moc = true | false");

	requestHandler.writeToSocket("mute | m = true | false :<callId>");

	requestHandler.writeToSocket(
	    "muteWhisperGroup | mwg = true | false :<callId>");

	requestHandler.writeToSocket(
	    "muteConference | mc = true | false :<callId>");

	requestHandler.writeToSocket(
	    "numberOfMembers | nm = <conferenceId> :<idString>");

	requestHandler.writeToSocket("outsideLinePrefix = <String>");

	requestHandler.writeToSocket("pause | p = <int ms>");

	requestHandler.writeToSocket("pauseTreatmentToCall = <callId>");

	requestHandler.writeToSocket("pauseTreatmentToConference = <conferenceId>");

	requestHandler.writeToSocket(
	    "voiceDetectionWhileMuted | vm = true | false [: <callId>]");

	requestHandler.writeToSocket(
	    "playTreatmentToCall | ptc = <treatment> [:<callId>]");

	requestHandler.writeToSocket(
	    "playTreatmentToConference | pc = <treatment> [:<conferenceId>]");

	requestHandler.writeToSocket(
	    "playTreatmentToAllConferences | pca = <treatment>");

	requestHandler.writeToSocket(
	    "packetLossConcealmentClass | plcc = <String plc class name> "
	    + ": <callId>");

	requestHandler.writeToSocket("powerThresholdLimit | ptl = <double>");

	requestHandler.writeToSocket("printStatistics | ps");

	requestHandler.writeToSocket(
 	    "pmx = <frontBack -1 to 1> : <leftRight -1 to 1> : "
	    + " <volume> : <callId> : <callId with private Mix>");

	requestHandler.writeToSocket(
 	    "privateMix | pm = <volumes> : <callId> : <callId with private Mix>");

	requestHandler.writeToSocket(
	    "recordConference | rc = true | false :<conferenceId> "
	    + ":<recording file path>");

	requestHandler.writeToSocket(
	    "recordingDirectory | rd = <directory path>");

	requestHandler.writeToSocket(
	    "recordFromMember | rfm = true | false :<callId> "
	    + ":<recording file path>");

	requestHandler.writeToSocket(
	    "recordToMember | rtm = true | false :<callId> "
	    + ":<recording file path>");

	requestHandler.writeToSocket("removeCallFromWhisperGroup | rcwg = "
	    + "<whisperGroupId> : <callId>");

	requestHandler.writeToSocket("releaseCalls = true | false");

	requestHandler.writeToSocket(
	    "removeConference | rconf = <conferenceId");

	requestHandler.writeToSocket("restartInputTreatment | rit = <callId>");

	requestHandler.writeToSocket("resumeTreatmentToCall = <callId>");

	requestHandler.writeToSocket("resumeTreatmentToConference = <callId>");

	requestHandler.writeToSocket("rtpTimeout | rt = <seconds> ");

        requestHandler.writeToSocket("sendSipUriToProxy = true | false");

	requestHandler.writeToSocket(
	    "conferenceJoinTreatment | jt = <join treatment>:<conferenceId>");

	requestHandler.writeToSocket("showWhisperGroups | swg");

	requestHandler.writeToSocket("setInputVolume | siv = <volume> : <callId>");

	requestHandler.writeToSocket("setOutputVolume | sov = <volume> : <callId>");

	//requestHandler.writeToSocket("speexEncode | se = true | false : <callId>");

	requestHandler.writeToSocket("statistics | stat = <seconds>");

	requestHandler.writeToSocket("stopTreatmentToConference | sc = "
	    + "<conferenceId> : <treatment>");

	requestHandler.writeToSocket("stopTreatmentToCall | stc = <callId> "
	    + ": <treatment>");

	/* don't advertise this */
	//requestHandler.writeToSocket("SHUTDOWN");

	requestHandler.writeToSocket("synchronousMode = true | false");

	requestHandler.writeToSocket("defaultProtocol | dp = <SIP Proxy host name>");

	requestHandler.writeToSocket("defaultSipProxy | dsp = <ip address>");

	requestHandler.writeToSocket("timestamp");

	requestHandler.writeToSocket("traceCall = true | false : <callId>");

	requestHandler.writeToSocket(
	    "transferCall | tc = <callId> : <conferenceId>");

	requestHandler.writeToSocket("tuneableParameters | tp");

	requestHandler.writeToSocket("useSingleSender = true | false");

	requestHandler.writeToSocket("useTelephoneEvent = true | false");

	requestHandler.writeToSocket(
	    "voIPGateways | vgs = <ip address>[,<ip address>...]");

 	requestHandler.writeToSocket(
	    "wisperGroupOptions | wgo  = <conferenceId> : <WhIsperGroupId> "
	    +   "[: Locked=t|f ]\n"
 	    +   "[: transient=t|f]\n"
	    +   "[: attenuation=<double>]\n"
	    +   "[: noCommonMix=t|f");

	requestHandler.writeToSocket(
	    "whisper | w = <whisperGroupId>: <callId>");

	requestHandler.writeToSocket("whisperAttenuation | wa = <double>");

	requestHandler.writeToSocket("writeThru | wt = true | false");

	requestHandler.writeToSocket(
	    "<callId> is a String identifying the call");

	requestHandler.writeToSocket(
	    "<whisperGroup> is a String identifying a whisper group");
    }

}
