package org.red5.server.webapp.voicebridge;


import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.NoSuchElementException;

import java.net.*;
import java.io.File;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;

import org.red5.server.adapter.ApplicationAdapter;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.IScope;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IServiceCapableConnection;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IPlayItem;
import org.red5.server.api.stream.IPlaylistSubscriberStream;
import org.red5.server.api.stream.IStreamAwareScopeHandler;
import org.red5.server.api.stream.ISubscriberStream;

import com.sun.voip.server.SipServer;
import com.sun.voip.server.Bridge;

public class Application extends ApplicationAdapter implements IStreamAwareScopeHandler {

    protected static Logger log = Red5LoggerFactory.getLogger( Application.class, "voicebridge" );
	private String version = "0.0.0.1";
	private Config config = Config.getInstance();

    @Override
    public boolean appStart( IScope scope ) {

        loginfo( "Red5VoiceBridge starting in scope " + scope.getName() + " " + System.getProperty( "user.dir" ) );
        loginfo(String.format("Red5VoiceBridge version %s", version));

		String appPath = System.getProperty("user.dir");
		String logDir = appPath + File.separator + "log" + File.separator;

		Properties properties = new Properties();

		System.setProperty("com.sun.voip.server.LOGLEVEL", "99");
		System.setProperty("com.sun.voip.server.SIPProxy", "");
		System.setProperty("user.name", "1002");
		//System.setProperty("com.sun.voip.server.VoIPGateways", "192.168.1.90;sip:1002@192.168.1.70");
		properties.setProperty("javax.sip.STACK_NAME", "JAIN SIP 1.1");
		properties.setProperty("javax.sip.RETRANSMISSION_FILTER", "on");
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "99");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG", logDir + "sip_server.log");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", logDir + "sip_debug.log");

		Bridge.setPublicHost(config.getPublicHost());
		Bridge.setPrivateHost(config.getPrivateHost());
		Bridge.setBridgeLocation("LCL");

		new SipServer(config.getPrivateHost(), properties);

        return true;
    }


    @Override
    public void appStop( IScope scope ) {

        loginfo( "Red5VoiceBridge stopping in scope " + scope.getName() );
    }


    @Override
    public boolean appConnect( IConnection conn, Object[] params ) {

        IServiceCapableConnection service = (IServiceCapableConnection) conn;
        loginfo( "Red5VoiceBridge Client connected " + conn.getClient().getId() + " service " + service );
        return true;
    }


    @Override
    public boolean appJoin( IClient client, IScope scope ) {

        loginfo( "Red5VoiceBridge Client joined app " + client.getId() );
        IConnection conn = Red5.getConnectionLocal();
        IServiceCapableConnection service = (IServiceCapableConnection) conn;

        return true;
    }


    @Override
    public void appLeave( IClient client, IScope scope ) {

        IConnection conn = Red5.getConnectionLocal();
        loginfo( "Red5VoiceBridge Client leaving app " + client.getId() );
    }


    private void loginfo( String s ) {

        log.info( s );
        System.out.println( s );
    }

    private void logerror( String s ) {

        log.error( s );
        System.out.println( "[ERROR] " + s );
    }

}
