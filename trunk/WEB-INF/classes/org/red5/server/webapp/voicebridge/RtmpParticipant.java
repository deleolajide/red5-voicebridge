package org.red5.server.webapp.voicebridge;

import java.io.*;
import java.util.*;

import java.nio.IntBuffer;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;
import org.red5.io.ITagReader;
import org.red5.io.flv.impl.FLVService;
import org.red5.io.flv.impl.FLV;
import org.red5.io.flv.impl.FLVReader;
import org.red5.io.flv.impl.Tag;
import org.red5.io.IoConstants;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.Channel;
import org.red5.server.net.rtmp.RTMPClient;
import org.red5.server.net.rtmp.INetStreamEventHandler;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.ClientExceptionHandler;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.event.SerializeUtils;
import org.red5.server.stream.AbstractClientStream;
import org.red5.server.stream.IStreamData;
import org.red5.server.stream.message.RTMPMessage;

import com.sun.voip.server.MemberReceiver;
import com.sun.voip.AudioConversion;

import com.sun.voip.AudioConversion;

import org.red5.codecs.asao.CodecImpl;

public class RtmpParticipant extends RTMPClient implements INetStreamEventHandler, ClientExceptionHandler, IPendingServiceCallback {

    public boolean createdPlayStream = false;
    public boolean startPublish = false;
    public Integer playStreamId;
    public Integer publishStreamId;
    public MemberReceiver memberReceiver;
    private String publishName;
    private String playName;
    private RTMPConnection conn;
    private ITagWriter writer;
    private ITagReader reader;
    private int videoTs = 0;
    private int audioTs = 0;
    private int kt = 0;
    private short kt2 = 0;
    private IoBuffer buffer;

    private static final int ULAW_CODEC_ID = 130;
    private static final int ULAW_AUDIO_LENGTH = 160;

	private final byte[] byteData = new byte[ULAW_AUDIO_LENGTH + 1];
	private final byte[] byteBuffer = new byte[ULAW_AUDIO_LENGTH];
	private final int[] l16Buffer = new int[ULAW_AUDIO_LENGTH];

	private long startTime = System.currentTimeMillis();

    public RtmpParticipant(MemberReceiver memberReceiver)
    {
		this.memberReceiver = memberReceiver;
	}

    // ------------------------------------------------------------------------
    //
    // Overide
    //
    // ------------------------------------------------------------------------

    @Override
    public void connectionOpened( RTMPConnection conn, RTMP state ) {

        loggerdebug( "connection opened" );
        super.connectionOpened( conn, state );
        this.conn = conn;
    }


    @Override
    public void connectionClosed( RTMPConnection conn, RTMP state ) {

        loggerdebug( "connection closed" );
        super.connectionClosed( conn, state );
    }


    @Override
    protected void onInvoke( RTMPConnection conn, Channel channel, Header header, Notify notify, RTMP rtmp ) {

        super.onInvoke( conn, channel, header, notify, rtmp );

        try {
            ObjectMap< String, String > map = (ObjectMap) notify.getCall().getArguments()[ 0 ];
            String code = map.get( "code" );

            if ( StatusCodes.NS_PLAY_STOP.equals( code ) ) {
                loggerdebug( "onInvoke, code == NetStream.Play.Stop, disconnecting" );
                disconnect();
            }
        }
        catch ( Exception e ) {

        }

    }


    // ------------------------------------------------------------------------
    //
    // Public
    //
    // ------------------------------------------------------------------------

    public void startStream(String host, String app, int port, String publishName, String playName, long conferenceStartTime )
    {
        System.out.println( "RtmpParticipant startStream" );

		if (publishName == null || playName == null)
		{
			loggererror( "RtmpParticipant startStream stream names invalid " + publishName + " " + playName);

		} else {

			this.publishName = publishName;
			this.playName = playName;

			createdPlayStream = false;
			startPublish = false;

			videoTs = 0;
			audioTs = 0;
			kt = 0;
			kt2 = 0;
         	startTime = System.currentTimeMillis();

			byteData[0] = (byte) ULAW_CODEC_ID;

			try {
				connect( host, port, app, this );

			}
			catch ( Exception e ) {
				loggererror( "RtmpParticipant startStream exception " + e );
			}
		}
    }


    public void stopStream()
    {
        System.out.println( "RtmpParticipant stopStream" );

		kt = 0;
		kt2 = 0;

        try {
            disconnect();
        }
        catch ( Exception e ) {
            loggererror( "RtmpParticipant stopStream exception " + e );
        }

    }


    // ------------------------------------------------------------------------
    //
    // Implementations
    //
    // ------------------------------------------------------------------------

	public void handleException(Throwable throwable)
	{
			System.out.println( throwable.getCause() );
	}


    public void onStreamEvent( Notify notify ) {

        loggerdebug( "onStreamEvent " + notify );

        ObjectMap map = (ObjectMap) notify.getCall().getArguments()[ 0 ];
        String code = (String) map.get( "code" );

        if ( StatusCodes.NS_PUBLISH_START.equals( code ) ) {
            loggerdebug( "onStreamEvent Publish start" );
            startPublish = true;
        }
    }


