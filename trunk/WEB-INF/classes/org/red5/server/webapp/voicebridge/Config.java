package org.red5.server.webapp.voicebridge;

import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.net.InetAddress;

public class Config {

	private HashMap<String, Conference> conferences;
	private HashMap<String, Conference> extensions;

	private ArrayList<ProxyCredentials> registrations;
	private ArrayList<String> registrars;

	private static Config singletonConfig;
	private String privateHost = "127.0.0.1";
	private String publicHost = "127.0.0.1";
	private String conferenceExten = "3000";
	private String defaultProxy = null;

    private boolean prefixPhoneNumber = true;
    private String internationalPrefix = "00";  // for international calls
    private String longDistancePrefix = "0";  	// for long Distancee
    private String outsideLinePrefix = "9";  	// for outside line
    private int internalExtenLength = 5;


	private Config() {

		conferences = new HashMap<String, Conference>();
		extensions = new HashMap<String, Conference>();

		registrations = new ArrayList<ProxyCredentials>();
    	registrars = new ArrayList<String>();

		String appPath = System.getProperty("user.dir");
		String configFile = appPath + File.separator + "webapps" + File.separator + "voicebridge" + File.separator + "WEB-INF" + File.separator + "red5voicebridge.xml";
		//String configFile = appPath + File.separator + ".." + File.separator + "plugins" + File.separator + "redfire" + File.separator + "WEB-INF" + File.separator + "red5voicebridge.xml";

		try {
			System.out.println(String.format("Red5VoiceBridge read config file: %s", configFile));

			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new File(configFile));

			Element tagPrivateHost = (Element) doc.getElementsByTagName("privateHost").item(0);

			if ((tagPrivateHost.getTextContent() != null) && (tagPrivateHost.getTextContent().length() > 0)) {
				privateHost = tagPrivateHost.getTextContent();
			}

			Element tagPublicHost = (Element) doc.getElementsByTagName("publicHost").item(0);

			if ((tagPublicHost.getTextContent() != null) && (tagPublicHost.getTextContent().length() > 0)) {
				publicHost = tagPublicHost.getTextContent();
			}

			Element tagDefaultProxy = (Element) doc.getElementsByTagName("defaultProxy").item(0);

			if ((tagDefaultProxy.getTextContent() != null) && (tagDefaultProxy.getTextContent().length() > 0)) {
				defaultProxy = tagDefaultProxy.getTextContent();
			}

			Element tagConfExten = (Element) doc.getElementsByTagName("conferences").item(0);
			conferenceExten = tagConfExten.getAttribute("exten");

			NodeList tagConfernces = doc.getElementsByTagName("conference");

			for (int i=0; i<tagConfernces.getLength(); i++)
			{
				Element conf = (Element) tagConfernces.item(i);

				Conference conference = new Conference();
				conference.id = conf.getAttribute("id");
				conference.pin = conf.getAttribute("pin");

				if (conference.pin != null && conference.pin.length() == 0)
					conference.pin = null;

				conference.exten = conf.getAttribute("exten");

				if (conference.exten != null && conference.exten.length() > 0)
				{
					extensions.put(conference.exten, conference);
				}

				conferences.put(conference.id, conference);

				System.out.println(String.format("Red5VoiceBridge conference: %s with pin %s", conference.id, conference.pin));
			}

			NodeList tagRegisters = doc.getElementsByTagName("register");

			for (int i=0; i<tagRegisters.getLength(); i++)
			{
				Element register = (Element) tagRegisters.item(i);
				System.out.println(String.format("Red5VoiceBridge registration host: %s username: %s authname: %s password: %s realm %s proxy: %s", register.getAttribute("host"), register.getAttribute("username"), register.getAttribute("authname"), register.getAttribute("password"), register.getAttribute("realm"), register.getAttribute("proxy")));

				ProxyCredentials credentials = new ProxyCredentials();

				credentials.setUserName(register.getAttribute("username"));
				credentials.setUserDisplay(register.getAttribute("display"));
				credentials.setAuthUserName(register.getAttribute("authname"));
				credentials.setPassword(register.getAttribute("password").toCharArray());
				credentials.setRealm(register.getAttribute("realm"));
				credentials.setProxy(register.getAttribute("proxy"));
				credentials.setHost(register.getAttribute("host"));

				try {
					InetAddress inetAddress = InetAddress.getByName(register.getAttribute("host"));
					registrars.add(register.getAttribute("host"));
					registrations.add(credentials);

				} catch (Exception e) {
					System.out.println(String.format("Bad Address  %s ", register.getAttribute("host")));
				}
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}


	public static Config getInstance() {

		if (singletonConfig == null) {

			singletonConfig = new Config();
		}

		return singletonConfig;
	}

	public boolean isValidConference(String id)
	{
		return conferences.containsKey(id);
	}

	public boolean isValidConferencePin(String id, String pin)
	{
		boolean valid = false;

		if (conferences.containsKey(id))
		{
			Conference conf = conferences.get(id);
			valid = conf.pin == null || pin.equals(conf.pin);
		}

		return valid;
	}

    public Conference getConferenceByPhone(String phoneNo)
    {
		Conference conf = null;

		if (extensions.containsKey(phoneNo))
		{
			conf = extensions.get(phoneNo);
		}

		return conf;
    }

    public String getMeetingCode(String phoneNo)
    {
		String id = null;

		if (extensions.containsKey(phoneNo))
		{
			Conference conf = extensions.get(phoneNo);
			id = conf.id;
		}

		return id;
    }

    public String getPassCode(String meetingId, String phoneNo)
    {
		String pin = null;

		if (extensions.containsKey(phoneNo))
		{
			Conference conf = extensions.get(phoneNo);
			pin = conf.pin;

		} else if (conferences.containsKey(meetingId)) {

			Conference conf = conferences.get(meetingId);
			pin = conf.pin;
		}

		return pin;
    }

	public String getPrivateHost()
	{
		return privateHost;
	}

	public String getPublicHost()
	{
		return publicHost;
	}

    public void setConferenceExten(String conferenceExten)
    {
		this.conferenceExten = conferenceExten;
    }

    public String getConferenceExten()
    {
		return conferenceExten;
    }

    public void setInternalExtenLength(int internalExtenLength)
    {
		this.internalExtenLength = internalExtenLength;
    }

    public int getInternalExtenLength()
    {
		return internalExtenLength;
    }

    public void setOutsideLinePrefix(String outsideLinePrefix)
    {
		this.outsideLinePrefix = outsideLinePrefix;
    }

    public String getOutsideLinePrefix()
    {
		return outsideLinePrefix;
    }

    public void setLongDistancePrefix(String longDistancePrefix)
    {
		this.longDistancePrefix = longDistancePrefix;
    }

    public String getLongDistancePrefix()
    {
		return longDistancePrefix;
    }


    public void setInternationalPrefix(String internationalPrefix)
    {
		this.internationalPrefix = internationalPrefix;
    }

    public String getInternationalPrefix()
    {
		return internationalPrefix;
    }


    public void setPrefixPhoneNumber(boolean prefixPhoneNumber)
    {
		this.prefixPhoneNumber = prefixPhoneNumber;
    }

    public boolean prefixPhoneNumber()
    {
		return prefixPhoneNumber;
    }

	public String getDefaultProxy()
	{
		return defaultProxy;
	}

	public ArrayList<String> getRegistrars()
	{
		return registrars;
	}


	public ArrayList<ProxyCredentials> getRegistrations()
	{
		return registrations;
	}

    public String formatPhoneNumber(String phoneNumber, String location)
    {
		if (phoneNumber == null) {
			return null;
		}
		/*
		 * It's a softphone number.  Leave it as is.
		 */

		if (phoneNumber.indexOf("sip:") == 0)
		{
			/*
			 * There is a problem where Meeting Central gives
			 * us a phone number with only "sip:" which isn't valid.
			 * Check for that here.
			 * XXX
			 */
			if (phoneNumber.length() < 5) {
			return null;
			}

			return phoneNumber;
		}

		if (phoneNumber.indexOf("@") >= 0)
		{
			return "sip:" + phoneNumber;
		}

		/*
		 * If number starts with "Id-" it's a callId.  Leave it as is.
		 */

		if (phoneNumber.indexOf("Id-") == 0)
		{
			return phoneNumber;
		}

        /*
         * Get rid of white space in the phone number
         */

        phoneNumber = phoneNumber.replaceAll("\\s", "");

        /*
         * Get rid of "-" in the phone number
         */

        phoneNumber = phoneNumber.replaceAll("-", "");

		/*
		 * For Jon Kaplan who likes to use "." as a phone number separator!
		 */

		phoneNumber = phoneNumber.replaceAll("\\.", "");

		if (phoneNumber.length() == 0)
		{
			return null;
		}

		if (prefixPhoneNumber == false) {
			return phoneNumber;
		}

		/*
		 * Replace leading "+" (from namefinder) with appropriate numbers.
		 * +1 is a US number and becomes outsideLinePrefix.
		 * +<anything else> is considered to be an international number and
		 * becomes internationalPrefix.
		 */

		if (phoneNumber.charAt(0) == '+')
		{
			if (phoneNumber.charAt(1) == '1')
			{
				phoneNumber = outsideLinePrefix + phoneNumber.substring(1);

			} else {
				phoneNumber = outsideLinePrefix + internationalPrefix + phoneNumber.substring(1);
			}

		} else if (phoneNumber.charAt(0) == 'x' || phoneNumber.charAt(0) == 'X') {

			phoneNumber = phoneNumber.substring(1);
		}

		if (phoneNumber.length() == internalExtenLength)
		{
			/*
			 * This is an internal extension.  Determine if it needs
			 * a prefix of "70".
			 */
	   		//phoneNumber = PhoneNumberPrefix.getPrefix(location) + phoneNumber;

        } else if (phoneNumber.length() > 7) {
            /*
             * It's an outside number
             *
             * XXX No idea what lengths of 8 and 9 would be for...
             */
            if (phoneNumber.length() == 10)
            {
                /*
                 * It's US or Canada, number needs 91
                 */
                phoneNumber = outsideLinePrefix + longDistancePrefix + phoneNumber;

            } else if (phoneNumber.length() >= 11) {
                /*
                 * If it starts with 9 or 1, it's US or Canada.
                 * Otherwise, it's international.
                 */
                if (phoneNumber.length() == 11 && longDistancePrefix.length() > 0 && phoneNumber.charAt(0) == longDistancePrefix.charAt(0))
                {
                    phoneNumber = outsideLinePrefix + phoneNumber;

                } else if (phoneNumber.length() == 11 && outsideLinePrefix.length() > 0 && phoneNumber.charAt(0) == outsideLinePrefix.charAt(0)) {

                    phoneNumber = outsideLinePrefix + longDistancePrefix + phoneNumber.substring(1);

                } else if (phoneNumber.length() == 12 && phoneNumber.substring(0,2).equals(outsideLinePrefix + longDistancePrefix)) {

                    // nothing to do

                } else {
                    /*
                     * It's international, number needs outsideLinePrefix plus internationalPrefix
                     */
                    if (phoneNumber.substring(0,3).equals(internationalPrefix))
                    {
                        /*
                         * international prefix is already there, just prepend
			 			 * outsideLinePrefix
                         */
                         phoneNumber = outsideLinePrefix + phoneNumber;

                    } else if (!phoneNumber.substring(0,4).equals(outsideLinePrefix + internationalPrefix)) {

                        phoneNumber = outsideLinePrefix + internationalPrefix + phoneNumber;
                    }
                }
            }
        }

        return phoneNumber;
    }

	private class Conference
	{
		public String pin = null;
		public String id = null;
		public String exten = null;

	}
}