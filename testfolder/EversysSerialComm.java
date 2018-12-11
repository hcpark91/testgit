package com.dalkomm.beat.booth.manager.comm.serial;

import java.beans.IntrospectionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dalkomm.beat.booth.manager.conf.Configuration;
import com.dalkomm.beat.booth.manager.dao.ConfigDao;
import com.dalkomm.beat.booth.manager.data.config.SerialConfig;
import com.dalkomm.beat.booth.manager.exception.DeviceInitializeException;
import com.dalkomm.beat.booth.manager.exception.SerialCommException;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;


/**
 * @author Park, Hyun-Cheol
 * @since 2018. 11. 28.
 * @version 1.0
 */


public class EversysSerialComm {
	
	private PacketEventListener mListener = null;

    public void setListener(PacketEventListener listener) {
        this.mListener = listener;
    }

	protected void fireAckNackReceived(boolean timeout) {
        if (mListener != null) {
            Object sender = this; 
            PacketEventArgs eventArgs = new PacketEventArgs(timeout);
            mListener.PacketEventFired(sender, eventArgs);
        }
    }
	
	protected void fireAckNackReceived(Packet_t packet) {
        if (mListener != null) {
            Object sender = this; 
            PacketEventArgs eventArgs = new PacketEventArgs(packet);
            mListener.PacketEventFired(sender, eventArgs);
        }
    }

    private void callPacketSentHandler(Packet_t p)
    {
        if (mListener != null)
        	mListener.packetSent(p);
    }

    private void callPacketReceivedHandler(Packet_t p)
    {
        if (mListener != null)
        	mListener.packetReceived(p);
    }
    private void callAckTimeoutHandler(Packet_t p)
    {
        if (mListener != null)
        	mListener.ackTimeout(p);
    }
    private void callNackFailHandler(Packet_t p)
    {
        if (mListener != null)
        	mListener.nackFail(p);
    }
    private void callResponseTimeoutHandler(Packet_t p)
    {
        if (mListener != null)
        	mListener.responseTimeout(p);
    }
    private void callDataSentInterceptHandler(byte[] arr)
    {
        if (mListener != null)
        	mListener.dataSentIntercept(arr);
    }
    
	
	/*  telegram structure: 
     * --------------------------------------------------------------------------------
     *  Byte number:     0 | 1 |2 |3 |4 |5 |6 | 7 | 8 | 9 |10 |10+n  |11+n|12+n|13+n
     *  Byte name:      SOH|PIP|PE|PN|SA|DA|MI|MP1|MP2|DL1|DL2|DATA[]|CRC1|CRC2|EOT
     *  n: data length (n=DL2 + DL1*256)
     *  SOH: Start of heading (0x01)
     *  PIP: currently not used (0x00)
     *  PIE: see typedef (command/response=0x68; request=0x6C; ack=0x6A; nack=0x6B
     *  PN: packet number (incrementing number)
     *  SA: source address ( usually 0x42)
     *  DA: destination address (usually 0x41)
     *  MI: command type (see documentation for more information)
     *  MP1: message parameter (lower byte)
     *  MP2: message parameter (higher byte)
     *  DL1: data length (lower byte)
     *  DL2: data length (higher byte)
     *  DATA[]: Data bytes
     *  CRC1: crc value (lower byte)
     *  CRC2: crc value (higher byte)
     *  EOT: End of text (0x04)
     ***********************************************************************************/
	
	/// <summary>
    /// Bit 0-2 in the PIE byte.
    /// </summary>
	public enum PacketType_t {
		Data_e( "Data_e", (byte)0 ),
		Reserved_e( "Reserved_e", (byte)1 ),	// reserved for future use
		PosAck_e( "PosAck_e", (byte)2 ),
		NegAck_e( "NegAck_e", (byte)3 ),
		Request_e( "Request_e", (byte)4 );
		
		private final String name;
	    private final byte data;

	    private PacketType_t(String name, byte data)
	    {
	        this.name = name;
	        this.data = data;
	    }
	    
	    public byte getData() {
	    	return this.data;
	    }
	    
	    public static PacketType_t fromInteger(int x) {
	        switch(x) {
	        case 0:
	            return Data_e;
	        case 1:
	            return Reserved_e;
	        case 2:
	            return PosAck_e;
	        case 3:
	            return NegAck_e;
	        case 4:
	            return Request_e;
	        }
	        return null;
	    }
	}
	
	/// <summary>
    /// Port ranges and what they are assigned to.
    /// Bit 3-6 in the PIE byte.
    /// </summary>
    public enum ApplicationPort_t {
        //ProgrammingProtocol_e = 0x0,    // 0x0 ... 0xB Reserviert für das Programmier-Protokoll
        //ProgrammingMax_e = 0xB,

        //DominoApplication_e = 0xC,      // 0xC ... 0xF, Applikationen
    	Api_e( "Api_e", (byte)0xD );	  // Api_e = 0xD
        //ApplicationMax_e = 0xF
        		
		private final String name;
	    private final byte data;

	    private ApplicationPort_t(String name, byte data)
	    {
	        this.name = name;
	        this.data = data;
	    }
	    
	    public byte getData() {
	    	return this.data;
	    }
	    
	    public static ApplicationPort_t fromInteger(int x) {
	        switch(x) {
	        case 0xD:
	            return Api_e;
	        }
	        return null;
	    }
    }
	
	/// <summary>
    /// Defines if the packet is encrypted.
    /// Bit 7 in the PIE byte.
    /// </summary>
	public enum Encrypt_t {
		No( "No", 0 ),
		Yes( "Yes", 1 );
		
		private final String name;
	    private final int data;

	    private Encrypt_t(String name, int data)
	    {
	        this.name = name;
	        this.data = data;
	    }
	    
	    public int getData() {
	    	return this.data;
	    }
	    
	    public static Encrypt_t fromInteger(int x) {
	        switch(x) {
	        case 0:
	            return No;
	        case 1:
	            return Yes;
	        }
	        return null;
	    }
    }
	
	/// <summary>
    /// Reserved and special characters
    /// </summary>
    public enum SpecialChars_t {
    	SOH_e( "SOH_e", (byte)0x01 ),   			// Start of Header (Beginn eines Pakets)
    	ETB_e( "ETB_e", (byte)0x17 ), 				// ETB_e = 0x17,           // End of Transmit Block (Ende eines Pakets)
    	ShiftChar_e( "ShiftChar_e", (byte)0x10 ),	// ShiftChar_e = 0x10,     // Shift Character (Next Char XOR with ShiftXOR)

    	NUL_e( "NUL_e", (byte)0x00 ), 	 	// NUL_e = 0x00,           // NULL char
    	STX_e( "STX_e", (byte)0x02 ),		// STX_e = 0x02,           // Start of Text
    	ETX_e( "ETX_e", (byte)0x03 ),		// ETX_e = 0x03,           // End of Text
    	EOT_e( "EOT_e", (byte)0x04 ),		// EOT_e = 0x04,           // End of Transmission
    	LF_e( "LF_e", (byte)0x0A ),			// LF_e = 0x0A,            // Line Feed
    	CR_e( "CR_e", (byte)0x0D ),			// CR_e = 0x0D,            // Carriage Return
    	ModemEsc_e( "ModemEsc_e", (byte)0x2B ),	// ModemEsc_e = 0x2B,      // '+' Standard Modem Escape Character