    public void resultReceived( IPendingServiceCall call ) {

        loggerdebug( "service call result: " + call );

        if ( "connect".equals( call.getServiceMethodName() ) ) {
            createPlayStream( this );

        }
        else if ( "createStream".equals( call.getServiceMethodName() ) ) {

            if ( createdPlayStream ) {
                publishStreamId = (Integer) call.getResult();
                loggerdebug( "createPublishStream result stream id: " + publishStreamId );
                loggerdebug( "publishing video by name: " + publishName );
                publish( publishStreamId, publishName, "live", this );
            }
            else {
                playStreamId = (Integer) call.getResult();
                loggerdebug( "createPlayStream result stream id: " + playStreamId );
                loggerdebug( "playing video by name: " + playName );
                play( playStreamId, playName, -2000, -1000 );

                createdPlayStream = true;
                createStream( this );
            }
        }
    }


	public void pushAudio(int[] pcmBuffer)
	{
		try {
	    	AudioConversion.linearToUlaw(pcmBuffer, byteData, 1);

			int ts = (int)(System.currentTimeMillis() - startTime);

			if ( buffer == null ) {
				buffer = IoBuffer.allocate( 1024 );
				buffer.setAutoExpand( true );
			}

			buffer.clear();
			buffer.put( byteData );
			buffer.flip();

			AudioData audioData = new AudioData( buffer );
			audioData.setTimestamp( ts );

			kt++;

			if ( kt < 10 ) {
				loggerdebug( "+++ " + audioData );
			}

			RTMPMessage rtmpMsg = new RTMPMessage();
			rtmpMsg.setBody( audioData );
			publishStreamData( publishStreamId, rtmpMsg );

		} catch (Exception e) {

            loggererror( "RtmpParticipant pushAudio exception " + e );
		}

	}

    // ------------------------------------------------------------------------
    //
    // Privates
    //
    // ------------------------------------------------------------------------

    private void loggerdebug( String s ) {

        System.out.println( s );
    }

    private void loggererror( String s ) {

        System.err.println( "[ERROR] " + s );
    }

    private void createPlayStream( IPendingServiceCallback callback ) {

        loggerdebug( "create play stream" );
        IPendingServiceCallback wrapper = new CreatePlayStreamCallBack( callback );
        invoke( "createStream", null, wrapper );
    }

    private class CreatePlayStreamCallBack implements IPendingServiceCallback {

        private IPendingServiceCallback wrapped;


        public CreatePlayStreamCallBack( IPendingServiceCallback wrapped ) {

            this.wrapped = wrapped;
        }


        public void resultReceived( IPendingServiceCall call ) {

            Integer streamIdInt = (Integer) call.getResult();

            if ( conn != null && streamIdInt != null ) {
                PlayNetStream stream = new PlayNetStream();
                stream.setConnection( conn );
                stream.setStreamId( streamIdInt.intValue() );
                conn.addClientStream( stream );
            }
            wrapped.resultReceived( call );
        }

    }

    private class PlayNetStream extends AbstractClientStream implements IEventDispatcher {

        public void close() {

        }


        public void start() {

        }


        public void stop() {

        }


        public void dispatchEvent( IEvent event )
        {

            if ( !( event instanceof IRTMPEvent ) ) {
                loggerdebug( "skipping non rtmp event: " + event );
                return;
            }

            IRTMPEvent rtmpEvent = (IRTMPEvent) event;

            //if ( logger.isDebugEnabled() ) {
                // loggerdebug("rtmp event: " + rtmpEvent.getHeader() + ", " +
                // rtmpEvent.getClass().getSimpleName());
            //}

            if ( !( rtmpEvent instanceof IStreamData ) ) {
                loggerdebug( "skipping non stream data" );
                return;
            }

            if ( rtmpEvent.getHeader().getSize() == 0 ) {
                loggerdebug( "skipping event where size == 0" );
                return;
            }

            if ( rtmpEvent instanceof VideoData ) {
                // videoTs += rtmpEvent.getTimestamp();
                // tag.setTimestamp(videoTs);

            }
            else if ( rtmpEvent instanceof AudioData ) {
                audioTs += rtmpEvent.getTimestamp();

                IoBuffer audioData = ( (IStreamData) rtmpEvent ).getData().asReadOnlyBuffer();

				audioData.rewind();
				audioData.position(audioData.position() + 1);
				audioData.get(byteBuffer);

		        AudioConversion.ulawToLinear(byteBuffer, 0, byteBuffer.length, l16Buffer);

				if (memberReceiver != null )
				{
					memberReceiver.handleRTMPMedia(l16Buffer, kt2);

					if ( kt2 < 10 ) {
						loggerdebug( "*** " + l16Buffer.length );
					}

					kt2++;
				}
 			}
        }
    }
}
