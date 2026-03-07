import com.example.modbus.master.SerialPortNettyBridge;
import com.example.modbus.master.codec.ModbusRtuEncoder;
import com.example.modbus.master.codec.ModbusRtuResponseDecoder;
import com.example.modbus.master.handler.MasterHandler;
import com.example.modbus.slave.codec.ModbusRtuRequestDecoder;
import com.example.modbus.slave.handler.SlaveHandler;
import io.netty.channel.embedded.EmbeddedChannel;

public class SerialIntegrationCheck {
    public static void main(String[] args) throws Exception {
        String masterPort = args.length > 0 ? args[0] : "CNCA0";
        String slavePort = args.length > 1 ? args[1] : "CNCB0";
        int baud = args.length > 2 ? Integer.parseInt(args[2]) : 9600;

        EmbeddedChannel slaveChannel = new EmbeddedChannel(
                new ModbusRtuRequestDecoder(),
                new com.example.modbus.slave.codec.ModbusRtuEncoder(),
                new SlaveHandler()
        );
        MasterHandler masterHandler = new MasterHandler();
        EmbeddedChannel masterChannel = new EmbeddedChannel(
                new ModbusRtuResponseDecoder(),
                new ModbusRtuEncoder(),
                masterHandler
        );

        try (com.example.modbus.slave.SerialPortNettyBridge slave = new com.example.modbus.slave.SerialPortNettyBridge(slavePort, baud, slaveChannel);
             SerialPortNettyBridge master = new SerialPortNettyBridge(masterPort, baud, masterChannel)) {
            slave.start();
            Thread.sleep(1000);
            master.start();
            Thread.sleep(7000);
            masterHandler.shutdown();
            Thread.sleep(500);
            master.close();
            Thread.sleep(300);
            slave.close();
        }
    }
}