    	ShiftXOR_e( "ShiftXOR_e", (byte)0x40 );	// ShiftXOR_e = 0x40       // Shift XOR Value */
        
        private final String name;
	    private final byte data;

	    private SpecialChars_t(String name, byte data)
	    {
	        this.name = name;
	        this.data = data;
	    }
	    
	    public byte getData() {
	    	return this.data;
	    }
    }
	
	/// <summary>
    /// Data-header without the data length.
    /// Contains the command id and the 16bit parameter value.
    /// MI, MP[0:7], MP[8:15]
    /// </summary>
    public class Message_t
    {
        public byte command;
        public short parameter;			// hcpark
        public Message_t(byte cmd, short param)
        {
            this.command = cmd;
            this.parameter = param;
        }
    }
    
  /// <summary>
    /// Raw telegram packet with data-array and the data length. (including SOH and EOT)
    /// Shifted character are still shifted.
    /// </summary>
    public class RawPacket_t
    {
        public byte[] data;
        public int length;
        public RawPacket_t(byte[] data, int length)
        {
            this.data = new byte[length];
            this.length = length;
            System.arraycopy(data, 0, this.data, length, length);
        }
    }
    
    /// <summary>
    /// CRC value; Byte CRC[0:7] and CRC[8:15]
    /// </summary>
    public class PacketCRC_t {
        public short As16BitVal;
        public byte LowByte;
        public byte HighByte;
        
        byte getLowByte() {
        	return (byte)(As16BitVal & 0xFF);		// hcpark
        }
        
        byte getHighByte() {
        	return (byte) ((As16BitVal >> 8) & 0xFF);
        }
    }
	
	/// <summary>
    /// Telegram packet without SOH and EOT
    /// isFail doesn't belong to the actual packet. It is used to tag length and crc fails.
    /// </summary>
	public class Packet_t {
		public byte parity;          // set for (number of bits set in header % 3) = 0
        public byte protoVersion;    // 0: initial version, 1: version with ack and nack
        public Encrypt_t isEncrypted;
        public ApplicationPort_t appPort;
        public PacketType_t type;
        public byte sequenceNumber;
        public byte source;
        public byte destination;
        public Message_t message;
        public int dataLength;
        public byte[] data;
        public PacketCRC_t CRC;

        public boolean isFail;         // true= this packet has an error in it

        /// <summary>
        /// Converts the packet to a byte array.
        /// </summary>
        /// <returns> packet as a byte array. With CRC, without EOT and SOH. Data is not shifted. </returns>
        public byte[] ToByteArray()
        {
            byte[] buffer;
            if (type == PacketType_t.Data_e || type == PacketType_t.Request_e)
            {
                buffer = new byte[DATAHEADER_OFFSET + PACKETHEADER_OFFSET + dataLength + CRC_SIZE];
            }
            else
            {
                buffer = new byte[PACKETHEADER_OFFSET];
            }

            buffer[0] = (byte)((parity << 6) | (protoVersion & 0x3F));
            
            //< hcpark
            buffer[1] = (byte)(((byte)isEncrypted.getData() << 7) | ((byte)appPort.getData() << 3) | ((byte)type.getData() & 0x7));
            //>
            
            buffer[2] = sequenceNumber;
            buffer[3] = (byte)source;
            buffer[4] = (byte)destination;
            if (type == PacketType_t.Data_e || type == PacketType_t.Request_e)
            {
                buffer[5] = (byte)message.command;
                buffer[6] = (byte)message.parameter;
                buffer[7] = (byte)(message.parameter >> 8);
                buffer[8] = (byte)dataLength;
                buffer[9] = (byte)(dataLength >> 8);

                if (dataLength > 0)
                {
                    //Array.Copy(data, 0, buffer, DATAHEADER_OFFSET + PACKETHEADER_OFFSET, dataLength);
                	System.arraycopy(data, 0, buffer, DATAHEADER_OFFSET + PACKETHEADER_OFFSET, dataLength);
                }

                CRC = new PacketCRC_t();
                CRC.As16BitVal = CalculateCRC(buffer, buffer.length - CRC_SIZE);		// hcpark ref buffer
                buffer[DATAHEADER_OFFSET + PACKETHEADER_OFFSET + dataLength] = CRC.getLowByte();
                buffer[DATAHEADER_OFFSET + PACKETHEADER_OFFSET + dataLength + 1] = CRC.getHighByte();
            }
            return buffer;
        }

        /// <summary>
        /// Converts this packet to a byte array with shifted chars, SOH and EOT
        /// </summary>
        /// <returns>raw data array</returns>
        public byte[] ToRawArray()
        {
            return ProcessToSend(this.ToByteArray());
        }
	}
	
	/// <summary>
    /// Address of this device.
    /// </summary>
    public byte Address;
    public byte getAddress() {
		return Address;
	}

	public void setAddress(byte address) {
		Address = address;
	}


    /// <summary>
    /// Maximal timeout until an Ack/Nack is received.
    /// </summary>
	public Duration AckTimeout;
    public Duration getAckTimeout() {
		return AckTimeout;
	}

	public void setAckTimeout(Duration ackTimeout) {
		AckTimeout = ackTimeout;
	}

    /// <summary>
    /// Maximal timeout until a response is received.
    /// </summary>
	public Duration ResponseTimeout;
    public Duration getResponseTimeout() {
		return ResponseTimeout;
	}

	public void setResponseTimeout(Duration responseTimeout) {
		ResponseTimeout = responseTimeout;
	}

    /// <summary>
    /// Maximal number of retries until an ack is received, without the initial attempt.
    /// </summary>
    /// <see cref="AckTimeout"/>
	public int MaxSendRepetition;
    public int getMaxSendRepetition() {
		return MaxSendRepetition;
	}

	public void setMaxSendRepetition(int maxSendRepetition) {
		MaxSendRepetition = maxSendRepetition;
	}

    /// <summary>
    /// How long the thread will be inactive after every execution.
    /// </summary>
	public int ThreadSleepTime;
    public int getThreadSleepTime() {
		return ThreadSleepTime;
	}

	public void setThreadSleepTime(int threadSleepTime) {
		ThreadSleepTime = threadSleepTime;
	}


    /// <summary>
    /// Maximal size of a packet in bytes.
    /// </summary>
	private int _maxPacketSize;
    public int getMaxPacketSize() {
		return _maxPacketSize;
	}

	public void setMaxPacketSize(int _maxPacketSize) {
		this._maxPacketSize = _maxPacketSize;
		if (ApiPort != null)
        {
            //ApiPort.WriteBufferSize = 2 * _maxPacketSize;
            //ApiPort.ReadBufferSize = 2 * _maxPacketSize;
        }
        //Array.Resize(ref rawDataBuffer, _maxPacketSize);
		rawDataBuffer = Arrays.copyOfRange(rawDataBuffer,0,_maxPacketSize);		//hcpark
	}

    /// <summary>
    /// Priority of the thread which manages the serial communication
    /// </summary>
    private int _transmissionThreadPriority;		// 1 ~ 10
    public int getTransmissionThreadPriority() {
		return _transmissionThreadPriority;
	}

