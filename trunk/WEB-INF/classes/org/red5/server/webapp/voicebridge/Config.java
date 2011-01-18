package org.red5.server.webapp.voicebridge;

import java.io.File;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Config {

	private HashMap<String, Conference> conferences;
	private NodeList tagConfernces = null;
	private static Config singletonConfig;
	private String privateHost = "127.0.0.1";
	private String publicHost = "127.0.0.1";

	private Config() {

		conferences = new HashMap<String, Conference>();
		String appPath = System.getProperty("user.dir");
		String configFile = appPath + File.separator + "webapps" + File.separator + "voicebridge" + File.separator + "red5voicebridge.xml";

		try {
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

			tagConfernces = doc.getElementsByTagName("conference");

			System.out.println(String.format("Red5VoiceBridge read config file: %s", configFile));

			for (int i=0; i<tagConfernces.getLength(); i++)
			{
				Element conf = (Element) tagConfernces.item(i);

				Conference conference = new Conference();
				conference.id = conf.getAttribute("id");
				conference.pin = conf.getAttribute("pin");
				conferences.put(conference.id, conference);

				System.out.println(String.format("Red5VoiceBridge conference: %s with pin %s", conference.id, conference.pin));
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

	public int getConferencesCount()
	{
		if (tagConfernces == null)
			return 0;
		else
			return tagConfernces.getLength();
	}

	public Boolean isValidConference(String id)
	{
		return conferences.containsKey(id);
	}

	public Boolean isValidConferencePin(String id, String pin)
	{
		Boolean valid = false;

		if (conferences.containsKey(id))
		{
			Conference conf = conferences.get(id);
			valid = pin.equals(conf.pin);
		}

		return valid;
	}

	public String getPrivateHost()
	{
		return privateHost;
	}

	public String getPublicHost()
	{
		return publicHost;
	}

	private class Conference
	{
		public String pin;
		public String id;

	}
}