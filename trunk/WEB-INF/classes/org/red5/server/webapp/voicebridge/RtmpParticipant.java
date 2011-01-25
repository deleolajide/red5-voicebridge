package org.red5.server.webapp.voicebridge;

import java.io.*;
import java.util.*;

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
	private long start = System.currentTimeMillis();
   	private float[] senderEncoderMap = new float[64];
   	private float[] recieverEncoderMap = new float[64];
   	private float[] tempBuffer = new float[ 256 ];
   	private int tempBufferOffset = 0;

   	private boolean asao_buffer_processed = false;
    private float[] tempSendBuffer  = new float[ 256 ];
    private int[] encodingBuffer = new int[ 160 ];
    private int encodingOffset = 0;
    private int tempBufferRemaining = 0;


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
			start = System.currentTimeMillis();

			senderEncoderMap = new float[64];
			recieverEncoderMap = new float[64];
			tempBuffer = new float[ 256 ];
			tempBufferOffset = 0;

            tempSendBuffer = new float[ 256 ];
            encodingBuffer = new int[ 160 ];

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


        int pcmBufferOffset = 0;
        int copySize = 0;
        boolean pcmBufferProcessed = false;

        do {
			if ( ( tempBuffer.length - tempBufferOffset ) <= ( pcmBuffer.length - pcmBufferOffset ) ) {

				copySize = tempBuffer.length - tempBufferOffset;
			}
			else {

				copySize = pcmBuffer.length - pcmBufferOffset;
			}


			bufferIndexedCopy(tempBuffer, tempBufferOffset, pcmBuffer, pcmBufferOffset, copySize );
			tempBufferOffset += copySize;
			pcmBufferOffset += copySize;

			if ( tempBufferOffset == 256  )
			{
				try {
					byte[] encodedStream = new byte[ 64 ];
					//tempBuffer = normalize(tempBuffer, 256);
					CodecImpl.encode(senderEncoderMap, tempBuffer, encodedStream);

					if (kt == 0)
					{
						start = System.currentTimeMillis();
						timeStamp = 0;

					} else {

						timeStamp = (int)(System.currentTimeMillis() - start);
					}

					pushAudio(64, encodedStream, timeStamp, 82);

				} catch (Exception e) {

					loggererror( "RtmpParticipant pushAudio exception " + e );
					e.printStackTrace();
				}

				tempBufferOffset = 0;
			}

			if ( pcmBufferOffset == pcmBuffer.length )
			{
				pcmBufferProcessed = true;
			}

        } while ( !pcmBufferProcessed );
	}

    private int bufferIndexedCopy(int[] destBuffer, int startDestBuffer, float[] origBuffer, int startOrigBuffer, int copySize )
    {
        int destBufferIndex = startDestBuffer;
        int origBufferIndex = startOrigBuffer;
        int counter = 0;

        if ( kt2 < 10 )
        {
			loggerdebug( "bufferIndexedCopy " +
					"destBuffer.length = " + destBuffer.length +
					", startDestBuffer = " + startDestBuffer +
					", origBuffer.length = " + origBuffer.length +
					", startOrigBuffer = " + startOrigBuffer +
					", copySize = " + copySize + "." );
		}

        if ( destBuffer.length < ( startDestBuffer + copySize ) ) {
            loggererror( "floatBufferIndexedCopy Size copy problem." );
            return -1;
        }

        for ( counter = 0; counter < copySize; counter++ ) {
            destBuffer[ destBufferIndex ] = (int) origBuffer[ origBufferIndex ];

            destBufferIndex++;
            origBufferIndex++;
        }

        //loggerdebug( "floatBufferIndexedCopy", counter + " bytes copied." );

        return counter;
    }

    private int bufferIndexedCopy(float[] destBuffer, int startDestBuffer, int[] origBuffer, int startOrigBuffer, int copySize )
    {
        int destBufferIndex = startDestBuffer;
        int origBufferIndex = startOrigBuffer;
        int counter = 0;

        if ( kt < 10 )
        {
			loggerdebug( "bufferIndexedCopy " +
					"destBuffer.length = " + destBuffer.length +
					", startDestBuffer = " + startDestBuffer +
					", origBuffer.length = " + origBuffer.length +
					", startOrigBuffer = " + startOrigBuffer +
					", copySize = " + copySize + "." );
		}

        if ( destBuffer.length < ( startDestBuffer + copySize ) ) {
            loggererror( "floatBufferIndexedCopy Size copy problem." );
            return -1;
        }

        for ( counter = 0; counter < copySize; counter++ ) {
            destBuffer[ destBufferIndex ] = (float) origBuffer[ origBufferIndex ];

            destBufferIndex++;
            origBufferIndex++;
        }

        //loggerdebug( "floatBufferIndexedCopy", counter + " bytes copied." );

        return counter;
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


   	public float[] normalize(float[] audio, int length)
   	{
	    // Scan for max peak value here
	    float peak = 0;
		for (int n = 0; n < length; n++)
		{
			float val = Math.abs(audio[n]);
			if (val > peak)
			{
				peak = val;
			}
		}

		// Peak is now the loudest point, calculate ratio
		float r1 = 32768 / peak;

		// Don't increase by over 500% to prevent loud background noise, and normalize to 98%
		float ratio = Math.min(r1, 5) * .98f;

		for (int n = 0; n < length; n++)
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
                byte[] asaoInput = SerializeUtils.ByteBufferToByteArray( audioData );

                int offset = 1;
                int num = asaoInput.length - 1;

                asao_buffer_processed = false;

				if ( num > 0 ) {

					do {
						byte [] asaoBuffer = new byte[num];
						System.arraycopy(asaoInput, offset, asaoBuffer, 0, num);
						int encodedBytes = decodeAsao( asaoBuffer );

						if ( encodedBytes == 0 ) {

							break;
						}

						if ( encodingOffset == 160) {

							//loggerdebug( "send", "Sending packet with " + encodedBytes + " bytes." );

							try {

								if (memberReceiver != null ) {
									memberReceiver.handleRTMPMedia(encodingBuffer, kt2);

									kt2++;

									if ( kt2 < 10 ) {
										loggerdebug( "*** " + encodingBuffer.length );
									}
								}
							}
							catch ( Exception e ) {
								loggererror( "RtmpParticipant => memberReceiver handleMedia error." );
								e.printStackTrace();
							}

							encodingOffset = 0;
						}

					} while ( !asao_buffer_processed );
				}
            }
        }

        private int decodeAsao(byte[] asaoBuffer)
        {
			boolean isBufferFilled = false;
			int copyingSize = 0;
			int finalCopySize = 0;
			byte[] codedBuffer = new byte[ 160 ];

            if ( ( tempBufferRemaining + encodingOffset ) >= 160 ) {

                copyingSize = encodingBuffer.length - encodingOffset;
                bufferIndexedCopy(encodingBuffer, encodingOffset, tempSendBuffer, tempSendBuffer.length - tempBufferRemaining, copyingSize );

                encodingOffset = 160;
                tempBufferRemaining -= copyingSize;
                finalCopySize = 160;
            }
            else {

                if ( tempBufferRemaining > 0 )
                {
                    bufferIndexedCopy(encodingBuffer,encodingOffset, tempSendBuffer, tempSendBuffer.length - tempBufferRemaining, tempBufferRemaining );

                    encodingOffset += tempBufferRemaining;
                    finalCopySize += tempBufferRemaining;
                    tempBufferRemaining = 0;

					if ( kt2 < 10 )
					{
						loggerdebug( "decodeAsao "
						        + "tempBufferRemaining copied -> "
						        + "encodingOffset = " + encodingOffset
						        + ", tempBufferRemaining = " + tempBufferRemaining + "." );
					}
                }
                asao_buffer_processed = true;

                CodecImpl.decode(recieverEncoderMap, asaoBuffer, tempSendBuffer);
                //tempSendBuffer = normalize(tempSendBuffer, 256);

                tempBufferRemaining = tempBuffer.length;

                if ( ( encodingOffset + tempBufferRemaining ) > 160 ) {
                    copyingSize = encodingBuffer.length - encodingOffset;
                }
                else {
                    copyingSize = tempBufferRemaining;
                }

                //println( "fillRtpPacketBuffer CopyingSize = " + copyingSize + "." );

                bufferIndexedCopy(encodingBuffer, encodingOffset, tempSendBuffer, 0, copyingSize );

                encodingOffset += copyingSize;
                tempBufferRemaining -= copyingSize;
                finalCopySize += copyingSize;
            }

        	return finalCopySize;
		}
    }
}