	public void setTransmissionThreadPriority(int _transmissionThreadPriority) {
		this._transmissionThreadPriority = _transmissionThreadPriority;
        if (TransmissionThread != null)
            TransmissionThread.setPriority( _transmissionThreadPriority );
	}

    /// <summary>
    /// Last packet which was given to the EnqueuePacket method
    /// </summary>
    private Packet_t _lastSentPacket;
    public Packet_t getLastSentPacket() {
		return _lastSentPacket;
	}

    private boolean _sendIsIdle = true;
    private boolean _receiveIsIdle = true;
    /// <summary>
    /// If the value is true then the port is currently not receiving or sending anything.
    /// This value is used to safely terminate the used thread and close the port.
    /// </summary>
    public boolean PortIsIdle() {
    	if (rawDataBufferLength == 0 && _sendIsIdle && _receiveIsIdle)
            return true;
        else
            return false;
	}


    /// <summary>
    /// True if the port is open.
    /// </summary>
    public boolean PortIsOpen() {
        return ApiPort.isOpen();
    }

    /// <summary>
    /// True if the port will be changed.
    /// </summary>
    public boolean PortIsChanging() {
        if (newSerialName != "")
            return true;
        else
            return false;
    }

    /// <summary>
    /// If this value is true nothing will be sent back.
    /// </summary>
    public boolean ReceiveOnlyMode = false;

    private int _baudRate;
    /// <summary>
    /// Baudrate of the serial port.
    /// </summary>
    public int BaudRate() {
        return _baudRate;
    }

    private String _portName;
    /// <summary>
    /// Name of the serial port.
    /// </summary>
    public String PortName() {
        return _portName;
    }

    /// <summary>
    /// Sequence number of the last sent packet.
    /// </summary>
    public byte LastSequenceNumber() {
        if (sequenceNumber == 0)
            return (byte)255;
        else
            return (byte)(sequenceNumber - 1);
    }

	/// <summary>
    /// Sequence number of the last incoming and acknowledged command
    /// </summary>
    private int lastAckCommand = -1;

    /// <summary>
    /// Counts Ack_e timeouts until it reaches MaxSendRepetition
    /// </summary>
    private int ackTimeoutCounter = 0;

    /// <summary>
    /// Counts Nacks until it reaches MaxSendRepetition
    /// </summary>
    private int nackCounter = 0;

    /// <summary>
    /// This number increments after every new command/request; repetitions are with the same number.
    /// </summary>
    private byte sequenceNumber = 0;

    /// <summary>
    /// Flag; set when a request is in the queue and data with the same sequenceNumber was received.
    /// </summary>
    private boolean responseReceived = false;

    /// <summary>
    /// Flag; set when a request was sent but the response was not yet received
    /// </summary>
    private boolean requestOpen = false;

    /// <summary>
    /// Always set to the time when the last packet was received.
    /// Used to prohibit the sending of a response or Ack/Nack after the timeout time was exceeded.
    /// </summary>
    private LocalDateTime incomingTimeStamp = LocalDateTime.now();		//hcpark : DateTime...

    /// <summary>
    /// Length of the rawDataBuffer array. (Number of filled fields rather than the array length).
    /// </summary>
    private int rawDataBufferLength = 0;

    /// <summary>
    /// Buffer to save the data which is received on the serial port. Its size equals MaxPacketSize.
    /// </summary>
    private byte[] rawDataBuffer = new byte[512];

    /// <summary>
    /// When this flag is set to true the Serialport will be closed as soon as the ongoing transmission is finished.
    /// </summary>
    private boolean stopSerialPort = false;

    /// <summary>
    /// When the ChangePort function is called and the port is currently busy then the new baudrate is stored here.
    /// </summary>
    private int newSerialBaudrate = 0;

    /// <summary>
    /// When the ChangePort function is called and the port is currently busy then the new name is stored here.
    /// </summary>
    private String newSerialName = "";

    /// <summary>
    /// Stop flag for the transmission thread.
    /// </summary>
    private boolean stopTransmissionThread = false;

    /// <summary>
    /// number of bytes in the packet-header(PIP+PIE+PN+SA+DA)
    /// </summary>
    final int PACKETHEADER_OFFSET = 5;
    /// <summary>
    /// number of bytes in the data-header(MI+MP+DL)
    /// </summary>
    final int DATAHEADER_OFFSET = 5;
    /// <summary>
    /// number of bytes in the packet and in the data-header(PIP+PIE+PN+SA+DA+MI+MP+DL)
    /// </summary>
    final int HEADER_OFFSET = 10;

    /// <summary>
    /// initial value for the crc calculation
    /// </summary>
    static final short CRC_INIT = (short)0xFFFF;					//hcpark size check
    /// <summary>
    /// size of the CRC in bytes
    /// </summary>
    final int CRC_SIZE = 2;
    /// <summary>
    /// Protocol version. Should be incremented after every major release.
    /// </summary>
    final byte PROTOCOL_VERSION = 0;

    
	private SerialPort ApiPort = null;
	private Thread TransmissionThread = null;
    private Queue<Packet_t> OutgoingQueue = new LinkedList<Packet_t>();
    private Queue<Packet_t> IncomingDataQueue = new LinkedList<Packet_t>();
    private Queue<Packet_t> IncomingAckQueue = new LinkedList<Packet_t>();
    private Queue<RawPacket_t> IncomingRawQueue = new LinkedList<RawPacket_t>();
    
