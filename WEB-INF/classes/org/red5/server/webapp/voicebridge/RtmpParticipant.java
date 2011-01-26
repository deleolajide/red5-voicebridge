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

   	private float[] senderEncoderMap = new float[64];
   	private float[] recieverEncoderMap = new float[64];

    private static final int NELLYMOSER_CODEC_ID = 82;
    private static final int L16_AUDIO_LENGTH = 256;
    private static final int NELLY_AUDIO_LENGTH = 64;
    private static final int ULAW_AUDIO_LENGTH = 160;
    private static final int MAX_BUFFER_LENGTH = 1280;

    private final IntBuffer l16AudioSender = IntBuffer.allocate(MAX_BUFFER_LENGTH);
    private final IntBuffer l16AudioRecv = IntBuffer.allocate(MAX_BUFFER_LENGTH);

    private IntBuffer viewBufferSender;
    private IntBuffer viewBufferRecv;

    private int[] tempNellyBuffer = new int[L16_AUDIO_LENGTH];
    private final byte[] nellyBytes = new byte[NELLY_AUDIO_LENGTH];

    private int[] tempL16Buffer = new int[L16_AUDIO_LENGTH];
    private int[] l16Buffer = new int[ULAW_AUDIO_LENGTH];

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

    public void startStream(String host, String app, int port, String publishName, String playName, long conferenceStartTime ) {

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

			recieverEncoderMap = new float[64];
			senderEncoderMap = new float[64];

			viewBufferSender = l16AudioSender.asReadOnlyBuffer();
			viewBufferRecv = l16AudioRecv.asReadOnlyBuffer();

			try {
				connect( host, port, app, this );

			}
			catch ( Exception e ) {
				loggererror( "RtmpParticipant startStream exception " + e );
			}
		}
    }


    public void stopStream() {

        System.out.println( "RtmpParticipant stopStream" );

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
		int timeStamp = 0;

        if ( kt < 10 ) {
        	System.out.println( "RtmpParticipant.pushAudio() - dataToSend -> length = " + pcmBuffer.length + ".");
		}

		try {
			l16AudioSender.put(pcmBuffer);

			if ((l16AudioSender.position() - viewBufferSender.position()) >= L16_AUDIO_LENGTH)
			{
				// We have enough L16 audio to generate a Nelly audio.
				// Get some L16 audio

				viewBufferSender.get(tempNellyBuffer);

				// adjust volume
				//normalize(tempNellyBuffer);

				// Convert it into Nelly

				CodecImpl.encode(senderEncoderMap, tempNellyBuffer, nellyBytes);

				// Having done all of that, we now see if we need to send the audio or drop it.
				// We have to encode to build the encoderMap so that data from previous audio packet
				// will be used for the next packet.

				boolean sendPacket = true;
				IConnection conn = Red5.getConnectionLocal();

				if (conn instanceof RTMPMinaConnection) {
					long pendingMessages = ((RTMPMinaConnection)conn).getPendingMessages();

					if (pendingMessages > 25) {
						// Message backed up probably due to slow connection to client (25 messages * 20ms ptime = 500ms audio)
						sendPacket = false;
						System.out.println(String.format("Dropping packet. Connection %s congested with %s pending messages (~500ms worth of audio) .", conn.getClient().getId(), pendingMessages));
					}
				}

				if (sendPacket) {

					if (kt == 0)
					{
						startTime = System.currentTimeMillis();
						timeStamp = 0;

					} else {

						timeStamp = (int)(System.currentTimeMillis() - startTime);
					}

					pushAudio(NELLY_AUDIO_LENGTH, nellyBytes, timeStamp, NELLYMOSER_CODEC_ID);
				}
			}

		} catch (Exception e) {

            loggererror( "RtmpParticipant pushAudio exception " + e );
		}

        if (l16AudioSender.position() == l16AudioSender.capacity()) {
        	// We've processed 8 Ulaw packets (5 Nelly packets), reset the buffers.
        	l16AudioSender.clear();
        	viewBufferSender.clear();
        }
	}

    public void pushAudio( int len, byte[] audio, int ts, int codec ) throws IOException {

        if ( buffer == null ) {
            buffer = IoBuffer.allocate( 1024 );
            buffer.setAutoExpand( true );
        }

        buffer.clear();

        buffer.put( (byte) codec ); // first byte 2 mono 5500; 6 mono 11025; 22
        // mono 11025 adpcm 82 nellymoser 8000 178
        // speex 8000
        buffer.put( audio );

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
    }


   	public int[] normalize(int[] audio)
   	{
	    // Scan for max peak value here
	    float peak = 0;
		for (int n = 0; n < audio.length; n++)
		{
			int val = Math.abs(audio[n]);
			if (val > peak)
			{
				peak = val;
			}
		}

		// Peak is now the loudest point, calculate ratio
		float r1 = 32768 / peak;

		// Don't increase by over 500% to prevent loud background noise, and normalize to 75%
		float ratio = Math.min(r1, 5) * .75f;

		for (int n = 0; n < audio.length; n++)
		{
			audio[n] *= ratio;
		}

		return audio;

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


        public void dispatchEvent( IEvent event ) {

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

				byte[] asaoInput = new byte[audioData.limit() - 1];
				audioData.rewind();
				audioData.position(audioData.position() + 1);
				audioData.get(asaoInput);

                CodecImpl.decode(recieverEncoderMap, asaoInput, tempL16Buffer);

				l16AudioRecv.put(tempL16Buffer);	// Store the L16 audio into the buffer

    			viewBufferRecv.get(l16Buffer);		// Read 160-int worth of audio
				sendToBridge(l16Buffer);

    			if (l16AudioRecv.position() == l16AudioRecv.capacity())
    			{
					/**
					 *  This means we already processed 5 Nelly packets and sent 5 Ulaw packets.
					 *  However, we have 3 extra Ulaw packets.
					 *  Fire them off to the bridge. We don't want to discard them as it will
					 *  result in choppy audio.
					 */

					for (int i=0; i<3; i++)
					{
						viewBufferRecv.get(l16Buffer);
						sendToBridge(l16Buffer);
					}

					// Reset the buffer's position back to zero and start over.

					l16AudioRecv.clear();
					viewBufferRecv.clear();
				}
			}
        }

        private void sendToBridge(int[] encodingBuffer)
        {
			try {

				if (memberReceiver != null ) {

					memberReceiver.handleRTMPMedia(encodingBuffer, kt2);

					if ( kt2 < 10 ) {
						loggerdebug( "*** " + encodingBuffer.length );
					}

					kt2++;
				}
			}
			catch ( Exception e ) {
				loggererror( "RtmpParticipant => sendToBridge error " + e );
				e.printStackTrace();
			}
		}
    }
}
