package com.dalkomm.beat.booth.manager.comm.serial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dalkomm.beat.booth.manager.boot.BoothManagerBootStrap;
import com.dalkomm.beat.booth.manager.comm.serial.EversysSerialComm.Packet_t;

/**
 * @author Park, Hyun-Cheol
 * @since 2018. 11. 28.
 * @version 1.0
 */

public class EversysManager implements PacketEventListener {
	
	private static Logger logger = LoggerFactory.getLogger(BoothManagerBootStrap.class);
	
	EversysSerialComm mSerialComm = null;
	
	public EversysManager() {}
	
	public void init() {
		 try {
             mSerialComm = EversysSerialComm.getInstance();
             mSerialComm.SerialComm("COM1", 115200, (byte)0x41);
             mSerialComm.Start();
             
             Send(Command_t.GetApiVersion_e, (short)0, null);
             
             Thread.sleep(1000 * 120);
             
         } catch(Exception exx) {
         	logger.info(exx.toString());
         }
		
	}
	
	public EversysSerialComm.Packet_t Send(Command_t cmd, short cmdParam, byte[] data)
    {
		return this.Send(mSerialComm. new Message_t(cmd.getData(), cmdParam), data, false);
    }

    public EversysSerialComm.Packet_t SendAndGetResponse(Command_t cmd, short cmdParam, byte[] data)
    {
    	return this.Send(mSerialComm. new Message_t((byte) cmd.getData(), cmdParam), data, true);
    }
    
    private EversysSerialComm.Packet_t Send(EversysSerialComm.Message_t message, byte[] data, boolean expectResponse)
    {
    	EversysSerialComm.Packet_t _packet = mSerialComm. new Packet_t();
    	
    	int dataLength;
        if (data == null)
        {
          data = new byte[2];
          dataLength = 0;
        }
        else
          dataLength = data.length;
        
        if (!expectResponse)
        	mSerialComm.EnqueuePacket((byte) 65, message, data, dataLength);
        else
        	mSerialComm.EnqueuePacket((byte) 65, message, data, dataLength, true);
    	
    	return _packet;
    }

	@Override
	public void PacketEventFired(Object sender, PacketEventArgs eventArgs) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}

	@Override
	public void packetSent(Packet_t packet) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}

	@Override
	public void packetReceived(Packet_t packet) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}

	@Override
	public void ackTimeout(Packet_t packet) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}

	@Override
	public void nackFail(Packet_t packet) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}

	@Override
	public void responseTimeout(Packet_t packet) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}

	@Override
	public void dataSentIntercept(byte[] data) {
		// TODO Auto-generated method stub
		int i = 0;
		i++;
	}
	
	//===========================================
	
	public enum Command_t
    {
        GetApiVersion_e( "GetApiVersion_e", (byte)0 ),
        GetStatus_e( "GetStatus_e", (byte)1 ),
        DoProduct_e( "DoProduct_e", (byte)2 ),
        DoRinse_e( "DoRinse_e", (byte)3 ),
        StartCleaning_e( "StartCleaning_e", (byte)4 ),
        Reserved1_e( "Reserved1_e", (byte)5 ),
        Reserved2_e( "Reserved2_e", (byte)6 ),
        Reserved3_e( "Reserved3_e", (byte)7 ),
        ScreenRinse_e( "ScreenRinse_e", (byte)8 ),
        Reserved4_e( "Reserved4_e", (byte)9 ),
        Reserved5_e( "Reserved5_e", (byte)10 ),
        
        Stop_e( "Stop_e", (byte)11 ),
        
        GetRequests_e( "GetRequests_e", (byte)12 ),
        GetInfoMessages_e( "GetInfoMessages_e", (byte)13 ),
        MilkOutletRinse_e( "MilkOutletRinse_e", (byte)14 ),
        DisplayAction_e( "DisplayAction_e", (byte)15 ),
        GetProductDump_e( "GetProductDump_e", (byte)16 ),
        GetSensorValues_e( "GetSensorValues_e", (byte)17 ),
        DoEtcCalibration_e( "DoEtcCalibration_e", (byte)18 ),
        DoProductOfDisplay_e( "DoProductOfDisplay_e", (byte)19 ),
        GetProductParameters_e( "GetProductParameters_e", (byte)20 ),
        
        GetMachineCounters_e( "GetMachineCounters_e", (byte)21 ),
        BlockedScreenMode_e( "BlockedScreenMode_e", (byte)22 ),
        GetBlockedScreenState_e( "GetBlockedScreenState_e", (byte)23 ),
        QRCodeMode_e( "QRCodeMode_e", (byte)24 ),
        GetQRCodeState_e( "GetQRCodeState_e", (byte)25 ),
        Undef_e( "Undef_e", (byte)0xFF );
        
        private final String name;
	    private final byte data;

	    private Command_t(String name, byte data)
	    {
	        this.name = name;
	        this.data = data;
	    }
	    
	    public byte getData() {
	    	return this.data;
	    }
	    
    }
}