	/// <summary>
    /// LUT for the CRC-16-IBM (x^16 + x^15 + x^2 + 1)
    /// </summary>
    static int[] crcPolynomTable = 
    {
    		// hcpark : ushort => int
        0x0000,0xc0c1,0xc181,0x0140,0xc301,0x03c0,0x0280,0xc241,
        0xc601,0x06c0,0x0780,0xc741,0x0500,0xc5c1,0xc481,0x0440,
        0xcc01,0x0cc0,0x0d80,0xcd41,0x0f00,0xcfc1,0xce81,0x0e40,
        0x0a00,0xcac1,0xcb81,0x0b40,0xc901,0x09c0,0x0880,0xc841,
        0xd801,0x18c0,0x1980,0xd941,0x1b00,0xdbc1,0xda81,0x1a40,
        0x1e00,0xdec1,0xdf81,0x1f40,0xdd01,0x1dc0,0x1c80,0xdc41,
        0x1400,0xd4c1,0xd581,0x1540,0xd701,0x17c0,0x1680,0xd641,
        0xd201,0x12c0,0x1380,0xd341,0x1100,0xd1c1,0xd081,0x1040,
        0xf001,0x30c0,0x3180,0xf141,0x3300,0xf3c1,0xf281,0x3240,
        0x3600,0xf6c1,0xf781,0x3740,0xf501,0x35c0,0x3480,0xf441,
        0x3c00,0xfcc1,0xfd81,0x3d40,0xff01,0x3fc0,0x3e80,0xfe41,
        0xfa01,0x3ac0,0x3b80,0xfb41,0x3900,0xf9c1,0xf881,0x3840,
        0x2800,0xe8c1,0xe981,0x2940,0xeb01,0x2bc0,0x2a80,0xea41,
        0xee01,0x2ec0,0x2f80,0xef41,0x2d00,0xedc1,0xec81,0x2c40,
        0xe401,0x24c0,0x2580,0xe541,0x2700,0xe7c1,0xe681,0x2640,
        0x2200,0xe2c1,0xe381,0x2340,0xe101,0x21c0,0x2080,0xe041,
        0xa001,0x60c0,0x6180,0xa141,0x6300,0xa3c1,0xa281,0x6240,
        0x6600,0xa6c1,0xa781,0x6740,0xa501,0x65c0,0x6480,0xa441,
        0x6c00,0xacc1,0xad81,0x6d40,0xaf01,0x6fc0,0x6e80,0xae41,
        0xaa01,0x6ac0,0x6b80,0xab41,0x6900,0xa9c1,0xa881,0x6840,
        0x7800,0xb8c1,0xb981,0x7940,0xbb01,0x7bc0,0x7a80,0xba41,
        0xbe01,0x7ec0,0x7f80,0xbf41,0x7d00,0xbdc1,0xbc81,0x7c40,
        0xb401,0x74c0,0x7580,0xb541,0x7700,0xb7c1,0xb681,0x7640,
        0x7200,0xb2c1,0xb381,0x7340,0xb101,0x71c0,0x7080,0xb041,
        0x5000,0x90c1,0x9181,0x5140,0x9301,0x53c0,0x5280,0x9241,
        0x9601,0x56c0,0x5780,0x9741,0x5500,0x95c1,0x9481,0x5440,
        0x9c01,0x5cc0,0x5d80,0x9d41,0x5f00,0x9fc1,0x9e81,0x5e40,
        0x5a00,0x9ac1,0x9b81,0x5b40,0x9901,0x59c0,0x5880,0x9841,
        0x8801,0x48c0,0x4980,0x8941,0x4b00,0x8bc1,0x8a81,0x4a40,
        0x4e00,0x8ec1,0x8f81,0x4f40,0x8d01,0x4dc0,0x4c80,0x8c41,
        0x4400,0x84c1,0x8581,0x4540,0x8701,0x47c0,0x4680,0x8641,
        0x8201,0x42c0,0x4380,0x8341,0x4100,0x81c1,0x8081,0x4040
    };
    
    //================
    // public methods
    //================
    /// <summary>
    /// Default values:
    /// ThreadSleepTime = 1ms
    /// MaxPacketSize = 256
    /// SequenceNumber = 0
    /// MaxSendRepetition = 3
    /// Timeouts = 200ms
    /// </summary>
    /// <param name="portName">Name of the port e.g. COM1</param>
    /// <param name="baudRate">Baudrate e.g. 9600</param>
    /// <param name="address">ID of this device (source id)</param>
    public void SerialComm(String portName, int baudRate, byte address)
    {
        Address = address;
        AckTimeout = Duration.ofMillis(200);
        ResponseTimeout = Duration.ofMillis(200);
        MaxSendRepetition = 3;
        ThreadSleepTime = 1;
        setMaxPacketSize(256);
        sequenceNumber = 0;
        setTransmissionThreadPriority(10); //ThreadPriority.Highest;

        _baudRate = baudRate;
        _portName = portName;

        //ApiPort = new SerialPort(portName, baudRate, Parity.None, 8, StopBits.One);
        
        try
        {
        	initialize();
            ApiPort = getSerialPort(portName);
            readySerialPort(ApiPort);
        } catch (DeviceInitializeException e) {
        	logger.error(e.toString());
        }
    }
    
    /**
	 * @param serial
	 */
	public void readySerialPort(SerialPort serial) {
		if(!serial.isOpen()) {
			serial.setBaudRate(_baudRate);
			serial.openPort();
			
			// 8N1 => default setting
			
			serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 
					DEFAULT_COMM_READ_TIMEOUT, DEFAULT_COMM_WRITE_TIMEOUT);
		}
	}
	
	/// <summary>
    /// Opens the port and starts the thread.
    /// </summary>
    public void Start()
    {
    	ApiPort.setBaudRate(_baudRate);
		
        boolean success = false;
        int tries = 0;
        if (!ApiPort.isOpen())
        {
            while (success == false && tries < 10)
            {
               	success = ApiPort.openPort();
                if (!success) tries++;
            
                stopSerialPort = false;
            }
        }

        if (TransmissionThread == null)
        {
            TransmissionThread = new Thread(new Runnable() {
				@Override
				public void run()
				{
					TransmissionThreadWorker();
				}
			});
            TransmissionThread.setPriority(getTransmissionThreadPriority());
            TransmissionThread.start();
        }

        //ApiPort.DataReceived += DataReceivedHandler;
        ApiPort.addDataListener(new SerialPortDataListener() {

			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
			}

			@Override
			public void serialEvent(SerialPortEvent event) {
				if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
					 return;
				_receiveIsIdle = false;
				byte[] buffer = new byte[ApiPort.bytesAvailable()];
				int bytesRead = ApiPort.readBytes(buffer, buffer.length);
				ProcessRawData(buffer, bytesRead);
				_receiveIsIdle = true;
			}
		});
    }
    
    /// <summary>
    /// Only starts the thread.
    /// </summary>
    public void StartThread()
    {
    	if (TransmissionThread == null)
        {
            TransmissionThread = new Thread(new Runnable() {
				@Override
				public void run()
				{
					TransmissionThreadWorker();
				}
			});
            TransmissionThread.setPriority(getTransmissionThreadPriority());
            TransmissionThread.start();
        }
        stopSerialPort = false;
    }
    
    public void Close()
    {
        Pause();
    }
    
  /// <summary>
    /// Closes the Port but does not terminate the thread.
    /// </summary>
    public void Pause()
    {
        stopSerialPort = true;
        if (PortIsIdle() && PortIsOpen())
        {
            ApiPort.closePort();
            _sendIsIdle = true;
            _receiveIsIdle = true;
        }
    }
    
    /// <summary>
    /// Initiates a port change.
    /// </summary>
    /// <param name="portname">new port name</param>
    /// <param name="baudrate">new baudrate</param>
    public void ChangePort(String portname, int baudrate)
    {
        if (!ApiPort.isOpen())
        {
            _baudRate = baudrate;
            _portName = portname;
            try
            {
            	initialize();
                ApiPort = getSerialPort(portname);
                ApiPort.setBaudRate( baudrate );
            } catch (DeviceInitializeException e) {
            	logger.error(e.toString());
            }
        }
        else
        {
            Pause();
            newSerialBaudrate = baudrate;
            newSerialName = portname;
        }
    }
    
    //#region EnqueuePacket overload
    /// <summary>
    /// Normaly this should not be used. Used to manually send an ack/nack or
    /// to send a response with an undef command (unknown request)
    /// data = null
    /// data length = 0
    /// isRequest = false
    /// parity = 0
    /// Encrypted = no
    /// Application Port = Api_e
    /// </summary>
    /// <param name="destination"> destination of this packet as byte </param>
    /// <param name="sequenzNr"> Sequence number which will be transmitted. Should only be set manually in a response. </param>
    /// <param name="command"> Type of the Command see byte. used in the Message_t struct </param>
    public void EnqueuePacket(byte destination, byte command, byte sequenzNr)
    {
        EnqueuePacket(destination, new Message_t(command, (short)0), null, 0, false, Encrypt_t.No, (byte)0, ApplicationPort_t.Api_e, sequenzNr);
    }

    /// <summary>
    /// Used to send a command.
    /// isRequest = false
    /// sequenceNumber = last Number + 1
    /// parity = 0
    /// Encrypted = no
    /// Application Port = Api_e
    /// </summary>
    /// <param name="destination"> destination of this packet as byte </param>
    /// <param name="message"> data-header w/o data length see Message_t </param>
    /// <param name="data"> data as a byte array. The size of this array should equal dataLength </param>
    /// <param name="dataLength"> how many data bytes will be sent </param>
    public void EnqueuePacket(byte destination, Message_t message, byte[] data, int dataLength)
    {
        EnqueuePacket(destination, message, data, dataLength, false, Encrypt_t.No, (byte)0, ApplicationPort_t.Api_e, NextSequenceNumber());
    }

    /// <summary>
    /// Used to send a request.
    /// sequenceNumber = last Number + 1
    /// parity = 0
    /// Encrypted = no
    /// Application Port = Api_e
    /// </summary>
    /// <param name="destination"> destination of this packet as byte </param>
    /// <param name="message"> data-header w/o data length see Message_t </param>
    /// <param name="data"> data as a byte array. The size of this array should equal dataLength </param>
    /// <param name="dataLength"> how many data bytes will be sent </param>
    /// <param name="request"> true when this packet is a request, else false. </param>
    public void EnqueuePacket(byte destination, Message_t message, byte[] data, int dataLength, boolean request)
    {
        EnqueuePacket(destination, message, data, dataLength, request, Encrypt_t.No, (byte)0, ApplicationPort_t.Api_e, NextSequenceNumber());
    }

    /// <summary>
    /// Used to send a response.
    /// isRequest = false
    /// parity = 0
    /// Encrypted = no
    /// Application Port = Api_e
    /// </summary>
    /// <param name="destination"> destination of this packet as byte </param>
    /// <param name="message"> data-header w/o data length see Message_t </param>
    /// <param name="data"> data as a byte array. The size of this array should equal dataLength </param>
    /// <param name="dataLength"> how many data bytes will be sent </param>
    /// <param name="sequenzNr"> Sequence number which will be transmitted. Should only be set manually in a response. </param>
    public void EnqueuePacket(byte destination, Message_t message, byte[] data, int dataLength, byte sequenzNr)
    {
        EnqueuePacket(destination, message, data, dataLength, false, Encrypt_t.No, (byte)0, ApplicationPort_t.Api_e, sequenzNr);
    }

    /// <summary>
    /// Used to repeat a packet or to sent a custom packet.
    /// </summary>
    /// <param name="p"> Packet_t which will be sent </param>
    public void EnqueuePacket(Packet_t p)
    {
        boolean request;
        if (p.type == PacketType_t.Request_e)
            request = true;
        else
            request = false;

        EnqueuePacket(p.destination, p.message, p.data, p.dataLength, request, p.isEncrypted, p.parity, p.appPort, p.sequenceNumber);
    }

    /// <summary>
    /// Adds a packet to the send queue.
    /// </summary>
    /// <param name="destination"> destination of this packet as byte </param>
    /// <param name="message"> data-header w/o data length see Message_t </param>
    /// <param name="data"> data as a byte array. The size of this array should equal dataLength </param>
    /// <param name="dataLength"> how many data bytes will be sent </param>
    /// <param name="request"> true when this packet is a request, else false. </param>
    /// <param name="sequenzNr"> Sequence number which will be transmitted. Should only be set manually in a response. </param>
    /// <param name="appPort"> Application port which will be sent in the packet </param>
    /// <param name="encrypted"> Is the Packet Encrypted </param>
    /// <param name="parity"> Which parity does the packet have </param>
    public void EnqueuePacket(byte destination, Message_t message, byte[] data, int dataLength, boolean request, Encrypt_t encrypted, byte parity, ApplicationPort_t appPort, byte sequenzNr)
    {
        // Prepare a new packet
        Packet_t packet = new Packet_t();
        packet.parity = parity;
        packet.protoVersion = PROTOCOL_VERSION;
        packet.isEncrypted = encrypted;
        packet.appPort = appPort;
        packet.sequenceNumber = sequenzNr;
        packet.source = Address;
        packet.destination = destination;
        packet.message = message;
        packet.dataLength = dataLength;
        packet.data = data;
        if (request == true)
            packet.type = PacketType_t.Request_e;
        else
            packet.type = PacketType_t.Data_e;
        packet.isFail = false;

        packet.ToByteArray();
        _lastSentPacket = packet;
        OutgoingQueue.add(packet);
    }

    /// <summary>
    /// This method is an alternate receive option.
    /// </summary>
    /// <param name="data">raw data</param>
    /// <param name="length">length of the raw data</param>
    /// <returns>true = successful, false = error occured</returns>
    public boolean addRawData(byte[] data, int length)
    {
        boolean success = false;
        try
        {
            if ((rawDataBufferLength + length) < getMaxPacketSize())
            {
                ProcessRawData(data, length);
                success = true;
            }
        }
        catch (Exception e) { }
        
        return success;
    }

    /// <summary>
    /// Reverts the shifted characters.
    /// </summary>
    /// <param name="data">raw data</param>
    /// <param name="length">length of the raw data</param>
    /// <returns>data array without shifted characters</returns>
    public static byte[] RevertShiftedChars(byte[] data, int length)
    {
        int newLength = 0;
        byte[] newData = new byte[length];

        for (int i = 0; length > i; i++)
        {
            if (data[i] == (byte)SpecialChars_t.ShiftChar_e.getData())
            {
                i++;
                newData[newLength] = (byte)(data[i] ^ (byte)SpecialChars_t.ShiftXOR_e.getData());
                newLength++;
            }
            else
            {
                newData[newLength] = data[i];
                newLength++;
            }
        }
        
        //Array.Resize(ref newData, newLength);
        newData = Arrays.copyOfRange(newData, 0, newLength);		//hcpark

        return newData;
    }

    /// <summary>
    /// Aborts the thread and closes the port.
    /// </summary>
    public void Dispose()
    {
        if (TransmissionThread != null)
        {
            stopTransmissionThread = true;
            
            try
            {
            	TransmissionThread.join(200);
            } 
            catch (Exception e)
            {
            }
        }
        
        if (ApiPort != null)
        {
            ApiPort.closePort();
        }
    }

    //#region private methods
    /// <summary>
    /// Increments the sequence number and returns it.
    /// </summary>
    /// <returns>Next valid sequence number.</returns>
    private byte NextSequenceNumber()
    {
        return sequenceNumber++;
    }
    
    
    //#region transmission state machine
    /// <summary>
    /// States of the state-machine in TransmissionThreadWorker()
    /// </summary>
    private enum SendStates
    {
        Idle,
        Sending,
        WaitingForAck,
        WaitingForResponse
    }

    private SendStates ThreadState = SendStates.Idle;

    /// <summary>
    /// Reads data from the serial ports and makes packets from the data.
    /// Writes data to the serial port and manages the ack/nack.
    /// </summary>
    private void TransmissionThreadWorker()
    {
        LocalDateTime sent_timestamp = LocalDateTime.now();

        while (stopTransmissionThread == false)
        {
            try
            {
                ProccessRawPacket();
                IncomingDataQueueHandler();

                switch (ThreadState)
                {
                    //#region State Idle
                    case Idle:
                        if (OutgoingQueue.size() > 0)
                        {
                            ThreadState = SendStates.Sending;
                        }
                        break;

                    //#region State Sending
                    case Sending:
                        sent_timestamp = SendState(sent_timestamp);
                        break;

                    //#region State WaitingForAck
                    case WaitingForAck:
                        WaitingForAckState(sent_timestamp);
                        break;

                    //#region State WaitingForResponse
                    case WaitingForResponse:
                        WaitingForResponseState(sent_timestamp);
                        break;
                }
            }
            catch (Exception e)
            {
                break;
            }

            //save shutdown and portswitch
            if (PortIsIdle() && stopSerialPort)
            {
                ApiPort.closePort();
                _sendIsIdle = true;
                _receiveIsIdle = true;

                if (newSerialBaudrate != 0)
                {
                    //ApiPort.BaudRate = newSerialBaudrate;
                    _baudRate = newSerialBaudrate;
                    //ApiPort.PortName = newSerialName;
                    _portName = newSerialName;
                    
                    try
                    {
                    	initialize();
                        ApiPort = getSerialPort(_portName);
                        ApiPort.setBaudRate(_baudRate);
                    } catch (DeviceInitializeException e) {
                    	logger.error(e.toString());
                    }
                    
                    newSerialName = "";
                    newSerialBaudrate = 0;
                    Start();
                }
            }

            try {
				Thread.sleep(ThreadSleepTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }

    /// <summary>
    /// Checks if there is something to send and sends it.
    /// </summary>
    private LocalDateTime SendState(LocalDateTime sent_timestamp)
    {
        synchronized (OutgoingQueue)
        {
            if (OutgoingQueue.size() > 0 && _sendIsIdle && stopSerialPort == false)
            {
            	//hcpark
            	long milis = ChronoUnit.MILLIS.between(incomingTimeStamp, LocalDateTime.now());
            	
                if (requestOpen == true && milis > ResponseTimeout.toMillis())
                {
                    // if this is a response and ResponseTimeout was exceeded, then ignore the packet
                    OutgoingQueue.poll();
                    requestOpen = false;
                    ThreadState = SendStates.Idle;
                    return sent_timestamp;
                }

                if (checkForReset(OutgoingQueue.peek(), true))
                {
                    lastAckCommand = -1;
                    sequenceNumber = 0;
                }
                
                WriteToPort(OutgoingQueue.peek().ToByteArray());

                if (OutgoingQueue.peek().type == PacketType_t.Data_e || OutgoingQueue.peek().type == PacketType_t.Request_e)
                {
                    sent_timestamp = LocalDateTime.now();
                    if (requestOpen == true)
                    {
                        requestOpen = false;
                        ThreadState = SendStates.Idle;
                        callPacketSentHandler(OutgoingQueue.poll());
                    }
                    else
                        ThreadState = SendStates.WaitingForAck;
                    return sent_timestamp;
                }
                else
                    OutgoingQueue.poll();
            }
            ThreadState = SendStates.Idle;
        }

        return sent_timestamp;
    }

    /// <summary>
    /// Checks if an ack or a nack was received or if a ackTimeout occured.
    /// </summary>
    private void WaitingForAckState(LocalDateTime sent_timestamp)
    {
    	synchronized (OutgoingQueue)
        {
    		synchronized (IncomingAckQueue)
            {
                if (IncomingAckQueue.size() > 0)
                {
                    Packet_t packet = IncomingAckQueue.poll();
                    if (packet.sequenceNumber != OutgoingQueue.peek().sequenceNumber)
                    {
                        // sequence number doesn't equal the last commands sequence number
                    }
                    else
                    {

                        switch (packet.type)
                        {
                            case PosAck_e:
                                ackTimeoutCounter = 0;
                                nackCounter = 0;
                                if (OutgoingQueue.peek().type == PacketType_t.Request_e)
                                {
                                    callPacketSentHandler(OutgoingQueue.peek());
                                    ThreadState = SendStates.WaitingForResponse;
                                }
                                else
                                {
                                    callPacketSentHandler(OutgoingQueue.poll());
                                    ThreadState = SendStates.Idle;
                                }
                                break;

                            case NegAck_e:
                                ackTimeoutCounter = 0;
                                if (++nackCounter > MaxSendRepetition)
                                {
                                    nackCounter = 0;
                                    callNackFailHandler(OutgoingQueue.poll());
                                    ThreadState = SendStates.Idle;
                                }
                                else
                                    ThreadState = SendStates.Sending;
                                break;

                            default:
                                break;
                        }
                    }
                }

                // ack timeout?
                else if (ChronoUnit.MILLIS.between(sent_timestamp, LocalDateTime.now()) > AckTimeout.toMillis())
                {
                   	fireAckNackReceived(true);

                    if (++ackTimeoutCounter > MaxSendRepetition)
                    {
                        ackTimeoutCounter = 0;
                        nackCounter = 0;
                        callAckTimeoutHandler(OutgoingQueue.poll());
                        ThreadState = SendStates.Idle;
                    }
                    else
                        ThreadState = SendStates.Sending;
                }
            }
        }
    }

    /// <summary>
    /// Checks if a response was received
    /// </summary>
    private void WaitingForResponseState(LocalDateTime sent_timestamp)
    {
        if (responseReceived == true)
        {
            responseReceived = false;
            synchronized (OutgoingQueue)
            {
                OutgoingQueue.poll();
            }
            ThreadState = SendStates.Idle;
        }
        //else if (DateTime.Now.Subtract(sent_timestamp) > ResponseTimeout)
        else if (ChronoUnit.MILLIS.between(sent_timestamp, LocalDateTime.now()) > ResponseTimeout.toMillis())
        {
        	synchronized (OutgoingQueue)
            {
                callResponseTimeoutHandler(OutgoingQueue.poll());
            }
            ThreadState = SendStates.Idle;
        }
    }
    //#endregion
    
  /// <summary>
    /// This method takes the raw packets and processes them.
    /// Then it puts the packet in the DataQueue or in the AckQueue
    /// </summary>
    /// <param name="data">data from serial port</param>
    /// <param name="length">number of bytes of data</param>
    private void ProccessRawPacket()
    {
        if (IncomingRawQueue.size() > 0)
        {
            int index = 0;
            Packet_t packet = new Packet_t();
            packet.isFail = false;
            RawPacket_t rawPacket = new RawPacket_t(new byte[getMaxPacketSize()], getMaxPacketSize());
            synchronized (IncomingRawQueue)
            {
                rawPacket = IncomingRawQueue.poll();
            }
            
            // delete SOT and EOT
            //Array.Copy(rawPacket.data, 1, rawPacket.data, 0, rawPacket.length - 2);
            System.arraycopy(rawPacket.data, 1, rawPacket.data, 0, rawPacket.length - 2);
            rawPacket.length -= 2;

            // revert shifted characters
            byte[] data = RevertShiftedChars(rawPacket.data, rawPacket.length);
            int length = data.length;

            if (length < 5)
            {
                // length fail -> NACK
                packet.isFail = true;
            }

            // cast the rawPacket into a packet
            packet.parity = (byte)(data[0] >> 6);
            packet.protoVersion = (byte)(data[0] & 0x3F);
            packet.isEncrypted = Encrypt_t.fromInteger(data[1] >> 7);
            packet.appPort = ApplicationPort_t.fromInteger((data[1] >> 3) & 0xF);
            packet.type = PacketType_t.fromInteger(data[1] & 0x7);
            packet.sequenceNumber = data[2];
            packet.source = (byte)data[3];
            packet.destination = (byte)data[4];

            switch (packet.type)
            {
                case Data_e:
                case Request_e:
                    // cast command id and parameter
                    packet.message = new Message_t(
                        (byte)data[5],
                        (short)(data[6] | (data[7] << 8))
                        );

                    // cast data
                    packet.dataLength = (int)(data[8] | ((int)data[9] << 8));

                    if (packet.dataLength > 0 && length < getMaxPacketSize())
                    {
                        packet.data = new byte[packet.dataLength];
                        while (index < packet.dataLength)
                        {
                            packet.data[index] = data[HEADER_OFFSET + index];
                            index++;
                        }
                    }

                    // cast crc
                    packet.CRC.LowByte = data[HEADER_OFFSET + index];
                    packet.CRC.HighByte = data[HEADER_OFFSET + index + 1];

                    // check crc
                    if (CalculateCRC(data, length) != 0)
                    {
                        // CRC fail -> NACK
                        packet.isFail = true;
                    }

                    synchronized (IncomingDataQueue)
                    {
                        IncomingDataQueue.add(packet);
                    }

                    break;
                case PosAck_e:
                case NegAck_e:
                    fireAckNackReceived(packet);
                    
                    synchronized (IncomingAckQueue)
                    {
                        IncomingAckQueue.add(packet);
                    }
                    break;
			default:
				break;
            }
        }
    }
    
  /// <summary>
    /// Checks the GetStatus response for the justReset bit. If it is set
    /// the sequence number will be set to 0.
    /// </summary>
    /// <param name="p"></param>
    private boolean checkForReset(Packet_t p, boolean outgoing)
    {
        // commandID = GetStatus (0x01) and justReset bit is set
        if (p.message.command == 0x01 && p.type == PacketType_t.Data_e)
        {
            if ((p.data[0] & 0x01) == 0x01)
            {
                if (outgoing)
                {
                    return true;
                }
                else
                {
                    if (OutgoingQueue.peek().sequenceNumber == p.sequenceNumber)
                        return true;
                }
            }
        }
        return false;
    }

    /// <summary>
    /// Sends the ack/nack packet, checks if the Data_e packet was a response (no ack/nack needed)
    /// and calls the packetReceived callback
    /// </summary>
    private void IncomingDataQueueHandler()
    {
    	synchronized (IncomingDataQueue)
        {
            if (IncomingDataQueue.size() > 0)
            {
                if (IncomingDataQueue.peek().destination == this.Address)
                {
                    if (OutgoingQueue.size() == 0 || OutgoingQueue.peek().type == PacketType_t.Data_e)
                    {   // if there is no request in the queue
                        Packet_t ackPacket = new Packet_t();
                        ackPacket.parity = 0;
                        ackPacket.protoVersion = PROTOCOL_VERSION;
                        ackPacket.isEncrypted = Encrypt_t.No;		// 0
                        ackPacket.appPort = ApplicationPort_t.Api_e;
                        ackPacket.sequenceNumber = IncomingDataQueue.peek().sequenceNumber;
                        ackPacket.source = this.Address;
                        ackPacket.destination = IncomingDataQueue.peek().source;
                        ackPacket.dataLength = 0;
                        ackPacket.data = new byte[0];

                        //if (DateTime.Now.Subtract(incomingTimeStamp) < AckTimeout)
                        if (ChronoUnit.MILLIS.between(incomingTimeStamp, LocalDateTime.now()) > AckTimeout.toMillis())
                        {
                            if (IncomingDataQueue.peek().isFail == false)
                            {
                                ackPacket.type = PacketType_t.PosAck_e;
                                _sendIsIdle = false;
                                WriteToPort(ackPacket.ToByteArray());
                                _sendIsIdle = true;

                                if (IncomingDataQueue.peek().type == PacketType_t.Request_e)
                                    requestOpen = true;

                                // if the sequence Number of the last received packet is
                                // the same as this then throw the packet away
                                if (lastAckCommand != (int)IncomingDataQueue.peek().sequenceNumber)
                                    callPacketReceivedHandler(IncomingDataQueue.poll());
                                else
                                    IncomingDataQueue.poll();

                                lastAckCommand = (int)ackPacket.sequenceNumber;
                            }
                            else
                            {
                                ackPacket.type = PacketType_t.NegAck_e;
                                _sendIsIdle = false;
                                WriteToPort(ackPacket.ToByteArray());
                                _sendIsIdle = true;
                                IncomingDataQueue.poll();
                            }
                        }
                    }
                    else // the data packet is a response
                    {
                        if (checkForReset(IncomingDataQueue.peek(), false))
                        {
                            lastAckCommand = -1;
                            sequenceNumber = 0;
                        }

                        responseReceived = true;
                        callPacketReceivedHandler(IncomingDataQueue.poll());
                    }
                }
                else
                {
                    IncomingDataQueue.poll();
                }
            }
        }
    }

    /// <summary>
    /// Writes the packet to the serial port.
    /// </summary>
    /// <param name="data">data from ToByteArray()</param>
    private void WriteToPort(byte[] data)
    {
        _sendIsIdle = false;
        byte[] buffer = ProcessToSend(data);

        if (ReceiveOnlyMode == false)
        {
            if (ApiPort != null && ApiPort.isOpen())
            {
                ApiPort.writeBytes(buffer, buffer.length);
            }

            callDataSentInterceptHandler(buffer);
        }
        _sendIsIdle = true;
    }
    
    /// <summary>
    /// Makes the Data ready to be sent to the port.
    /// </summary>
    /// <param name="data">data from ToByteArray()</param>
    /// <returns>final byte array with SOH and EOT</returns>
    private static byte[] ProcessToSend(byte[] data)
    {
        int count = 0;
        byte[] buffer = new byte[data.length * 2 + 2]; // Length*2 + 2 = maximum possible length (every byte shifted)
        buffer[count++] = (byte)SpecialChars_t.SOH_e.getData();
        for (int i = 0; i < data.length; i++)
        {   // shift every special character
            if (IsSpecialChar(data[i]))
            {
                buffer[count++] = (byte)SpecialChars_t.ShiftChar_e.getData();
                buffer[count++] = (byte)(data[i] ^ (byte)SpecialChars_t.ShiftXOR_e.getData());
            }
            else
            {
                buffer[count++] = data[i];
            }
        }
        buffer[count++] = (byte)SpecialChars_t.EOT_e.getData();
        // Array.Resize(ref buffer, count);
        buffer = Arrays.copyOfRange(buffer,0,count);		//hcpark		
        return buffer;
    }
    
    public static int ArrayIndexOf(byte[] array, byte key) {
    	int returnvalue = -1;
        for (int i = 0; i < array.length; ++i) {
            if (key == array[i]) {
                returnvalue = i;
                break;
            }
        }
        return returnvalue;
    }
    
    /// <summary>
    /// This method searches for a SOH and it will save the data after it.
    /// Then it waits until it finds a EOT. If the MaxPacketSize was not exceeded,
    /// then it will save the data with the SOH and EOT as a rawPacket in the IncomingRawQueue.
    /// If the packet size grows over MaxPacketSize it will ignore the packet.
    /// If there is still data after the EOT it will recursively call itself until there is no data left.
    /// </summary>
    /// <param name="buffer">data as byte array</param>
    /// <param name="bytesRead">number of data bytes</param>
    private void ProcessRawData(byte[] buffer, int bytesRead)
    {
        int indexOfSoh = -1;
        int indexOfEot = -1;
        int bytesToProcess = bytesRead;

        // no SOH in the rawDataBuffer?
        if (rawDataBuffer[0] != (byte)SpecialChars_t.SOH_e.getData())
        {
            //indexOfSoh = Array.IndexOf(buffer, (byte)SpecialChars_t.SOH_e);
        	indexOfSoh = ArrayIndexOf(buffer, (byte)SpecialChars_t.SOH_e.getData());
            if (indexOfSoh >= 0)
            {
                //Array.Copy(buffer, indexOfSoh, buffer, 0, bytesRead - indexOfSoh);
            	System.arraycopy(buffer, indexOfSoh, buffer, 0, bytesRead - indexOfSoh);
                bytesRead = bytesToProcess = bytesRead - indexOfSoh;
            }
        }

        if (rawDataBuffer[0] == (byte)SpecialChars_t.SOH_e.getData() || indexOfSoh >= 0)
        {
            indexOfEot = ArrayIndexOf(buffer, (byte)SpecialChars_t.EOT_e.getData());
            if (indexOfEot >= 0)
            {
                bytesToProcess = indexOfEot + 1;
                // would MaxPacketSize be exceeded?
                if (bytesToProcess + rawDataBufferLength <= getMaxPacketSize())
                {
                    // create a RawPacket and add it to the IncomingRawQueue
                    RawPacket_t rawPacket = new RawPacket_t(new byte[bytesToProcess + rawDataBufferLength],
                                                            bytesToProcess + rawDataBufferLength);
                    System.arraycopy(rawDataBuffer, 0, rawPacket.data, 0, rawDataBufferLength);
                    System.arraycopy(buffer, 0, rawPacket.data, rawDataBufferLength, bytesToProcess);
                    
                    synchronized (IncomingRawQueue) {
                        if (rawPacket.length >= 10)
                            rawDataBufferLength = 0;
                        IncomingRawQueue.add(rawPacket);
                    }
                    
                    incomingTimeStamp = LocalDateTime.now();

                    // reset rawDataBuffer
                    rawDataBuffer = new byte[getMaxPacketSize()];
                    rawDataBufferLength = 0;
                }
                else
                {
                    // MaxPacketSize would be exceeded, then ignore the packet and reset rawDataBuffer
                    rawDataBuffer = new byte[getMaxPacketSize()];
                    rawDataBufferLength = 0;
                }

                if (bytesToProcess < bytesRead)
                {
                    // call this method recursively
                	System.arraycopy(buffer, bytesToProcess, buffer, 0, bytesRead - bytesToProcess);
                    //Array.Resize(ref buffer, bytesRead - bytesToProcess);
                	buffer = Arrays.copyOfRange(buffer, 0, bytesRead - bytesToProcess);		//hcpark
                    ProcessRawData(buffer, bytesRead - bytesToProcess);
                }

            }
            else
            {
                // would MaxPacketSize be exceeded?
                if (bytesRead + rawDataBufferLength <= getMaxPacketSize())
                {
                	System.arraycopy(buffer, 0, rawDataBuffer, rawDataBufferLength, bytesRead);
                    rawDataBufferLength += bytesRead;
                }
                else
                {
                    rawDataBuffer = new byte[getMaxPacketSize()];
                    rawDataBufferLength = 0;
                }
            }
        }
    }
    
    

    /// <summary>
    /// Checks for special character (shifted characters)
    /// </summary>
    /// <param name="c">to be checked character</param>
    /// <returns>true/false</returns>
    private static boolean IsSpecialChar(byte c)
    {
        return (boolean)(c <= (byte)SpecialChars_t.EOT_e.getData() ||
                        c == (byte)SpecialChars_t.ETB_e.getData() ||
                        c == (byte)SpecialChars_t.LF_e.getData() ||
                        c == (byte)SpecialChars_t.CR_e.getData() ||
                        c == (byte)SpecialChars_t.ShiftChar_e.getData());
    }
	
	/// <summary>
    /// calculates the crc value with the crcPolynomTable
    /// </summary>
    /// <param name="data_p">reference to a byte array. the CRC will be calculated on this array.</param>
    /// <param name="dataLength"> length of the array </param>
    /// <returns> crc value as 16bit value </returns>
    private static short CalculateCRC(byte[] data_p, int dataLength)
    {
        short checksum;

        checksum = CRC_INIT;

        for (int nIndex = 0; nIndex < dataLength; nIndex++)
        {
        	// hcpark : short ?
            checksum = (short)((checksum >> 8) ^ (short)crcPolynomTable[(checksum ^ data_p[nIndex]) & 0xFF]);
        }

        return checksum;
    }
	
	//================================================================================

	
	private static Logger logger = LoggerFactory.getLogger(EversysSerialComm.class);
	
	private static final int SERIAL_COMM_ERROR = -1;
	private static final int DEFAULT_COMM_READ_TIMEOUT = 5000;
	private static final int DEFAULT_COMM_WRITE_TIMEOUT = 5000;
	private static final int DEFAULT_BAUD_RATE = 115200;
	
	
	private Map<String, SerialPort> comPorts = new ConcurrentHashMap<>();
	
	private Map<String, SerialConfig> serials = new ConcurrentHashMap<>();
	
	private Object _lock = new Object();
	
	private static EversysSerialComm _instance = new EversysSerialComm();
	
	public static EversysSerialComm getInstance() {
		return _instance;
	}
	
	/**
	 * @throws DeviceInitializeException
	 * @throws SQLException 
	 * @throws IntrospectionException 
	 * @throws InstantiationException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	private void initialize() throws DeviceInitializeException {
		
		synchronized(_lock) {
			
			SerialPort[] serialPorts = SerialPort.getCommPorts();
			
			if(serialPorts == null || serialPorts.length == 0) {
				throw new DeviceInitializeException("error occurred during serial device initialization");
			}
			
			comPorts.clear();
			
			for(SerialPort serialPort : serialPorts) {
				if(serialPort.isOpen()) {
					serialPort.closePort();
				}
				
				comPorts.put(serialPort.getSystemPortName(), serialPort);
			}
		}
	}
	
	
	/**
	 * @param name
	 * @return SerialPort
	 */
	public SerialPort getSerialPort(String name) {
		synchronized(_lock) {
			return comPorts.get(name);
		}
	}
	
	
}
